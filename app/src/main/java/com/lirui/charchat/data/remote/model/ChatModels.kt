package com.lirui.charchat.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 对话请求（OpenAI 兼容 /chat/completions）。 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMsg>,
    val temperature: Double = 0.9,
    val stream: Boolean = true,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ChatMsg(
    val role: String,          // system / user / assistant
    val content: String,
    val name: String? = null   // 群聊时区分角色
)

/** SSE 流式分片（仅解析需要的部分，忽略未知字段）。 */
@Serializable
data class SseChunk(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

/** GET /models 的响应（连通性探测用，大部分 OpenAI 兼容网关支持）。 */
@Serializable
data class ModelsListResponse(
    val `object`: String? = null,
    val data: List<ModelInfo> = emptyList()
)

@Serializable
data class ModelInfo(
    val id: String,
    val `object`: String? = null,
    val owned_by: String? = null
)
