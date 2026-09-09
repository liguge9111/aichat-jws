package com.lirui.charchat.ui.group

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lirui.charchat.data.db.entity.CharacterCardEntity
import com.lirui.charchat.data.db.entity.GroupEntity
import java.io.File

/**
 * P7：群聊列表。列出已有群（成员头像 + 名称），点击进入群聊；
 * 右下 + 新建群（填名称 + 勾选成员）；长按/点删除可删群。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onBack: () -> Unit,
    onOpenGroup: (String) -> Unit,
    vm: GroupListViewModel = hiltViewModel()
) {
    val groups by vm.groupsFlow.collectAsState()
    val cards by vm.cardsFlow.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GroupEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("群聊") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, "新建群聊")
            }
        }
    ) { padding ->
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有群聊", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "点右下 + 把几个角色拉到一个群里",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groups, key = { it.id }) { g ->
                    GroupRow(
                        group = g,
                        cards = cards,
                        onClick = { onOpenGroup(g.id) },
                        onDelete = { pendingDelete = g }
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateGroupDialog(
            cards = cards,
            onDismiss = { showCreate = false },
            onConfirm = { name, ids ->
                showCreate = false
                vm.createGroup(name, ids) { onOpenGroup(it) }
            }
        )
    }

    pendingDelete?.let { g ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除群聊") },
            text = { Text("将删除「${g.name}」及其全部聊天记录，角色卡不受影响。") },
            confirmButton = {
                TextButton(onClick = { vm.deleteGroup(g.id); pendingDelete = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun GroupRow(
    group: GroupEntity,
    cards: List<CharacterCardEntity>,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val avatars = remember(group.memberIdsJson, cards) {
        val ids = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(group.memberIdsJson)
                .let { it as? kotlinx.serialization.json.JsonArray }
                ?.mapNotNull { it.toString().trim('"') }
        }.getOrNull() ?: emptyList()
        ids.mapNotNull { id -> cards.firstOrNull { it.id == id }?.avatarPath }
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 成员头像横排（最多显示 4 个）
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
            avatars.take(4).forEach { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            if (avatars.isEmpty()) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                        .padding(end = 10.dp),
                    contentAlignment = Alignment.Center
                ) { Text("群") }
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${avatars.size} 位成员",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
    }
}

@Composable
private fun CreateGroupDialog(
    cards: List<CharacterCardEntity>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建群聊") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("群名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (cards.isEmpty()) {
                    Text("还没有角色，先去导入一张卡", modifier = Modifier.padding(top = 12.dp))
                } else {
                    Text("选择成员", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    LazyColumn {
                        items(cards, key = { it.id }) { c ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        if (selected.contains(c.id)) selected.remove(c.id)
                                        else selected.add(c.id)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected.contains(c.id),
                                    onCheckedChange = { checked ->
                                        if (checked) selected.add(c.id) else selected.remove(c.id)
                                    }
                                )
                                Text(c.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(name, selected.toList()) }
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
