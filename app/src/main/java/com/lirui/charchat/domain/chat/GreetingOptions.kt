package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CharacterCard

/**
 * 首次进入聊天的开场白候选组装。
 *
 * 候选来源 = 默认开场白 firstMes + 导入的备选开场白（alternateGreetings）。
 * 每条先过通用清洗（剥控制标记/PHOTO/Markdown 残留），再 trim 去空去重——
 * 保证弹框预览与最终落库气泡一致，不会出现"预览是原文、进聊天变另一种"的偏差。
 */
object GreetingOptions {

    /** 该卡可选的完整开场白列表；为空表示该卡没有开场白（直接进空聊天）。 */
    fun candidates(card: CharacterCard): List<String> {
        val raw = listOf(card.firstMes) + card.alternateGreetings
        return raw
            .map { RoundController.sanitizePreview(it).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
