package com.lirui.charchat.domain.repository

import android.util.Base64
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.data.db.dao.GroupMessageDao
import com.lirui.charchat.data.db.entity.GroupMessageEntity
import com.lirui.charchat.data.remote.model.ChatMsg
import com.lirui.charchat.data.storage.FileStorage
import com.lirui.charchat.domain.chat.AttributeEngine
import com.lirui.charchat.domain.chat.GroupContextBuilder
import com.lirui.charchat.domain.chat.GroupPhase
import com.lirui.charchat.domain.chat.InputGuardrail
import com.lirui.charchat.domain.chat.MentionParser
import com.lirui.charchat.domain.chat.PromptBuilder
import com.lirui.charchat.domain.chat.ReplyLanguage
import com.lirui.charchat.domain.chat.RoundController
import com.lirui.charchat.domain.chat.WorldBookTrigger
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.Group
import com.lirui.charchat.domain.model.GroupMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * P7：群聊编排器。
 *
 * 一轮流程：
 * 1. 玩家输入 → 护栏脱敏 → 落库玩家消息
 * 2. 解析 @提及：命中成员则只让被 @ 的回复；无 @ 或 @全体 则全员依次回复
 * 3. 每个目标角色**独立一次调用**：用 GroupContextBuilder 把公共历史翻译成该角色视角，
 *    配群聊版系统提示词；返回内容经 RoundController 切成 ≤5 气泡各自落库，
 *    含 [[PHOTO]] 的照常出图；最后 AttributeEngine 各自推进自己的好感。
 *
 * 设计取舍：多角色并行调用会更拟真，但串行能保证消息顺序（seq 单调递增）
 * 且避免多角色同时出图把界面刷乱，故采用串行。
 */
class GroupOrchestrator @Inject constructor(
    private val chatRepo: ChatRepository,
    private val groupMessages: GroupMessageDao,
    private val cards: CharacterCardDao,
    private val storage: FileStorage
) {

    fun send(
        group: Group,
        members: List<CharacterCard>,
        history: List<GroupMessage>,
        rawUserText: String,
        playerName: String
    ): Flow<GroupPhase> = flow {
        val groupId = group.id
        val memberNames = members.map { it.name }

        // 1. 护栏 + 落库玩家消息（群聊里对"第三人称描述任一成员"都做脱敏）
        var guarded = rawUserText.trim()
        members.forEach { c -> guarded = InputGuardrail.sanitize(guarded, c.name).text }
        if (guarded.isBlank()) {
            emit(GroupPhase.Done(error = "消息内容被过滤，未发送"))
            return@flow
        }
        groupMessages.insert(
            GroupMessageEntity(
                groupId = groupId,
                senderId = null,
                senderName = playerName,
                role = "USER",
                text = guarded
            )
        )

        // 2. 定向：@成员 优先，否则全员
        val mentioned = MentionParser.parse(guarded, memberNames)
        val broadcast = MentionParser.isBroadcast(guarded) || mentioned.isEmpty()
        val targets = if (broadcast) members
        else members.filter { mentioned.contains(it.name) }

        if (targets.isEmpty()) {
            emit(GroupPhase.Done(error = "群里还没有成员，先添加角色"))
            return@flow
        }

        val updatedHistory = history + GroupMessage(
            senderId = null,
            senderName = playerName,
            isUser = true,
            text = guarded
        )

        // 3. 串行：每个目标角色独立一次调用
        targets.forEach { card ->
            emit(GroupPhase.Thinking(card.name))

            val ctx = GroupContextBuilder.build(updatedHistory, card.id, playerName)
            val others = members.map { it.name }.filter { it != card.name }
            // 世界书：对本角色按本轮玩家输入触发
            val worldTexts = listOf(guarded) + ctx.filter { it.role == "user" }.map { it.content }
            val activeWorld = WorldBookTrigger.matches(card.worldBookJson, *worldTexts.toTypedArray())
            val system = PromptBuilder.buildForGroup(card, group.name, others, activeWorld, ReplyLanguage.detect(guarded))
            val req = listOf(ChatMsg("system", system)) + ctx

            val raw = StringBuilder()
            try {
                chatRepo.stream(req).collect { chunk ->
                    raw.append(chunk)
                    emit(
                        GroupPhase.Streaming(
                            card.name,
                            RoundController.sanitizePreview(raw.toString())
                        )
                    )
                }
            } catch (e: Throwable) {
                emit(GroupPhase.Done(failed = card.name, error = e.message ?: "对话失败"))
                return@flow
            }

            val parsed = RoundController.parseRound(raw.toString())
            parsed.bubbles.forEachIndexed { idx, bubble ->
                if (bubble.photoPrompt != null) {
                    emit(GroupPhase.GeneratingPhoto(card.name, idx + 1, parsed.photoCount))
                }
                val seq = groupMessages.insert(
                    GroupMessageEntity(
                        groupId = groupId,
                        senderId = card.id,
                        senderName = card.name,
                        role = "CHARACTER",
                        text = bubble.text
                    )
                )
                if (bubble.photoPrompt != null) {
                    runCatching {
                        val resp = chatRepo.generateImage(bubble.photoPrompt)
                        val bytes = resp.data.firstNotNullOfOrNull { d -> d.b64Json?.let { decodeB64(it) } }
                        if (bytes != null) {
                            groupMessages.updateImagePath(seq, storage.savePhoto(bytes))
                        } else {
                            resp.data.firstNotNullOfOrNull { it.url }
                                ?.let { groupMessages.updateImagePath(seq, it) }
                        }
                    }
                }
            }

            // 各自推进自己的好感/关系/状态栏/约定
            val upd = AttributeEngine.parse(raw.toString())
            if (upd != null) {
                if (upd.affectionDelta != 0 || upd.relationship != null) {
                    val next = AttributeEngine.apply(card.attributes, upd)
                    cards.updateAffinity(card.id, next.affection, next.relationship)
                }
                upd.statusText?.let { cards.updateStatusText(card.id, it) }
                upd.newNote?.let { appendNote(card.id, card.additionalNotes, it) }
            }
        }

        emit(GroupPhase.Done())
    }

    /** 追加一条约定：与已有行去重（忽略大小写），封顶 60 条。 */
    private suspend fun appendNote(cardId: String, existing: String, note: String) {
        val cur = existing.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (cur.any { it.equals(note, ignoreCase = true) }) return
        cur.add(note)
        cards.updateAdditionalNotes(cardId, cur.take(60).joinToString("\n"))
    }

    private fun decodeB64(s: String): ByteArray? = runCatching {
        Base64.decode(s, Base64.DEFAULT)
    }.getOrNull()
}
