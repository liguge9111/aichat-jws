package com.lirui.charchat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lirui.charchat.data.db.entity.GroupMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupMessageDao {

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY seq ASC")
    fun observeForGroup(groupId: String): Flow<List<GroupMessageEntity>>

    @Query("SELECT * FROM group_messages WHERE groupId = :groupId ORDER BY seq ASC")
    suspend fun listForGroup(groupId: String): List<GroupMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: GroupMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<GroupMessageEntity>)

    @Query("UPDATE group_messages SET imagePath = :path WHERE seq = :seq")
    suspend fun updateImagePath(seq: Long, path: String)

    @Query("SELECT COUNT(*) FROM group_messages WHERE groupId = :groupId")
    suspend fun countForGroup(groupId: String): Int

    @Query("DELETE FROM group_messages WHERE groupId = :groupId")
    suspend fun clear(groupId: String)
}
