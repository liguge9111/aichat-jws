package com.lirui.charchat.domain.chat

import org.junit.Assert.*
import org.junit.Test

class RoundControllerStatusTest {

    @Test
    fun `状态栏信号被剥离不进气泡`() {
        val raw = "我没事的。[[STATUS: 心情:平静 ｜ 精力:60%]]"
        val parsed = RoundController.parseRound(raw)
        assertEquals(1, parsed.bubbles.size)
        assertEquals("我没事的。", parsed.bubbles[0].text)
        assertFalse(parsed.bubbles[0].text.contains("STATUS"))
    }

    @Test
    fun `带换行的状态栏同样剥离`() {
        val raw = "嗯。\n[[STATUS: 身体:累\n情绪:好]]"
        val parsed = RoundController.parseRound(raw)
        val text = parsed.bubbles.joinToString(" ") { it.text }
        assertFalse(text.contains("STATUS"))
        assertTrue(text.contains("嗯"))
    }

    @Test
    fun `sanitizePreview 隐藏状态栏信号`() {
        val preview = RoundController.sanitizePreview("好呀。[[STATUS: 心情:开心]]")
        assertFalse(preview.contains("STATUS"))
        assertTrue(preview.contains("好呀"))
    }

    @Test
    fun `状态栏与 NEXT 组合解析`() {
        val raw = "第一条。[[NEXT]] 第二条。[[STATUS: 状态：前进]]"
        val parsed = RoundController.parseRound(raw)
        assertEquals(2, parsed.bubbles.size)
        assertEquals("第一条。", parsed.bubbles[0].text)
        assertEquals("第二条。", parsed.bubbles[1].text)
    }

    @Test
    fun `NOTE 约定信号被剥离不进气泡`() {
        val raw = "好呀，以后就这么办。[[NOTE: 玩家要求我称呼他为老师]]"
        val parsed = RoundController.parseRound(raw)
        assertEquals(1, parsed.bubbles.size)
        assertEquals("好呀，以后就这么办。", parsed.bubbles[0].text)
        assertFalse(parsed.bubbles[0].text.contains("NOTE"))
    }

    @Test
    fun `sanitizePreview 隐藏 NOTE`() {
        assertFalse(RoundController.sanitizePreview("可以。[[NOTE: 以后你叫我主人]]").contains("NOTE"))
    }
}
