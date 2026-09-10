package com.lirui.charchat.data.remote

import android.util.Base64
import com.lirui.charchat.data.remote.model.ChatSpeechResponse
import com.lirui.charchat.data.remote.model.TranscriptionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URI

/** 合成结果：音频字节 + 落盘扩展名（不同网关返回的容器格式不同）。 */
data class SynthesizedAudio(
    val bytes: ByteArray,
    val extension: String
) {
    // ByteArray 是引用类型，data class 需要手写 equals/hashCode
    override fun equals(other: Any?): Boolean =
        this === other || (other is SynthesizedAudio && extension == other.extension && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + extension.hashCode()
}

/**
 * 语音客户端，按 Base URL 的域名自动选择协议：
 *
 * **A. 小米 MiMo 原生协议**（`*.xiaomimimo.com`）
 * 语音不走 OpenAI 的 audio 系列路径，而是复用 `POST /v1/chat/completions`：
 * - ASR：`messages[user].content = [{type: input_audio, input_audio: {data: "data:audio/wav;base64,…"}}]`
 *   → 识别文本在 `choices[0].message.content`；只收 **wav / mp3**，base64 后 ≤ 10MB。
 * - TTS：目标文本放 `messages[assistant].content`，音色/格式放 `audio: {voice, format}`
 *   → 音频 base64 在 `choices[0].message.audio.data`（**不是**二进制流）。
 *
 * **B. OpenAI 兼容协议**（其他网关，如硅基流动 / OpenAI 官方）
 * - ASR：`POST {base}/audio/transcriptions`（multipart）→ `{text}`
 * - TTS：`POST {base}/audio/speech` → 音频二进制
 *
 * 注意：阿里云百炼的 compatible-mode 不提供 audio 系列路径（实测 404），需另填支持该路径的网关。
 */
class OpenAISpeechClient(
    baseUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 归一化后的 Base URL：剥离误粘的端点后缀，小米域名缺 `/v1` 时补齐。 */
    private val base: String = normalizeBase(baseUrl)

    /** 是否走小米 MiMo 原生协议。 */
    val isMiMo: Boolean = runCatching {
        URI(base).host?.lowercase()?.contains("xiaomimimo.com") == true
    }.getOrDefault(false)

    val defaultAsrModel: String get() = if (isMiMo) "mimo-v2.5-asr" else "whisper-1"
    val defaultTtsModel: String get() = if (isMiMo) "mimo-v2.5-tts" else "tts-1"
    val defaultVoice: String get() = if (isMiMo) "mimo_default" else "alloy"

    /** MiMo 非流式 TTS 用 wav 最稳（流式才需 pcm16）；OpenAI 兼容端点按 mp3 请求。 */
    val ttsFormat: String get() = if (isMiMo) "wav" else "mp3"

    /**
     * 文本转语音。
     * @param voice 音色 ID。MiMo 内置音色如 mimo_default / 冰糖 / 茉莉 / 苏打 / 白桦 / Mia / Chloe / Milo / Dean；
     *              若模型是 `mimo-v2.5-tts-voicedesign`，此参数会被当作"音色描述"传进 user 指令。
     */
    fun synthesize(text: String, model: String, voice: String = "", format: String = ttsFormat): SynthesizedAudio =
        if (isMiMo) synthesizeMiMo(text, model, voice, format) else synthesizeOpenAI(text, model, voice, format)

    /**
     * 语音转文字。
     * @param language MiMo 的 asr_options.language（auto / zh / en）；OpenAI 侧作为 language 表单字段。
     */
    fun transcribe(file: File, model: String, language: String = "auto"): String =
        if (isMiMo) transcribeMiMo(file, model, language) else transcribeOpenAI(file, model, language)

    // ---------------- 小米 MiMo 原生协议 ----------------

    private fun transcribeMiMo(file: File, model: String, language: String): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) throw ApiException("录音文件为空，请重新录制")
        // MiMo 硬限制：base64 编码后 ≤ 10MB（16k mono wav ≈ 32KB/s，约可录 3.9 分钟）
        if (bytes.size > 7_000_000) throw ApiException("录音过长（约超过 3.5 分钟），请分几次发送")

        val mime = if (file.extension.equals("mp3", true)) "audio/mpeg" else "audio/wav"
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val payload = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            add(
                                buildJsonObject {
                                    put("type", "input_audio")
                                    putJsonObject("input_audio") {
                                        put("data", "data:$mime;base64,$b64")
                                    }
                                }
                            )
                        }
                    }
                )
            }
            putJsonObject("asr_options") {
                put("language", language.ifBlank { "auto" })
            }
        }.toString()

        val raw = postJson("$base/chat/completions", payload, "语音识别")
        val parsed = json.decodeFromString(ChatSpeechResponse.serializer(), raw)
        return parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
    }

    private fun synthesizeMiMo(text: String, model: String, voice: String, format: String): SynthesizedAudio {
        val voiceDesign = model.contains("voicedesign", ignoreCase = true)
        val payload = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                // voicedesign 模型：音色由 user 侧的自然语言描述生成（必填）
                if (voiceDesign) {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", voice.ifBlank { "自然、清晰、亲切的普通话女声" })
                        }
                    )
                }
                // 目标文本必须放 assistant 角色
                add(
                    buildJsonObject {
                        put("role", "assistant")
                        put("content", text)
                    }
                )
            }
            putJsonObject("audio") {
                put("format", format)
                if (!voiceDesign) put("voice", voice.ifBlank { "mimo_default" })
            }
        }.toString()

        val raw = postJson("$base/chat/completions", payload, "语音合成")
        val parsed = json.decodeFromString(ChatSpeechResponse.serializer(), raw)
        val message = parsed.choices.firstOrNull()?.message
        val data = message?.audio?.data
        if (data.isNullOrBlank()) {
            throw ApiException("语音合成未返回音频（确认模型是 TTS 模型，当前为 $model）")
        }
        val bytes = Base64.decode(data, Base64.DEFAULT)
        if (bytes.isEmpty()) throw ApiException("语音合成返回空音频")
        val ext = message.audio.format?.lowercase()?.takeIf { it == "wav" || it == "mp3" }
            ?: format.lowercase().takeIf { it == "wav" || it == "mp3" }
            ?: "wav"
        return SynthesizedAudio(bytes, ext)
    }

    // ---------------- OpenAI 兼容协议 ----------------

    private fun synthesizeOpenAI(text: String, model: String, voice: String, format: String): SynthesizedAudio {
        val payload = buildJsonObject {
            put("model", model)
            put("input", text)
            if (voice.isNotBlank()) put("voice", voice)
            put("response_format", format)
        }.toString()

        val request = Request.Builder()
            .url("$base/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey) // 兼容只认 api-key 头的网关
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string()?.take(300) ?: response.message
            throw ApiException("语音合成失败 HTTP ${response.code} ${response.message}：$err")
        }
        val bytes = response.body?.bytes() ?: throw ApiException("语音合成返回空响应")
        if (bytes.isEmpty()) throw ApiException("语音合成返回空音频（检查模型是否支持出声）")
        return SynthesizedAudio(bytes, if (format.equals("wav", true)) "wav" else "mp3")
    }

    private fun transcribeOpenAI(file: File, model: String, language: String): String {
        val mediaType = when (file.extension.lowercase()) {
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            else -> "audio/mpeg"
        }.toMediaType()

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .addFormDataPart("model", model)
        if (language.isNotBlank() && language != "auto") {
            builder.addFormDataPart("language", language)
        }
        val body = builder.build()

        val request = Request.Builder()
            .url("$base/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)
            .post(body)
            .build()

        val response = http.newCall(request).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string()?.take(300) ?: response.message
            throw ApiException("语音识别失败 HTTP ${response.code} ${response.message}：$err")
        }
        val raw = response.body?.string() ?: throw ApiException("语音识别返回空响应")
        return json.decodeFromString(TranscriptionResponse.serializer(), raw).text.trim()
    }

    // ---------------- 公共 ----------------

    /** POST JSON 并返回响应体字符串；失败时把端点一并写进错误，便于排查拼错 URL。 */
    private fun postJson(url: String, payload: String, label: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()
        val raw = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw ApiException("$label 失败 HTTP ${response.code}（$url）：${raw.take(300)}")
        }
        return raw
    }

    /**
     * 归一化 Base URL：
     * 1) 去首尾空白与尾部 `/`；
     * 2) 反复剥离误粘的端点后缀（玩家常把完整端点粘进 Base URL 框）；
     * 3) 小米域名若没带路径，补 `/v1`。
     */
    private fun normalizeBase(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (s.isBlank()) return s

        val suffixes = listOf(
            "/chat/completions",
            "/audio/speech",
            "/audio/transcriptions",
            "/audio/translations",
            "/completions"
        )
        var changed = true
        while (changed) {
            changed = false
            for (suffix in suffixes) {
                if (s.endsWith(suffix, ignoreCase = true)) {
                    s = s.dropLast(suffix.length).trimEnd('/')
                    changed = true
                }
            }
        }

        val uri = runCatching { URI(s) }.getOrNull()
        val host = uri?.host?.lowercase().orEmpty()
        if (host.contains("xiaomimimo.com") && uri?.path.isNullOrBlank()) {
            s = "$s/v1"
        }
        return s
    }
}
