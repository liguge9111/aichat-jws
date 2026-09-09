package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.*
import org.junit.Test

class AttributeEngineStatusTest {

    @Test
    fun `解析状态栏信号`() {
        val upd = AttributeEngine.parse(
            "今天也谢谢你来。\n[[STATUS: 心情：开心 ｜ 精力：70% ｜ 好感在升温]]"
        )
        assertNotNull(upd)
        assertEquals("心情：开心 ｜ 精力：70% ｜ 好感在升温", upd!!.statusText)
    }

    @Test
    fun `状态栏含换行也能整段捕获`() {
        val upd = AttributeEngine.parse(
            "嗯……\n[[STATUS: 身体状态：疲惫\n情绪：低落\n关系：朋友]]"
        )
        assertNotNull(upd)
        assertTrue(upd!!.statusText!!.contains("疲惫"))
        assertTrue(upd.statusText!!.contains("低落"))
    }

    @Test
    fun `无状态信号时 statusText 为 null`() {
        val upd = AttributeEngine.parse("只是普通的回复")
        assertNull(upd)
    }

    @Test
    fun `无状态变化时只更新好感不丢状态`() {
        val upd = AttributeEngine.parse("哈哈好吧 [[AFF:+2]]")
        assertEquals(2, upd!!.affectionDelta)
        assertNull(upd.statusText)
    }

    @Test
    fun `同时携带好感与状态`() {
        val upd = AttributeEngine.parse("[[AFF:+3]][[STATUS: 状态A→B]]")
        assertEquals(3, upd!!.affectionDelta)
        assertEquals("状态A→B", upd.statusText)
    }

    @Test
    fun `apply 不改变好感只更新状态时正常`() {
        val current = StrategyAttributes(affection = 30, relationship = "朋友")
        val upd = AttributeEngine.AffinityUpdate(0, null, "心情：很好")
        val next = AttributeEngine.apply(current, upd)
        assertEquals(30, next.affection)
        assertEquals("朋友", next.relationship)
    }
}
