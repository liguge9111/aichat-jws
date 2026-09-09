package com.lirui.charchat.domain.repository

import com.lirui.charchat.data.db.dao.ChatMessageDao
import com.lirui.charchat.data.db.entity.ChatMessageEntity
import com.lirui.charchat.data.remote.model.ChatMsg
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 聊天消息仓储：包装 ChatMessageDao，提供插入/观察/截断，以及把历史映射为模型上下文。
 * 截断（Q4=B）：编辑或重生成时按 seq 删除该轮及其后全部消息。
 */
class MessageRepository @Inject constructor(
    private val dao: ChatMessageDao
) {
    fun observe(cardId: String): Flow<List<ChatMessageEntity>> = dao.observeForCard(cardId)

    suspend fun insertUser(cardId: String, text: String): Long =
        dao.insert(ChatMessageEntity(cardId = cardId, role = "USER", text = text))

    suspend fun insertCharacter(cardId: String, text: String, imagePath: String? = null): Long =
        dao.insert(ChatMessageEntity(cardId = cardId, role = "CHARACTER", text = text, imagePath = imagePath))

    suspend fun updateImagePath(seq: Long, path: String) = dao.updateImagePath(seq, path)

    /** 直接查库计数（避免依赖 UI 状态的时序竞态，用于判断是否需要播种开场白）。 */
    suspend fun count(cardId: String): Int = dao.countForCard(cardId)

    suspend fun truncateFrom(cardId: String, fromSeq: Long) = dao.truncateFrom(cardId, fromSeq)

    suspend fun clear(cardId: String) = dao.clear(cardId)

    /** 历史上下文：最近 window 条（USER→user，CHARACTER→assistant），供模型续写。 */
    suspend fun getHistory(cardId: String, window: Int = 40): List<ChatMsg> {
        return dao.listForCard(cardId).takeLast(window).mapNotNull { e ->
            when (e.role) {
                "USER" -> ChatMsg("user", e.text)
                "CHARACTER" -> ChatMsg("assistant", e.text)
                else -> null
            }
        }
    }

    suspend fun getLastUserSeq(cardId: String): Long? =
        dao.listForCard(cardId).lastOrNull { it.role == "USER" }?.seq

    suspend fun getLastUserText(cardId: String): String? =
        dao.listForCard(cardId).lastOrNull { it.role == "USER" }?.text
}
