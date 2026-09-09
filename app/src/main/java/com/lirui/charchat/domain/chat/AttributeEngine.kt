package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.StrategyAttributes

/**
 * 好感/关系自然演进 + 状态栏/约定更新（§5.8）：
 * 解析回复末尾隐藏信号 [[AFF:+n]] / [[REL:阶段]] / [[STATUS: 整段新状态栏]] /
 * [[NOTE: 一条新长期约定]]，全部复用同一次模型返回，零额外调用。纯函数，便于单测。
 */
object AttributeEngine {

    data class AffinityUpdate(
        val affectionDelta: Int,
        val relationship: String?,
        /** 整段新状态栏；null 表示本轮未更新。 */
        val statusText: String? = null,
        /** 本轮角色新认可的一条长期约定；null 表示没有。 */
        val newNote: String? = null,
        /** 本轮玩家讲起并被角色接受的共同回忆；null 表示没有。 */
        val newMemory: String? = null
    )

    private val AFF = Regex("(?i)\\[\\[\\s*AFF\\s*:\\s*([+-]?\\d+)\\s*\\]\\]")
    private val REL = Regex("(?i)\\[\\[\\s*REL\\s*:\\s*([^\\]]+)\\s*\\]\\]")
    // STATUS/NOTE/MEM 内容为整段文本（可能含换行），跨行非贪婪匹配到第一个 ]]
    private val STATUS = Regex("(?is)\\[\\[\\s*STATUS\\s*:\\s*(.*?)\\]\\]")
    private val NOTE = Regex("(?is)\\[\\[\\s*NOTE\\s*:\\s*(.*?)\\]\\]")
    private val MEM = Regex("(?is)\\[\\[\\s*MEM\\s*:\\s*(.*?)\\]\\]")

    fun parse(raw: String): AffinityUpdate? {
        val affMatch = AFF.find(raw)
        val relMatch = REL.find(raw)
        val statusMatch = STATUS.find(raw)
        val noteMatch = NOTE.find(raw)
        val memMatch = MEM.find(raw)
        if (affMatch == null && relMatch == null && statusMatch == null &&
            noteMatch == null && memMatch == null
        ) return null
        val delta = affMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(-10, 10) ?: 0
        val rel = relMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val status = statusMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val note = noteMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val memory = memMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        return AffinityUpdate(delta, rel, status, note, memory)
    }

    fun apply(current: StrategyAttributes, update: AffinityUpdate?): StrategyAttributes {
        if (update == null) return current
        val newAff = (current.affection + update.affectionDelta).coerceIn(0, 100)
        val newRel = update.relationship ?: current.relationship
        return current.copy(affection = newAff, relationship = newRel)
    }
}
