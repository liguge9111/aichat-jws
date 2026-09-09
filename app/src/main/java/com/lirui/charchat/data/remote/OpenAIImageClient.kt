package com.lirui.charchat.data.remote

import com.lirui.charchat.data.remote.model.ImageRequest
import com.lirui.charchat.data.remote.model.ImageResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容 /images/generations 客户端（非流式）。
 * 返回 b64_json 或 url，由上层保存为本地文件后插入对话。
 */
class OpenAIImageClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(req: ImageRequest): ImageResponse {
        val mediaType = "application/json".toMediaType()
        val body = json.encodeToString(ImageRequest.serializer(), req).toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/images/generations")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)          // 兼容只认 api-key 头的网关
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string()?.take(200) ?: response.message
            throw ApiException("图像生成失败 HTTP ${response.code} ${response.message}：$err")
        }
        val raw = response.body?.string() ?: throw ApiException("图像生成返回空响应")
        return json.decodeFromString(ImageResponse.serializer(), raw)
    }
}
