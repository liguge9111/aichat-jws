package com.lirui.charchat.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 角色卡持久化。攻略属性(StrategyAttributes)与玩家背景(PlayerProfile)以 JSON 字符串存储，
 * 读取时由 AttributeEngine 反序列化（避免 Room 嵌入复杂对象）。
 */
@Entity(tableName = "cards")
data class CharacterCardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val personality: String,
    val scenario: String,
    val firstMes: String,
    val mesExample: String,
    val avatarPath: String?,
    val attributesJson: String,
    val playerJson: String,
    val source: String,
    val affection: Int = 0,
    val relationship: String = "陌生人",
    val createdAt: Long = System.currentTimeMillis(),
    /** 世界书原始 JSON（Tavern character_book），v3 起支持。 */
    val worldBookJson: String = "",
    /** 角色状态栏文本，随对话由模型经 [[STATUS:...]] 更新。 */
    val statusText: String = "",
    /** 已达成的长期约定/追加设定（一行一条，经 [[NOTE:...]] 固化）。 */
    val additionalNotes: String = "",
    /** 绑定的玩家档案 id（v5，null=首次进入聊天时选定）。 */
    val profileId: String? = null,
    /**
     * 共同回忆（v6，一行一条）：玩家在对话中讲起的"我们之前一起做过的事"，
     * 由角色经 [[MEM:...]] 认可后固化，之后每轮注入，避免"聊过就忘"。
     */
    val memories: String = "",
    /**
     * 备选开场白（v7，JSON 字符串数组）：SillyTavern 卡的 alternate_greetings。
     * 首次进入聊天时与 firstMes 一起作为候选，供玩家选择用哪条开场。
     */
    val alternateGreetings: String = "[]",
    /**
     * 角色视觉档案（v8）：提炼后固定不变的中文外貌基线（发色/发型/瞳色/服装等），
     * 每次图像生成都拼入提示词以保证跨图一致性；空串 = 尚未提炼。
     */
    val visualAnchor: String = ""
)
