package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTest {

    private val card = CharacterCard(
        id = "c1",
        name = "小樱",
        description = "来自小镇的咖啡师",
        attributes = StrategyAttributes(
            appearance = "黑长直",
            clothing = "围裙",
            personality = "温柔",
            location = "咖啡店",
            doing = "煮咖啡",
            kink = "轻吻",
            affection = 42,
            relationship = "朋友"
        ),
        player = PlayerProfile(name = "阿明", relationToChar = "网友")
    )

    @Test
    fun `系统提示词包含四段与硬性规则`() {
        val p = PromptBuilder.build(card)
        assertTrue("含角色名", p.contains("小樱"))
        assertTrue("禁第三人称旁白", p.contains("第三人称旁白"))
        assertTrue("禁 Markdown 与剧场标注", p.contains("Markdown"))
        assertTrue("分条标记", p.contains("[[NEXT]]"))
        assertTrue("出图标记", p.contains("[[PHOTO:"))
        assertTrue("好感信号", p.contains("[[AFF:"))
        assertTrue("玩家背景", p.contains("阿明") && p.contains("网友"))
        assertTrue("当前攻略状态", p.contains("42/100") && p.contains("朋友"))
    }

    @Test
    fun `角色设定以原文注入`() {
        val p = PromptBuilder.build(card)
        assertTrue("含 description 原文", p.contains("来自小镇的咖啡师"))
        // personality 字段非空且不在 description 中时，以【性格补充】注入
        assertTrue("含性格补充", PromptBuilder.build(card.copy(personality = "温柔")).contains("温柔"))
    }

    @Test
    fun `情境与示例对话注入`() {
        val withExtra = card.copy(scenario = "雨夜的咖啡店", mesExample = "{{user}}: 好香\n{{char}}: 是刚磨的豆子")
        val p = PromptBuilder.build(withExtra)
        assertTrue("含当前情境", p.contains("雨夜的咖啡店"))
        assertTrue("含示例对话", p.contains("刚磨的豆子"))
    }

    @Test
    fun `无示例对话时不出现该段落`() {
        val p = PromptBuilder.build(card)
        assertFalse(p.contains("【对话风格示例】"))
    }

    @Test
    fun `未设定性癖时不写入提示词`() {
        val noKink = card.copy(
            attributes = card.attributes.copy(kink = "未设定")
        )
        val p = PromptBuilder.build(noKink)
        assertFalse(p.contains("性癖"))
    }
}
