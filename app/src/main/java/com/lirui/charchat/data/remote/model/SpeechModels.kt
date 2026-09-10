package com.lirui.charchat.data.remote.model

import kotlinx.serialization.Serializable

/** OpenAI 兼容语音识别响应（音频转写端点，形如 /v1/audio/transcriptions）。 */
@Serializable
data class TranscriptionResponse(
    val text: String = ""
)

/**
 * 小米 MiMo 语音协议的响应外壳（ASR 与 TTS 共用 chat/completions 端点）。
 *
 * - ASR：识别文本落在 `choices[0].message.content`
 * - TTS：音频 base64 落在 `choices[0].message.audio.data`
 *
 * 未知字段一律忽略，方便服务端加字段而不炸解析。
 */
@Serializable
data class ChatSpeechResponse(
    val choices: List<Choice> = emptyList()
) {
    @Serializable
    data class Choice(
        val message: Message = Message()
    )

    @Serializable
    data class Message(
        val role: String = "",
        val content: String? = null,
        val audio: AudioPayload? = null
    )

    @Serializable
    data class AudioPayload(
        /** base64 编码的音频数据。 */
        val data: String = "",
        /** 服务端实际返回的容器格式（wav / mp3）。 */
        val format: String? = null
    )
}
