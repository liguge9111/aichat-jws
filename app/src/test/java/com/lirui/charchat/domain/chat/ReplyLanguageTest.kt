package com.lirui.charchat.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyLanguageTest {

    @Test fun `中文输入检测为中文`() {
        assertEquals(ReplyLanguage.CHINESE, ReplyLanguage.detect("你好呀"))
        assertEquals(ReplyLanguage.CHINESE, ReplyLanguage.detect("今天天气真好啊。"))
    }

    @Test fun `纯英文检测为英文`() {
        assertEquals(ReplyLanguage.ENGLISH, ReplyLanguage.detect("Hello there!"))
    }

    @Test fun `日文假名判定为日文`() {
        assertEquals("日本語", ReplyLanguage.detect("こんにちは"))
    }

    @Test fun `韩文判定为韩文`() {
        assertEquals("한국어", ReplyLanguage.detect("안녕하세요"))
    }

    @Test fun `空白回落中文`() {
        assertEquals(ReplyLanguage.CHINESE, ReplyLanguage.detect(""))
        assertEquals(ReplyLanguage.CHINESE, ReplyLanguage.detect("   "))
    }
}
