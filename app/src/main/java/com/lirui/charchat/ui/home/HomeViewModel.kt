package com.lirui.charchat.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.backup.BackupManager
import com.lirui.charchat.data.backup.RestoreMode
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import com.lirui.charchat.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 主页一次性提示（操作结果反馈）。 */
data class HomeNotice(val text: String, val isError: Boolean = false)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cards: CharacterCardDao,
    private val messages: MessageRepository,
    private val backup: BackupManager
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** 搜索 + 列表：按名称/关系过滤，空查询返回全部。 */
    private val filtered = combine(cards.observeAll(), query) { list, kw ->
        if (kw.isBlank()) list
        else list.filter {
            it.name.contains(kw, ignoreCase = true) ||
                it.relationship.contains(kw, ignoreCase = true)
        }
    }

    val cardsFlow: StateFlow<List<CharacterCardEntity>> =
        filtered.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val queryFlow: StateFlow<String> = query.asStateFlow()

    private val _notice = MutableStateFlow<HomeNotice?>(null)
    val notice: StateFlow<HomeNotice?> = _notice

    /** 待导出内容：由 UI 拿到后经 SAF 写入用户指定位置。 */
    private val _pendingExport = MutableStateFlow<String?>(null)
    val pendingExport: StateFlow<String?> = _pendingExport

    fun onQueryChange(q: String) { query.value = q }

    fun consumeNotice() { _notice.value = null }

    fun consumeExport() { _pendingExport.value = null }

    // ---------- 卡片操作 ----------

    /** 删除角色：连同其聊天记录一起清除，避免留下孤儿消息。 */
    fun delete(card: CharacterCardEntity) {
        viewModelScope.launch {
            messages.clear(card.id)
            cards.delete(card)
            _notice.value = HomeNotice("已删除「${card.name}」")
        }
    }

    /** 只清空聊天记录，保留角色卡与其属性。 */
    fun clearChat(card: CharacterCardEntity) {
        viewModelScope.launch {
            messages.clear(card.id)
            _notice.value = HomeNotice("已清空「${card.name}」的聊天记录")
        }
    }

    // ---------- 备份 ----------

    /** 生成备份 JSON，UI 通过 pendingExport 取走后写文件。 */
    fun requestExport() {
        viewModelScope.launch {
            runCatching { backup.buildBackupJson() }
                .onSuccess { _pendingExport.value = it }
                .onFailure { _notice.value = HomeNotice("导出失败：${it.message}", isError = true) }
        }
    }

    /** 导入备份：mode 决定同 id 卡片是跳过还是覆盖。 */
    fun importBackup(raw: String, mode: RestoreMode) {
        viewModelScope.launch {
            backup.restoreFromJson(raw, mode)
                .onSuccess { n ->
                    _notice.value = HomeNotice(
                        if (n > 0) "已导入 $n 个角色" else "备份中没有可导入的角色"
                    )
                }
                .onFailure {
                    _notice.value = HomeNotice("导入失败：${it.message}", isError = true)
                }
        }
    }
}
