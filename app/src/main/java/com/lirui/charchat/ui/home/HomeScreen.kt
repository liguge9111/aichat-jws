package com.lirui.charchat.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import com.lirui.charchat.data.backup.RestoreMode
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import java.io.File

/**
 * P6：角色管理主页。
 * - 搜索（按名称/关系）、点击进入单聊。
 * - 卡片菜单：清空聊天记录 / 删除角色（均带确认）。
 * - 顶栏菜单：备份导出（SAF 写 JSON）、备份导入（SAF 读 JSON，可选跳过/覆盖）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenGroups: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel()
) {
    val cards by vm.cardsFlow.collectAsState()
    val query by vm.queryFlow.collectAsState()
    val notice by vm.notice.collectAsState()
    val pendingExport by vm.pendingExport.collectAsState()
    val context = LocalContext.current

    var pendingDelete by remember { mutableStateOf<CharacterCardEntity?>(null) }
    var pendingClear by remember { mutableStateOf<CharacterCardEntity?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    // 导出：VM 生成 JSON → 拿到后唤起 SAF 让用户选择保存位置
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && pendingExport != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(pendingExport!!.toByteArray())
                }
            }.onSuccess {
                Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "写入失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
        vm.consumeExport()
    }

    LaunchedEffect(pendingExport) {
        if (pendingExport != null) {
            exportLauncher.launch("charchat-backup-${System.currentTimeMillis()}.json")
        }
    }

    // 导入：SAF 选文件 → 读文本 → 询问冲突策略 → 还原
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }
            }.getOrNull()
            if (raw.isNullOrBlank()) {
                Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
            } else {
                pendingRestore = raw
            }
        }
    }

    LaunchedEffect(notice) {
        notice?.let {
            Toast.makeText(context, it.text, Toast.LENGTH_SHORT).show()
            vm.consumeNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色列表") },
                actions = {
                    TextButton(onClick = onOpenProfiles) { Text("档案") }
                    TextButton(onClick = onOpenGroups) { Text("群聊") }
                    TextButton(onClick = onOpenSettings) { Text("设置") }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, "更多")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("导出备份") },
                                onClick = { menuExpanded = false; vm.requestExport() }
                            )
                            DropdownMenuItem(
                                text = { Text("导入备份") },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onImport) { Text("+") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("搜索角色名或关系") },
                singleLine = true
            )

            if (cards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (query.isBlank()) "还没有角色" else "没有匹配的角色",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (query.isBlank()) "点右下 + 导入一张角色卡" else "换个关键词试试",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cards, key = { it.id }) { card ->
                        CardRow(
                            card = card,
                            onClick = { onOpenChat(card.id) },
                            onDelete = { pendingDelete = card },
                            onClearChat = { pendingClear = card }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { card ->
        ConfirmDialog(
            title = "删除角色",
            text = "将删除「${card.name}」及其全部聊天记录，此操作不可恢复。",
            confirmText = "删除",
            onDismiss = { pendingDelete = null },
            onConfirm = { vm.delete(card); pendingDelete = null }
        )
    }

    pendingClear?.let { card ->
        ConfirmDialog(
            title = "清空聊天记录",
            text = "将清空「${card.name}」的全部聊天记录，角色卡与攻略属性会保留。",
            confirmText = "清空",
            onDismiss = { pendingClear = null },
            onConfirm = { vm.clearChat(card); pendingClear = null }
        )
    }

    // 导入冲突策略：同 id 已存在时是跳过还是覆盖
    pendingRestore?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("导入备份") },
            text = { Text("如果已存在同名角色（相同 ID），要如何处理？") },
            confirmButton = {
                TextButton(onClick = {
                    vm.importBackup(raw, RestoreMode.OVERWRITE)
                    pendingRestore = null
                }) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.importBackup(raw, RestoreMode.MERGE)
                    pendingRestore = null
                }) { Text("跳过已有") }
            }
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun CardRow(
    card: CharacterCardEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onClearChat: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (card.avatarPath != null) {
                AsyncImage(
                    model = File(card.avatarPath),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(card.name.take(1)) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${card.relationship} · ❤${card.affection}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = card.affection.coerceIn(0, 100) / 100f,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, "更多操作")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("清空聊天记录") },
                        onClick = { menu = false; onClearChat() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除角色") },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }
    }
}
