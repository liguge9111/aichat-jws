package com.lirui.charchat.domain.model

import kotlinx.serialization.Serializable

/** 卡来源。 */
enum class CardSource { PNG, JSON }

/**
 * 攻略对象属性（§4.1）：解析优先，缺省默认。
 * affection/relationship 运行时可被 AttributeEngine 自然演进（P5）。
 */
@Serializable
data class StrategyAttributes(
    val appearance: String = "未设定",   // 外貌
    val clothing: String = "日常便装",   // 穿着
    val personality: String = "未设定",  // 性格
    val location: String = "未设定",     // 所在位置
    val doing: String = "空闲",          // 在做什么
    val kink: String = "未设定",         // 性癖（NSFW 允许）
    val affection: Int = 0,              // 0..100
    val relationship: String = "陌生人"  // 关系阶段
)

/** 玩家背景设定（§4.2）。 */
@Serializable
data class PlayerProfile(
    val name: String = "玩家",
    val personality: String = "",
    val relationToChar: String = "",     // 例如"网友/同事/青梅竹马"
    val extra: String = ""
)

/** 解析后的角色卡（domain）。 */
data class CharacterCard(
    val id: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMes: String = "",
    val mesExample: String = "",
    val avatarPath: String? = null,
    val attributes: StrategyAttributes = StrategyAttributes(),
    val player: PlayerProfile = PlayerProfile(),
    val source: CardSource = CardSource.JSON,
    /** 世界书（Tavern character_book）原始 JSON，运行时按关键字触发注入。 */
    val worldBookJson: String = "",
    /** 角色状态栏（当前身体/情绪/处境等，随对话由模型更新），空表示该角色无状态栏。 */
    val statusText: String = "",
    /**
     * 已达成的长期约定/追加设定（一行一条）。由角色在对话中认可玩家提出的
     * 新设定后，经隐藏信号 [[NOTE:…]] 固化为记忆，每轮注入提示词（永不遗忘）。
     */
    val additionalNotes: String = "",
    /**
     * 共同回忆（一行一条）：玩家在对话中讲起的"我们之前一起经历过的事"，
     * 角色经隐藏信号 [[MEM:…]] 认可后固化，每轮注入，保证后续对话能接得住。
     */
    val memories: String = "",
    /**
     * 备选开场白：导入角色卡 alternate_greetings 得到的候选项。
     * 首次进入聊天弹框时与 firstMes 一起展示，让玩家挑一条再开聊。
     */
    val alternateGreetings: List<String> = emptyList(),
    /**
     * 角色视觉档案（v8）：提炼后固定不变的中文外貌基线，每次出图拼入提示词保证跨图一致性。
     * 空串 = 尚未提炼（首次出图前自动提炼一次并落库）。
     */
    val visualAnchor: String = ""
)
