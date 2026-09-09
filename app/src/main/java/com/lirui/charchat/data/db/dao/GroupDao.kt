package com.lirui.charchat.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lirui.charchat.data.db.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM `groups` ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    fun observeById(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM `groups` WHERE id = :id")
    suspend fun getById(id: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity)

    @Query("DELETE FROM `groups` WHERE id = :id")
    suspend fun deleteById(id: String)
}
