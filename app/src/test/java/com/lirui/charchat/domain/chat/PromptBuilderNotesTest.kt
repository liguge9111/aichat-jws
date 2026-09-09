package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderNotesTest {

    private fun card(notes: String = "") = CharacterCard(
        id = "c1",
        name = "小樱",
        attributes = StrategyAttributes(personality = "温柔"),
        additionalNotes = notes
    )

    @Test
    fun `有约定时注入已达成的约定段落`() {
        val p = PromptBuilder.build(card("以后你叫我主人\n每周五一起去夜市"))
        assertTrue(p.contains("【已达成的约定"))
        assertTrue(p.contains("以后你叫我主人"))
        assertTrue(p.contains("每周五一起去夜市"))
    }

    @Test
    fun `无约定时不出现该段落`() {
        val p = PromptBuilder.build(card())
        assertFalse(p.contains("已达成的约定"))
    }

    @Test
    fun `NOTE 信号规则始终在提示词中`() {
        assertTrue(PromptBuilder.build(card()).contains("[[NOTE:"))
    }

    @Test
    fun `群聊版同样带出约定`() {
        val p = PromptBuilder.buildForGroup(card("我是小樱，是大家的朋友"), "好友群", listOf("小狼"))
        assertTrue(p.contains("是大家的朋友"))
    }
}
