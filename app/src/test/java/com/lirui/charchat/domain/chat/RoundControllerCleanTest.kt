package com.lirui.charchat.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回复清洗：模型偶发的标记变体、Markdown 符号、未知标签都不应出现在气泡里。
 */
class RoundControllerCleanTest {

    @Test
    fun `剥离 PHOTO 后不留方括号残留`() {
        val parsed = RoundController.parseRound("给你看一下[[PHOTO: 小雪在窗边微笑的自拍]]。")
        assertEquals(1, parsed.bubbles.size)
        assertEquals("给你看一下 。", parsed.bubbles[0].text)
        assertEquals("小雪在窗边微笑的自拍", parsed.bubbles[0].photoPrompt)
    }

    @Test
    fun `隐藏信号与未知标签都被剥离`() {
        val raw = "好呀～[[NEXT]]那我们走吧。[[AFF:+2]][[STATUS: 心情：开心]][[MOOD: 兴奋]]"
        val parsed = RoundController.parseRound(raw)
        assertEquals(2, parsed.bubbles.size)
        assertEquals("好呀～", parsed.bubbles[0].text)
        assertEquals("那我们走吧。", parsed.bubbles[1].text)
    }

    @Test
    fun `中文黑括号写法也能剥离`() {
        val parsed = RoundController.parseRound("嗯。[[NEXT]]走吧。【STATUS: 状态：出门】")
        assertEquals(2, parsed.bubbles.size)
        assertEquals("走吧。", parsed.bubbles[1].text)
    }

    @Test
    fun `markdown 与换行被清洗成单条文本`() {
        val parsed = RoundController.parseRound("**今天**很开心\n呢`真的`")
        assertEquals(1, parsed.bubbles.size)
        val t = parsed.bubbles[0].text
        assertTrue("不含 markdown 粗体", !t.contains("**"))
        assertTrue("不含反引号", !t.contains("`"))
        assertTrue("不含换行", !t.contains("\n"))
        assertTrue(t.contains("今天") && t.contains("很开心"))
    }

    @Test
    fun `流式预览同样干净`() {
        val p = RoundController.sanitizePreview("我在[[NEXT]]想你了[[AFF:+1]]")
        assertTrue(!p.contains("[["))
        assertTrue(p.contains("我在"))
        assertTrue(p.contains("想你了"))
    }

    @Test
    fun `开场白风格的 Markdown 与水平线被清洗`() {
        // 模拟角色卡 firstMes 里的 markdown 排版：# 标题、**加粗**、--- 分割线
        val raw = "# **所以，你和清月师尊的故事是怎么样的呢？**\n\n---\n\n② 开场白：清冷人设\n\n开场白一。"
        val cleaned = RoundController.sanitizeVisible(raw)
        assertFalse("去除 # 标题", cleaned.startsWith("#"))
        assertFalse("去除 ** 加粗", cleaned.contains("**"))
        assertFalse("去除 --- 水平线", cleaned.contains("---"))
        assertTrue("保留中文内容", cleaned.contains("开场白一"))
    }

    @Test
    fun `全角方括号未知标签被剥除`() {
        val cleaned = RoundController.sanitizeVisible("神念微动，嗯嗯。【SFW/qingyue/gxcp/calm/17】")
        assertFalse("【】不应残留", cleaned.contains("【"))
        assertTrue("保留对话", cleaned.contains("嗯嗯"))
    }

    @Test
    fun `全角已知控制标签不被误删`() {
        // 模型偶发用全角方括号写控制信号：不能一刷全涨
        val raw = "给你看看【PHOTO: 在窗边笑的自拍】。【NEXT】还想看吗？"
        val parsed = RoundController.parseRound(raw)
        assertEquals(2, parsed.bubbles.size)
        assertEquals("给你看看 。", parsed.bubbles[0].text)
        assertEquals("在窗边笑的自拍", parsed.bubbles[0].photoPrompt)
        assertEquals("还想看吗？", parsed.bubbles[1].text)
    }
}