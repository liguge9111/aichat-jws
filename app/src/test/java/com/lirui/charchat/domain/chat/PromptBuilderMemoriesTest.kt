package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CharacterCard
import org.junit.Assert.assertTrue
import org.junit.Test

/** 系统提示词：共同记忆注入 + 话术引导规则 + 发照片规则。 */
class PromptBuilderMemoriesTest {

    private fun card(memories: String = "") = CharacterCard(
        id = "c1",
        name = "小雪",
        description = "黑长直的咖啡店店员",
        memories = memories
    )

    @Test
    fun `共同回忆注入提示词`() {
        val p = PromptBuilder.build(card("昨天一起在夜市吃章鱼烧\n上周一起看了电影"))
        assertTrue(p.contains("昨天一起在夜市吃章鱼烧"))
        assertTrue(p.contains("上周一起看了电影"))
        assertTrue(p.contains("共同回忆"))
    }

    @Test
    fun `提示词要求把玩家讲起的往事当真`() {
        val p = PromptBuilder.build(card())
        assertTrue("要有 MEM 信号规则", p.contains("[[MEM:"))
        assertTrue("不许否认共同经历", p.contains("我不记得"))
    }

    @Test
    fun `提示词有强制发照片规则`() {
        val p = PromptBuilder.build(card())
        assertTrue(p.contains("[[PHOTO:"))
        assertTrue(p.contains("发照片"))
    }

    @Test
    fun `角色设定排在玩家背景之前`() {
        val p = PromptBuilder.build(card())
        val settingIdx = p.indexOf("黑长直的咖啡店店员")
        val playerIdx = p.indexOf("【玩家背景】")
        assertTrue(settingIdx >= 0 && playerIdx > settingIdx)
    }
}
