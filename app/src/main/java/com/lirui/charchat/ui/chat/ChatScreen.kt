package com.lirui.charchat.ui.chat

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.lirui.charchat.data.db.entity.ChatMessageEntity
import com.lirui.charchat.data.db.entity.PlayerProfileEntity
import com.lirui.charchat.domain.chat.ChatPhase
import com.lirui.charchat.domain.chat.RoundController
import com.lirui.charchat.ui.profile.PlayerAvatar
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * 单聊界面（微信风格）：
 * - 顶部白底居中角色名；内容区浅灰；角色消息左侧带头像白气泡，自己消息右侧绿气泡（带玩家头像）。
 * - 长按自己的文字消息 → 编辑并截断重生成；底栏左侧独立"重生成"。
 * - 麦克风：轻点切到"按住说话"语音模式；按住录音、松开发送（上滑取消）。语音消息转写为
 *   文字后发给角色，角色回复会带一条语音（v9 云端 TTS），点气泡播放。
 * - 回复完成短震动反馈；进行中显示"对方正在输入…"。
 */
private val WeChatGreen = Color(0xFF95EC69)
private val WeChatBg = Color(0xFFEDEDED)
private val WeChatInput = Color(0xFFF2F2F2)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    cardId: String,
    onBack: () -> Unit,
    onOpenAttrs: () -> Unit = {},
    onOpenWorldBook: () -> Unit = {},
    vm: ChatViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var showStatus by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var showVoicePick by remember { mutableStateOf(false) }
    val phase = state.phase
    val busy = phase !is ChatPhase.Done

    // 长按录音所需权限；结果仅提示，授权后需再次按住说话
    val recordPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "已获得麦克风权限，长按「按住 说话」即可发语音" else "需要录音权限才能发语音",
            Toast.LENGTH_SHORT
        ).show()
    }

    // 新消息/阶段变化 → 自动滚动到底部（发送、回复、流式气泡出现都会触发）
    LaunchedEffect(state.messages.size, busy, state.guardNote) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    // 回复完成 → 短震动（仅当从"进行中"变为成功完成时）
    val vibrator = remember {
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    }
    var wasBusy by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (busy) wasBusy = true
        if (phase is ChatPhase.Done && phase.success && wasBusy) {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") v.vibrate(60)
                }
            }
            wasBusy = false
        }
    }

    Column(Modifier.fillMaxSize().background(WeChatBg)) {
        // 顶栏：返回 + 居中名称（副标题档案·好感）+ 状态栏 + 更多菜单
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 2.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF111111))
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    state.card?.name ?: "聊天",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF111111),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = (state.boundProfile?.name?.let { "$it · " } ?: "") +
                    (state.card?.let { "❤${it.attributes.affection} ${it.attributes.relationship}" } ?: "")
                Text(
                    sub.ifBlank { "请先选择玩家档案" },
                    fontSize = 11.sp,
                    color = Color(0xFF999999),
                    maxLines = 1
                )
            }
            IconButton(onClick = { showStatus = true }) {
                Icon(Icons.Filled.List, "查看状态栏", tint = Color(0xFF111111))
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, "更多", tint = Color(0xFF111111))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("角色设定与属性") },
                        onClick = { menuExpanded = false; onOpenAttrs() }
                    )
                    DropdownMenuItem(
                        text = { Text("世界书") },
                        leadingIcon = { Icon(Icons.Filled.Book, null) },
                        onClick = { menuExpanded = false; onOpenWorldBook() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.card?.ttsVoice.isNullOrBlank()) "角色语音音色（默认）" else "角色语音音色：${state.card!!.ttsVoice}") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, null) },
                        onClick = { menuExpanded = false; showVoicePick = true }
                    )
                    if (state.profiles.size > 1) {
                        DropdownMenuItem(
                            text = { Text("切换玩家档案") },
                            onClick = { menuExpanded = false; vm.requestSwitch() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("清空这段聊天") },
                        onClick = { menuExpanded = false; confirmClear = true }
                    )
                }
            }
        }

        if (state.awaitingProfile) {
            ProfilePicker(
                profiles = state.profiles,
                onPick = vm::bindProfile,
                onCreate = vm::createAndBind,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                state.guardNote?.let {
                    item { CenterHint(it, isError = true) }
                }
                state.notice?.let {
                    item { CenterHint(it, isError = true) }
                }
                items(state.messages, key = { it.seq }) { msg ->
                    WeChatBubble(
                        msg = msg,
                        avatarPath = state.card?.avatarPath,
                        playerAvatarPath = state.boundProfile?.avatarPath,
                        playerName = state.boundProfile?.name ?: "",
                        playingAudioPath = state.playingAudioPath,
                        onLongPressUser = { editing = it.seq to it.text },
                        onTogglePlay = { vm.togglePlayAudio(it) }
                    )
                }
                when (phase) {
                    is ChatPhase.Thinking -> item { WeChatTypingBubble(state.card?.avatarPath) }
                    is ChatPhase.Streaming -> item {
                        if (phase.preview.isBlank()) WeChatTypingBubble(state.card?.avatarPath)
                        else CenterHint(phase.preview, isError = false)
                    }
                    is ChatPhase.GeneratingPhoto ->
                        item { PhotoReceivingBubble(state.card?.avatarPath, phase.index, phase.total) }
                    is ChatPhase.GeneratingVoice ->
                        item { WeChatTypingBubble(state.card?.avatarPath, hint = "正在合成语音…") }
                    is ChatPhase.Done -> if (!phase.success) {
                        item { CenterHint(phase.error ?: "回复失败", isError = true) }
                    }
                    else -> {}
                }
            }

            WeChatInputBar(
                text = state.input,
                enabled = !busy,
                voiceMode = voiceMode,
                recording = state.isRecording,
                voiceBusy = state.voiceBusy,
                onTextChange = vm::onInputChange,
                onSend = vm::send,
                onToggleVoice = {
                    val turningOn = !voiceMode
                    voiceMode = turningOn
                    // 进入语音模式前先确保麦克风权限，避免按住时才弹窗打断录音手势
                    if (turningOn && !vm.hasRecordPermission()) {
                        recordPerm.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onRegenerate = vm::regenerateLast,
                onSwitchProfile = vm::requestSwitch,
                switchEnabled = state.profiles.size > 1,
                onRecordStart = {
                    if (vm.hasRecordPermission()) vm.startRecording()
                    else recordPerm.launch(Manifest.permission.RECORD_AUDIO)
                },
                onRecordEnd = { send -> if (send) vm.stopRecording(false) else vm.stopRecording(true) }
            )
        }
    }

    if (editing != null) {
        EditDialog(
            initial = editing!!.second,
            onDismiss = { editing = null },
            onConfirm = { newText ->
                vm.editAndResend(editing!!.first, newText)
                editing = null
            }
        )
    }

    if (showStatus) {
        val st = state.card?.statusText?.trim()
        AlertDialog(
            onDismissRequest = { showStatus = false },
            title = { Text("${state.card?.name ?: "角色"} · 状态栏") },
            text = {
                if (st.isNullOrEmpty()) {
                    Text(
                        "该角色暂无状态栏。\n\n部分角色卡自带状态（身体/情绪/处境等），导入后会自动抓取；也可以在属性面板里手动填写。",
                        fontSize = 13.sp,
                        color = Color(0xFF666666)
                    )
                } else {
                    Text(
                        st,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = Color(0xFF111111)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatus = false }) { Text("知道了") }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空这段聊天") },
            text = { Text("将清空当前全部聊天记录，角色设定、好感与记忆都会保留。") },
            confirmButton = {
                TextButton(onClick = { vm.clearChat(); confirmClear = false }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }

    if (state.showGreetingDialog) {
        GreetingDialog(
            candidates = state.greetingCandidates,
            onPick = { vm.startWithGreeting(it) },
            onSkip = { vm.skipGreeting() }
        )
    }

    if (state.showSwitchDialog) {
        ProfileSwitchDialog(
            profiles = state.profiles,
            onPick = { vm.switchProfile(it) },
            onDismiss = { vm.cancelSwitch() }
        )
    }

    if (showVoicePick) {
        VoicePickDialog(
            current = state.card?.ttsVoice.orEmpty(),
            onDismiss = { showVoicePick = false },
            onConfirm = { voice ->
                vm.updateTtsVoice(voice)
                showVoicePick = false
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeChatBubble(
    msg: ChatMessageEntity,
    avatarPath: String?,
    playerAvatarPath: String?,
    playerName: String,
    playingAudioPath: String?,
    onLongPressUser: (ChatMessageEntity) -> Unit,
    onTogglePlay: (String) -> Unit
) {
    val isUser = msg.role == "USER"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            CharacterAvatar(avatarPath, 40.dp)
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (msg.audioPath != null) {
                // 语音消息：气泡显示播放态 + 时长；文字转写在气泡下方小字（ASR 内容透明可见）
                val playing = playingAudioPath == msg.audioPath
                val shape = if (isUser) {
                    RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 14.dp)
                } else {
                    RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp)
                }
                Row(
                    Modifier
                        .clip(shape)
                        .background(if (isUser) WeChatGreen else Color.White)
                        .clickable { onTogglePlay(msg.audioPath) }
                        .widthIn(min = 78.dp, max = 220.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val iconColor = when {
                        playing -> Color(0xFF07C160)
                        isUser -> Color(0xFF2E7D32)
                        else -> Color(0xFF666666)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        if (playing) "停止播放" else "播放语音",
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (playing) {
                            "播放中…"
                        } else {
                            if (msg.durationMs > 0) "${((msg.durationMs + 500) / 1000).coerceAtLeast(1)}″"
                            else "语音"
                        },
                        fontSize = 14.sp,
                        color = Color(0xFF111111)
                    )
                }
                if (msg.text.isNotBlank()) {
                    val transcript = remember(msg.text) { RoundController.sanitizeVisible(msg.text) }
                    Text(
                        transcript,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = if (isUser) Color(0xFF4E6B3E) else Color(0xFF9E9E9E),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp, start = 4.dp).widthIn(max = 220.dp)
                    )
                }
            } else if (msg.text.isNotBlank()) {
                val shape = if (isUser) {
                    RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomEnd = 14.dp, bottomStart = 14.dp)
                } else {
                    RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp)
                }
                Box(
                    Modifier
                        .clip(shape)
                        .background(if (isUser) WeChatGreen else Color.White)
                        .combinedClickable(
                            enabled = isUser,
                            onClick = {},
                            onLongClick = { onLongPressUser(msg) }
                        )
                        .widthIn(max = 258.dp)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    // 渲染层兜底：历史消息（firstMes 入库前未清洗、模型早期轮残留等）也走一次清洗，
                    // 防止玩家看到残留的 **、#、---、未知【…】标签。
                    val cleaned = remember(msg.text) { RoundController.sanitizeVisible(msg.text) }
                    Text(
                        cleaned,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFF111111),
                        softWrap = true
                    )
                }
            }
            msg.imagePath?.let { path ->
                val model: Any = if (path.startsWith("http")) path else File(path)
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
        if (isUser) {
            Spacer(Modifier.width(6.dp))
            PlayerAvatar(
                model = playerAvatarPath?.let { File(it) },
                name = playerName,
                size = 40.dp
            )
        }
    }
}

@Composable
private fun CharacterAvatar(avatarPath: String?, size: Dp) {
    if (avatarPath != null) {
        AsyncImage(
            model = File(avatarPath),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(Color(0xFFB9B9B9)),
            contentAlignment = Alignment.Center
        ) { Text("聊", color = Color.White, fontSize = 15.sp) }
    }
}

@Composable
private fun WeChatTypingBubble(avatarPath: String?, hint: String = "对方正在输入…") {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterAvatar(avatarPath, 40.dp)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(hint, fontSize = 14.sp, color = Color(0xFF888888))
        }
    }
}

/**
 * 图片"接收中"的动态占位气泡：与聊天风格一致（白底气泡 + 左侧角色头像），
 * 用转圈 + 呼吸文案模拟"对方正在把图片发过来"，避免"正在生成照片 n/m"这类后台口吻出戏。
 */
@Composable
private fun PhotoReceivingBubble(avatarPath: String?, index: Int, total: Int) {
    val transition = rememberInfiniteTransition(label = "photoReceiving")
    val textAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recvAlpha"
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterAvatar(avatarPath, 40.dp)
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 待接收的图片占位框 + 动态加载环
                Box(
                    Modifier
                        .width(132.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEFEFEF)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF9E9E9E),
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
                Spacer(Modifier.height(7.dp))
                val hint = if (total > 1) "图片接收中 $index/$total" else "图片接收中"
                Text(
                    hint,
                    fontSize = 13.sp,
                    color = Color(0xFF888888).copy(alpha = textAlpha)
                )
            }
        }
    }
}

@Composable
private fun CenterHint(text: String, isError: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (isError) Color(0xFFD32F2F) else Color(0xFF999999),
            modifier = Modifier.padding(vertical = 4.dp),
            maxLines = 3
        )
    }
}

@Composable
private fun WeChatInputBar(
    text: String,
    enabled: Boolean,
    voiceMode: Boolean,
    recording: Boolean,
    voiceBusy: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleVoice: () -> Unit,
    onRegenerate: () -> Unit,
    onSwitchProfile: () -> Unit = {},
    switchEnabled: Boolean = false,
    onRecordStart: () -> Unit,
    onRecordEnd: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        if (recording) {
            Text(
                "正在录音… 松开发送，上滑取消",
                fontSize = 12.sp,
                color = Color(0xFFD32F2F),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        } else if (voiceBusy) {
            Text(
                "正在识别语音…",
                fontSize = 12.sp,
                color = Color(0xFF07C160),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (voiceMode) {
                // 语音模式：左侧绿色麦克风 = 返回文字输入；中间大按钮按住说话
                IconButton(onClick = onToggleVoice) {
                    Icon(Icons.Filled.Mic, "返回文字输入", tint = Color(0xFF07C160))
                }
                Spacer(Modifier.width(2.dp))
                VoiceHoldButton(
                    enabled = enabled,
                    busy = voiceBusy,
                    onStart = onRecordStart,
                    onEnd = onRecordEnd
                )
            } else {
                if (switchEnabled) {
                    IconButton(onClick = onSwitchProfile) {
                        Icon(Icons.Filled.Person, "切换玩家档案", tint = Color(0xFF666666))
                    }
                }
                IconButton(onClick = onRegenerate, enabled = enabled) {
                    Icon(Icons.Filled.Refresh, "重生成上一条", tint = Color(0xFF666666))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("", fontSize = 16.sp) },
                    maxLines = 4,
                    enabled = enabled,
                    shape = RoundedCornerShape(22.dp),
                    textStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF111111)),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = WeChatInput,
                        unfocusedContainerColor = WeChatInput,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF111111)
                    )
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onToggleVoice, enabled = enabled) {
                    Icon(Icons.Filled.Mic, "按住说话", tint = Color(0xFF666666))
                }
                val canSend = enabled && text.isNotBlank()
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (canSend) Color(0xFF07C160) else Color(0xFFA8D8B4))
                        .clickable(enabled = canSend, onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "发送",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * 按住说话按钮：按下开始录音，松开发送；上滑超过阈值取消（微信式）。
 * 识别处理中（voiceBusy）禁用，避免并发录音。用指针手动跟踪按下/抬起：
 * detectTapGestures 会把轻微移动当"非点击"取消按压，不适合录音场景。
 */
@Composable
private fun RowScope.VoiceHoldButton(
    enabled: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onEnd: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val cancelPx = with(density) { 90.dp.toPx() }
    val bg = if (busy) Color(0xFFE6E6E6) else WeChatInput
    val label = if (busy) "正在识别…" else "按住 说话"
    Box(
        Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .then(
                if (enabled && !busy) {
                    Modifier.pointerInput(enabled && !busy, cancelPx) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown()
                                onStart()
                                var sending = false
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val c = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (c.position.y < down.position.y - cancelPx) {
                                            break // 上滑取消
                                        }
                                        if (c.changedToUpIgnoreConsumed()) {
                                            sending = true
                                            break
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    onEnd(false)
                                    throw e
                                }
                                onEnd(sending)
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 15.sp, color = Color(0xFF555555))
    }
}

/** 每角色独立音色设置：留空则回落设置页全局音色（设置→语音模型→全局音色）。 */
@Composable
private fun VoicePickDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(current) { mutableStateOf(current) }
    val presets = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer", "Cherry", "Serena")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("角色语音音色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "这是 TA 回复语音所用的音色 ID，只对该角色生效。留空则用设置页「语音模型」里的全局音色。",
                    fontSize = 13.sp,
                    color = Color(0xFF777777)
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { p ->
                        TextButton(
                            onClick = { value = p },
                            enabled = value != p
                        ) {
                            Text(p, fontSize = 12.sp)
                        }
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("音色 ID（可输入自定义）") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EditDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑并重新生成") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 8,
                placeholder = { Text("修改你的消息，确认后删除该轮回复并重新生成") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("重新生成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 首次进入：选择用哪份玩家档案开始聊天。 */
@Composable
private fun ProfilePicker(
    profiles: List<PlayerProfileEntity>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var quickName by remember { mutableStateOf("") }
    Column(
        modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("选择玩家档案", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111111))
        Text(
            "开始聊天前选一份你的设定（名字/性格/与 TA 的关系）。之后可在右上角菜单里切换，切换会开启一段新聊天。",
            fontSize = 13.sp,
            color = Color(0xFF777777)
        )
        if (profiles.isEmpty()) {
            OutlinedTextField(
                value = quickName,
                onValueChange = { quickName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("你的名字") },
                singleLine = true
            )
            androidx.compose.material3.Button(
                enabled = quickName.trim().isNotEmpty(),
                onClick = { onCreate(quickName) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("用这个名字开始") }
            Text(
                "之后可在「主页 → 我的玩家档案」里补充更多设定或创建更多档案。",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA)
            )
        } else {
            profiles.forEach { p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { onPick(p.id) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerAvatar(
                        model = p.avatarPath?.let { File(it) },
                        name = p.name,
                        size = 44.dp
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111111))
                        val d = listOfNotNull(p.personality.ifBlank { null }, p.relationToChar.ifBlank { null })
                            .joinToString(" · ")
                        if (d.isNotBlank()) {
                            Text(d, fontSize = 12.sp, color = Color(0xFF888888), maxLines = 2)
                        }
                    }
                }
            }
            Text(
                "想换一份设定？去「主页 → 我的玩家档案」新建。",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA)
            )
        }
    }
}

/** 聊天中切换档案：将清空当前聊天并作为新聊天开始。 */
@Composable
private fun ProfileSwitchDialog(
    profiles: List<PlayerProfileEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换玩家档案") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "切换会清空这段聊天并作为新聊天重新开始（角色会重新打招呼）。",
                    fontSize = 13.sp,
                    color = Color(0xFF777777)
                )
                profiles.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF2F2F2))
                            .clickable { onPick(p.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerAvatar(
                            model = p.avatarPath?.let { File(it) },
                            name = p.name,
                            size = 32.dp
                        )
                        Text(p.name, fontSize = 15.sp, color = Color(0xFF111111), modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 首次进入聊天：角色卡带开场白时，先弹候选让玩家预览并选择用哪条开场，
 * 选中的那条会作为第一条角色消息写入；也允许跳过直接进空聊天。
 */
@Composable
private fun GreetingDialog(
    candidates: List<String>,
    onPick: (Int) -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        // 点外部/返回键 = 跳过开场白，不会卡死玩家
        onDismissRequest = onSkip,
        title = { Text("选择开场白") },
        text = {
            Column {
                Text(
                    if (candidates.size > 1) {
                        "TA 准备了 ${candidates.size} 条开场白，选一条作为你们的第一次对话："
                    } else {
                        "TA 会用下面这段话与你开始第一次对话："
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF777777)
                )
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    candidates.forEachIndexed { i, text ->
                        val label = if (candidates.size > 1) "开场白 ${i + 1}" else "开场白"
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (i % 2 == 0) Color(0xFFF7F7F8) else Color(0xFFEFF3F7))
                                .clickable { onPick(i) }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF4A7FB5)
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "用它开场 →",
                                    fontSize = 12.sp,
                                    color = Color(0xFF4A7FB5)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = Color(0xFF222222)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSkip) { Text("跳过开场白") }
        }
    )
}
