package com.lirui.charchat.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 出图提示词拼装：中文、锚点优先、缺失回落、超长截断。 */
class PhotoPromptComposerTest {

    @Test
    fun `包含角色名与三段式结构`() {
        val p = PhotoPromptComposer.compose(
            name = "清月",
            visualAnchor = "银白长发，淡金瞳，常着月白道袍",
            historyLines = listOf("玩家：师尊在吗", "清月：在打坐"),
            scene = "她站在山巅回眸"
        )
        assertTrue(p.startsWith("中文绘图描述。这是清月发来的一张照片"))
        assertTrue(p.contains("【人物形象"))
        assertTrue(p.contains("银白长发，淡金瞳，常着月白道袍"))
        assertTrue(p.contains("【当前情境"))
        assertTrue(p.contains("清月：在打坐"))
        assertTrue(p.contains("【这张照片的内容】"))
        assertTrue(p.contains("她站在山巅回眸"))
        assertTrue(p.contains("必须与此一致"))
    }

    @Test
    fun `视觉档案为空时回落属性面板兜底`() {
        val p = PhotoPromptComposer.compose(
            name = "清月",
            visualAnchor = "",
            fallbackAppearance = "银发及腰，常穿月白长裙",
            historyLines = emptyList(),
            scene = "微笑"
        )
        assertTrue(p.contains("银发及腰，常穿月白长裙"))
        assertFalse(p.contains("没有可用的外貌设定"))
        // 无历史时不出现情境段
        assertFalse(p.contains("【当前情境"))
    }

    @Test
    fun `锚点与属性都缺失时给出占位提示而非留空`() {
        val p = PhotoPromptComposer.compose(name = "清月", visualAnchor = "", fallbackAppearance = "", scene = "挥手")
        assertTrue(p.contains("没有可用的外貌设定"))
        assertTrue(p.contains("形象一旦出现需在后续图片保持一致"))
    }

    @Test
    fun `历史行数与单行长度受限`() {
        val many = (1..20).map { "第${it}句".repeat(50) }   // 20 行 x 长文本
        val p = PhotoPromptComposer.compose(name = "清月", visualAnchor = "锚", historyLines = many, scene = "x")
        // 历史行最多 8 行，每行最多 60 字
        val ctxStart = p.indexOf("【当前情境")
        val ctxEnd = p.indexOf("【这张照片的内容】")
        val ctx = p.substring(ctxStart, ctxEnd)
        val lineCount = ctx.lines().count { it.startsWith("- ") }
        assertTrue("history line count=$lineCount", lineCount <= 8)
        ctx.lines().filter { it.startsWith("- ") }.forEach {
            assertTrue("line too long: ${it.length}", it.length <= 63)   // "- " 前缀 + 60
        }
    }

    @Test
    fun `画面描述超长被裁剪`() {
        val longScene = "风".repeat(1000)
        val p = PhotoPromptComposer.compose(name = "清月", visualAnchor = "锚", scene = longScene)
        val seg = p.substringAfter("【这张照片的内容】").substringBefore("要求：")
        assertTrue("scene too long: ${seg.trim().length}", seg.trim().length <= 300)
    }

    @Test
    fun `fallbackAppearance 组合规则`() {
        assertEquals("银发，常穿道袍", PhotoPromptComposer.fallbackAppearance("银发", "道袍"))
        assertEquals("银发", PhotoPromptComposer.fallbackAppearance("银发", "日常便装"))
        assertEquals("衣着长裙", PhotoPromptComposer.fallbackAppearance("未设定", "长裙"))
        assertEquals("", PhotoPromptComposer.fallbackAppearance("未设定", "日常便装"))
        assertEquals("", PhotoPromptComposer.fallbackAppearance("", ""))
    }
}
