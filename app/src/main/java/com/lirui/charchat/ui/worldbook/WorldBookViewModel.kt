package com.lirui.charchat.ui.worldbook

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.db.dao.CharacterCardDao
import com.lirui.charchat.domain.worldbook.WorldBookJson
import com.lirui.charchat.domain.worldbook.WorldEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class WorldBookUiState(
    val loading: Boolean = true,
    val cardName: String = "",
    val entries: List<WorldEntry> = emptyList()
)

@HiltViewModel
class WorldBookViewModel @Inject constructor(
    private val cards: CharacterCardDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: String = savedStateHandle.get<String>("cardId") ?: ""

    private val _state = MutableStateFlow(WorldBookUiState())
    val state: StateFlow<WorldBookUiState> = _state.asStateFlow()

    private var entryCache: List<WorldEntry> = emptyList()

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
            entryCache = WorldBookJson.parse(e.worldBookJson)
            _state.value = WorldBookUiState(
                loading = false,
                cardName = e.name,
                entries = entryCache
            )
        }
    }

    private fun persist() {
        viewModelScope.launch {
            cards.updateWorldBook(cardId, WorldBookJson.toJson(entryCache))
            _state.value = _state.value.copy(entries = entryCache)
        }
    }

    fun add(name: String, keysText: String, content: String, constant: Boolean) {
        entryCache = entryCache + WorldEntry(
            id = UUID.randomUUID().toString(),
            keys = splitKeys(keysText),
            content = content.trim(),
            constant = constant,
            insertionOrder = (entryCache.maxOfOrNull { it.insertionOrder } ?: -1) + 1
        )
        persist()
    }

    fun update(entry: WorldEntry, name: String, keysText: String, content: String, constant: Boolean) {
        entryCache = entryCache.map {
            if (it.id == entry.id) {
                it.copy(
                    keys = splitKeys(keysText),
                    content = content.trim(),
                    constant = constant
                )
            } else it
        }
        persist()
    }

    fun remove(entry: WorldEntry) {
        entryCache = entryCache.filter { it.id != entry.id }
        persist()
    }

    fun toggleEnabled(entry: WorldEntry) {
        entryCache = entryCache.map { if (it.id == entry.id) it.copy(enabled = !it.enabled) else it }
        persist()
    }

    private fun splitKeys(text: String): List<String> =
        text.split(',', '，', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }
}
