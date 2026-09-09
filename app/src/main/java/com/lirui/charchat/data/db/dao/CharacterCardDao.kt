package com.lirui.charchat.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterCardDao {

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CharacterCardEntity>>

    /** 一次性列表（备份导出用，非 Flow）。 */
    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    suspend fun listAll(): List<CharacterCardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: String): CharacterCardEntity?

    /** 好感/关系随对话演进，单聊顶栏需实时刷新，故用 Flow 观察。 */
    @Query("SELECT * FROM cards WHERE id = :id")
    fun observeById(id: String): Flow<CharacterCardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CharacterCardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<CharacterCardEntity>)

    @Update
    suspend fun update(card: CharacterCardEntity)

    @Delete
    suspend fun delete(card: CharacterCardEntity)

    /** 好感/关系自然演进（AttributeEngine 调用）。 */
    @Query("UPDATE cards SET affection = :affection, relationship = :relationship WHERE id = :id")
    suspend fun updateAffinity(id: String, affection: Int, relationship: String)

    /** 攻略属性（外貌/穿着/性格/位置/在做什么/性癖）手改后整体写回 JSON。 */
    @Query("UPDATE cards SET attributesJson = :attributesJson WHERE id = :id")
    suspend fun updateAttributesJson(id: String, attributesJson: String)

    /** 玩家背景（名字/性格/与角色关系/补充）手改后整体写回 JSON。 */
    @Query("UPDATE cards SET playerJson = :playerJson WHERE id = :id")
    suspend fun updatePlayerJson(id: String, playerJson: String)

    /** 状态栏随对话更新（AttributeEngine 解析 [[STATUS:...]] 后写入）。 */
    @Query("UPDATE cards SET statusText = :statusText WHERE id = :id")
    suspend fun updateStatusText(id: String, statusText: String)

    /** 长期约定（[[NOTE:...]] 固化）整体写回（编排层负责与旧值合并去重）。 */
    @Query("UPDATE cards SET additionalNotes = :notes WHERE id = :id")
    suspend fun updateAdditionalNotes(id: String, notes: String)

    /** 编辑角色原始设定（酒馆化后 description 为唯一角色设定来源，可手改）。 */
    @Query("UPDATE cards SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: String, description: String)

    /** 绑定该卡当前使用的玩家档案；null 表示未绑定（首次进入聊天时选定）。 */
    @Query("UPDATE cards SET profileId = :profileId WHERE id = :id")
    suspend fun updateProfileId(id: String, profileId: String?)

    /** 世界书维护页整体写回（标准化 entries JSON）。 */
    @Query("UPDATE cards SET worldBookJson = :json WHERE id = :id")
    suspend fun updateWorldBook(id: String, json: String)

    /** 共同回忆（[[MEM:...]] 固化）整体写回（编排层负责与旧值合并去重）。 */
    @Query("UPDATE cards SET memories = :memories WHERE id = :id")
    suspend fun updateMemories(id: String, memories: String)

    /** 角色视觉档案（v8）：图像一致性锚点，首次出图前提炼一次后固化，可手动覆盖。 */
    @Query("UPDATE cards SET visualAnchor = :anchor WHERE id = :id")
    suspend fun updateVisualAnchor(id: String, anchor: String)
}
