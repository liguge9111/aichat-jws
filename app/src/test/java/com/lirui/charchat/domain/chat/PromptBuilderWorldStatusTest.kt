package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderWorldStatusTest {

    private fun card(status: String = "") = CharacterCard(
        id = "c1",
        name = "小樱",
        description = "咖啡师",
        attributes = StrategyAttributes(personality = "温柔", affection = 42, relationship = "朋友"),
        player = PlayerProfile(name = "阿明"),
        statusText = status
    )

    @Test
    fun `有状态栏时注入当前状态栏与更新规则`() {
        val p = PromptBuilder.build(card("心情:平静 ｜ 体力:80%"))
        assertTrue(p.contains("【当前状态栏"))
        assertTrue(p.contains("心情:平静"))
        assertTrue(p.contains("[[STATUS:"))
    }

    @Test
    fun `无状态栏时不出现相关段落与规则`() {
        val p = PromptBuilder.build(card())
        assertFalse(p.contains("状态栏"))
    }

    @Test
    fun `世界书命中条目注入世界设定段`() {
        val p = PromptBuilder.build(card(), activeWorld = listOf("小樱工作的咖啡店在旧城街角。"))
        assertTrue(p.contains("【世界设定"))
        assertTrue(p.contains("旧城街角"))
    }

    @Test
    fun `无命中世界条目时无世界设定段`() {
        val p = PromptBuilder.build(card())
        // 段落头会随世界书是否存在而出现；无命中时不应有"此刻相关的背景"这段内容
        assertFalse(p.contains("此刻相关的背景"))
    }

    @Test
    fun `群聊版同样注入状态栏与世界设定`() {
        val p = PromptBuilder.buildForGroup(
            card("心情:平静"),
            "好友群",
            listOf("小狼"),
            activeWorld = listOf("全城多雾。")
        )
        assertTrue(p.contains("心情:平静"))
        assertTrue(p.contains("全城多雾"))
        assertTrue(p.contains("【群聊环境】"))
    }
}
