package com.lirui.charchat.ui.import

import com.lirui.charchat.domain.model.CharacterCard

/** 导入页 UI 状态（与 ViewModel 分离文件，避免影响 kapt stub 生成）。 */
sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Preview(val card: CharacterCard, val avatarBytes: ByteArray?) : ImportUiState
    data class Saved(val card: CharacterCard) : ImportUiState
    data class Error(val message: String) : ImportUiState
}
