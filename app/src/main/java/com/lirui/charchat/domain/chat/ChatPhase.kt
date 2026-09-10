package com.lirui.charchat.domain.chat

/** 单轮聊天的阶段事件（由 ChatOrchestrator 发射，供 UI 反映状态）。 */
sealed interface ChatPhase {
    /** 已落库玩家消息，正在向模型请求。 */
    object Thinking : ChatPhase

    /** 流式返回中，preview 为去掉控制标记的部分原文（用于"正在输入"气泡）。 */
    data class Streaming(val preview: String) : ChatPhase

    /** 正在为第 index 张（共 total 张）照片调用图像 API。 */
    data class GeneratingPhoto(val index: Int, val total: Int) : ChatPhase

    /**
     * 语音轮进行中（玩家本轮发的是语音，角色会以语音回复）。
     * 从生成回复到合成语音完成，UI 全程只显示"对方正在讲话…"，
     * 文字气泡与语音一起出现，避免"先看到字、再补上语音"的割裂感。
     */
    object SpeakingVoice : ChatPhase

    /** 非致命提示（如图片生成失败）：本轮对话照常完成，只是给玩家一条可见说明。 */
    data class Notice(val message: String, val isError: Boolean = true) : ChatPhase

    /** 本轮结束。 */
    data class Done(val success: Boolean, val error: String? = null) : ChatPhase
}
