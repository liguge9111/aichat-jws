package com.lirui.charchat.data.group

import com.lirui.charchat.data.db.entity.GroupEntity
import com.lirui.charchat.data.db.entity.GroupMessageEntity
import com.lirui.charchat.domain.model.Group
import com.lirui.charchat.domain.model.GroupMessage
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** domain Group/GroupMessage ↔ Room 实体互转；成员 id 列表以 JSON 数组存储。 */
object GroupMapper {
    private val json = Json { ignoreUnknownKeys = true }
    private val idsSerializer = ListSerializer(serializer<String>())

    fun toDomain(e: GroupEntity): Group = Group(
        id = e.id,
        name = e.name,
        memberIds = runCatching {
            json.decodeFromString(idsSerializer, e.memberIdsJson)
        }.getOrDefault(emptyList()),
        createdAt = e.createdAt
    )

    fun toEntity(g: Group): GroupEntity = GroupEntity(
        id = g.id,
        name = g.name,
        memberIdsJson = json.encodeToString(idsSerializer, g.memberIds),
        createdAt = g.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
    )

    fun toDomainMessage(e: GroupMessageEntity): GroupMessage = GroupMessage(
        seq = e.seq,
        senderId = e.senderId,
        senderName = e.senderName,
        isUser = e.role == "USER",
        text = e.text,
        imagePath = e.imagePath,
        createdAt = e.createdAt
    )
}
