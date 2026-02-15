package com.unoassistant.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.unoassistant.overlay.persist.OverlayStateRepository
import com.unoassistant.overlay.ui.theme.UnoAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UnoAssistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OverlayControlPage()
                }
            }
        }
    }
}

@Composable
fun OverlayControlPage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlayPermission by remember { mutableStateOf(canDrawOverlays(context)) }
    var isOverlayShowing by remember { mutableStateOf(OverlayPanelManager.isShowing()) }
    var maxOpponents by remember { mutableStateOf(OverlayStateRepository.get(context).maxOpponents) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = canDrawOverlays(context)
                isOverlayShowing = OverlayPanelManager.isShowing()
                maxOpponents = OverlayStateRepository.get(context).maxOpponents
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF7F5F2), Color(0xFFEAF5F3), Color(0xFFF7F5F2))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "UNO 记牌助手",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "轻量悬浮记录，专注手动推理",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475569)
            )

            StatusCard(
                hasOverlayPermission = hasOverlayPermission,
                isOverlayShowing = isOverlayShowing
            )

            ActionCard(
                onGrant = { openOverlayPermissionSettings(context) },
                onStart = {
                    if (!hasOverlayPermission) {
                        openOverlayPermissionSettings(context)
                        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                        return@ActionCard
                    }
                    OverlayServiceController.start(context)
                    // 真实状态以服务内 addView 结果为准，这里仅做乐观更新并在返回/恢复时刷新。
                    isOverlayShowing = true
                },
                onStop = {
                    OverlayServiceController.stop(context)
                    isOverlayShowing = false
                    Toast.makeText(context, "已关闭悬浮面板", Toast.LENGTH_SHORT).show()
                },
                maxOpponents = maxOpponents,
                onIncreaseMax = {
                    val next = (maxOpponents + 1).coerceAtMost(12)
                    OverlayStateRepository.update(context) { cur -> cur.copy(maxOpponents = next) }
                    maxOpponents = next
                },
                onDecreaseMax = {
                    val next = (maxOpponents - 1).coerceAtLeast(1)
                    OverlayStateRepository.update(context) { cur -> cur.copy(maxOpponents = next) }
                    maxOpponents = next
                }
            )

            GuideCard()
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusCard(
    hasOverlayPermission: Boolean,
    isOverlayShowing: Boolean
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("当前状态", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    label = if (hasOverlayPermission) "权限已授权" else "权限未授权",
                    ok = hasOverlayPermission
                )
                StatusPill(
                    label = if (isOverlayShowing) "面板已开启" else "面板未开启",
                    ok = isOverlayShowing
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, ok: Boolean) {
    val bg = if (ok) Color(0xFFE6FFFA) else Color(0xFFFFF1F2)
    val fg = if (ok) Color(0xFF0F766E) else Color(0xFFBE123C)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = fg, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActionCard(
    onGrant: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    maxOpponents: Int,
    onIncreaseMax: () -> Unit,
    onDecreaseMax: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("操作", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最大对手数", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDecreaseMax) { Text("-") }
                    Text("$maxOpponents", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onIncreaseMax) { Text("+") }
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onGrant,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFE0F2FE),
                    contentColor = Color(0xFF0C4A6E)
                )
            ) { Text("前往授权引导") }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStart
            ) { Text("开启悬浮面板") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStop
            ) { Text("关闭悬浮面板") }
        }
    }
}

@Composable
private fun GuideCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.88f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("使用说明", style = MaterialTheme.typography.titleMedium)
            Text("• 仅提供悬浮覆盖形态的手动记录", style = MaterialTheme.typography.bodySmall)
            Text("• 不联网，不做自动识别", style = MaterialTheme.typography.bodySmall)
            Text("• 建议首次先授权，再开启悬浮面板", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun canDrawOverlays(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
}

private fun openOverlayPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
private fun OverlayControlPagePreview() {
    UnoAssistantTheme {
        OverlayControlPage()
    }
}
