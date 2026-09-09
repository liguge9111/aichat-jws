package com.lirui.charchat.data.cardparser

import com.lirui.charchat.domain.model.CardSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32

class SillyTavernParserTest {

    private val parser = SillyTavernParser()

    @Test
    fun parseJsonText_v2FieldsAndSegments() {
        val raw = """
        {
          "name": "测试角色",
          "description": "Appearance: 黑色长发，红瞳\nOutfit: 黑色连衣裙\nPersonality: 傲娇但温柔\nLocation: 咖啡厅\nDoing: 看书\nSexual: 轻度支配",
          "personality": "表面冷淡",
          "scenario": "现代都市",
          "first_mes": "你好。",
          "mes_example": "A: hi\nB: hello"
        }
        """.trimIndent()
        val r = parser.parseJsonText(raw) as SillyTavernParser.ParseResult.Success
        assertEquals("测试角色", r.card.name)
        assertEquals("现代都市", r.card.scenario)
        assertEquals("你好。", r.card.firstMes)
        assertEquals("A: hi\nB: hello", r.card.mesExample)
        assertEquals(CardSource.JSON, r.card.source)
        // 酒馆化：设定以原文保存（description/personality 原样），不再做字段化拆解
        assertTrue(r.card.description.contains("Appearance: 黑色长发，红瞳"))
        assertTrue(r.card.description.contains("Personality: 傲娇但温柔"))
        assertEquals("表面冷淡", r.card.personality)
        assertNull(r.avatarBytes)
    }

    @Test
    fun parseJsonText_charAiV1Aliases() {
        val raw = """
        {
          "char_name": "爱丽丝",
          "char_persona": "温柔的魔法少女",
          "char_greeting": "欢迎来到我的世界",
          "world_scenario": "幻想大陆",
          "example_dialogue": "A: 你好\nB: 你好呀"
        }
        """.trimIndent()
        val r = parser.parseJsonText(raw) as SillyTavernParser.ParseResult.Success
        assertEquals("爱丽丝", r.card.name)
        assertEquals("温柔的魔法少女", r.card.description)
        assertEquals("欢迎来到我的世界", r.card.firstMes)
        assertEquals("幻想大陆", r.card.scenario)
        assertEquals("A: 你好\nB: 你好呀", r.card.mesExample)
    }

    @Test
    fun parseJsonText_defaultsWhenNoSegments() {
        val raw = """{"name":"x","description":"一个普通女孩"}"""
        val r = parser.parseJsonText(raw) as SillyTavernParser.ParseResult.Success
        with(r.card.attributes) {
            assertEquals("未设定", appearance)
            assertEquals("日常便装", clothing)
            assertEquals("空闲", doing)
            assertEquals("未设定", kink)
            assertEquals("未设定", location)
        }
    }

    @Test
    fun parseJsonText_invalidReturnsError() {
        val r = parser.parseJsonText("{ this is not json ")
        assertTrue(r is SillyTavernParser.ParseResult.Error)
    }

    @Test
    fun parseJsonText_avatarBase64() {
        val raw = """{"name":"x","description":"d","avatar":"AAAA"}"""
        val r = parser.parseJsonText(raw) as SillyTavernParser.ParseResult.Success
        assertTrue(r.avatarBytes != null && r.avatarBytes!!.size == 3) // "AAAA" -> 3 bytes
    }

    @Test
    fun parseJsonText_alternateGreetings_v2Style() {
        val raw = """
        {
          "name": "多开场角色",
          "description": "d",
          "first_mes": "默认开场：你好呀。",
          "alternate_greetings": ["备选一：我们在雨夜相遇。", "备选二：你今天来晚了哦。", "   "]
        }
        """.trimIndent()
        val r = parser.parseJsonText(raw) as SillyTavernParser.ParseResult.Success
        assertEquals("默认开场：你好呀。", r.card.firstMes)
        assertEquals(
            listOf("备选一：我们在雨夜相遇。", "备选二：你今天来晚了哦。"),
            r.card.alternateGreetings
        )
    }

    @Test
    fun parseJsonText_alternateGreetings_missingOrEmpty() {
        val raw = """{"name":"无备选","description":"d","first_mes":"仅默认"}"""
        val r = parser.parseJsonText(raw) as SillyTavernParser.ParseResult.Success
        assertTrue(r.card.alternateGreetings.isEmpty())
    }

    @Test
    fun parsePng_charaEmbedded() {
        val cardJson = """{"name":"PNG角色","description":"Appearance: 金发"}"""
        val png = buildPngWithChara(cardJson)
        val r = parser.parsePng(png) as SillyTavernParser.ParseResult.Success
        assertEquals("PNG角色", r.card.name)
        assertEquals("Appearance: 金发", r.card.description)
        // PNG 本身作为头像
        assertTrue(r.avatarBytes != null && r.avatarBytes!!.contentEquals(png))
    }

    @Test
    fun parsePng_nonPngReturnsError() {
        val r = parser.parsePng("not a png".toByteArray(StandardCharsets.UTF_8))
        assertTrue(r is SillyTavernParser.ParseResult.Error)
    }

    // ---- 构造最小合法 PNG（signature + IHDR + tEXt:chara + IEND）----

    private fun buildPngWithChara(cardJson: String): ByteArray {
        val sig = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        val ihdr = chunk("IHDR", ByteArray(13))
        val b64 = Base64.getEncoder().encodeToString(cardJson.toByteArray(StandardCharsets.UTF_8))
        // tEXt 数据格式：keyword(ASCII) + 0x00 + 文本（base64）
        val tEXtData = "chara".toByteArray(StandardCharsets.ISO_8859_1) +
            byteArrayOf(0) +
            b64.toByteArray(StandardCharsets.UTF_8)
        val tEXt = chunk("tEXt", tEXtData)
        val iend = chunk("IEND", ByteArray(0))
        return sig + ihdr + tEXt + iend
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.ISO_8859_1)
        val out = ByteArray(8 + data.size + 4)
        writeInt(out, 0, data.size)                 // length (BE)
        System.arraycopy(typeBytes, 0, out, 4, 4)  // type
        System.arraycopy(data, 0, out, 8, data.size) // data
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(out, 8 + data.size, crc.value.toInt()) // crc (BE)
        return out
    }

    private fun writeInt(out: ByteArray, off: Int, v: Int) {
        out[off] = (v shr 24).toByte()
        out[off + 1] = (v shr 16).toByte()
        out[off + 2] = (v shr 8).toByte()
        out[off + 3] = v.toByte()
    }
}
