package com.lirui.charchat.ui.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.cardparser.CardMapper
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.GroupDao
import com.lirui.charchat.data.db.dao.GroupMessageDao
import com.lirui.charchat.data.db.dao.PlayerProfileDao
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import com.lirui.charchat.data.db.entity.GroupMessageEntity
import com.lirui.charchat.data.group.GroupMapper
import com.lirui.charchat.data.settings.SettingsRepository
import com.lirui.charchat.domain.chat.GroupPhase
import com.lirui.charchat.domain.chat.InputGuardrail
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.Group
import com.lirui.charchat.domain.repository.GroupOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupChatUiState(
    val group: Group? = null,
    val members: List<CharacterCard> = emptyList(),
    val messages: List<GroupMessageEntity> = emptyList(),
    val phase: GroupPhase = GroupPhase.Idle,
    val input: String = "",
    val playerName: String = "我",
    val guardNote: String? = null
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val orchestrator: GroupOrchestrator,
    private val groups: GroupDao,
    private val groupMessages: GroupMessageDao,
    private val cards: CharacterCardDao,
    private val settings: SettingsRepository,
    private val playerProfiles: PlayerProfileDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: String = savedStateHandle.get<String>("groupId") ?: ""

    private val _state = MutableStateFlow(GroupChatUiState())
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    /** 全量卡片缓存：用于按成员 id 取头像/名字，并感知属性演进。 */
    private var allCards: List<CharacterCardEntity> = emptyList()

    init {
        observeGroup()
        observeCards()
        observeMessages()
        loadPlayerName()
    }

    private fun observeGroup() {
        viewModelScope.launch {
            groups.observeById(groupId).collect { e ->
                _state.value = _state.value.copy(group = e?.let { GroupMapper.toDomain(it) })
                refreshMembers()
            }
        }
    }

    private fun observeCards() {
        viewModelScope.launch {
            cards.observeAll().collect { list ->
                allCards = list
                refreshMembers()
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            groupMessages.observeForGroup(groupId).collect { list ->
                _state.value = _state.value.copy(messages = list)
            }
        }
    }

    /** 玩家显示名：档案库第一个档案优先，其次旧全局设置，最后回落"我"。 */
    private fun loadPlayerName() {
        viewModelScope.launch {
            val firstProfile = playerProfiles.listAll().firstOrNull()
            if (firstProfile != null) {
                _state.value = _state.value.copy(playerName = firstProfile.name.ifBlank { "我" })
                return@launch
            }
            val global = settings.player.value.name
            if (global.isNotBlank() && global != "玩家") {
                _state.value = _state.value.copy(playerName = global)
                return@launch
            }
            val first = cards.listAll().firstOrNull() ?: return@launch
            val n = CardMapper.toDomain(first).player.name
            _state.value = _state.value.copy(playerName = n.ifBlank { "我" })
        }
    }

    /** 按成员 id 顺序还原成员列表（成员被删卡时自动跳过）。 */
    private fun refreshMembers() {
        val g = _state.value.group ?: return
        val list = g.memberIds.mapNotNull { id ->
            allCards.firstOrNull { it.id == id }?.let { CardMapper.toDomain(it) }
        }
        _state.value = _state.value.copy(members = list)
    }

    fun onInputChange(s: String) {
        _state.value = _state.value.copy(input = s, guardNote = null)
    }

    /** 点成员头像时插入 @提及。 */
    fun mention(name: String) {
        val cur = _state.value.input
        val next = if (cur.endsWith("@")) "$cur$name " else if (cur.isBlank()) "@$name " else "$cur @$name "
        _state.value = _state.value.copy(input = next)
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isBlank()) return
        _state.value = _state.value.copy(input = "")

        viewModelScope.launch {
            val g = _state.value.group ?: return@launch
            // 每次发送前重取成员，保证好感/关系为上一轮演进后的最新值
            val fresh = g.memberIds.mapNotNull { id ->
                cards.getById(id)?.let { CardMapper.toDomain(it) }
            }
            if (fresh.isEmpty()) {
                _state.value = _state.value.copy(guardNote = "群里没有可用成员")
                return@launch
            }

            val members = fresh.ifEmpty { _state.value.members }
            var stripped = false
            members.forEach { c ->
                if (InputGuardrail.sanitize(text, c.name).stripped) stripped = true
            }
            if (stripped) {
                _state.value = _state.value.copy(guardNote = "已忽略内容中的越权/第三人称描述")
            }

            val history = groupMessages.listForGroup(groupId).map { GroupMapper.toDomainMessage(it) }
            orchestrator.send(g, members, history, text, _state.value.playerName).collect { phase ->
                _state.value = _state.value.copy(phase = phase)
            }
        }
    }
}
