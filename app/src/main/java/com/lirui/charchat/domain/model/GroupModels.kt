package com.lirui.charchat.domain.model

/** 群聊（成员引用 cards 表的 id，不复制角色数据）。 */
data class Group(
    val id: String,
    val name: String,
    val memberIds: List<String> = emptyList(),
    val createdAt: Long = 0L
)

/** 群聊消息视图（domain 层，避免纯函数依赖 Room 实体）。 */
data class GroupMessage(
    val seq: Long = 0L,
    val senderId: String? = null,   // null 表示玩家
    val senderName: String = "",
    val isUser: Boolean = false,
    val text: String = "",
    val imagePath: String? = null,
    val createdAt: Long = 0L
)
