package com.lirui.charchat.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 聊天消息。`seq` 为自增单调序号，用于"长按编辑截断续写"（Q4=B）：
 * 编辑消息 seq=i → 删除该卡下 seq>=i 的全部消息，从该轮续写。
 */
@Entity(
    tableName = "messages",
    indices = [Index("cardId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val cardId: String,
    val role: String,          // USER / CHARACTER / SYSTEM
    val text: String,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)
