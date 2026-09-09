package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.GroupMessage
import org.junit.Assert.*
import org.junit.Test

class GroupContextBuilderTest {

    private val selfId = "c1"

    private fun msg(
        senderId: String?,
        name: String,
        text: String,
        isUser: Boolean = false,
        imagePath: String? = null
    ) = GroupMessage(
        senderId = senderId,
        senderName = name,
        isUser = isUser,
        text = text,
        imagePath = imagePath
    )

    private val history = listOf(
        msg(null, "我", "大家好", isUser = true),
        msg("c1", "小樱", "你好呀"),
        msg("c2", "小狼", "我也在"),
        msg(null, "我", "晚上一起吃饭？", isUser = true)
    )

    @Test
    fun `玩家发言映射为 user 并带名字前缀`() {
        val ctx = GroupContextBuilder.build(history, selfId, "我")
        val first = ctx.first()
        assertEquals("user", first.role)
        assertEquals("我: 大家好", first.content)
    }

    @Test
    fun `自己说过的话映射为 assistant`() {
        val ctx = GroupContextBuilder.build(history, selfId, "我")
        val mine = ctx.first { it.role == "assistant" }
        assertEquals("你好呀", mine.content)
    }

    @Test
    fun `其他人发言被合并进一条 user，不会污染 assistant`() {
        val ctx = GroupContextBuilder.build(history, selfId, "我")
        val assistantCount = ctx.count { it.role == "assistant" }
        assertEquals("只有自己一条 assistant", 1, assistantCount)

        val others = ctx.first { it.content.startsWith("（群里其他人说）") }
        assertTrue("带其他成员名字", others.content.contains("小狼: 我也在"))
    }

    @Test
    fun `尾部是他人发言时也会被 flush`() {
        val h = history + msg("c2", "小狼", "我先走了")
        val ctx = GroupContextBuilder.build(h, selfId, "我")
        assertTrue(ctx.last().content.contains("我先走了"))
    }

    @Test
    fun `换一个角色视角，assistant 归属随之改变`() {
        val ctx = GroupContextBuilder.build(history, "c2", "我")
        val assistant = ctx.filter { it.role == "assistant" }
        assertEquals(1, assistant.size)
        assertEquals("我也在", assistant.first().content)
        // 此时小樱的话应被归到"其他人"
        val others = ctx.first { it.content.startsWith("（群里其他人说）") }
        assertTrue(others.content.contains("小樱: 你好呀"))
    }

    @Test
    fun `带照片的消息会附加照片提示`() {
        val h = listOf(msg("c2", "小狼", "看这个", imagePath = "/tmp/a.png"))
        val ctx = GroupContextBuilder.build(h, selfId, "我")
        assertTrue(ctx.single().content.contains("[发了张照片]"))
    }

    @Test
    fun `连续的他人发言合并成一条`() {
        val h = listOf(
            msg("c2", "小狼", "一句"),
            msg("c3", "知世", "两句")
        )
        val ctx = GroupContextBuilder.build(h, selfId, "我")
        assertEquals("合并为一条", 1, ctx.size)
        val c = ctx.single().content
        assertTrue(c.contains("小狼: 一句"))
        assertTrue(c.contains("知世: 两句"))
    }
}
