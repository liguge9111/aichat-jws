package com.lirui.charchat.domain.chat

import com.lirui.charchat.data.remote.model.ChatMsg
import com.lirui.charchat.domain.model.GroupMessage

/**
 * 群聊上下文构造：把"整群的公共历史"翻译成"某一个角色视角"的模型上下文。
 * 纯函数，便于单测。
 *
 * 映射规则（对角色 X 而言）：
 * - 玩家发言            → user
 * - X 自己说过的话      → assistant（模型才知道这是自己说过的）
 * - 其他角色说的话      → 合并成一条 user，前缀「（群里其他人说）」并带名字
 *   注意：不能塞进 assistant，否则模型会以为那些话也是自己说的，导致串人格。
 */
object GroupContextBuilder {

    private const val OTHERS_PREFIX = "（群里其他人说）"
    private const val PHOTO_HINT = "[发了张照片]"

    fun build(
        history: List<GroupMessage>,
        selfCardId: String,
        playerName: String
    ): List<ChatMsg> {
        val out = ArrayList<ChatMsg>(history.size + 2)
        val others = ArrayList<String>()

        fun flushOthers() {
            if (others.isEmpty()) return
            out.add(ChatMsg("user", (listOf(OTHERS_PREFIX) + others).joinToString("\n")))
            others.clear()
        }

        history.forEach { m ->
            when {
                m.isUser -> {
                    flushOthers()
                    out.add(ChatMsg("user", decorate(m, playerName)))
                }
                m.senderId == selfCardId -> {
                    flushOthers()
                    out.add(ChatMsg("assistant", m.text))
                }
                else -> {
                    others.add(decorate(m, m.senderName.ifBlank { "其他人" }))
                }
            }
        }
        flushOthers()
        return out
    }

    private fun decorate(m: GroupMessage, name: String): String =
        if (m.imagePath != null) "$name: ${m.text} $PHOTO_HINT".trim()
        else "$name: ${m.text}"
}
