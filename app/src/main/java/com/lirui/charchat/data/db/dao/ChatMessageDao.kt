package com.lirui.charchat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lirui.charchat.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM messages WHERE cardId = :cardId ORDER BY seq ASC")
    fun observeForCard(cardId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM messages WHERE cardId = :cardId ORDER BY seq ASC")
    suspend fun listForCard(cardId: String): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE cardId = :cardId")
    suspend fun countForCard(cardId: String): Int

    @Query("UPDATE messages SET imagePath = :path WHERE seq = :seq")
    suspend fun updateImagePath(seq: Long, path: String)

    /** 语音回复合成完成后回写音频路径与时长。 */
    @Query("UPDATE messages SET audioPath = :path, durationMs = :durationMs, isVoice = 1 WHERE seq = :seq")
    suspend fun updateAudioPath(seq: Long, path: String, durationMs: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM messages WHERE cardId = :cardId AND seq >= :fromSeq")
    suspend fun truncateFrom(cardId: String, fromSeq: Long)

    @Query("DELETE FROM messages WHERE cardId = :cardId")
    suspend fun clear(cardId: String)
}
