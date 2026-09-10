package com.lirui.charchat.domain.repository

import android.media.MediaMetadataRetriever
import android.util.Base64
import android.util.Log
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.remote.model.ChatMsg
import com.lirui.charchat.data.storage.FileStorage
import com.lirui.charchat.domain.chat.AttributeEngine
import com.lirui.charchat.domain.chat.ChatPhase
import com.lirui.charchat.domain.chat.InputGuardrail
import com.lirui.charchat.domain.chat.PhotoIntent
import com.lirui.charchat.domain.chat.PhotoPromptComposer
import com.lirui.charchat.domain.chat.PromptBuilder
import com.lirui.charchat.domain.chat.ReplyLanguage
import com.lirui.charchat.domain.chat.RoundController
import com.lirui.charchat.domain.chat.UserIdentity
import com.lirui.charchat.domain.chat.VisualAnchor
import com.lirui.charchat.domain.chat.WorldBookTrigger
import com.lirui.charchat.domain.model.CharacterCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * 单聊编排器：驱动"玩家输入 → 护栏 → 流式回复 → 出图 → 好感演进 → 落库"的完整一轮。
 * 消息直接落库；UI 通过 MessageRepository.observe 自动刷新，无需手动管理列表。
 *
 * @param card 当前角色卡（attributes.affection/relationship 须为最新值，由调用方每次发送前从 DB 重新取）
 * @param history 历史上下文（已映射为 ChatMsg，不含本次玩家消息）
 * @param rawUserText 玩家原始输入（内部做护栏脱敏后再存入/发送）
 */
class ChatOrchestrator @Inject constructor(
    private val chatRepo: ChatRepository,
    private val messages: MessageRepository,
    private val cards: CharacterCardDao,
    private val storage: FileStorage
) {

    fun send(
        card: CharacterCard,
        history: List<ChatMsg>,
        rawUserText: String,
        /**
         * 玩家语音（音频路径 → 时长 ms）；非空表示本轮是语音输入：
         * 玩家消息带上音频，且角色也回语音（P2：发语音才回语音）。
         */
        voiceAudio: Pair<String, Long>? = null
    ): Flow<ChatPhase> = flow {
        val cardId = card.id
        val guarded = InputGuardrail.sanitize(rawUserText, card.name).text
        if (guarded.isBlank()) {
            emit(ChatPhase.Done(false, "消息内容被过滤，未发送"))
            return@flow
        }

        messages.insertUser(cardId, guarded, voiceAudio?.first, voiceAudio?.second ?: 0L)
        // 世界书：按本轮玩家输入 + 最近若干轮上下文（含角色自己说过的话）做关键字命中
        val worldTexts = listOf(guarded) + history.takeLast(8).map { it.content }
        val activeWorld = WorldBookTrigger.matches(card.worldBookJson, *worldTexts.toTypedArray())
        // 回复语言跟随玩家本轮输入：英文角色卡 + 中文提问时，不应跟着卡说英文
        val replyLanguage = ReplyLanguage.detect(guarded)
        val system = PromptBuilder.build(card, activeWorld, replyLanguage)
        // 历史与本次玩家消息里的 {{user}}/{{char}} 宏统一替换为玩家/角色名（system 已在 build 内替换，不重复）。
        // 覆盖开场白等角色消息中残留的占位符——多数模型不认识这些宏，原样下发会把"玩家是谁"的线索丢掉。
        val playerName = UserIdentity.displayName(card.player)
        val cleanUserText = UserIdentity.replace(guarded, playerName, card.name)
        val cleanHistory = history.map { m ->
            m.copy(content = UserIdentity.replace(m.content, playerName, card.name))
        }
        val req = listOf(ChatMsg("system", system)) + cleanHistory + ChatMsg("user", cleanUserText)

        // 语音轮：玩家发语音 → 角色以语音回复。整轮不逐字展示，统一显示"对方正在讲话…"，
        // 等文字与语音都就绪后一起落库，避免"先看到文字、再补上语音"的割裂感。
        val isVoiceTurn = voiceAudio != null

        emit(if (isVoiceTurn) ChatPhase.SpeakingVoice else ChatPhase.Thinking)
        Log.i(TAG, "send start card=$cardId history=${history.size} voice=$isVoiceTurn")

        val raw = StringBuilder()
        try {
            chatRepo.stream(req).collect { chunk ->
                raw.append(chunk)
                if (!isVoiceTurn) {
                    emit(ChatPhase.Streaming(RoundController.sanitizePreview(raw.toString())))
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "stream failed: ${e.message}", e)
            emit(ChatPhase.Done(false, e.message ?: "对话失败"))
            return@flow
        }
        Log.i(TAG, "stream done, raw=${raw.length} chars")

        val parsed = RoundController.parseRound(raw.toString())

        // 语音轮：先把这一轮要说的话合成好（此时 UI 仍停留在"对方正在讲话…"），
        // 拿到音频后再连同文字一次性写入，两条消息一起出现。
        var voicePath: String? = null
        var voiceMs = 0L
        if (isVoiceTurn) {
            val speakText = parsed.bubbles.joinToString(" ") { it.text }.trim()
            if (speakText.isNotBlank()) {
                val outcome = generateVoice(card, speakText)
                voicePath = outcome.path
                voiceMs = outcome.durationMs
                outcome.error?.let { emit(ChatPhase.Notice(it)) }
            }
        }

        var lastCharSeq: Long? = null
        parsed.bubbles.forEachIndexed { idx, bubble ->
            if (bubble.photoPrompt != null) {
                emit(ChatPhase.GeneratingPhoto(idx + 1, parsed.photoCount))
            }
            // 音频只挂最后一条气泡（本轮全部文字已合成成一条语音）
            val isLast = idx == parsed.bubbles.lastIndex
            val seq = messages.insertCharacter(
                cardId = cardId,
                text = bubble.text,
                audioPath = if (isLast) voicePath else null,
                durationMs = if (isLast) voiceMs else 0L
            )
            lastCharSeq = seq
            if (bubble.photoPrompt != null) {
                generatePhoto(card, history, seq, bubble.photoPrompt)?.let { emit(ChatPhase.Notice(it)) }
            }
        }

        // 兜底出图：玩家明显想看照片，但模型只给了文字描述 → 把图挂到最后一条角色消息上
        if (parsed.photoCount == 0 && lastCharSeq != null && PhotoIntent.wantsPhoto(guarded)) {
            val scene = PhotoIntent.fallbackPrompt(card.name, card.description, guarded)
            emit(ChatPhase.GeneratingPhoto(1, 1))
            generatePhoto(card, history, lastCharSeq!!, scene)?.let { emit(ChatPhase.Notice(it)) }
        }

        // 好感/关系/状态栏/约定/回忆演进：解析同一次返回里的隐藏信号，更新卡片行
        val upd = AttributeEngine.parse(raw.toString())
        if (upd != null) {
            if (upd.affectionDelta != 0 || upd.relationship != null) {
                val next = AttributeEngine.apply(card.attributes, upd)
                cards.updateAffinity(cardId, next.affection, next.relationship)
            }
            upd.statusText?.let { cards.updateStatusText(cardId, it) }
            upd.newNote?.let { appendNote(cardId, card.additionalNotes, it) }
            upd.newMemory?.let { appendMemory(cardId, card.memories, it) }
        }

        emit(ChatPhase.Done(true))
    }

    /** 语音合成结果：成功给 path/durationMs，失败给 error 文案（文字回复照常）。 */
    private class VoiceOutcome(val path: String?, val durationMs: Long, val error: String?)

    /**
     * 把角色这一轮的话合成语音并落盘，但**不写库**——由调用方在插入角色消息时一并带上，
     * 以保证文字与语音同时出现。失败不中断对话，只返回一条可展示的说明。
     */
    private suspend fun generateVoice(card: CharacterCard, text: String): VoiceOutcome {
        return try {
            val audio = chatRepo.synthesizeSpeech(text, card.ttsVoice)
            val path = storage.saveAudio(audio.bytes, audio.extension)
            VoiceOutcome(path, readAudioDuration(path), null)
        } catch (e: Throwable) {
            Log.w(TAG, "voice synthesis failed: ${e.message}", e)
            VoiceOutcome(null, 0L, "语音生成失败：${e.message ?: "未知错误"}（文字回复不受影响）")
        }
    }

    /** 读取音频时长（毫秒）；读不到回落 0，UI 只展示时长，失败不影响播放。 */
    private fun readAudioDuration(path: String): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        retriever.release()
        ms
    }.getOrDefault(0L)

    /**
     * 生成图片并挂到指定消息上；失败不中断对话，返回可直接展示给玩家的错误文案。
     * 提示词走 PhotoPromptComposer 三层拼装（视觉档案 + 最近对话 + 本轮画面），
     * 首次出图前若档案为空会先提炼一次并落库。
     */
    private suspend fun generatePhoto(
        card: CharacterCard,
        history: List<ChatMsg>,
        seq: Long,
        scene: String
    ): String? {
        return try {
            val fullPrompt = buildPhotoPrompt(card, history, scene)
            val resp = chatRepo.generateImage(fullPrompt)
            val bytes = resp.data.firstNotNullOfOrNull { d -> d.b64Json?.let { decodeB64(it) } }
            val path = if (bytes != null) {
                storage.savePhoto(bytes)
            } else {
                resp.data.firstNotNullOfOrNull { it.url }
            }
            if (path != null) {
                messages.updateImagePath(seq, path)
                null
            } else {
                Log.w(TAG, "image generation returned empty payload")
                "照片生成失败：接口没有返回图片（检查图像 API 配置）"
            }
        } catch (e: Throwable) {
            Log.w(TAG, "image generation failed: ${e.message}", e)
            "照片生成失败：${e.message ?: "未知错误"}"
        }
    }

    /** 拼装出图提示词：角色视觉档案（空则提炼）+ 最近约 4 轮对话 + 本轮画面描述。 */
    private suspend fun buildPhotoPrompt(card: CharacterCard, history: List<ChatMsg>, scene: String): String {
        var anchor = card.visualAnchor
        if (anchor.isBlank()) {
            anchor = extractVisualAnchor(card) ?: ""
        }
        val fallback = PhotoPromptComposer.fallbackAppearance(
            card.attributes.appearance,
            card.attributes.clothing
        )
        val recent = history.takeLast(8).map { msg ->
            val who = if (msg.role.equals("user", ignoreCase = true)) "玩家" else card.name
            "$who：${RoundController.sanitizeVisible(msg.content)}"
        }
        return PhotoPromptComposer.compose(
            name = card.name,
            visualAnchor = anchor,
            fallbackAppearance = fallback,
            historyLines = recent,
            scene = scene
        )
    }

    /**
     * 提炼角色视觉档案并落库（首次出图前只做一次，之后读卡上缓存）。
     * 任一步失败都返回 null，调用方降级为属性面板兜底，不阻断出图。
     */
    private suspend fun extractVisualAnchor(card: CharacterCard): String? {
        return try {
            val sb = StringBuilder()
            chatRepo.stream(
                messages = listOf(ChatMsg("user", VisualAnchor.buildPrompt(card))),
                temperature = 0.2
            ).collect { sb.append(it) }
            val anchor = VisualAnchor.parse(sb.toString())
            if (anchor.isBlank()) {
                Log.w(TAG, "visual anchor extraction returned empty")
                null
            } else {
                cards.updateVisualAnchor(card.id, anchor)
                Log.i(TAG, "visual anchor saved (${anchor.length} chars)")
                anchor
            }
        } catch (e: Throwable) {
            Log.w(TAG, "visual anchor extraction failed: ${e.message}", e)
            null
        }
    }

    /** 追加一条约定：与已有行去重（忽略大小写），封顶 60 条。 */
    private suspend fun appendNote(cardId: String, existing: String, note: String) {
        val cur = existing.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (cur.any { it.equals(note, ignoreCase = true) }) return
        cur.add(note)
        cards.updateAdditionalNotes(cardId, cur.take(60).joinToString("\n"))
    }

    /** 追加一条共同回忆：去重后封顶 60 条。 */
    private suspend fun appendMemory(cardId: String, existing: String, memory: String) {
        val cur = existing.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (cur.any { it.equals(memory, ignoreCase = true) }) return
        cur.add(memory)
        cards.updateMemories(cardId, cur.take(60).joinToString("\n"))
    }

    private fun decodeB64(s: String): ByteArray? = runCatching {
        Base64.decode(s, Base64.DEFAULT)
    }.getOrNull()

    companion object {
        private const val TAG = "ChatOrchestrator"
    }
}
