package com.lirui.charchat.ui.import

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lirui.charchat.domain.model.CharacterCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.factory(LocalContext.current.applicationContext)
    )
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importUri(context, it) }
    }

    var jsonText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导入角色卡") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹ 返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择文件（.png / .json 角色卡）")
            }

            Text("或粘贴角色卡 JSON：")
            OutlinedTextField(
                value = jsonText,
                onValueChange = { jsonText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("角色卡 JSON") },
                singleLine = false,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Button(
                onClick = { viewModel.importJson(jsonText) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("解析并预览")
            }

            when (val s = state) {
                is ImportUiState.Idle -> Unit
                is ImportUiState.Loading -> CircularProgressIndicator()
                is ImportUiState.Error -> Text(
                    "导入失败：${s.message}",
                    color = MaterialTheme.colorScheme.error
                )
                is ImportUiState.Saved -> {
                    Text("已保存：${s.card.name}", color = MaterialTheme.colorScheme.primary)
                    Button(onClick = onSaved, modifier = Modifier.fillMaxWidth()) { Text("完成") }
                }
                is ImportUiState.Preview -> PreviewCard(card = s.card, onSave = { viewModel.save(it) })
            }
        }
    }
}

@Composable
private fun PreviewCard(card: CharacterCard, onSave: (CharacterCard) -> Unit) {
    var name by remember(card.id) { mutableStateOf(card.name) }
    var description by remember(card.id) { mutableStateOf(card.description) }
    var statusBar by remember(card.id) { mutableStateOf(card.statusText) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("解析预览（可编辑）", style = MaterialTheme.typography.titleSmall)
        Labelled("名称", name) { name = it }
        Labelled("角色设定（角色卡原文，含外貌/性格/背景等全部内容）", description, multiline = true) { description = it }
        Labelled("状态栏模板(可选)", statusBar, multiline = true) { statusBar = it }
        Text(
            "卡里的 Status/状态/Stats 段会自动抓取，可在此微调。若卡带世界书（character_book）也会一并启用，可在属性页单独维护。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                onSave(
                    card.copy(
                        name = name.trim().ifBlank { card.name },
                        description = description.trim(),
                        statusText = statusBar.trim()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存并开始")
        }
    }
}

@Composable
private fun Labelled(
    label: String,
    value: String,
    multiline: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        maxLines = if (multiline) 6 else 1
    )
}
