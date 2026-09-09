package com.lirui.charchat.domain.chat

import org.junit.Assert.*
import org.junit.Test

class RoundControllerTest {

    @Test
    fun `无标记返回单气泡`() {
        val parsed = RoundController.parseRound("嗨，你回来啦")
        assertEquals(1, parsed.bubbles.size)
        assertEquals("嗨，你回来啦", parsed.bubbles[0].text)
        assertEquals(0, parsed.photoCount)
    }

    @Test
    fun `按 NEXT 切分为多条`() {
        val raw = "在吗[[NEXT]]我刚到家[[NEXT]]想你了"
        val parsed = RoundController.parseRound(raw)
        assertEquals(3, parsed.bubbles.size)
        assertEquals("在吗", parsed.bubbles[0].text)
        assertEquals("想你了", parsed.bubbles[2].text)
    }

    @Test
    fun `超过5条时合并为5条`() {
        val raw = (1..6).joinToString("[[NEXT]]") { "消息$it" }
        val parsed = RoundController.parseRound(raw)
        assertEquals(RoundController.MAX_BUBBLES, parsed.bubbles.size)
        // 第5条应为第5、第6合并
        assertTrue(parsed.bubbles[4].text.contains("消息5") && parsed.bubbles[4].text.contains("消息6"))
    }

    @Test
    fun `PHOTO 提示被提取并从正文移除`() {
        val raw = "给你看张照片[[PHOTO: 她穿着围裙微笑 ]]好看吗"
        val parsed = RoundController.parseRound(raw)
        assertEquals(1, parsed.bubbles.size)
        assertEquals("给你看张照片 好看吗", parsed.bubbles[0].text)
        assertEquals("她穿着围裙微笑", parsed.bubbles[0].photoPrompt)
        assertEquals(1, parsed.photoCount)
    }

    @Test
    fun `隐藏信号不进入气泡`() {
        val raw = "晚安[[AFF:+2]][[REL:恋人]]"
        val parsed = RoundController.parseRound(raw)
        assertEquals("晚安", parsed.bubbles[0].text)
    }

    @Test
    fun `预览去除所有控制标记`() {
        val preview = RoundController.sanitizePreview("嗨[[NEXT]]看照[[PHOTO: x ]]片[[AFF:+1]]")
        assertFalse(preview.contains("[["))
    }
}
