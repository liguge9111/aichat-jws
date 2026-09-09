package com.lirui.charchat.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.lirui.charchat.data.db.entity.GroupMessageEntity
import com.lirui.charchat.domain.chat.GroupPhase
import java.io.File

/**
 * P7：群聊界面。
 * - 顶栏：群名 + 成员头像横排（点头像即插入 @提及，实现定向）。
 * - 消息流：玩家右侧；角色左侧带头像与名字，含图片气泡。
 * - 输入栏：@按钮弹出成员列表插入提及；无 @ 时全员依次回复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    vm: GroupChatViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val phase = state.phase
    val busy = phase !is GroupPhase.Done && phase !is GroupPhase.Idle
    var mentionMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.group?.name ?: "群聊")
                        Text(
                            "${state.members.size} 位成员",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    // 成员头像：点击插入 @提及（定向该角色）
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.members.take(4).forEach { m ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { vm.mention(m.name) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (m.avatarPath != null) {
                                    AsyncImage(
                                        model = File(m.avatarPath),
                                        contentDescription = m.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                } else {
                                    Text(m.name.take(1), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box {
                    IconButton(onClick = { mentionMenu = true }, enabled = !busy) {
                        Icon(Icons.Filled.Person, "@提及")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = mentionMenu,
                        onDismissRequest = { mentionMenu = false }
                    ) {
                        state.members.forEach { m ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = { mentionMenu = false; vm.mention(m.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.input,
                    onValueChange = vm::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发消息…（@名字 可定向）") },
                    maxLines = 4,
                    enabled = !busy
                )
                Button(
                    onClick = vm::send,
                    enabled = !busy && state.input.isNotBlank(),
                    modifier = Modifier.padding(start = 6.dp)
                ) { Text("发送") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            state.guardNote?.let { note ->
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            note,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            items(state.messages, key = { it.seq }) { msg ->
                GroupBubble(msg)
            }
            when (phase) {
                is GroupPhase.Thinking ->
                    item { TypingLine("${phase.charName} 正在输入…") }
                is GroupPhase.Streaming ->
                    item { TypingLine(phase.preview.ifBlank { "${phase.charName} 正在输入…" }) }
                is GroupPhase.GeneratingPhoto ->
                    item { TypingLine("${phase.charName} 正在发照片 ${phase.index}/${phase.total}…") }
                is GroupPhase.Done -> {
                    phase.error?.let { err ->
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "${phase.failed ?: ""} $err".trim(),
                                    modifier = Modifier.padding(10.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                GroupPhase.Idle -> {}
            }
        }
    }
}

@Composable
private fun GroupBubble(msg: GroupMessageEntity) {
    val isUser = msg.role == "USER"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (!isUser) {
                Text(
                    msg.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
            }
            if (msg.text.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        msg.text,
                        modifier = Modifier.padding(10.dp).widthIn(max = 260.dp),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            msg.imagePath?.let { path ->
                AsyncImage(
                    model = if (path.startsWith("http")) path else File(path),
                    contentDescription = null,
                    modifier = Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun TypingLine(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text,
                modifier = Modifier.padding(10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
