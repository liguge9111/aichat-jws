package com.lirui.charchat.data.cardparser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMapperGreetingsTest {

    @Test
    fun `encode_decode 往返一致`() {
        val src = listOf("备选一", "备选二")
        val json = CardMapper.encodeGreetings(src)
        assertEquals(src, CardMapper.decodeGreetings(json))
    }

    @Test
    fun `编码时剔除空白项`() {
        val json = CardMapper.encodeGreetings(listOf("a", "  ", "b"))
        assertEquals(listOf("a", "b"), CardMapper.decodeGreetings(json))
    }

    @Test
    fun `脏 JSON 回落空表`() {
        assertTrue(CardMapper.decodeGreetings("不是json").isEmpty())
        assertTrue(CardMapper.decodeGreetings("").isEmpty())
        assertTrue(CardMapper.decodeGreetings("[]").isEmpty())
    }
}
