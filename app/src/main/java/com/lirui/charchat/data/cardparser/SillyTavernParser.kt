package com.lirui.charchat.data.cardparser

import com.lirui.charchat.domain.model.CardSource
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.model.StrategyAttributes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import java.util.Base64

/**
 * SillyTavern / TavernAI 角色卡解析器。
 * - V2/V3 PNG 嵌卡：读 tEXt:chara（base64 → JSON），PNG 本身作为头像。
 * - V1 / 纯 JSON：直接解析，兼容 char_name/char_persona/char_greeting/world_scenario/example_dialogue 等别名。
 * - 分段抽取：从 description 按「关键词:」切出 外貌/穿着/性格/位置/在做什么/性癖，缺段回落默认。
 * 仅用 java.util.Base64（Android/ JVM 通用，便于单测），不依赖 Android 类。
 */
class SillyTavernParser @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface ParseResult {
        data class Success(val card: CharacterCard, val avatarBytes: ByteArray? = null) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    fun parsePng(bytes: ByteArray): ParseResult {
        if (!isPng(bytes)) return ParseResult.Error("不是有效的 PNG 文件")
        val charaB64 = findCharaText(bytes)
            ?: return ParseResult.Error("PNG 中未找到角色卡数据（缺少 tEXt:chara）")
        val jsonText = runCatching { decodeB64(charaB64) }
            .getOrElse { return ParseResult.Error("chara 字段 base64 解码失败：${it.message}") }
        return when (val r = parseJsonText(jsonText)) {
            is ParseResult.Success -> r.copy(avatarBytes = bytes) // PNG 本身即头像
            is ParseResult.Error -> r
        }
    }

    fun parseJsonText(text: String): ParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ParseResult.Error("内容为空")
        val element = runCatching { json.parseToJsonElement(trimmed) }
            .getOrElse { return ParseResult.Error("JSON 解析失败：${it.message}") }
        if (element !is JsonObject) return ParseResult.Error("角色卡 JSON 根必须是对象")

        // V3 卡数据包在顶层 data 下，其余版本在顶层。取最深层可用对象。
        val dataObj = (element["data"] as? JsonObject) ?: element

        val name = pick(dataObj, "name", "char_name") ?: "未命名"
        val description = pick(dataObj, "description", "char_persona") ?: ""
        val personalityField = pick(dataObj, "personality") ?: ""
        val scenario = pick(dataObj, "scenario", "world_scenario") ?: ""
        val firstMes = pick(dataObj, "first_mes", "char_greeting") ?: ""
        val mesExample = pick(dataObj, "mes_example", "example_dialogue") ?: ""
        // 备选开场白：ST V2/V3 的 alternate_greetings 字符串数组；data 与顶层都可能出现。
        val alternateGreetings = pickStringList(dataObj, "alternate_greetings")
            .ifEmpty { pickStringList(element, "alternate_greetings") }

        val attrs = StrategyAttributes()   // 好感/关系归运行时系统，不再做字段化拆解
        // 状态栏：优先解析 description/personality 里的 状态/Status/Stats 段
        val statusBar = extractSegment(
            "$description\n$personalityField",
            listOf("Status", "状态", "状态栏", "Stats")
        ) ?: ""

        // 世界书：V2/V3 顶层或 data 下的 character_book；TavernAI V1 顶层 world
        val bookJson = extractWorldBook(element, dataObj)

        val avatarB64 = pick(element, "avatar") ?: pick(dataObj, "avatar")
        val avatarBytes = if (!avatarB64.isNullOrBlank()) {
            runCatching { Base64.getDecoder().decode(avatarB64) }.getOrNull()
        } else null

        val card = CharacterCard(
            id = generateId(name),
            name = name,
            description = description,
            personality = personalityField.ifBlank { attrs.personality },
            scenario = scenario,
            firstMes = firstMes,
            mesExample = mesExample,
            alternateGreetings = alternateGreetings,
            attributes = attrs,
            player = PlayerProfile(),
            source = CardSource.JSON,
            worldBookJson = bookJson,
            statusText = statusBar.trim()
        )
        return ParseResult.Success(card, avatarBytes)
    }

    /** 取世界书：先 V2/V3（data/顶层 character_book），再 TavernAI（顶层 world）。 */
    private fun extractWorldBook(root: JsonObject, data: JsonObject): String {
        val candidate = (data["character_book"] as? JsonObject)
            ?: (root["character_book"] as? JsonObject)
            ?: (root["world"] as? JsonObject)
        return candidate?.let { runCatching { json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it) }.getOrNull() }
            ?: ""
    }

    // ---- 分段抽取 ----

    /**
     * 从文本抽取「Label: 内容」，内容截到下一个同类标签或结尾。
     * 启发式，覆盖中英常见写法；漏抽时回落默认，不影响导入。
     */
    private fun extractSegment(text: String, labels: List<String>): String? {
        val lookahead = listOf(
            "Appearance", "外貌", "外表", "Outfit", "Clothing", "穿着", "服装",
            "Personality", "性格", "Location", "位置", "所在", "Doing", "在做什么",
            "Sexual", "NSFW", "性癖", "性向", "偏好"
        ).joinToString("|") { Regex.escape(it) }
        for (label in labels) {
            val pattern = Regex(
                "(?is)${Regex.escape(label)}\\s*[:：]\\s*(.+?)(?=\\n\\s*(?:$lookahead)\\s*[:：]|$)"
            )
            val m = pattern.find(text) ?: continue
            val value = m.groupValues[1].trim().trimEnd('\n').trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun pick(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val v = obj[k] ?: continue
            if (v is JsonObject) continue
            val s = (v as? JsonPrimitive)?.contentOrNull?.trim()
            if (!s.isNullOrBlank()) return s
        }
        return null
    }

    /** 取字符串数组字段（alternate_greetings 等），元素逐一 trim 并去空。 */
    private fun pickStringList(obj: JsonObject, key: String): List<String> {
        val arr = obj[key] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun decodeB64(s: String): String = String(Base64.getDecoder().decode(s), Charsets.UTF_8)

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
                0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
        )

    /** 遍历 PNG 块，找 tEXt 中 keyword=="chara" 的文本。 */
    private fun findCharaText(bytes: ByteArray): String? {
        var i = 8
        while (i + 8 <= bytes.size) {
            val len = readInt(bytes, i)
            val type = String(bytes.copyOfRange(i + 4, i + 8), Charsets.ISO_8859_1)
            val dataStart = i + 8
            val dataEnd = dataStart + len
            if (type == "tEXt" && dataEnd <= bytes.size) {
                val data = bytes.copyOfRange(dataStart, dataEnd)
                val zero = data.indexOf(0)
                if (zero > 0) {
                    val keyword = String(data.copyOfRange(0, zero), Charsets.ISO_8859_1)
                    if (keyword == "chara") {
                        return String(data.copyOfRange(zero + 1, data.size), Charsets.UTF_8)
                    }
                }
            }
            if (type == "IEND") break
            i = dataEnd + 4 // 跳过 CRC
        }
        return null
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun generateId(name: String): String =
        "${name}_${System.currentTimeMillis()}_${(0..9999).random()}"
}
