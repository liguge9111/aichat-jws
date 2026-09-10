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
    /** 语音消息本地音频路径（v9）；为空表示这是条纯文字/图片消息。 */
    val audioPath: String? = null,
    /** 语音时长（毫秒），用于气泡展示"3\"\""。 */
    val durationMs: Long = 0,
    /** 是否为语音消息（v9）：玩家长按录音发出，或角色语音回复。 */
    val isVoice: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)
