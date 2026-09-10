package com.lirui.charchat.data.remote

import com.lirui.charchat.data.remote.model.TranscriptionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * OpenAI 兼容语音接口客户端（非流式）。
 * - TTS：POST /audio/speech → 直接返回音频二进制（mp3/wav）。
 * - ASR：POST /audio/transcriptions（multipart）→ JSON { text }。
 *
 * 注意：阿里云百炼的 compatible-mode 不提供 audio 系列路径（实测 404），
 * 需要语音时请填一个真正支持该路径的网关（如硅基流动 / 火山 / OpenAI 官方）。
 */
class OpenAISpeechClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 文本转语音。
     * @param voice 音色 ID，如 alloy / Cherry / zh-CN-XiaoxiaoNeural；留空由服务端决定。
     */
    suspend fun synthesize(
        text: String,
        model: String,
        voice: String = "",
        format: String = "mp3"
    ): ByteArray {
        val payload = buildJsonObject {
            put("model", model)
            put("input", text)
            if (voice.isNotBlank()) put("voice", voice)
            put("response_format", format)
        }.toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)          // 兼容只认 api-key 头的网关
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string()?.take(200) ?: response.message
            throw ApiException("语音合成失败 HTTP ${response.code} ${response.message}：$err")
        }
        val bytes = response.body?.bytes() ?: throw ApiException("语音合成返回空响应")
        if (bytes.isEmpty()) throw ApiException("语音合成返回空音频（检查模型是否支持出声）")
        return bytes
    }

    /** 语音转文字：把录音文件交给识别模型，返回识别文本。 */
    suspend fun transcribe(file: File, model: String): String {
        val mediaType = when (file.extension.lowercase()) {
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            else -> "audio/mpeg"
        }.toMediaType()

        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .addFormDataPart("model", model)
            .build()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)
            .post(body)
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string()?.take(200) ?: response.message
            throw ApiException("语音识别失败 HTTP ${response.code} ${response.message}：$err")
        }
        val raw = response.body?.string() ?: throw ApiException("语音识别返回空响应")
        return json.decodeFromString(TranscriptionResponse.serializer(), raw).text.trim()
    }
}
