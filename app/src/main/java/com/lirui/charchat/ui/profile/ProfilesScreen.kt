package com.lirui.charchat.ui.profile

import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lirui.charchat.data.db.entity.PlayerProfileEntity
import java.io.File
import java.util.UUID

/**
 * 玩家档案：多套"玩家设定"，聊天前按卡片绑定/切换；每个档案可自定义头像。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    vm: ProfilesViewModel = hiltViewModel()
) {
    val profiles by vm.profiles.collectAsState()
    var editing by remember { mutableStateOf<PlayerProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    /** 正在换头像的档案 id（null 表示为"新建中的档案"选头像）。 */
    var pickingFor by remember { mutableStateOf<String?>(null) }
    var newAvatarUri by remember { mutableStateOf<Uri?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val id = pickingFor
            if (id != null) {
                vm.setAvatar(id, uri)
                pickingFor = null
            } else {
                newAvatarUri = uri
            }
        }
    }

    fun launchPick(profileId: String?) {
        pickingFor = profileId
        pickLauncher.launch("image/*")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的玩家档案") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newAvatarUri = null; creating = true }) {
                Icon(Icons.Filled.Add, "新建档案")
            }
        }
    ) { padding ->
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "还没有玩家档案。点右下 + 新建一份，\n聊天时会先让你选择用哪份档案。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles, key = { it.id }) { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerAvatar(
                                model = p.avatarPath?.let { File(it) },
                                name = p.name,
                                size = 48.dp,
                                onClick = { launchPick(p.id) }
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                val detail = listOfNotNull(
                                    p.personality.ifBlank { null },
                                    p.relationToChar.ifBlank { null }
                                ).joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "点头像可自定义",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { editing = p }) {
                                Icon(Icons.Filled.Edit, "编辑档案")
                            }
                            IconButton(onClick = { vm.delete(p.id) }) {
                                Icon(Icons.Filled.Delete, "删除档案", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ProfileEditDialog(
            title = "新建档案",
            avatarModel = newAvatarUri,
            onDismiss = { creating = false },
            onPickAvatar = { launchPick(null) },
            onSave = { name, persona, relation, extra ->
                val p = PlayerProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    personality = persona,
                    relationToChar = relation,
                    extra = extra
                )
                vm.save(p)
                newAvatarUri?.let { vm.setAvatar(p.id, it) }
                newAvatarUri = null
                creating = false
            }
        )
    }

    editing?.let { p ->
        ProfileEditDialog(
            title = "编辑档案",
            initial = p,
            avatarModel = p.avatarPath?.let { File(it) },
            onDismiss = { editing = null },
            onPickAvatar = { launchPick(p.id) },
            onSave = { name, persona, relation, extra ->
                vm.save(p.copy(name = name, personality = persona, relationToChar = relation, extra = extra))
                editing = null
            }
        )
    }
}

/** 玩家头像：有图显图，无图显示名字首字（或人形占位）。 */
@Composable
fun PlayerAvatar(
    model: Any?,
    name: String,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null
) {
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (name.isBlank()) {
                Icon(
                    Icons.Filled.Person,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.6f)
                )
            } else {
                Text(
                    name.take(1),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    title: String,
    onDismiss: () -> Unit,
    onPickAvatar: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
    initial: PlayerProfileEntity? = null,
    avatarModel: Any? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var persona by remember { mutableStateOf(initial?.personality ?: "") }
    var relation by remember { mutableStateOf(initial?.relationToChar ?: "") }
    var extra by remember { mutableStateOf(initial?.extra ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerAvatar(model = avatarModel, name = name, size = 56.dp, onClick = onPickAvatar)
                    Text(
                        "点击头像自定义",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名字") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    label = { Text("性格 / 扮演风格") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relation,
                    onValueChange = { relation = it },
                    label = { Text("默认与角色的关系") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("补充设定") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotBlank(),
                onClick = { onSave(name.trim(), persona.trim(), relation.trim(), extra.trim()) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
