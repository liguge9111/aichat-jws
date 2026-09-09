package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住「回复语言」章节：必须出现于提示词中，并随 replyLanguage 切换。
 * 解决英文角色卡 + 中文提问时被带跑英文的问题。
 */
class PromptBuilderLanguageTest {

    private fun card() = CharacterCard(
        id = "c1", name = "小樱", description = "She is a cheerful barista.", personality = "",
        scenario = "", mesExample = "",
        worldBookJson = "", additionalNotes = "",
        avatarPath = null, statusText = "", memories = "",
        player = PlayerProfile(name = "玩家"), attributes = StrategyAttributes()
    )

    @Test fun `默认中文提示词应包含回复语言与简体中文`() {
        val p = PromptBuilder.build(card())
        assertTrue("含回复语言章节", p.contains("【回复语言"))
        assertTrue("指明简体中文", p.contains("简体中文"))
        assertTrue("要求中文标点", p.contains("中文标点"))
    }

    @Test fun `英文输入时切换为英文规则`() {
        val p = PromptBuilder.build(card(), replyLanguage = ReplyLanguage.ENGLISH)
        assertTrue(p.contains("全程用English"))
        assertFalse("英文模式不应强制中文标点", p.contains("每条消息句尾用中文标点"))
    }

    @Test fun `群聊也跟随语言`() {
        val p = PromptBuilder.buildForGroup(
            card(), "好友群", listOf("小狼"), replyLanguage = ReplyLanguage.CHINESE
        )
        assertTrue(p.contains("简体中文"))
    }
}
