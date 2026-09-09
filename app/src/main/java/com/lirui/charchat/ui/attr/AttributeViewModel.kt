package com.lirui.charchat.ui.attr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.domain.model.StrategyAttributes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 属性面板草稿：仅维护"角色自身"可编辑内容；好感/关系锁定，玩家档案在档案页/聊天页管理。 */
data class AttributeDraft(
    val description: String = "",
    val statusText: String = "",
    val notes: String = "",
    val memories: String = ""
)

data class AttributeUiState(
    val loading: Boolean = true,
    val name: String = "",
    val avatarPath: String? = null,
    val attributes: StrategyAttributes = StrategyAttributes(),
    val statusText: String = "",
    val notes: String = "",
    val memories: String = "",
    /** 世界书条目摘要（只读预览，完整维护在世界书页）。 */
    val worldBookPreview: List<String> = emptyList(),
    val worldBookCount: Int = 0,
    val editing: Boolean = false,
    val draft: AttributeDraft = AttributeDraft(),
    val saved: Boolean = false
)

@HiltViewModel
class AttributeViewModel @Inject constructor(
    private val cards: CharacterCardDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: String = savedStateHandle.get<String>("cardId") ?: ""

    private val _state = MutableStateFlow(AttributeUiState())
    val state: StateFlow<AttributeUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val e = cards.getById(cardId)
            if (e == null) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }
            _state.value = AttributeUiState(
                loading = false,
                name = e.name,
                avatarPath = e.avatarPath,
                attributes = StrategyAttributes(affection = e.affection, relationship = e.relationship),
                statusText = e.statusText,
                notes = e.additionalNotes,
                memories = e.memories,
                worldBookPreview = WorldBookEntryCount.preview(e.worldBookJson),
                worldBookCount = WorldBookEntryCount.count(e.worldBookJson),
                draft = AttributeDraft(
                    description = e.description,
                    statusText = e.statusText,
                    notes = e.additionalNotes,
                    memories = e.memories
                )
            )
        }
    }

    fun startEdit() {
        val s = _state.value
        _state.value = s.copy(
            editing = true,
            saved = false,
            draft = AttributeDraft(s.draft.description, s.statusText, s.notes)
        )
    }

    fun cancelEdit() {
        _state.value = _state.value.copy(editing = false)
    }

    fun updateDraft(draft: AttributeDraft) {
        _state.value = _state.value.copy(draft = draft, saved = false)
    }

    fun save() {
        val d = _state.value.draft
        viewModelScope.launch {
            cards.updateDescription(cardId, d.description.trim())
            cards.updateStatusText(cardId, d.statusText.trim())
            cards.updateAdditionalNotes(cardId, d.notes.trim())
            cards.updateMemories(cardId, d.memories.trim())
            load()
            _state.value = _state.value.copy(editing = false, saved = true)
        }
    }
}

/** 世界书统计与只读摘要（属性面板内直接可查，完整维护在世界书页）。 */
object WorldBookEntryCount {
    fun count(worldBookJson: String): Int = runCatching {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(worldBookJson)
            as? kotlinx.serialization.json.JsonObject ?: return 0
        (root["entries"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }.getOrDefault(0)

    /** 返回形如「关键字：内容（截断 120 字）」的摘要列表，最多 20 条。 */
    fun preview(worldBookJson: String, max: Int = 20): List<String> = runCatching {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(worldBookJson)
            as? kotlinx.serialization.json.JsonObject ?: return emptyList()
        val arr = (root["entries"] as? kotlinx.serialization.json.JsonArray) ?: return emptyList()
        arr.take(max).mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val keys = (o["keys"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                ?.filter { it.isNotBlank() } ?: emptyList()
            val content = (o["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                .replace("\n", " ").trim()
            val head = if (keys.isEmpty()) "（常驻）" else keys.joinToString("/")
            val tail = if (content.length > 120) content.take(120) + "…" else content
            "$head：$tail"
        }
    }.getOrDefault(emptyList())
}
