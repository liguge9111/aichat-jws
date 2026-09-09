package com.lirui.charchat.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 群聊。成员为角色卡 id 列表（JSON 数组字符串），
 * 群聊本身不复制角色数据，始终引用 cards 表，避免成员属性演进后不同步。
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberIdsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)
