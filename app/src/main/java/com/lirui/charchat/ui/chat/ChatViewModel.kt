package com.lirui.charchat.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.cardparser.CardMapper
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.PlayerProfileDao
import com.lirui.charchat.data.db.entity.ChatMessageEntity
import com.lirui.charchat.data.db.entity.PlayerProfileEntity
import com.lirui.charchat.domain.chat.ChatPhase
import com.lirui.charchat.domain.chat.GreetingOptions
import com.lirui.charchat.domain.chat.InputGuardrail
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.repository.ChatOrchestrator
import com.lirui.charchat.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val card: CharacterCard? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val phase: ChatPhase = ChatPhase.Done(true),
    val input: String = "",
    val guardNote: String? = null,
    /** 可用的玩家档案库。 */
    val profiles: List<PlayerProfileEntity> = emptyList(),
    /** 当前卡绑定的档案；null = 未绑定（进入聊天先选档案）。 */
    val boundProfile: PlayerProfileEntity? = null,
    /** 是否正在等待玩家选择档案（进入聊天首步）。 */
    val awaitingProfile: Boolean = false,
    /** 切换档案确认弹窗。 */
    val showSwitchDialog: Boolean = false,
    /**
     * 首次进入的开场白候选（默认 firstMes + 备选，已清洗去重）。
     * 有开场白的卡不再自动注入，弹框让玩家选一条再开聊。
     */
    val greetingCandidates: List<String> = emptyList(),
    /** 是否正在展示开场白选择弹框。 */
    val showGreetingDialog: Boolean = false,
    /** 非致命提示（如照片生成失败），下一次发送/输入时清除。 */
    val notice: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val orchestrator: ChatOrchestrator,
    private val messages: MessageRepository,
    private val cards: CharacterCardDao,
    private val profiles: PlayerProfileDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: String = savedStateHandle.get<String>("cardId") ?: ""

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var greetingHandled = false

    init {
        loadCard()
        observeMessages()
        observeProfiles()
    }

    private fun observeProfiles() {
        viewModelScope.launch {
            profiles.observeAll().collect { list ->
                _state.value = _state.value.copy(profiles = list)
            }
        }
    }

    /** 观察卡片（好感/关系/状态栏演进后自动同步；档案绑定随 DB 变化刷新）。 */
    private fun loadCard() {
        viewModelScope.launch {
            cards.observeById(cardId).collect { e ->
                val c = CardMapper.toDomain(e)
                val bound = e.profileId?.let { profiles.getById(it) }
                val awaiting = e.profileId == null
                _state.value = _state.value.copy(
                    card = c,
                    boundProfile = bound,
                    awaitingProfile = awaiting
                )
                if (!awaiting) offerGreeting(c)
            }
        }
    }

    /**
     * 开场白决策（原自动播种改造）：已绑定档案且该卡无历史时，
     * 若卡带开场白（默认+备选），弹出候选让玩家挑一条再开始；玩家选后
     * 才把该条作为第一条角色消息写入。无开场白或已有历史则直接放行。
     */
    private suspend fun offerGreeting(card: CharacterCard) {
        if (greetingHandled) return
        if (messages.count(cardId) > 0) {
            greetingHandled = true
            return
        }
        val cands = GreetingOptions.candidates(card)
        if (cands.isEmpty()) {
            greetingHandled = true
            return
        }
        _state.value = _state.value.copy(greetingCandidates = cands, showGreetingDialog = true)
    }

    /** 玩家选定第 index 条开场白：关闭弹框并以该条开场。 */
    fun startWithGreeting(index: Int) {
        val text = _state.value.greetingCandidates.getOrNull(index) ?: return
        _state.value = _state.value.copy(showGreetingDialog = false)
        greetingHandled = true
        viewModelScope.launch {
            // 开场白清洗在候选组装（GreetingOptions）时已完成，这里直接落库。
            messages.insertCharacter(cardId, text)
        }
    }

    /** 跳过开场白：不写任何消息，直接进入空聊天。 */
    fun skipGreeting() {
        greetingHandled = true
        _state.value = _state.value.copy(showGreetingDialog = false)
    }

    private fun observeMessages() {
        viewModelScope.launch {
            messages.observe(cardId).collect { list ->
                _state.value = _state.value.copy(messages = list)
            }
        }
    }

    fun onInputChange(s: String) {
        _state.value = _state.value.copy(input = s, guardNote = null)
    }

    /** 语音识别结果直接填入输入框（不自动发送，方便玩家改完再发）。 */
    fun setInput(s: String) {
        _state.value = _state.value.copy(input = s)
    }

    fun clearGuardNote() {
        _state.value = _state.value.copy(guardNote = null)
    }

    /** 清空当前这段聊天并重新开场（若带开场白则重新弹候选选择）。 */
    fun clearChat() {
        viewModelScope.launch {
            messages.clear(cardId)
            greetingHandled = false
            val e = cards.getById(cardId) ?: return@launch
            offerGreeting(CardMapper.toDomain(e))
            _state.value = _state.value.copy(notice = null)
        }
    }

    // ---------- 玩家档案 ----------

    /** 首次进入：为该卡选定档案（保留已存在历史）。 */
    fun bindProfile(profileId: String) {
        viewModelScope.launch {
            greetingHandled = false
            cards.updateProfileId(cardId, profileId)
            _state.value = _state.value.copy(awaitingProfile = false)
        }
    }

    /** 聊天中切换档案：开启新聊天（清空该卡当前历史并重新开场）。 */
    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            messages.clear(cardId)
            greetingHandled = false
            cards.updateProfileId(cardId, profileId)
            _state.value = _state.value.copy(showSwitchDialog = false)
        }
    }

    fun requestSwitch() {
        if (_state.value.profiles.size <= 1) return
        _state.value = _state.value.copy(showSwitchDialog = true)
    }

    fun cancelSwitch() {
        _state.value = _state.value.copy(showSwitchDialog = false)
    }

    /** 库里还没有档案时：直接建一份并绑定，让玩家最快开聊。 */
    fun createAndBind(name: String) {
        viewModelScope.launch {
            val e = PlayerProfileEntity(
                id = java.util.UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "我" }
            )
            profiles.insert(e)
            greetingHandled = false
            cards.updateProfileId(cardId, e.id)
            _state.value = _state.value.copy(awaitingProfile = false)
        }
    }

    // ---------- 发送 ----------

    fun send() {
        val text = _state.value.input.trim()
        if (text.isBlank()) return
        _state.value = _state.value.copy(input = "")
        viewModelScope.launch {
            try {
                val card = freshCard() ?: return@launch
                val guard = InputGuardrail.sanitize(text, card.name)
                if (guard.stripped) {
                    _state.value = _state.value.copy(guardNote = "已忽略内容中的越权/第三人称描述")
                }
                val history = messages.getHistory(cardId)
                _state.value = _state.value.copy(notice = null)
                orchestrator.send(card, history, text).collect { phase ->
                    applyPhase(phase)
                }
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    phase = ChatPhase.Done(false, e.message ?: "发送失败")
                )
            }
        }
    }

    /** 把编排器阶段写入 UI；Notice 单独留存，避免被紧随其后的 Done 冲掉。 */
    private fun applyPhase(phase: ChatPhase) {
        if (phase is ChatPhase.Notice) {
            _state.value = _state.value.copy(notice = phase.message)
            return
        }
        _state.value = _state.value.copy(phase = phase)
    }

    /** 独立重生成（Q6）：删除最后一轮（玩家+角色回复）并用原玩家文本重发。 */
    fun regenerateLast() {
        viewModelScope.launch {
            val seq = messages.getLastUserSeq(cardId) ?: return@launch
            val text = messages.getLastUserText(cardId) ?: return@launch
            messages.truncateFrom(cardId, seq)
            resend(text)
        }
    }

    /** 长按编辑玩家消息后重生成（Q4=B）：截断该轮及之后全部，用新文本续写。 */
    fun editAndResend(seq: Long, newText: String) {
        viewModelScope.launch {
            messages.truncateFrom(cardId, seq)
            resend(newText.trim())
        }
    }

    private suspend fun resend(text: String) {
        if (text.isBlank()) return
        val card = freshCard() ?: return
        val history = messages.getHistory(cardId)
        try {
            orchestrator.send(card, history, text).collect { phase ->
                applyPhase(phase)
            }
        } catch (e: Throwable) {
            _state.value = _state.value.copy(
                phase = ChatPhase.Done(false, e.message ?: "发送失败")
            )
        }
    }

    /**
     * 每次发送前从 DB 重新取卡（好感/状态/约定最新），并把卡内玩家背景
     * 替换为当前绑定的档案（v5：玩家设定独立于卡片）。
     */
    private suspend fun freshCard(): CharacterCard? {
        val e = cards.getById(cardId) ?: return null
        var c = CardMapper.toDomain(e)
        val bound = e.profileId?.let { profiles.getById(it) }
        if (bound != null) {
            c = c.copy(
                player = PlayerProfile(
                    name = bound.name,
                    personality = bound.personality,
                    relationToChar = bound.relationToChar,
                    extra = bound.extra
                )
            )
        }
        _state.value = _state.value.copy(card = c, boundProfile = bound)
        return c
    }
}
