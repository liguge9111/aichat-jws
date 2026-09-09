package com.lirui.charchat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lirui.charchat.data.db.entity.PlayerProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerProfileDao {

    @Query("SELECT * FROM player_profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PlayerProfileEntity>>

    @Query("SELECT * FROM player_profiles ORDER BY createdAt ASC")
    suspend fun listAll(): List<PlayerProfileEntity>

    @Query("SELECT * FROM player_profiles WHERE id = :id")
    suspend fun getById(id: String): PlayerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(p: PlayerProfileEntity)

    @Update
    suspend fun update(p: PlayerProfileEntity)

    @Query("DELETE FROM player_profiles WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 自定义玩家头像（v6）。 */
    @Query("UPDATE player_profiles SET avatarPath = :path WHERE id = :id")
    suspend fun updateAvatar(id: String, path: String?)
}
