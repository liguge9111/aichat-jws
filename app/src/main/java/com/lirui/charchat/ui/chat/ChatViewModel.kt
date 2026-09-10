package com.lirui.charchat.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.cardparser.CardMapper
import com.lirui.charchat.data.storage.FileStorage
import com.lirui.charchat.data.voice.WavRecorder
import com.lirui.charchat.domain.repository.ChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.PlayerProfileDao
import com.lirui.charchat.data.db.entity.ChatMessageEntity
import com.lirui.charchat.data.db.entity.PlayerProfileEntity
import com.lirui.charchat.domain.chat.ChatPhase
import com.lirui.charchat.domain.chat.GreetingOptions
import com.lirui.charchat.domain.chat.InputGuardrail
import com.lirui.charchat.domain.chat.UserIdentity
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
    val notice: String? = null,
    /** 是否正在长按录音。 */
    val isRecording: Boolean = false,
    /** 语音识别中（松开后转文字）。 */
    val voiceBusy: Boolean = false,
    /** 当前正在播放的语音消息路径（null=没在播）。 */
    val playingAudioPath: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val orchestrator: ChatOrchestrator,
    private val messages: MessageRepository,
    private val cards: CharacterCardDao,
    private val profiles: PlayerProfileDao,
    private val storage: FileStorage,
    private val chatRepo: ChatRepository,
    @ApplicationContext private val appContext: Context,
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
            // 落库前把 {{user}}/{{char}} 占位符替换为玩家/角色名：既避免 UI 显示宏原文，
            // 也让开场白进入模型上下文时"玩家是谁"不被宏吃掉。
            val pName = _state.value.boundProfile?.name?.trim()?.ifBlank { "玩家" } ?: "玩家"
            val cardName = _state.value.card?.name.orEmpty()
            val finalText = UserIdentity.replace(text, pName, cardName)
            messages.insertCharacter(cardId, finalText)
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

    // ---------- 语音：录音 → 转写 → 带语音发送 ----------

    private var recorder: WavRecorder? = null
    private var recordFile: File? = null
    private var recordStartedAt = 0L
    private var player: MediaPlayer? = null

    /** 麦克风权限是否已授予（UI 长按录音前先查）。 */
    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** 按下开始录音（调用前须确认已授权）。输出 16k 单声道 WAV 到应用私有 audio 目录。 */
    fun startRecording() {
        if (_state.value.isRecording || _state.value.voiceBusy) return
        val file = File(storage.audioDir(), "rec_${System.currentTimeMillis()}.wav")
        val rec = WavRecorder(file)
        if (!rec.start()) {
            file.delete()
            _state.value = _state.value.copy(notice = "无法启动录音：麦克风被占用或不可用")
            return
        }
        recorder = rec
        recordFile = file
        recordStartedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(isRecording = true)
    }

    /** 松开结束录音。cancel=true 丢弃；否则转写为文字并按语音消息发送。 */
    fun stopRecording(cancel: Boolean) {
        val rec = recorder ?: return
        val file = recordFile
        val started = recordStartedAt
        recorder = null
        recordFile = null
        recordStartedAt = 0L
        _state.value = _state.value.copy(isRecording = false)
        if (cancel) {
            rec.cancel()
            return
        }
        // 以实际写入的 PCM 长度为准，比墙上时钟更准（避免启动抖动）
        val duration = rec.stop().takeIf { it > 0 } ?: (SystemClock.elapsedRealtime() - started)
        if (file == null) return
        if (duration < 900) {
            file.delete()
            _state.value = _state.value.copy(notice = "说话时间太短，未发送")
            return
        }
        sendVoice(file, duration)
    }

    /** 语音消息链路：云端 ASR 转文字 → 编排器带音频发送（角色会回语音）。 */
    private fun sendVoice(file: File, durationMs: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(voiceBusy = true, notice = null)
            try {
                val card = freshCard() ?: return@launch
                val text = chatRepo.transcribeAudio(file).trim()
                if (text.isBlank()) {
                    file.delete()
                    _state.value = _state.value.copy(
                        notice = "没听清，未发送。可以再按住说一次，或检查设置里的语音识别配置"
                    )
                    return@launch
                }
                val history = messages.getHistory(cardId)
                orchestrator.send(
                    card = card,
                    history = history,
                    rawUserText = text,
                    voiceAudio = file.absolutePath to durationMs
                ).collect { phase -> applyPhase(phase) }
            } catch (e: Throwable) {
                file.delete()
                _state.value = _state.value.copy(
                    phase = ChatPhase.Done(false, e.message ?: "语音发送失败")
                )
            } finally {
                _state.value = _state.value.copy(voiceBusy = false)
            }
        }
    }

    // ---------- 语音：播放 ----------

    /** 点语音气泡播放/停止；正在播同一条则停止。 */
    fun togglePlayAudio(path: String?) {
        if (path.isNullOrBlank()) return
        if (_state.value.playingAudioPath == path) {
            stopPlayback()
            return
        }
        stopPlayback()
        player = try {
            MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { stopPlayback() }
                setOnErrorListener { _, _, _ -> stopPlayback(); true }
                prepareAsync()
            }
        } catch (e: Throwable) {
            _state.value = _state.value.copy(notice = "无法播放语音：${e.message ?: "未知错误"}")
            null
        } ?: return
        _state.value = _state.value.copy(playingAudioPath = path)
    }

    fun stopPlayback() {
        runCatching { player?.release() }
        player = null
        if (_state.value.playingAudioPath != null) {
            _state.value = _state.value.copy(playingAudioPath = null)
        }
    }

    // ---------- 每角色音色 ----------

    /** 保存该角色专属音色；留空则回落设置页全局音色。 */
    fun updateTtsVoice(voice: String) {
        viewModelScope.launch {
            runCatching { cards.updateTtsVoice(cardId, voice.trim()) }
        }
    }

    override fun onCleared() {
        stopPlayback()
        val rec = recorder
        if (rec != null) {
            recorder = null
            recordFile = null
            rec.cancel()
        }
        super.onCleared()
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
