package com.lirui.charchat.data.cardparser

import com.lirui.charchat.data.db.entity.CharacterCardEntity
import com.lirui.charchat.domain.model.CardSource
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.model.StrategyAttributes
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** domain CharacterCard ↔ Room CharacterCardEntity 互转；属性/玩家以 JSON 存储。 */
object CardMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSer = ListSerializer(String.serializer())

    /** 备选开场白列表 → 存储 JSON（'[]' 兜底）。 */
    fun encodeGreetings(list: List<String>): String = runCatching {
        json.encodeToString(stringListSer, list.filter { it.isNotBlank() })
    }.getOrDefault("[]")

    /** 存储 JSON → 备选开场白列表（脏数据回落空表）。 */
    fun decodeGreetings(jsonStr: String): List<String> = runCatching {
        json.decodeFromString(stringListSer, jsonStr).map { it.trim() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())

    fun toEntity(card: CharacterCard, avatarPath: String?): CharacterCardEntity =
        CharacterCardEntity(
            id = card.id,
            name = card.name,
            description = card.description,
            personality = card.personality,
            scenario = card.scenario,
            firstMes = card.firstMes,
            mesExample = card.mesExample,
            avatarPath = avatarPath ?: card.avatarPath,
            attributesJson = json.encodeToString(StrategyAttributes.serializer(), card.attributes),
            playerJson = json.encodeToString(PlayerProfile.serializer(), card.player),
            source = card.source.name,
            affection = card.attributes.affection,
            relationship = card.attributes.relationship,
            worldBookJson = card.worldBookJson,
            statusText = card.statusText,
            additionalNotes = card.additionalNotes,
            memories = card.memories,
            alternateGreetings = encodeGreetings(card.alternateGreetings),
            visualAnchor = card.visualAnchor
        )

    fun toDomain(entity: CharacterCardEntity): CharacterCard {
        val attrs = runCatching {
            json.decodeFromString(StrategyAttributes.serializer(), entity.attributesJson)
        }.getOrDefault(StrategyAttributes())
        val player = runCatching {
            json.decodeFromString(PlayerProfile.serializer(), entity.playerJson)
        }.getOrDefault(PlayerProfile())
        return CharacterCard(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            personality = entity.personality,
            scenario = entity.scenario,
            firstMes = entity.firstMes,
            mesExample = entity.mesExample,
            avatarPath = entity.avatarPath,
            attributes = attrs.copy(affection = entity.affection, relationship = entity.relationship),
            player = player,
            source = runCatching { CardSource.valueOf(entity.source) }.getOrDefault(CardSource.JSON),
            worldBookJson = entity.worldBookJson,
            statusText = entity.statusText,
            additionalNotes = entity.additionalNotes,
            memories = entity.memories,
            alternateGreetings = decodeGreetings(entity.alternateGreetings),
            visualAnchor = entity.visualAnchor
        )
    }
}
