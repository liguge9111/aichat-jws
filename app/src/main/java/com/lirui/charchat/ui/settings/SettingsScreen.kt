package com.lirui.charchat.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lirui.charchat.data.settings.ApiConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val current by viewModel.config.collectAsState()
    val testState by viewModel.testState.collectAsState()

    // 本地编辑态（保存时回写 repository）
    var chatBaseUrl by remember { mutableStateOf(current.chatBaseUrl) }
    var chatApiKey by remember { mutableStateOf(current.chatApiKey) }
    var chatModel by remember { mutableStateOf(current.chatModel) }
    var imageBaseUrl by remember { mutableStateOf(current.imageBaseUrl) }
    var imageApiKey by remember { mutableStateOf(current.imageApiKey) }
    var imageModel by remember { mutableStateOf(current.imageModel) }
    var nsfw by remember { mutableStateOf(current.nsfwFilterEnabled) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("API 设置 (BYOK)") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("对话模型", style = MaterialTheme.typography.titleSmall)
            Field("Base URL", chatBaseUrl) { chatBaseUrl = it }
            Field("API Key", chatApiKey, isSecret = true) { chatApiKey = it }
            Field("Model", chatModel) { chatModel = it }

            Text("图像模型", style = MaterialTheme.typography.titleSmall)
            Field("Base URL", imageBaseUrl) { imageBaseUrl = it }
            Field("API Key", imageApiKey, isSecret = true) { imageApiKey = it }
            Field("Model", imageModel) { imageModel = it }

            Text(
                "留空则复用对话侧配置。注意：多数对话网关（如小米 MiMo）并不提供出图接口，需要单独填一个支持 /images/generations 的地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 图像侧独立探测：先保存再探测，确保使用最新配置
            val imageState by viewModel.imageState.collectAsState()
            val imageModels by viewModel.imageModels.collectAsState()
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = imageState !is TestState.Testing,
                    onClick = {
                        saveAll(viewModel, chatBaseUrl, chatApiKey, chatModel, imageBaseUrl, imageApiKey, imageModel, nsfw)
                        viewModel.testImageConnection()
                    }
                ) {
                    Text(if (imageState is TestState.Testing) "检测中…" else "测试图像接口")
                }
                TextButton(
                    modifier = Modifier.weight(1f),
                    enabled = imageState !is TestState.Testing,
                    onClick = {
                        saveAll(viewModel, chatBaseUrl, chatApiKey, chatModel, imageBaseUrl, imageApiKey, imageModel, nsfw)
                        viewModel.testImageGeneration()
                    }
                ) {
                    Text("发一张测试图")
                }
            }

            when (val s = imageState) {
                is TestState.Idle -> Unit
                is TestState.Testing -> Text(
                    "正在检测图像接口…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is TestState.Ok -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is TestState.Error -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (imageModels.isNotEmpty()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    imageModels.take(12).forEach { m ->
                        TextButton(onClick = { imageModel = m }) { Text(m, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            androidx.compose.material3.HorizontalDivider()

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("内容过滤（个人把玩默认关）")
                Switch(checked = nsfw, onCheckedChange = { nsfw = it })
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    saveAll(viewModel, chatBaseUrl, chatApiKey, chatModel, imageBaseUrl, imageApiKey, imageModel, nsfw)
                }
            ) {
                Text("保存")
            }

            // 连通冒烟：先保存再探测，确保使用最新配置
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = testState !is TestState.Testing,
                onClick = {
                    saveAll(viewModel, chatBaseUrl, chatApiKey, chatModel, imageBaseUrl, imageApiKey, imageModel, nsfw)
                    viewModel.testConnection()
                }
            ) {
                if (testState is TestState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("测试连接")
                }
            }

            when (val s = testState) {
                is TestState.Idle -> Unit
                is TestState.Testing -> Text(
                    "正在探测连通性…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is TestState.Ok -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                is TestState.Error -> Text(
                    "连接失败：${s.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                "Key 仅加密保存在本机，App 不做后端、不收集数据。玩家设定请到「主页 → 档案」管理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 保存前统一走这里：三处按钮（保存/测试对话/测试图像）都要求"先落盘最新输入再动作"。 */
private fun saveAll(
    vm: SettingsViewModel,
    chatBaseUrl: String,
    chatApiKey: String,
    chatModel: String,
    imageBaseUrl: String,
    imageApiKey: String,
    imageModel: String,
    nsfw: Boolean
) = vm.save(
    ApiConfig(
        chatBaseUrl = chatBaseUrl,
        chatApiKey = chatApiKey,
        chatModel = chatModel,
        imageBaseUrl = imageBaseUrl,
        imageApiKey = imageApiKey,
        imageModel = imageModel,
        nsfwFilterEnabled = nsfw
    )
)

@Composable
private fun Field(
    label: String,
    value: String,
    isSecret: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (isSecret)
            KeyboardOptions(keyboardType = KeyboardType.Password)
        else
            KeyboardOptions.Default
    )
}
