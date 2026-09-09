package com.lirui.charchat.domain.chat

import com.lirui.charchat.domain.model.CardSource
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.StrategyAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 视觉档案：提炼模板组装与响应解析（纯函数层）。 */
class VisualAnchorTest {

    private fun card(
        name: String = "清月",
        description: String = "仙门师尊，喜着白衣",
        personality: String = "清冷",
        appearance: String = "未设定",
        clothing: String = "日常便装"
    ) = CharacterCard(
        id = "c1",
        name = name,
        description = description,
        personality = personality,
        scenario = "古风仙侠宗门",
        attributes = StrategyAttributes(appearance = appearance, clothing = clothing)
    )

    @Test
    fun `buildSource 汇总卡资料且跳过未设定项`() {
        val src = VisualAnchor.buildSource(card())
        assertTrue(src.contains("清月"))
        assertTrue(src.contains("仙门师尊"))
        assertTrue(src.contains("古风仙侠"))
        // appearance=未设定 / clothing=日常便装 应被跳过
        assertFalse(src.contains("外貌（属性面板）"))
        assertFalse(src.contains("穿着（属性面板）"))
    }

    @Test
    fun `buildSource 收录属性面板的显式外貌穿着`() {
        val src = VisualAnchor.buildSource(card(appearance = "银发及腰", clothing = "月白长裙"))
        assertTrue(src.contains("银发及腰"))
        assertTrue(src.contains("月白长裙"))
    }

    @Test
    fun `buildPrompt 要求严格输出格式标签`() {
        val p = VisualAnchor.buildPrompt(card())
        assertTrue(p.contains("<视觉档案>"))
        assertTrue(p.contains("</视觉档案>"))
        assertTrue(p.contains("严禁自行编造"))
    }

    @Test
    fun `parse 提取标签内容并压成单行`() {
        val raw = "好的，以下是提炼结果：\n<视觉档案>她有着\n雪白长发，浅金瞳色，身形清瘦，常着月白道袍。</视觉档案>\n完毕"
        val a = VisualAnchor.parse(raw)
        assertEquals("她有着 雪白长发，浅金瞳色，身形清瘦，常着月白道袍。", a)
    }

    @Test
    fun `parse 无标签时取原文去代码块`() {
        val raw = "```\n银发紫瞳，气质清冷。\n```"
        val a = VisualAnchor.parse(raw)
        assertFalse(a.contains("```"))
        assertTrue(a.contains("银发紫瞳"))
    }

    @Test
    fun `parse 裁剪到 200 字`() {
        val long = "<视觉档案>" + "甲".repeat(500) + "</视觉档案>"
        val a = VisualAnchor.parse(long)
        assertTrue(a.length <= 200)
    }

    @Test
    fun `parse 空输入返回空串`() {
        assertEquals("", VisualAnchor.parse(""))
        assertEquals("", VisualAnchor.parse("   \n  "))
    }
}
