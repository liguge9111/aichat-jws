package com.lirui.charchat.domain.chat

import org.junit.Assert.*
import org.junit.Test

class AttributeEngineNotesTest {

    @Test
    fun `解析 NOTE 约定`() {
        val upd = AttributeEngine.parse("好呀，以后就这么办。\n[[NOTE: 玩家要求我称呼他为老师]]")
        assertNotNull(upd)
        assertEquals("玩家要求我称呼他为老师", upd!!.newNote)
    }

    @Test
    fun `无 NOTE 时为 null`() {
        val upd = AttributeEngine.parse("普通的回复而已")
        assertNull(upd)
    }

    @Test
    fun `与好感信号共存`() {
        val upd = AttributeEngine.parse("嗯嗯[[AFF:+1]][[NOTE: 约定每周五一起去夜市]]")
        assertEquals(1, upd!!.affectionDelta)
        assertEquals("约定每周五一起去夜市", upd.newNote)
    }

    @Test
    fun `只带 NOTE 不误改好感`() {
        val upd = AttributeEngine.parse("可以。[[NOTE: 以后你叫我主人]]")
        assertEquals(0, upd!!.affectionDelta)
        assertNotNull(upd.newNote)
        assertNull(upd.relationship)
    }
}
