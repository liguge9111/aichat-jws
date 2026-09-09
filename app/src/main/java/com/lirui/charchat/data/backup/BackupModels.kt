package com.lirui.charchat.data.backup

import kotlinx.serialization.Serializable

/** 备份中的一条消息。 */
@Serializable
data class BackupMessage(
    val role: String,
    val text: String,
    val imagePath: String? = null,
    val createdAt: Long = 0L
)

/** 备份中的一个角色（含其全部聊天记录与内嵌头像）。 */
@Serializable
data class BackupCard(
    val id: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    /** 头像原路径，仅作参考；还原时优先用 avatarBase64 重建本地文件。 */
    val avatarPath: String? = null,
    val avatarBase64: String? = null,
    val attributesJson: String = "{}",
    val playerJson: String = "{}",
    val source: String = "JSON",
    val affection: Int = 0,
    val relationship: String = "陌生人",
    val createdAt: Long = 0L,
    val worldBookJson: String = "",
    val statusText: String = "",
    val additionalNotes: String = "",
    val memories: String = "",
    /** 备选开场白列表（SillyTavern alternate_greetings）。 */
    val alternateGreetings: List<String> = emptyList(),
    /** 角色视觉档案（v8）：图像一致性锚点，导出/还原随卡走。 */
    val visualAnchor: String = "",
    val messages: List<BackupMessage> = emptyList()
)

/**
 * 备份文件结构。
 * 设计要点：头像以 base64 内嵌，使备份自包含（换机/重装后头像不丢）。
 */
@Serializable
data class BackupFile(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = 0L,
    val cards: List<BackupCard> = emptyList()
) {
    companion object {
        const val BACKUP_VERSION = 1
    }
}
