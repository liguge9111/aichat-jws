package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CardSource
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserIdentityTest {

    @Test
    fun `大小写变体与尖括号形式都被替换为玩家名`() {
        val src = "{{user}}来了 {{User}}在吗 {{USER}}？<user>你好 {{ user }} 有空格的也换"
        val out = UserIdentity.replace(src, "小明", "小红")
        assertFalse("不应残留任何 user 宏", UserIdentity.containsUserMacro(out))
        assertEquals("5 处宏应全部变成小明", 5, Regex("小明").findAll(out).count())
    }

    @Test
    fun `char 宏替换为角色名`() {
        assertEquals("小红微微一笑", UserIdentity.replace("{{char}}微微一笑", "小明", "小红"))
        assertEquals("小红微微一笑", UserIdentity.replace("<CHAR>微微一笑", "小明", "小红"))
    }

    @Test
    fun `玩家名为空时保持原文不动`() {
        val src = "{{user}}你好"
        assertEquals(src, UserIdentity.replace(src, "  ", ""))
    }

    @Test
    fun `显示名空档案回落为玩家`() {
        assertEquals("玩家", UserIdentity.displayName(PlayerProfile()))
        assertEquals("小明", UserIdentity.displayName(PlayerProfile(name = " 小明 ")))
    }

    @Test
    fun `普通文本不受影响`() {
        val src = "今天天气真好"
        assertEquals(src, UserIdentity.replace(src, "小明", "小红"))
    }
}

class PromptBuilderIdentityTest {

    private fun card(
        description: String = "",
        scenario: String = "",
        personality: String = "",
        mesExample: String = "",
        playerName: String = ""
    ) = CharacterCard(
        id = "c1",
        name = "小雪",
        description = description,
        scenario = scenario,
        personality = personality,
        mesExample = mesExample,
        attributes = StrategyAttributes(),
        player = PlayerProfile(name = playerName),
        source = CardSource.JSON
    )

    @Test
    fun `角色设定中的 user 宏替换为玩家档案名`() {
        val c = card(
            description = "{{user}}是我的主人，我从小被{{user}}养大。",
            playerName = "阿明"
        )
        val sys = PromptBuilder.build(c)
        assertTrue("user 宏应替换为档案名", sys.contains("阿明是我的主人，我从小被阿明养大。"))
        assertFalse("system 中不应残留宏", sys.contains("{{user}}"))
    }

    @Test
    fun `玩家档案为空时 user 宏回落为玩家`() {
        val c = card(description = "{{user}}是来店里喝茶的客人。")
        val sys = PromptBuilder.build(c)
        assertTrue("空档案回落为\"玩家\"", sys.contains("玩家是来店里喝茶的客人。"))
    }

    @Test
    fun `情境与示例中的宏同样被替换`() {
        val c = card(
            scenario = "深夜，{{user}}推开书房的门。",
            mesExample = "<START>\n{{user}}: 我回来了。\n{{char}}: 你终于回来了。",
            playerName = "阿明"
        )
        val sys = PromptBuilder.build(c)
        assertTrue(sys.contains("深夜，阿明推开书房的门。"))
        assertTrue(sys.contains("阿明: 我回来了。"))
        assertTrue(sys.contains("小雪: 你终于回来了。"))
        assertFalse(sys.contains("{{user}}") || sys.contains("{{char}}"))
    }

    @Test
    fun `注入玩家身份锚定段_要求不把玩家当陌生人`() {
        val c = card(description = "你是王国的骑士团团长。", playerName = "")
        val sys = PromptBuilder.build(c)
        assertTrue("应有玩家身份段落", sys.contains("【玩家身份"))
        assertTrue("应指明对话者即设定中的你/玩家", sys.contains("以「你」「玩家」身份出现"))
        assertTrue("应包含不把对方当外人的约束", sys.contains("把对方当外人的反应") || sys.contains("当陌生人"))
    }

    @Test
    fun `关系阶段标记附带认知豁免注记`() {
        val c = card(description = "设定：我是青梅竹马线。")
        val sys = PromptBuilder.build(c)
        assertTrue("关系阶段注记应存在", sys.contains("感情进度标记"))
        assertTrue("注记应说明以设定为准", sys.contains("以【角色设定】【当前情境】为准"))
    }

    @Test
    fun `玩家背景中的关系与性格原样展示`() {
        val c = card(playerName = "阿明").copy(
            player = PlayerProfile(name = "阿明", relationToChar = "青梅竹马", personality = "温柔")
        )
        val sys = PromptBuilder.build(c)
        assertTrue(sys.contains("玩家与你的关系：青梅竹马"))
        assertTrue(sys.contains("玩家性格：温柔"))
    }
}
