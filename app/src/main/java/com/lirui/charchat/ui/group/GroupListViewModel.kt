package com.lirui.charchat.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.cardparser.CardMapper
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.GroupDao
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import com.lirui.charchat.data.db.entity.GroupEntity
import com.lirui.charchat.data.group.GroupMapper
import com.lirui.charchat.data.settings.SettingsRepository
import com.lirui.charchat.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GroupListViewModel @Inject constructor(
    private val groups: GroupDao,
    private val cards: CharacterCardDao,
    private val settings: SettingsRepository
) : ViewModel() {

    val groupsFlow: StateFlow<List<GroupEntity>> =
        groups.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val cardsFlow: StateFlow<List<CharacterCardEntity>> =
        cards.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    /** 玩家显示名：优先全局设置，其次第一张卡的玩家背景，最后回落"我"。 */
    suspend fun playerName(): String {
        val global = settings.player.value.name
        if (global.isNotBlank() && global != "玩家") return global
        val first = cards.listAll().firstOrNull() ?: return "我"
        return CardMapper.toDomain(first).player.name.ifBlank { "我" }
    }

    fun createGroup(name: String, memberIds: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val group = Group(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "未命名群聊" },
                memberIds = memberIds
            )
            groups.insert(GroupMapper.toEntity(group))
            onCreated(group.id)
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch { groups.deleteById(id) }
    }
}
