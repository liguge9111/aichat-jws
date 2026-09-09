package com.lirui.charchat.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地文件存储：角色头像（P2 导入时写入）与聊天照片（P3 出图时写入）。
 * 文件存于应用私有目录 filesDir，随应用卸载清除，无外部同步。
 */
@Singleton
class FileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun dir(name: String): File =
        File(context.filesDir, name).apply { if (!exists()) mkdirs() }

    fun savePhoto(bytes: ByteArray): String {
        val file = File(dir("photos"), "${UUID.randomUUID()}.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun saveAvatar(bytes: ByteArray): String {
        val file = File(dir("avatars"), "${UUID.randomUUID()}.png")
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
