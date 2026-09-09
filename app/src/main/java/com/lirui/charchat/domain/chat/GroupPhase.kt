package com.lirui.charchat.domain.chat

/** 群聊一轮的阶段事件（由 GroupOrchestrator 发射）。 */
sealed interface GroupPhase {
    object Idle : GroupPhase

    /** 正在请求 charName 的回复。 */
    data class Thinking(val charName: String) : GroupPhase

    /** charName 流式返回中。 */
    data class Streaming(val charName: String, val preview: String) : GroupPhase

    /** 正在为 charName 的第 index 张（共 total 张）照片出图。 */
    data class GeneratingPhoto(val charName: String, val index: Int, val total: Int) : GroupPhase

    /** 本轮结束。failed 为出错的角色名（null 表示全部成功）。 */
    data class Done(val failed: String? = null, val error: String? = null) : GroupPhase
}
