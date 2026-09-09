package com.lirui.charchat.di

import android.content.Context
import com.lirui.charchat.BuildConfig
import com.lirui.charchat.data.db.AppDatabase
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.ChatMessageDao
import com.lirui.charchat.data.db.dao.GroupDao
import com.lirui.charchat.data.db.dao.GroupMessageDao
import com.lirui.charchat.data.db.dao.PlayerProfileDao
import com.lirui.charchat.data.settings.SettingsRepository
import com.lirui.charchat.domain.repository.ChatRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = SettingsRepository(context)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.create(context)

    @Provides
    fun provideCharacterCardDao(db: AppDatabase): CharacterCardDao = db.cardDao()

    @Provides
    fun provideChatMessageDao(db: AppDatabase): ChatMessageDao = db.messageDao()

    @Provides
    fun provideGroupDao(db: AppDatabase): GroupDao = db.groupDao()

    @Provides
    fun provideGroupMessageDao(db: AppDatabase): GroupMessageDao = db.groupMessageDao()

    @Provides
    fun providePlayerProfileDao(db: AppDatabase): PlayerProfileDao = db.playerProfileDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)   // 流式读超时放宽
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        http: OkHttpClient,
        settings: SettingsRepository
    ): ChatRepository = ChatRepository(http, settings)
}
