package com.lirui.charchat.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 图像生成请求（OpenAI 兼容 /images/generations）。 */
@Serializable
data class ImageRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    @SerialName("response_format") val responseFormat: String = "b64_json",
    val quality: String? = null,      // gpt-image-1 / dall-e-3 支持
    val style: String? = null,         // dall-e-3 支持 natural/vivid
    val user: String? = null
)

@Serializable
data class ImageResponse(
    val created: Long? = null,
    val data: List<ImageData> = emptyList()
)

@Serializable
data class ImageData(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null
)
