package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CardSource
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingOptionsTest {

    private fun card(firstMes: String = "", alternates: List<String> = emptyList()) = CharacterCard(
        id = "c1",
        name = "测试",
        description = "",
        firstMes = firstMes,
        alternateGreetings = alternates,
        attributes = StrategyAttributes(),
        player = PlayerProfile(),
        source = CardSource.JSON
    )

    @Test
    fun `默认开场白在首位_备选按序跟随_重复去重`() {
        val c = card(
            firstMes = "默认：你好呀。",
            alternates = listOf("备选一", "默认：你好呀。", "备选二")
        )
        val got = GreetingOptions.candidates(c)
        assertEquals(listOf("默认：你好呀。", "备选一", "备选二"), got)
    }

    @Test
    fun `firstMes 为空但有备选时只列备选`() {
        val c = card(firstMes = "", alternates = listOf("备选A", "备选B"))
        assertEquals(listOf("备选A", "备选B"), GreetingOptions.candidates(c))
    }

    @Test
    fun `全空或无开场白返回空表`() {
        assertTrue(GreetingOptions.candidates(card()).isEmpty())
        assertTrue(GreetingOptions.candidates(card("   ", listOf(" ", ""))).isEmpty())
    }

    @Test
    fun `开场白清洗后再展示_剥控制标记与排版杂质`() {
        val c = card(firstMes = "## 你好呀\n**很高兴**见到你 [[NEXT]] [PHOTO: 半身像]")
        val got = GreetingOptions.candidates(c)
        assertEquals(1, got.size)
        val t = got[0]
        assertTrue(t.contains("你好呀"))
        assertTrue(t.contains("很高兴见到你"))
        assertTrue("不应残留 PHOTO 标记", !t.contains("PHOTO"))
        assertTrue("不应残留 NEXT 标记", !t.contains("NEXT"))
    }
}
