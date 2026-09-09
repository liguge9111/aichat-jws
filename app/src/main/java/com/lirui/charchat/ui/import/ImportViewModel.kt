package com.lirui.charchat.ui.import

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lirui.charchat.data.cardparser.SillyTavernParser
import com.lirui.charchat.data.db.AppDatabase
import com.lirui.charchat.data.settings.SettingsRepository
import com.lirui.charchat.domain.model.CharacterCard
import com.lirui.charchat.domain.model.PlayerProfile
import com.lirui.charchat.domain.repository.CardImportService
import com.lirui.charchat.domain.repository.ImportOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val service: CardImportService,
    private val settings: SettingsRepository
) : ViewModel() {

    /**
     * 手动工厂（Hilt 注解处理对本类偶发不生成，导致 hiltViewModel() 在运行时
     * NoSuchMethodException；依赖链很浅，直接构造即可，行为与 Hilt 图一致）。
     */
    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val dao = AppDatabase.create(appContext).cardDao()
                ImportViewModel(
                    service = CardImportService(dao, SillyTavernParser(), appContext),
                    settings = SettingsRepository(appContext)
                )
            }
        }
    }

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private var pendingAvatar: ByteArray? = null

    fun importJson(text: String) = runImport { service.importJson(text) }

    fun importBytes(bytes: ByteArray) = runImport {
        if (isPng(bytes)) service.importPng(bytes)
        else service.importJson(String(bytes, StandardCharsets.UTF_8))
    }

    fun importUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportUiState.Loading
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    .getOrNull()
            }
            if (bytes == null) {
                _state.value = ImportUiState.Error("无法读取文件")
                return@launch
            }
            importBytes(bytes)
        }
    }

    private fun runImport(block: suspend () -> ImportOutcome) {
        viewModelScope.launch {
            _state.value = ImportUiState.Loading
            when (val o = block()) {
                is ImportOutcome.Success -> {
                    pendingAvatar = o.avatarBytes
                    // 卡内没写玩家背景时，用全局默认玩家背景预填（仍可在预览里改）
                    val withGlobal = o.card.copy(player = mergeGlobalDefault(o.card.player))
                    _state.value = ImportUiState.Preview(withGlobal, o.avatarBytes)
                }
                is ImportOutcome.Error -> _state.value = ImportUiState.Error(o.message)
            }
        }
    }

    /** 全局默认背景仅在"卡里没有"时补位：名字是默认值或空 → 用全局；其余字段空 → 用全局。 */
    private fun mergeGlobalDefault(p: PlayerProfile): PlayerProfile {
        val g = settings.player.value
        val dflt = PlayerProfile()
        return PlayerProfile(
            name = if (p.name.isBlank() || p.name == dflt.name) {
                g.name.ifBlank { dflt.name }
            } else p.name,
            personality = p.personality.ifBlank { g.personality },
            relationToChar = p.relationToChar.ifBlank { g.relationToChar },
            extra = p.extra.ifBlank { g.extra }
        )
    }

    fun save(card: CharacterCard) {
        viewModelScope.launch {
            _state.value = ImportUiState.Loading
            when (val o = service.saveCard(card, pendingAvatar)) {
                is ImportOutcome.Success -> _state.value = ImportUiState.Saved(o.card)
                is ImportOutcome.Error -> _state.value = ImportUiState.Error(o.message)
            }
            pendingAvatar = null
        }
    }
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
    0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
)

private fun isPng(bytes: ByteArray): Boolean =
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)
