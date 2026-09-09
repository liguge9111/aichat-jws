package com.lirui.charchat.domain.chat

import org.junit.Assert.*
import org.junit.Test

class WorldBookTriggerTest {

    private val book = """
        {"entries":[
          {"keys":["咖啡店","拿铁"],"content":"小樱工作的咖啡店在旧城街角，下午三点后客人很少。","insertion_order":1,"enabled":true},
          {"keys":["ALLEY","小巷"],"content":"后巷常有一只橘猫蹲守，小樱偶尔会去喂它。","insertion_order":2,"enabled":true},
          {"keys":["禁忌"],"content":"她从不提过去那段事。","insertion_order":3,"enabled":false},
          {"keys":[],"content":"常驻设定：这座城市全年多雾。","insertion_order":0,"constant":true}
        ]}
    """.trimIndent()

    @Test
    fun `命中关键字后注入对应条目`() {
        val active = WorldBookTrigger.matches(book, "我们约在咖啡店见？")
        assertTrue(active.any { it.contains("旧城街角") })
    }

    @Test
    fun `大小写不敏感匹配`() {
        val active = WorldBookTrigger.matches(book, "那条 alley 在哪")
        assertTrue(active.any { it.contains("橘猫") })
    }

    @Test
    fun `禁用条目不注入`() {
        val active = WorldBookTrigger.matches(book, "告诉我你的禁忌")
        assertFalse(active.any { it.contains("不提过去") })
    }

    @Test
    fun `常驻条目无需命中也注入`() {
        val active = WorldBookTrigger.matches(book, "天气怎么样")
        assertTrue(active.any { it.contains("全年多雾") })
    }

    @Test
    fun `空书或不合法书返回空`() {
        assertTrue(WorldBookTrigger.matches(null, "xx").isEmpty())
        assertTrue(WorldBookTrigger.matches("not json", "xx").isEmpty())
        assertTrue(WorldBookTrigger.matches("{}", "xx").isEmpty())
    }

    @Test
    fun `无命中的正常对话不注入任何条目`() {
        val active = WorldBookTrigger.matches(book, "今晚吃什么好呢")
        // 常驻条目仍应注入；无 constant 时此处应只有常驻
        assertEquals(1, active.size)
    }
}
