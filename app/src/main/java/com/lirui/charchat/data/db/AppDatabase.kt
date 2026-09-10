package com.lirui.charchat.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.ChatMessageDao
import com.lirui.charchat.data.db.dao.GroupDao
import com.lirui.charchat.data.db.dao.GroupMessageDao
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import com.lirui.charchat.data.db.entity.ChatMessageEntity
import com.lirui.charchat.data.db.entity.GroupEntity
import com.lirui.charchat.data.db.entity.GroupMessageEntity
import com.lirui.charchat.data.db.entity.PlayerProfileEntity
import com.lirui.charchat.data.db.dao.PlayerProfileDao

/**
 * v1 → v2：新增群聊两张表（groups / group_messages）。
 * 必须写真实迁移：工程原先配置了 fallbackToDestructiveMigration，
 * 若直接升版本会清空用户已导入的角色卡与聊天记录。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `groups` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `memberIdsJson` TEXT NOT NULL DEFAULT '[]',
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `group_messages` (
                `seq` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `groupId` TEXT NOT NULL,
                `senderId` TEXT,
                `senderName` TEXT NOT NULL DEFAULT '',
                `role` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `imagePath` TEXT,
                `createdAt` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_group_messages_groupId` ON `group_messages` (`groupId`)"
        )
    }
}

@Database(
    entities = [
        CharacterCardEntity::class,
        ChatMessageEntity::class,
        GroupEntity::class,
        GroupMessageEntity::class,
        PlayerProfileEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CharacterCardDao
    abstract fun messageDao(): ChatMessageDao
    abstract fun groupDao(): GroupDao
    abstract fun groupMessageDao(): GroupMessageDao
    abstract fun playerProfileDao(): PlayerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun create(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "charchat.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/**
 * v8 → v9：语音消息与每角色音色。
 * - messages 新增 audioPath / durationMs / isVoice：语音消息（长按录音发送、角色语音回复）。
 * - cards 新增 ttsVoice：该角色专用的语音合成音色（留空则回落设置里的全局音色）。
 * 幂等：与 5_6/6_7/7_8 一致先查列存在，避免历史"伪版本库"重复 ALTER 崩库。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "messages", "audioPath")) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `audioPath` TEXT")
        }
        if (!hasColumn(db, "messages", "durationMs")) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
        }
        if (!hasColumn(db, "messages", "isVoice")) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `isVoice` INTEGER NOT NULL DEFAULT 0")
        }
        if (!hasColumn(db, "cards", "ttsVoice")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `ttsVoice` TEXT NOT NULL DEFAULT ''")
        }
    }
}

/**
 * v7 → v8：cards 新增 visualAnchor（角色视觉档案——提炼后固定不变的外貌基线，
 * 供图像生成保持跨图一致性）。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "cards", "visualAnchor")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `visualAnchor` TEXT NOT NULL DEFAULT ''")
        }
    }
}

/**
 * v6 → v7：cards 新增 alternateGreetings（备选开场白，SillyTavern alternate_greetings 的 JSON 数组）。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "cards", "alternateGreetings")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `alternateGreetings` TEXT NOT NULL DEFAULT '[]'")
        }
    }
}

/**
 * v5 → v6：
 * - cards 新增 memories（共同回忆，玩家话术讲起的"我们之间的经历"）。
 * - player_profiles 新增 avatarPath（玩家可自定义头像）。
 *
 * 幂等：曾有一版把 version 停在了 5 但实体已含这些列，导致部分安装库出现
 * 「user_version=5 但列已存在」。加列前检查列存在性，避免重复 ALTER 崩溃。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "cards", "memories")) {
            db.execSQL("ALTER TABLE `cards` ADD COLUMN `memories` TEXT NOT NULL DEFAULT ''")
        }
        if (!hasColumn(db, "player_profiles", "avatarPath")) {
            db.execSQL("ALTER TABLE `player_profiles` ADD COLUMN `avatarPath` TEXT")
        }
    }
}

/**
 * v4 → v5：玩家设定独立为多档案（player_profiles 表），cards 记录绑定档案 id。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `player_profiles` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `personality` TEXT NOT NULL DEFAULT '',
                `relationToChar` TEXT NOT NULL DEFAULT '',
                `extra` TEXT NOT NULL DEFAULT '',
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE `cards` ADD COLUMN `profileId` TEXT")
    }
}

/**
 * v3 → v4：cards 表新增 additionalNotes（对话中角色认可玩家设定后固化的长期约定）。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cards` ADD COLUMN `additionalNotes` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v2 → v3：cards 表新增 worldBookJson / statusText 两列（世界书 + 状态栏）。
 * 必须写真实迁移：fallbackToDestructiveMigration 会清空用户已导入的角色卡与聊天记录。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cards` ADD COLUMN `worldBookJson` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `cards` ADD COLUMN `statusText` TEXT NOT NULL DEFAULT ''")
    }
}

/** 迁移辅助：某表是否已含指定列（避免历史迁移重复 ALTER 崩库）。 */
private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
    db.query("PRAGMA table_info($table)").use { c ->
        val nameIdx = c.getColumnIndex("name")
        while (c.moveToNext()) {
            if (c.getString(nameIdx) == column) return true
        }
    }
    return false
}
