package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.*
import org.junit.Test

class AttributeEngineTest {

    @Test
    fun `解析好感增量`() {
        val u = AttributeEngine.parse("晚安[[AFF:+3]]")
        assertNotNull(u)
        assertEquals(3, u!!.affectionDelta)
        assertNull(u.relationship)
    }

    @Test
    fun `解析关系阶段`() {
        val u = AttributeEngine.parse("我们在一起吧[[REL:恋人]]")
        assertNotNull(u)
        assertEquals("恋人", u!!.relationship)
        assertEquals(0, u.affectionDelta)
    }

    @Test
    fun `无信号返回null`() {
        assertNull(AttributeEngine.parse("今天天气真好"))
    }

    @Test
    fun `apply 累加并钳制范围`() {
        val base = StrategyAttributes(affection = 98, relationship = "朋友")
        val applied = AttributeEngine.apply(base, AttributeEngine.AffinityUpdate(10, "恋人"))
        assertEquals(100, applied.affection)
        assertEquals("恋人", applied.relationship)

        val applied2 = AttributeEngine.apply(base, AttributeEngine.AffinityUpdate(-200, null))
        assertEquals(0, applied2.affection)
    }
}
