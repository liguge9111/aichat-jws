package com.lirui.charchat.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 群聊消息。`senderId` 为 null 表示玩家，否则是发言角色的卡 id。
 * `senderName` 冗余存一份，避免删卡后历史消息无法显示发言人。
 */
@Entity(
    tableName = "group_messages",
    indices = [Index("groupId")]
)
data class GroupMessageEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val groupId: String,
    val senderId: String? = null,
    val senderName: String = "",
    val role: String,                 // USER / CHARACTER
    val text: String,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
