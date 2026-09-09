package com.lirui.charchat.ui.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 18+ 入口：首次启动勾选"已满18岁"方可进入。
 * 确认后由调用方持久化 ageVerified 并导航（见 AppNav）。
 */
@Composable
fun AgeGateScreen(onVerified: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "成年内容提示",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
            text = "本应用含 NSFW 内容，仅限成年用户个人娱乐使用。继续即表示你已年满 18 岁，并遵守当地法律法规。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = agreed, onCheckedChange = { agreed = it })
            Text(text = "我已年满 18 岁，并知悉上述内容")
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = agreed,
            onClick = onVerified
        ) {
            Text(text = "进入")
        }
    }
}
