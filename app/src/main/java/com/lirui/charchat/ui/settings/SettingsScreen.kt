package com.lirui.charchat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lirui.charchat.data.settings.ApiConfig

/**
 * BYOK 设置页：对话 / 图像 / 语音三组配置各自折叠，默认收起，点击标题展开编辑。
 * 三组均支持任意 OpenAI 兼容网关；语音侧留空时逐级复用（ASR 复用 TTS，TTS 复用对话）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val current by viewModel.config.collectAsState()
    val testState by viewModel.testState.collectAsState()

    // 本地编辑态：整份配置一起改，避免多字段各自 remember 时保存漏项
    var cfg by remember { mutableStateOf(current) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 设置 (BYOK)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Section("对话模型", "Base URL / API Key / Model，默认折叠",
                summary = cfg.chatModel.ifBlank { "未设置模型" }) {
                Field("Base URL", cfg.chatBaseUrl) { cfg = cfg.copy(chatBaseUrl = it) }
                Field("API Key", cfg.chatApiKey, isSecret = true) { cfg = cfg.copy(chatApiKey = it) }
                Field("Model", cfg.chatModel) { cfg = cfg.copy(chatModel = it) }
            }

            Section("图像模型", "出图网关配置，留空复用对话侧",
                summary = cfg.imageModel.ifBlank { "复用对话侧" }) {
                Field("Base URL", cfg.imageBaseUrl) { cfg = cfg.copy(imageBaseUrl = it) }
                Field("API Key", cfg.imageApiKey, isSecret = true) { cfg = cfg.copy(imageApiKey = it) }
                Field("Model", cfg.imageModel) { cfg = cfg.copy(imageModel = it) }
                Text(
                    "留空则复用对话侧配置。注意：多数对话网关（如小米 MiMo）并不提供出图接口，需要单独填一个支持 /images/generations 的地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 图像侧独立探测：先保存再探测，确保使用最新配置
                val imageState by viewModel.imageState.collectAsState()
                val imageModels by viewModel.imageModels.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = imageState !is TestState.Testing,
                        onClick = {
                            viewModel.save(cfg)
                            viewModel.testImageConnection()
                        }
                    ) {
                        Text(if (imageState is TestState.Testing) "检测中…" else "测试图像接口")
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = imageState !is TestState.Testing,
                        onClick = {
                            viewModel.save(cfg)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imageModels.take(12).forEach { m ->
                            TextButton(onClick = { cfg = cfg.copy(imageModel = m) }) {
                                Text(m, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            Section("语音模型", "语音合成(TTS)与识别(ASR)，留空逐级复用",
                summary = cfg.ttsModel.ifBlank { "复用对话侧" }) {
                Text(
                    "TTS 把角色的回复读成语音；ASR 把玩家发的语音转成文字再进对话模型。留空时 ASR 复用 TTS 配置、TTS 复用对话配置。\n" +
                        "填 Base URL 时只需填到 /v1 即可（例如 https://api.xiaomimimo.com/v1），" +
                        "误粘 /chat/completions 之类的端点后缀会自动去掉。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("语音合成（TTS）", style = MaterialTheme.typography.labelLarge)
                Field("TTS Base URL", cfg.ttsBaseUrl) { cfg = cfg.copy(ttsBaseUrl = it) }
                Field("TTS API Key", cfg.ttsApiKey, isSecret = true) { cfg = cfg.copy(ttsApiKey = it) }
                Field("TTS Model（留空按协议默认）", cfg.ttsModel) { cfg = cfg.copy(ttsModel = it) }
                Field("音色 / 口音（Voice ID）", cfg.ttsVoice) { cfg = cfg.copy(ttsVoice = it) }
                Text(
                    "小米 MiMo 音色：mimo_default / 冰糖 / 茉莉 / 苏打 / 白桦 / Mia / Chloe / Milo / Dean",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))
                Text("语音识别（ASR，可选）", style = MaterialTheme.typography.labelLarge)
                Field("ASR Base URL", cfg.asrBaseUrl) { cfg = cfg.copy(asrBaseUrl = it) }
                Field("ASR API Key", cfg.asrApiKey, isSecret = true) { cfg = cfg.copy(asrApiKey = it) }
                Field("ASR Model（留空按协议默认）", cfg.asrModel) { cfg = cfg.copy(asrModel = it) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("识别语言", style = MaterialTheme.typography.bodyMedium)
                    listOf("auto", "zh", "en").forEach { lang ->
                        TextButton(
                            onClick = { cfg = cfg.copy(asrLanguage = lang) },
                            enabled = cfg.asrLanguage != lang
                        ) {
                            Text(
                                when (lang) {
                                    "auto" -> "自动"
                                    "zh" -> "中文"
                                    else -> "英文"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("内容过滤（个人把玩默认关）")
                Switch(checked = cfg.nsfwFilterEnabled, onCheckedChange = { cfg = cfg.copy(nsfwFilterEnabled = it) })
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.save(cfg) }
            ) {
                Text("保存")
            }

            // 连通冒烟：先保存再探测，确保使用最新配置
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = testState !is TestState.Testing,
                onClick = {
                    viewModel.save(cfg)
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

/**
 * 可折叠配置分区：默认收起，点击标题行展开。
 * 收起时显示 summary（如当前模型名），方便不展开也能确认配置状态。
 */
@Composable
private fun Section(
    title: String,
    subtitle: String,
    summary: String,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (expanded) subtitle else "$subtitle · 当前：$summary",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content()
                }
            }
        }
    }
}

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
