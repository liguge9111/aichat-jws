package com.lirui.charchat.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 玩家档案（v5 起：玩家设定从卡片中独立，支持多档案维护与切换）。
 * 一个档案 = 一套玩家背景（名字/性格/与角色的默认关系/补充），
 * 聊天前绑定到具体卡片：绑定关系存在 cards.profileId。
 */
@Entity(tableName = "player_profiles")
data class PlayerProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val personality: String = "",
    val relationToChar: String = "",
    val extra: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** 玩家头像本地路径（v6，可自定义；null=用默认占位）。 */
    val avatarPath: String? = null
)
