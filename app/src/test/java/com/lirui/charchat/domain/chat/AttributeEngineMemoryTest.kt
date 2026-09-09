package com.lirui.charchat.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [[MEM:…]] 共同回忆信号解析。 */
class AttributeEngineMemoryTest {

    @Test
    fun `解析共同回忆`() {
        val upd = AttributeEngine.parse("当然记得啦～[[MEM: 昨天和玩家一起在夜市吃章鱼烧]]")
        assertEquals("昨天和玩家一起在夜市吃章鱼烧", upd?.newMemory)
    }

    @Test
    fun `回忆可跨行`() {
        val upd = AttributeEngine.parse("嗯嗯[[MEM: 上周一起看电影\n坐在最后一排]]")
        assertEquals("上周一起看电影\n坐在最后一排", upd?.newMemory)
    }

    @Test
    fun `没有 MEM 时返回 null 字段`() {
        assertNull(AttributeEngine.parse("好呀。")?.newMemory)
    }

    @Test
    fun `MEM 与 AFF 可同时解析`() {
        val upd = AttributeEngine.parse("好开心！[[AFF:+2]][[MEM: 一起去了海边]]")
        assertEquals(2, upd?.affectionDelta)
        assertEquals("一起去了海边", upd?.newMemory)
    }
}
