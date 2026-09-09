package com.lirui.charchat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lirui.charchat.data.settings.ApiConfig
import com.lirui.charchat.data.settings.SettingsRepository
import com.lirui.charchat.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 连通性探测状态机。 */
sealed interface TestState {
    data object Idle : TestState
    data object Testing : TestState
    data class Ok(val message: String) : TestState
    data class Error(val message: String) : TestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    val config: StateFlow<ApiConfig> = repository.config

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    /** 图像侧探测状态（独立于对话侧）。 */
    private val _imageState = MutableStateFlow<TestState>(TestState.Idle)
    val imageState: StateFlow<TestState> = _imageState.asStateFlow()

    /** 探测到的出图模型，供设置页一键填入 Model 字段。 */
    private val _imageModels = MutableStateFlow<List<String>>(emptyList())
    val imageModels: StateFlow<List<String>> = _imageModels.asStateFlow()

    fun save(cfg: ApiConfig) = repository.save(cfg)

    fun setAgeVerified() = repository.setAgeVerified()

    /** 发起连通冒烟（P1 核心交付），结果推进 testState。 */
    fun testConnection() {
        _testState.value = TestState.Testing
        viewModelScope.launch {
            chatRepository.testConnection()
                .onSuccess { _testState.value = TestState.Ok(it) }
                .onFailure { _testState.value = TestState.Error(it.message ?: "未知错误") }
        }
    }

    /** 图像侧探测：不消耗额度，列出端点上的出图模型。 */
    fun testImageConnection() {
        _imageState.value = TestState.Testing
        _imageModels.value = emptyList()
        viewModelScope.launch {
            chatRepository.testImageConnection()
                .onSuccess { probe ->
                    _imageModels.value = probe.imageModels
                    _imageState.value = when {
                        probe.imageModels.isEmpty() && probe.models.isEmpty() ->
                            TestState.Error("端点连通，但没有返回模型列表，无法判断是否支持出图。可点「发一张测试图」实测。")
                        probe.imageModels.isEmpty() ->
                            TestState.Error("端点共 ${probe.models.size} 个模型，没有发现任何出图模型（该网关可能只支持文本/语音）。需换一个支持出图的图像接口。")
                        else ->
                            TestState.Ok("检测到 ${probe.imageModels.size} 个可能的出图模型，点下方名称可直接填入。")
                    }
                }
                .onFailure { _imageState.value = TestState.Error(it.message ?: "未知错误") }
        }
    }

    /** 真实出图一次（消耗额度），确认链路真的能跑通。 */
    fun testImageGeneration() {
        _imageState.value = TestState.Testing
        viewModelScope.launch {
            chatRepository.testImageGeneration()
                .onSuccess { _imageState.value = TestState.Ok(it) }
                .onFailure {
                    android.util.Log.e("CharChatNet", "test image generation failed: ${it.message}")
                    _imageState.value = TestState.Error(it.message ?: "未知错误")
                }
        }
    }
}
