package com.lirui.charchat.domain.chat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 世界书（Tavern character_book / world）关键字触发注入。纯函数，便于单测。
 *
 * 规则：
 * - 解析已保存的原始 book JSON（无需 @Serializable 模型，字段缺失全部容错）。
 * - 每条目：keys 命中任意关键字即激活（默认忽略大小写，尊重 case_sensitive）；
 *   constant=true 的条目常驻（无需命中）。
 * - 结果按 insertion_order 稳定排序；默认最多取 8 条，单条内容截断 800 字符，
 *   总注入量受 MAX_TOTAL 限制，避免把世界书整个塞进上下文。
 */
object WorldBookTrigger {

    data class Entry(val keys: List<String>, val content: String, val order: Int, val constant: Boolean)

    const val MAX_ENTRIES = 10
    const val MAX_ENTRY_LEN = 800
    const val MAX_TOTAL_LEN = 3000

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析 book JSON → 激活条目列表（已排序、已截断）。 */
    fun matches(bookJson: String?, vararg texts: String): List<String> {
        val book = bookJson?.takeIf { it.isNotBlank() } ?: return emptyList()
        val entries = parseEntries(book)
        if (entries.isEmpty()) return emptyList()

        val haystack = texts.filter { it.isNotBlank() }.joinToString("\n")

        val active = entries
            .filter { e ->
                e.constant || e.keys.isEmpty() ||
                    (haystack.isNotEmpty() && e.keys.any { key ->
                        val k = key.trim()
                        if (k.isEmpty()) false
                        else if (haystack.contains(k, ignoreCase = true)) true
                        else false
                    })
            }
            .sortedBy { it.order }
            .take(MAX_ENTRIES)

        val out = ArrayList<String>()
        var total = 0
        for (e in active) {
            val c = e.content.trim()
            if (c.isEmpty()) continue
            val piece = if (c.length > MAX_ENTRY_LEN) c.take(MAX_ENTRY_LEN) else c
            if (total + piece.length > MAX_TOTAL_LEN) break
            out.add(piece)
            total += piece.length
        }
        return out
    }

    private fun parseEntries(bookJson: String): List<Entry> = runCatching {
        val root = json.parseToJsonElement(bookJson).jsonObject
        val entries = (root["entries"] as? kotlinx.serialization.json.JsonArray) ?: return emptyList()
        entries.mapNotNull { el ->
            runCatching {
                val o = el.jsonObject
                val enabled = (o["enabled"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.contentOrNull?.toBooleanStrictOrNull() ?: true
                if (!enabled) return@mapNotNull null
                val keysArr = (o["keys"] as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(emptyList())
                val keys = keysArr.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                val content = (o["content"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: ""
                val order = (o["insertion_order"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.contentOrNull?.toIntOrNull() ?: 0
                val constant = (o["constant"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.contentOrNull?.toBooleanStrictOrNull() ?: false
                Entry(keys, content, order, constant)
            }.getOrNull()
        }
    }.getOrDefault(emptyList())
}
