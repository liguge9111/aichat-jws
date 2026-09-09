package com.lirui.charchat.data.remote

import com.lirui.charchat.data.remote.model.ImageData
import com.lirui.charchat.data.remote.model.ImageResponse
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 阿里云百炼（DashScope）原生文生图适配。
 *
 * 背景：百炼的 OpenAI 兼容端点只提供 chat/embeddings，没有 /images/generations（实测 404），
 * 但 App 的通用图片客户端只认 OpenAI 兼容格式，导致配百炼 Base URL 时"角色说发了却不出图"。
 * 百炼出图走异步任务协议：
 *   1) POST {host}/api/v1/services/aigc/text2image/image-synthesis  建任务 → output.task_id
 *   2) GET  {host}/api/v1/tasks/{task_id}                           轮询 → SUCCEEDED 后 output.results[].url
 * 图片 URL 有效期 24h，由上层直接入库展示（不做本地持久化）。
 */
class DashScopeImageClient(
    private val host: String,      // 仅 scheme+host，如 https://dashscope.aliyuncs.com
    private val apiKey: String,
    private val http: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val submitUrl = "$host/api/v1/services/aigc/text2image/image-synthesis"
    private val mediaType = "application/json".toMediaType()

    /** 文生图最长等待（百炼文档：十几秒到几分钟不等）。 */
    private val maxWaitMs = 120_000L
    private val pollIntervalMs = 3_000L

    suspend fun generate(prompt: String, model: String, size: String = "1024*1024"): ImageResponse {
        val taskId = submit(prompt, model, normalizeSize(size))
        return pollUntilDone(taskId)
    }

    /** 1) 提交任务：返回 task_id。 */
    private suspend fun submit(prompt: String, model: String, size: String): String {
        val body = json.encodeToString(
            SubmitRequest.serializer(),
            SubmitRequest(model = model, input = SubmitInput(prompt), parameters = SubmitParameters(size = size, n = 1))
        )
        val request = Request.Builder()
            .url(submitUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("X-DashScope-Async", "enable")   // 缺失会报 "does not support synchronous calls"
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(mediaType))
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw httpError("创建图像任务失败", resp)
            }
            val raw = resp.body?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString(SubmitResponse.serializer(), raw) }.getOrNull()
            val taskId = parsed?.output?.taskId
                ?: throw ApiException("创建图像任务失败：响应缺少 task_id（${raw.take(120)}）")
            return taskId
        }
    }

    /** 2) 轮询任务直到 SUCCEEDED / FAILED / 超时。 */
    private suspend fun pollUntilDone(taskId: String): ImageResponse {
        val deadline = System.currentTimeMillis() + maxWaitMs
        var lastStatus = "PENDING"
        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            val request = Request.Builder()
                .url("$host/api/v1/tasks/$taskId")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw httpError("查询图像任务失败", resp)
                val poll = json.decodeFromString(PollResponse.serializer(), resp.body?.string().orEmpty())
                val status = poll.output?.taskStatus?.uppercase() ?: "UNKNOWN"
                lastStatus = status
                when (status) {
                    "SUCCEEDED" -> {
                        val urls = poll.output?.results.orEmpty().mapNotNull { it.url }
                            .filter { it.isNotBlank() }
                        if (urls.isEmpty()) {
                            throw ApiException("图像任务成功但没有返回图片 URL（output.results 为空）")
                        }
                        return ImageResponse(data = urls.map { ImageData(url = it) })
                    }
                    "FAILED" -> {
                        val reason = poll.output?.message?.takeIf { it.isNotBlank() }
                            ?: poll.output?.code?.takeIf { it.isNotBlank() }
                            ?: "未知原因"
                        throw ApiException("图像生成失败：$reason")
                    }
                    // PENDING / RUNNING / 其它：继续等
                    else -> Unit
                }
            }
        }
        throw ApiException("图像生成超时（${maxWaitMs / 1000}s），任务状态停留在 $lastStatus，请重试")
    }

    /** 把 HTTP 错误的响应体原文透出（百炼错误形如 {"code":..,"message":..}），方便玩家据此调整配置。 */
    private fun httpError(action: String, resp: okhttp3.Response): ApiException {
        val body = resp.body?.string().orEmpty()
        val detail = body.take(200).ifBlank { resp.message }
        return ApiException("$action HTTP ${resp.code}：$detail")
    }

    /** 百炼 size 用星号分隔（1024*1024），兼容 OpenAI 风格 x 分隔的入参。 */
    private fun normalizeSize(size: String): String = size.trim().replace('x', '*').replace('X', '*')

    @Serializable
    private data class SubmitRequest(val model: String, val input: SubmitInput, val parameters: SubmitParameters)

    @Serializable
    private data class SubmitInput(val prompt: String)

    @Serializable
    private data class SubmitParameters(val size: String, val n: Int)

    @Serializable
    private data class SubmitResponse(val output: SubmitOutput? = null)

    @Serializable
    private data class SubmitOutput(@SerialName("task_id") val taskId: String? = null)

    @Serializable
    private data class PollResponse(val output: PollOutput? = null)

    @Serializable
    private data class PollOutput(
        @SerialName("task_status") val taskStatus: String? = null,
        val results: List<PollResult>? = null,
        val code: String? = null,
        val message: String? = null
    )

    @Serializable
    private data class PollResult(val url: String? = null)
}
