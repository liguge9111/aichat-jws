package com.lirui.charchat.data.backup

import android.util.Base64
import com.lirui.charchat.data.cardparser.CardMapper
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.ChatMessageDao
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import com.lirui.charchat.data.db.entity.ChatMessageEntity
import com.lirui.charchat.data.storage.FileStorage
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 还原时的冲突策略。 */
enum class RestoreMode { MERGE, OVERWRITE }

/**
 * P6：本地备份导出/导入。
 *
 * 导出：把全部角色卡 + 其聊天记录 + 头像(base64 内嵌) 序列化为一份 JSON，
 *      使备份自包含，换机或重装后头像不丢。
 * 导入：按 RestoreMode 决定同名(id)卡片是跳过还是覆盖。
 *
 * 注意：仅处理结构化文本，不复制"聊天照片"的二进制内容（历史上出过的图是本地文件，
 * 还原后 imagePath 仍保留，但文件可能不存在，UI 会显示占位——这是可接受的有损点）。
 */
@Singleton
class BackupManager @Inject constructor(
    private val cards: CharacterCardDao,
    private val messages: ChatMessageDao,
    private val storage: FileStorage
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun buildBackupJson(): String {
        val all = cards.listAll()
        val cardDtos = all.map { c ->
            BackupCard(
                id = c.id,
                name = c.name,
                description = c.description,
                personality = c.personality,
                scenario = c.scenario,
                firstMes = c.firstMes,
                mesExample = c.mesExample,
                avatarPath = c.avatarPath,
                avatarBase64 = c.avatarPath?.let { readAsBase64(it) },
                attributesJson = c.attributesJson,
                playerJson = c.playerJson,
                source = c.source,
                affection = c.affection,
                relationship = c.relationship,
                createdAt = c.createdAt,
                worldBookJson = c.worldBookJson,
                statusText = c.statusText,
                additionalNotes = c.additionalNotes,
                memories = c.memories,
                alternateGreetings = CardMapper.decodeGreetings(c.alternateGreetings),
                visualAnchor = c.visualAnchor,
                messages = messages.listForCard(c.id).map { m ->
                    BackupMessage(
                        role = m.role,
                        text = m.text,
                        imagePath = m.imagePath,
                        createdAt = m.createdAt
                    )
                }
            )
        }
        return json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                version = BackupFile.BACKUP_VERSION,
                exportedAt = System.currentTimeMillis(),
                cards = cardDtos
            )
        )
    }

    /**
     * 还原备份。返回成功导入的卡片数。
     * MERGE：已存在同 id 卡片则跳过；OVERWRITE：先删旧卡与其消息再写入。
     */
    suspend fun restoreFromJson(raw: String, mode: RestoreMode): Result<Int> {
        val file = runCatching {
            json.decodeFromString(BackupFile.serializer(), raw)
        }.getOrElse { return Result.failure(IllegalArgumentException("不是有效的备份文件：${it.message}")) }

        if (file.version > BackupFile.BACKUP_VERSION) {
            return Result.failure(IllegalArgumentException("备份版本 ${file.version} 高于当前支持的 ${BackupFile.BACKUP_VERSION}，请升级 App"))
        }

        return runCatching {
            var imported = 0
            file.cards.forEach { dto ->
                if (dto.id.isBlank() || dto.name.isBlank()) return@forEach
                val existing = cards.getById(dto.id)
                if (existing != null) {
                    if (mode == RestoreMode.MERGE) return@forEach
                    messages.clear(dto.id)
                    cards.delete(existing)
                }
                val avatarPath = dto.avatarBase64?.let { writeFromBase64(it) } ?: dto.avatarPath
                cards.insert(
                    CharacterCardEntity(
                        id = dto.id,
                        name = dto.name,
                        description = dto.description,
                        personality = dto.personality,
                        scenario = dto.scenario,
                        firstMes = dto.firstMes,
                        mesExample = dto.mesExample,
                        avatarPath = avatarPath,
                        attributesJson = dto.attributesJson,
                        playerJson = dto.playerJson,
                        source = dto.source,
                        affection = dto.affection,
                        relationship = dto.relationship,
                        createdAt = dto.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        worldBookJson = dto.worldBookJson,
                        statusText = dto.statusText,
                        additionalNotes = dto.additionalNotes,
                        memories = dto.memories,
                        alternateGreetings = CardMapper.encodeGreetings(dto.alternateGreetings),
                        visualAnchor = dto.visualAnchor
                    )
                )
                messages.insertAll(
                    dto.messages.map { m ->
                        ChatMessageEntity(
                            cardId = dto.id,
                            role = m.role,
                            text = m.text,
                            imagePath = m.imagePath,
                            createdAt = m.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                        )
                    }
                )
                imported++
            }
            imported
        }
    }

    private fun readAsBase64(path: String): String? = runCatching {
        val f = File(path)
        if (!f.exists()) return null
        Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
    }.getOrNull()

    private fun writeFromBase64(b64: String): String? = runCatching {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        if (bytes.isEmpty()) return null
        storage.saveAvatar(bytes)
    }.getOrNull()
}
