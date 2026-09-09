package com.lirui.charchat.ui.worldbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lirui.charchat.domain.worldbook.WorldEntry

/**
 * 世界书维护（独立页）：条目的 增/删/改、启用开关、常驻开关。
 * 改动即时保存并参与后续聊天触发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookScreen(
    onBack: () -> Unit,
    vm: WorldBookViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<WorldEntry?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("世界书 · ${state.cardName}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, "新增条目")
            }
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("加载中…")
            }
            return@Scaffold
        }
        if (state.entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有世界书条目", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点击右下 + 添加：写下 触发词 与 设定内容。\n聊天中玩家提到触发词时，对应内容会注入给角色。",
                    modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.entries, key = { it.id.ifBlank { it.content } }) { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = { editing = entry },
                        onToggle = { vm.toggleEnabled(entry) },
                        onDelete = { vm.remove(entry) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        EntryDialog(
            title = "新增条目",
            onDismiss = { showAdd = false },
            onSave = { name, keys, content, constant ->
                vm.add(name, keys, content, constant)
                showAdd = false
            }
        )
    }
    editing?.let { entry ->
        EntryDialog(
            title = "编辑条目",
            entry = entry,
            onDismiss = { editing = null },
            onSave = { name, keys, content, constant ->
                vm.update(entry, name, keys, content, constant)
                editing = null
            }
        )
    }
}

@Composable
private fun EntryRow(
    entry: WorldEntry,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.material3.Card(
        Modifier
            .fillMaxWidth()
            .background(
                if (entry.enabled) Color.Unspecified
                else Color(0x22AAAAAA)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.keys.joinToString(" / ").ifBlank { "(无常驻触发词)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.constant) {
                    Text(
                        "常驻",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Text(
                    entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = entry.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EntryDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit,
    entry: WorldEntry? = null
) {
    var keys by remember { mutableStateOf(entry?.keys?.joinToString("、") ?: "") }
    var content by remember { mutableStateOf(entry?.content ?: "") }
    var constant by remember { mutableStateOf(entry?.constant ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = keys,
                    onValueChange = { keys = it },
                    label = { Text("触发词（用 逗号 分隔）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("设定内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("常驻（每次对话都注入）", modifier = Modifier.weight(1f))
                    Switch(checked = constant, onCheckedChange = { constant = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = content.trim().isNotEmpty(),
                onClick = { onSave("", keys, content, constant) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
