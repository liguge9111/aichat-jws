package com.lirui.charchat.domain.worldbook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 世界书条目（兼容 SillyTavern character_book entries 字段名，
 * 可直接解码 Tavern 原始 JSON，也可用本自定义结构保存与编辑）。
 */
@Serializable
data class WorldEntry(
    val id: String = "",
    @SerialName("keys") val keys: List<String> = emptyList(),
    @SerialName("content") val content: String = "",
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("constant") val constant: Boolean = false,
    @SerialName("insertion_order") val insertionOrder: Int = 0
)

/** 自维护的世界书结构（只存条目，忽略 Tavern 顶层附加信息）。 */
@Serializable
data class WorldBook(
    val version: Int = 1,
    val entries: List<WorldEntry> = emptyList()
)

/** 世界书解析/序列化工具。 */
object WorldBookJson {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 读取任意存量 JSON（Tavern character_book 或本应用结构），缺失/非法返回空。 */
    fun parse(raw: String?): List<WorldEntry> {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString(WorldBook.serializer(), text).entries
        }.getOrElse {
            // 兜底：若顶层没有 entries（异常结构），尝试取数组
            runCatching {
                val elem = Json.parseToJsonElement(text)
                val arr = (elem as? kotlinx.serialization.json.JsonObject)
                    ?.get("entries") as? kotlinx.serialization.json.JsonArray
                    ?: return@runCatching emptyList()
                arr.mapNotNull { el ->
                    runCatching {
                        json.decodeFromJsonElement(WorldEntry.serializer(), el)
                    }.getOrNull()
                }
            }.getOrDefault(emptyList())
        }
    }

    fun toJson(entries: List<WorldEntry>): String =
        json.encodeToString(WorldBook.serializer(), WorldBook(entries = entries))
}
