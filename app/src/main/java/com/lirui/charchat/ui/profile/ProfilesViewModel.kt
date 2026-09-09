package com.lirui.charchat.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.db.dao.PlayerProfileDao
import com.lirui.charchat.data.db.entity.PlayerProfileEntity
import com.lirui.charchat.data.storage.FileStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val dao: PlayerProfileDao,
    private val storage: FileStorage,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val profiles: StateFlow<List<PlayerProfileEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun save(p: PlayerProfileEntity) {
        viewModelScope.launch { dao.insert(p) }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.deleteById(id) }
    }

    /**
     * 自定义玩家头像：把选中的图片读成字节存入应用私有目录，路径写回档案。
     * IO 放 Default 线程，避免主线程读图卡顿。
     */
    fun setAvatar(id: String, uri: Uri, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()?.let { bytes ->
                    runCatching { storage.saveAvatar(bytes) }.getOrNull()
                }
            }
            if (path != null) dao.updateAvatar(id, path)
            onDone(path != null)
        }
    }

    fun clearAvatar(id: String) {
        viewModelScope.launch { dao.updateAvatar(id, null) }
    }
}
