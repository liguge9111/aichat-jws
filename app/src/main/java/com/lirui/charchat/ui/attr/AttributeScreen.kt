package com.lirui.charchat.ui.attr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lirui.charchat.domain.model.StrategyAttributes
import java.io.File

/**
 * 属性面板（酒馆化后）：
 * - 顶部：头像 + 名称 + 好感进度条 + 关系（只读）。
 * - 角色原始设定（description，可直接编辑）/ 状态栏 / 已达成的约定。
 * - 世界书入口（独立维护页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributeScreen(
    onBack: () -> Unit,
    onOpenWorldBook: () -> Unit = {},
    vm: AttributeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val draft = state.draft

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { "角色属性" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (state.editing) {
                        IconButton(onClick = vm::save) { Icon(Icons.Filled.Check, "保存") }
                    } else {
                        IconButton(onClick = vm::startEdit) { Icon(Icons.Filled.Edit, "编辑") }
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) { Text("加载中…") }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.saved && !state.editing) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "已保存",
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            ProfileHeader(name = state.name, avatarPath = state.avatarPath, attributes = state.attributes)

            // 世界书维护入口
            Button(
                onClick = onOpenWorldBook,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.MenuBook, null, modifier = Modifier.size(18.dp))
                Text(" 管理世界书（${state.worldBookCount} 条）")
            }

            SectionCard(
                title = "角色设定（原文）",
                hint = "直接沿用角色卡原文；修改会写回，后续聊天以这段原文扮演"
            ) {
                if (state.editing) {
                    OutlinedTextField(
                        value = draft.description,
                        onValueChange = { vm.updateDraft(draft.copy(description = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                        maxLines = 18
                    )
                } else {
                    Text(
                        state.draft.description.ifBlank { "（无原文设定）" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SectionCard(title = "状态栏", hint = "随对话由角色更新；空表示该角色暂无状态栏") {
                if (state.editing) {
                    OutlinedTextField(
                        value = draft.statusText,
                        onValueChange = { vm.updateDraft(draft.copy(statusText = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 8
                    )
                } else {
                    Text(
                        state.statusText.ifBlank { "（暂无状态栏）" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SectionCard(
                title = "已达成的约定",
                hint = "对话中角色认可你的新设定后自动记在这里，每轮提醒 TA；可手动整理（一行一条）"
            ) {
                if (state.editing) {
                    OutlinedTextField(
                        value = draft.notes,
                        onValueChange = { vm.updateDraft(draft.copy(notes = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 12
                    )
                } else {
                    val lines = state.notes.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty()) {
                        Text(
                            "暂无约定。试着在聊天中向 TA 提出一条设定，TA 认可后会自动记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lines.forEach { line -> Text("• $line", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            SectionCard(
                title = "共同回忆",
                hint = "玩家在对话里讲起的「我们一起做过的事」，角色接受后自动记录；之后每轮都会记得"
            ) {
                if (state.editing) {
                    OutlinedTextField(
                        value = draft.memories,
                        onValueChange = { vm.updateDraft(draft.copy(memories = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 12
                    )
                } else {
                    val lines = state.memories.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty()) {
                        Text(
                            "暂无共同回忆。对 TA 说「还记得昨天我们一起…吗」，TA 会接住并记下来。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        lines.forEach { line -> Text("• $line", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }

            SectionCard(
                title = "世界书（${state.worldBookCount} 条）",
                hint = "命中关键字时注入对话；点上方按钮可增删改条目"
            ) {
                if (state.worldBookPreview.isEmpty()) {
                    Text(
                        "这张卡没有世界书条目。可在世界书页手动添加背景设定（地点、组织、人物关系等）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.worldBookPreview.forEach { line ->
                        Text("• $line", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = vm::cancelEdit, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = vm::save, modifier = Modifier.weight(1f)) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    avatarPath: String?,
    attributes: StrategyAttributes
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (avatarPath != null) {
                AsyncImage(
                    model = File(avatarPath),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        attributes.relationship,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                LinearProgressIndicator(
                    progress = attributes.affection.coerceIn(0, 100) / 100f,
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Text(
                    "好感度 ${attributes.affection.coerceIn(0, 100)} / 100",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    hint: String? = null,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            hint?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}
