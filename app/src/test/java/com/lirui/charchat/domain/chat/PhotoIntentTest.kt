package com.lirui.charchat.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 照片意图识别：模型漏写 [[PHOTO:]] 时客户端兜底出图。 */
class PhotoIntentTest {

    @Test
    fun `玩家要看照片应识别为照片意图`() {
        assertTrue(PhotoIntent.wantsPhoto("发张照片看看"))
        assertTrue(PhotoIntent.wantsPhoto("想看你的自拍"))
        assertTrue(PhotoIntent.wantsPhoto("你现在穿的什么？给我看看"))
        assertTrue(PhotoIntent.wantsPhoto("send me a selfie"))
    }

    @Test
    fun `普通闲聊不应误判`() {
        assertFalse(PhotoIntent.wantsPhoto("今天过得怎么样"))
        assertFalse(PhotoIntent.wantsPhoto("我们昨天去看了电影"))
        assertFalse(PhotoIntent.wantsPhoto(""))
    }

    @Test
    fun `兜底提示词包含角色名与设定摘要`() {
        val p = PhotoIntent.fallbackPrompt("小雪", "黑长直，咖啡店店员", "发张照片看看")
        assertTrue(p.contains("小雪"))
        assertTrue(p.contains("咖啡店店员"))
        assertTrue(p.length < 600)
    }
}
