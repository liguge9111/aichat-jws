package com.lirui.charchat.domain.repository

import com.lirui.charchat.BuildConfig
import com.lirui.charchat.data.remote.ApiError
import com.lirui.charchat.data.remote.ApiException
import com.lirui.charchat.data.remote.ApiKey
import com.lirui.charchat.data.remote.DashScopeImageClient
import com.lirui.charchat.data.remote.OpenAIChatClient
import com.lirui.charchat.data.remote.OpenAIImageClient
import com.lirui.charchat.data.remote.model.ChatMsg
import com.lirui.charchat.data.remote.model.ChatRequest
import com.lirui.charchat.data.remote.model.ImageRequest
import com.lirui.charchat.data.remote.model.ImageResponse
import com.lirui.charchat.data.remote.model.ModelsListResponse
import com.lirui.charchat.data.settings.ApiConfig
import com.lirui.charchat.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** 图像侧探测结果：全量模型、其中疑似出图的、以及当前配置的模型名。 */
data class ImageProbe(
    val models: List<String> = emptyList(),
    val imageModels: List<String> = emptyList(),
    val configured: String = ""
)

/** 出图模型名特征：覆盖主流与国产网关的常见命名。 */
private val IMAGE_MODEL_HINT = Regex(
    "(?i)image|dall-e|dalle|flux|stable-?diffusion|midjourney|seedream|kolors|wanx|recraft|ideogram|sd-?xl|sd3|jimeng|hunyuan-image"
)
private fun isImageModel(id: String): Boolean = IMAGE_MODEL_HINT.containsMatchIn(id)

private const val TEST_IMAGE_PROMPT = "a single red apple on a plain white background, simple, centered"

/**
 * 对话域仓库：把 BYOK 配置（来自 SettingsRepository）接进网络客户端。
 * - stream / generateImage 直接读当前配置，支持设置页热切换后即时生效。
 * - testConnection 用 GET /models 做连通冒烟（不消耗额度），端点不支持时降级为尝试一次极短对话。
 */
class ChatRepository(
    private val http: OkHttpClient,
    private val settings: SettingsRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 对话侧鉴权头：同时给 Authorization 与 api-key，兼容只用其中一种的网关（如 MiMo）。 */
    private fun Request.Builder.auth(key: String): Request.Builder =
        addHeader("Authorization", "Bearer $key").addHeader("api-key", key)

    /** 流式对话：逐 token 发射模型原文。 */
    fun stream(messages: List<ChatMsg>, temperature: Double = 0.9): Flow<String> {
        val cfg = settings.config.value
        val key = ApiKey.normalize(cfg.chatApiKey)
        if (key.isBlank()) throw ApiException("对话 API Key 未配置，请先到设置页填写")
        val client = OpenAIChatClient(cfg.chatBaseUrl, key, http)
        return client.stream(
            ChatRequest(
                model = cfg.chatModel.ifBlank { "gpt-4o" },
                messages = messages,
                temperature = temperature
            )
        )
    }

    /**
     * 图像生成：返回首个图像的 b64 或 url。
     * 配置兜底：图像侧字段留空时回落到对话侧配置（很多网关同时提供两个接口），
     * 这样只填了对话配置也能出图，不会静默失败。
     */
    suspend fun generateImage(prompt: String, size: String = "1024x1024"): ImageResponse =
        // 网络必须切 IO：send 链路经 flow{} 默认跑在调用方（主线程），
        // 不切会抛 NetworkOnMainThreadException，请求在 DNS 阶段就被掐断、表现为"角色说发了但没图"。
        withContext(Dispatchers.IO) {
            val cfg = settings.config.value
            val baseUrl = cfg.imageBaseUrl.ifBlank { cfg.chatBaseUrl }
            val apiKey = ApiKey.normalize(cfg.imageApiKey.ifBlank { cfg.chatApiKey })
            var model = cfg.imageModel.ifBlank { "dall-e-3" }
            if (apiKey.isBlank()) throw ApiException("图像 API Key 未配置，请到设置页填写（也可留空图像配置以复用对话 Key）")
            if (baseUrl.isBlank()) throw ApiException("图像 Base URL 未配置")
            // 协议路由：阿里云百炼没有 OpenAI 兼容 /images/generations（404），需走原生异步任务协议；
            // 其它 OpenAI 兼容网关走通用 images/generations。
            val hostUrl = runCatching { baseUrl.trimEnd('/').toHttpUrl() }.getOrNull()
            if (hostUrl != null && isDashScopeHost(hostUrl.host)) {
                val origin = "${hostUrl.scheme}://${hostUrl.host}"
                val client = DashScopeImageClient(origin, apiKey, http)
                // 文本模型名（qwen-plus/qwen-turbo 等）与占位 dall-e-3 会被百炼以 "url error" 拒绝：
                // 先按配置试一次，若命中该错误再用官方稳定模型兜底重试，避免玩家被模型名卡住。
                try {
                    client.generate(model = model, prompt = prompt, size = size)
                } catch (e: ApiException) {
                    if (model != DASHSCOPE_FALLBACK_MODEL && e.isInvalidModelName()) {
                        client.generate(model = DASHSCOPE_FALLBACK_MODEL, prompt = prompt, size = size)
                    } else throw e
                }
            } else {
                OpenAIImageClient(baseUrl, apiKey, http).generate(
                    ImageRequest(model = model, prompt = prompt, size = size)
                )
            }
        }

    /** 是否为阿里云百炼域名（国内 dashscope.aliyuncs.com / 国际 dashscope-intl.aliyuncs.com）。 */
    private fun isDashScopeHost(host: String): Boolean {
        val h = host.lowercase()
        return h == "dashscope.aliyuncs.com" || h.endsWith(".dashscope.aliyuncs.com") ||
            h == "dashscope-intl.aliyuncs.com" || h.endsWith(".dashscope-intl.aliyuncs.com")
    }

    /** 阿里云百炼 text2image 主域官方稳定文生图模型（多数百炼账号自带试用额度）。 */
    private companion object {
        const val DASHSCOPE_FALLBACK_MODEL = "wanx2.1-t2i-turbo"
    }

    /** 判断是否为"模型名不是出图模型"类错误（服务端 InvalidParameter url error）。 */
    private fun ApiException.isInvalidModelName(): Boolean {
        val m = message.orEmpty()
        return m.contains("url error", ignoreCase = true) || m.contains("InvalidParameter", ignoreCase = true)
    }

    /**
     * 连通冒烟：尝试 GET {baseUrl}/models。
     * 成功解析出模型列表则展示数量；端点不支持 /models（如部分代理）则降级尝试一次极短 echo 对话。
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val cfg = settings.config.value
        val key = ApiKey.normalize(cfg.chatApiKey)
        if (key.isBlank()) return@withContext Result.failure(ApiException("未配置对话 API Key"))
        if (cfg.chatBaseUrl.isBlank()) return@withContext Result.failure(ApiException("未配置对话 Base URL"))

        // Key 类型与 Base URL 不匹配时可提前定性（如 MiMo 的 sk-/tp- 混用）
        val hint = ApiKey.mismatchHint(key, cfg.chatBaseUrl)?.let { "｜$it" } ?: ""

        // 1) 优先探测 GET /models
        runCatching {
            val base = cfg.chatBaseUrl.trim().trimEnd('/').toHttpUrl()
            val modelsUrl = base.newBuilder().addPathSegment("models").build()
            http.newCall(
                Request.Builder().url(modelsUrl).auth(key).get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val detail = ApiError.describe(body)
                    diag("/models -> HTTP ${resp.code}｜$detail")
                    throw ApiException("HTTP ${resp.code} ${resp.message}" +
                        (if (detail.isNotBlank()) "｜服务端：$detail" else "") + hint)
                }
                val raw = resp.body?.string().orEmpty()
                val names = runCatching {
                    json.decodeFromString(ModelsListResponse.serializer(), raw).data.map { it.id }
                }.getOrDefault(emptyList())
                if (names.isNotEmpty()) {
                    "连通成功，端点可用模型 ${names.size} 个（如 ${names.first()}）"
                } else {
                    "连通成功（端点未返回 /models 列表，可能不支持该探测接口）"
                }
            }
        }.recoverCatching { modelsErr ->
            // 2) 端点不支持 /models，降级用一次极短 echo 对话验证
            tryEcho(cfg).getOrElse { throw modelsErr }
        }
    }

    /** 图像侧配置解析：留空时回落对话侧（与 generateImage 保持一致）。 */
    private fun imageCreds(): Triple<String, String, String> {
        val cfg = settings.config.value
        return Triple(
            cfg.imageBaseUrl.ifBlank { cfg.chatBaseUrl }.trim(),
            ApiKey.normalize(cfg.imageApiKey.ifBlank { cfg.chatApiKey }),
            cfg.imageModel.ifBlank { "dall-e-3" }
        )
    }

    /** GET /models 取模型 id 列表；失败抛带服务端原文的 ApiException。 */
    private fun fetchModels(baseUrl: String, key: String): List<String> {
        val base = baseUrl.trimEnd('/').toHttpUrl()
        val url = base.newBuilder().addPathSegment("models").build()
        http.newCall(Request.Builder().url(url).auth(key).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val detail = ApiError.describe(resp.body?.string().orEmpty())
                diag("/models -> HTTP ${resp.code}｜$detail")
                throw ApiException("HTTP ${resp.code} ${resp.message}" +
                    (if (detail.isNotBlank()) "｜服务端：$detail" else ""))
            }
            val raw = resp.body?.string().orEmpty()
            return runCatching {
                json.decodeFromString(ModelsListResponse.serializer(), raw).data.map { it.id }
            }.getOrDefault(emptyList())
        }
    }

    /**
     * 图像侧冒烟（不消耗额度）：探测 /models 并挑出出图模型。
     * 列得出出图模型即判定可用；列不出会如实告知——很多网关（如 MiMo）只提供文本/语音，
     * 这种情况再怎么试也出不了图，早说比让用户反复撞墙强。
     */
    suspend fun testImageConnection(): Result<ImageProbe> = withContext(Dispatchers.IO) {
        val (baseUrl, key, model) = imageCreds()
        if (key.isBlank()) return@withContext Result.failure(
            ApiException("未配置图像 API Key（留空会复用对话 Key，请确认对话侧已填写）")
        )
        if (baseUrl.isBlank()) return@withContext Result.failure(ApiException("未配置图像 Base URL"))
        runCatching {
            val models = fetchModels(baseUrl, key)
            val imageModels = models.filter { isImageModel(it) }
            diag("image probe: total=${models.size} image=${imageModels.size}")
            ImageProbe(models = models, imageModels = imageModels, configured = model)
        }
    }

    /** 真实出图一次（消耗额度），作为链路可通的最终确认。 */
    suspend fun testImageGeneration(): Result<String> = withContext(Dispatchers.IO) {
        val (_, _, model) = imageCreds()
        runCatching {
            val resp = generateImage(TEST_IMAGE_PROMPT, "1024x1024")
            val hasImage = resp.data.firstOrNull()?.let { !it.b64Json.isNullOrBlank() || !it.url.isNullOrBlank() }
            if (hasImage == true) "出图成功（模型 $model），照片功能可用"
            else "端点返回成功但没有图像数据，换一个出图模型再试"
        }
    }

    /** 诊断日志：仅调试构建输出，方便连电脑抓 logcat 定位（不记录 Key 本身）。 */
    private fun diag(msg: String) {
        if (BuildConfig.DEBUG) android.util.Log.d("CharChatNet", msg)
    }

    private fun tryEcho(cfg: ApiConfig): Result<String> {
        return runCatching {
            val key = ApiKey.normalize(cfg.chatApiKey)
            val client = OpenAIChatClient(cfg.chatBaseUrl, key, http)
            var got = false
            runBlocking {
                client.stream(
                    ChatRequest(
                        model = cfg.chatModel.ifBlank { "gpt-4o" },
                        messages = listOf(ChatMsg("user", "ping")),
                        temperature = 0.0,
                        maxTokens = 8
                    )
                ).collect { got = true }
            }
            if (got) "连通成功（已用一次极短对话验证，端点无 /models 接口）"
            else "端点返回空响应，可能 Key 无效或模型不可用"
        }
    }
}
