package com.lirui.charchat.data.remote

import com.lirui.charchat.data.remote.model.ChatRequest
import com.lirui.charchat.data.remote.model.SseChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容 /chat/completions 客户端（流式 + 非流式自适应）。
 *
 * 背景：不同网关对 stream=true 的处理不一致——
 * - OpenAI/vLLM/llama.cpp 等：返回 content-type: text/event-stream，逐 token SSE。
 * - 部分网关（如小米 MiMo api.xiaomimimo.com）：忽略流式，直接返回 application/json 完整回复。
 *
 * 兼容策略：请求仍带 stream=true；按响应的 Content-Type 分流——
 *   text/event-stream → 逐行解析 SSE 增量并逐 token 发射；
 *   其他（application/json）→ 一次性解析完整回复后整体发射。
 * 保证官方与"假流式"网关都能跑通。
 */
class OpenAIChatClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient
) : ChatApi {

    private val json = Json { ignoreUnknownKeys = true }

    override fun stream(req: ChatRequest): Flow<String> = flow {
        val mediaType = "application/json".toMediaType()
        val body = json.encodeToString(ChatRequest.serializer(), req).toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)          // 兼容只认 api-key 头的网关（如 MiMo /models）
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .post(body)
            .build()

        val response = withContext(Dispatchers.IO) { http.newCall(request).execute() }
        try {
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty().take(300)
                throw ApiException("HTTP ${response.code} ${response.message} $errBody")
            }
            val contentType = response.header("Content-Type") ?: ""
            if (contentType.contains("text/event-stream")) {
                // 真流式：逐行读 SSE
                val source = response.body!!.source()
                withContext(Dispatchers.IO) {
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val t = line.trim()
                        if (!t.startsWith("data:")) continue   // 忽略注释/event:/id:/retry/空行
                        val payload = t.removePrefix("data:").trim()
                        if (payload == "[DONE]") break
                        parseContentChunk(payload)?.let { emit(it) }
                    }
                }
            } else {
                // 假流式：网关直接返回完整 JSON，按非流式解析整体发射
                val text = withContext(Dispatchers.IO) { response.body?.string().orEmpty() }
                val content = parseNonStream(text)
                    ?: throw ApiException("非流式响应解析失败（content-type=$contentType）")
                emit(content)
            }
        } finally {
            response.close()
        }
    }

    /** 非流式 ChatCompletion JSON：choices[0].message.content。 */
    private fun parseNonStream(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject
            ?.get("content")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    companion object {
        /**
         * 纯函数：从一条 SSE `data:` 载荷中提取模型增量文本。
         * 返回 null 表示该行不是有效内容分片（如 [DONE]、注释、心跳、usage 行）。
         * 抽成纯函数以便单元测试，无需真实网络。
         */
        fun parseContentChunk(data: String): String? {
            if (data == "[DONE]") return null
            val chunk = Json { ignoreUnknownKeys = true }.decodeFromString(SseChunk.serializer(), data)
            return chunk.choices.firstNotNullOfOrNull { c ->
                c.delta?.content?.takeIf { it.isNotEmpty() }
            }
        }
    }
}
