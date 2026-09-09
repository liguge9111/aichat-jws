package com.lirui.charchat.domain.repository

import android.content.Context
import com.lirui.charchat.data.cardparser.CardMapper
import com.lirui.charchat.data.cardparser.SillyTavernParser
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.domain.model.CharacterCard
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/** 导入结果。 */
sealed interface ImportOutcome {
    data class Success(val card: CharacterCard, val avatarBytes: ByteArray? = null) : ImportOutcome
    data class Error(val message: String) : ImportOutcome
}

/**
 * 角色卡导入服务：解析 → 存头像文件 → 落库。
 * 解析失败时返回 Error，不写库（见 §8.3 边界：不支持的卡格式不写入）。
 */
class CardImportService @Inject constructor(
    private val dao: CharacterCardDao,
    private val parser: SillyTavernParser,
    @ApplicationContext private val context: Context
) {
    suspend fun importJson(text: String): ImportOutcome = when (val r = parser.parseJsonText(text)) {
        is SillyTavernParser.ParseResult.Success -> persist(r.card, r.avatarBytes)
        is SillyTavernParser.ParseResult.Error -> ImportOutcome.Error(r.message)
    }

    suspend fun importPng(bytes: ByteArray): ImportOutcome = when (val r = parser.parsePng(bytes)) {
        is SillyTavernParser.ParseResult.Success -> persist(r.card, r.avatarBytes)
        is SillyTavernParser.ParseResult.Error -> ImportOutcome.Error(r.message)
    }

    /** 保存（含用户编辑后的）角色卡。 */
    suspend fun saveCard(card: CharacterCard, avatarBytes: ByteArray?): ImportOutcome =
        persist(card, avatarBytes)

    private suspend fun persist(card: CharacterCard, avatarBytes: ByteArray?): ImportOutcome {
        return runCatching {
            val avatarPath = avatarBytes?.let { saveAvatar(card.id, it) }
            val entity = CardMapper.toEntity(card, avatarPath)
            dao.insert(entity)
            ImportOutcome.Success(CardMapper.toDomain(entity), avatarBytes)
        }.getOrElse { ImportOutcome.Error("保存失败：${it.message}") }
    }

    private fun saveAvatar(id: String, bytes: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(dir, "$id.png")
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()
}
