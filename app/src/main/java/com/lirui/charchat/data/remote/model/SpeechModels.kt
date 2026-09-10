package com.lirui.charchat.data.remote.model

import kotlinx.serialization.Serializable

/** OpenAI 兼容语音识别响应（/v1/audio/transcriptions）。 */
@Serializable
data class TranscriptionResponse(
    val text: String = ""
)
