package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.PlayerProfile

/**
 * 玩家身份锚定工具（纯函数）。
 *
 * SillyTavern 角色卡常用 {{user}} / {{User}} / <USER> 等宏指代玩家（开场白、情境、
 * 示例对话里尤甚）。App 若不替换就原样发给模型，多数模型不认识这个宏，会把它当
 * 字面量忽略——玩家身份线索随之断裂，角色就"不认识/把玩家当陌生人"。
 *
 * 本工具在 prompt 组装层把所有宏统一替换为玩家显示名，并在 PromptBuilder 中加
 * 规则性锚定（见 build 的【玩家身份】段）。
 */
object UserIdentity {

    /** 玩家显示名：档案名为空回落"玩家"。 */
    fun displayName(p: PlayerProfile): String = p.name.trim().ifBlank { "玩家" }

    private val userMacro = Regex("""\{\{\s*user\s*\}\}|<\s*user\s*>""", RegexOption.IGNORE_CASE)
    private val charMacro = Regex("""\{\{\s*char\s*\}\}|<\s*char\s*>""", RegexOption.IGNORE_CASE)

    /**
     * 替换文本中的玩家/角色宏。
     * @param playerName 替换 {{user}} 等；传空串则保持原文不动。
     * @param charName   替换 {{char}} 等；传空串则保持原文不动。
     */
    fun replace(text: String, playerName: String, charName: String): String {
        var out = text
        if (playerName.isNotBlank()) out = userMacro.replace(out, playerName.trim())
        if (charName.isNotBlank()) out = charMacro.replace(out, charName.trim())
        return out
    }

    /** 文本中是否含有玩家宏（用于测试/提示）。 */
    fun containsUserMacro(text: String): Boolean = userMacro.containsMatchIn(text)
}
