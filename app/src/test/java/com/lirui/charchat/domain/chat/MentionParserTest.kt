package com.lirui.charchat.domain.chat

import org.junit.Assert.*
import org.junit.Test

class MentionParserTest {

    private val members = listOf("小樱", "小狼", "知世")

    @Test
    fun `单个提及能识别`() {
        assertEquals(listOf("小樱"), MentionParser.parse("@小樱 今晚有空吗", members))
    }

    @Test
    fun `多个提及按出现顺序返回`() {
        assertEquals(
            listOf("小樱", "知世"),
            MentionParser.parse("@小樱 @知世 一起吃饭", members)
        )
    }

    @Test
    fun `重复提及去重`() {
        assertEquals(listOf("小樱"), MentionParser.parse("@小樱 @小樱 在吗", members))
    }

    @Test
    fun `没有提及时返回空（视为广播）`() {
        assertTrue(MentionParser.parse("大家好呀", members).isEmpty())
    }

    @Test
    fun `@了非成员不算定向`() {
        assertTrue(MentionParser.parse("@路人甲 你好", members).isEmpty())
    }

    @Test
    fun `长名优先，避免前缀抢匹配`() {
        val names = listOf("小樱", "小樱酱")
        assertEquals(listOf("小樱酱"), MentionParser.parse("@小樱酱 在吗", names))
        assertEquals(listOf("小樱"), MentionParser.parse("@小樱 在吗", names))
    }

    @Test
    fun `全员广播关键词可识别`() {
        assertTrue(MentionParser.isBroadcast("@全体成员 集合"))
        assertTrue(MentionParser.isBroadcast("@所有人 集合"))
        assertTrue(MentionParser.isBroadcast("@all hi"))
        assertFalse(MentionParser.isBroadcast("@小樱 在吗"))
    }

    @Test
    fun `剥离提及后得到正文`() {
        assertEquals("今晚有空吗", MentionParser.strip("@小樱 今晚有空吗", members))
    }

    @Test
    fun `剥离多个提及`() {
        assertEquals("一起吃饭", MentionParser.strip("@小樱 @知世 一起吃饭", members))
    }

    @Test
    fun `非成员提及保留在正文中`() {
        assertEquals("mail@example.com", MentionParser.strip("mail@example.com", members))
    }
}
