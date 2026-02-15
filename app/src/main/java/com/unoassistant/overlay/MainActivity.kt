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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
    val canDecreaseMax = maxOpponents > 1
    val canIncreaseMax = maxOpponents < 12

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
                    colors = listOf(
                        Color(0xFFF5F7FC),
                        Color(0xFFEAF4FF),
                        Color(0xFFF2F6FF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeroCard()

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
                canIncreaseMax = canIncreaseMax,
                canDecreaseMax = canDecreaseMax,
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
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF0F2E4D), Color(0xFF1B4A76))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "UNO 记牌助手",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "更简洁的悬浮记录体验，专注手动推理与临场决策",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFD9E8FF)
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    hasOverlayPermission: Boolean,
    isOverlayShowing: Boolean
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x1A0F172A)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("运行状态", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .border(1.dp, fg.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(fg, CircleShape)
        )
        Text(text = label, color = fg, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActionCard(
    onGrant: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    maxOpponents: Int,
    canIncreaseMax: Boolean,
    canDecreaseMax: Boolean,
    onIncreaseMax: () -> Unit,
    onDecreaseMax: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x1A0F172A)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("快捷操作", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("最大对手数", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "范围 1 ~ 12（当前 $maxOpponents）",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        enabled = canDecreaseMax,
                        onClick = onDecreaseMax,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFEFF4FF), RoundedCornerShape(12.dp))
                    ) {
                        Text("-", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                    Text(
                        "$maxOpponents",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        enabled = canIncreaseMax,
                        onClick = onIncreaseMax,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFEFF4FF), RoundedCornerShape(12.dp))
                    ) {
                        Text("+", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onGrant,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color(0xFFDAEEFF),
                    contentColor = Color(0xFF12385B)
                )
            ) { Text("前往授权引导") }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStart,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) { Text("开启悬浮面板") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStop,
                shape = RoundedCornerShape(12.dp)
            ) { Text("关闭悬浮面板") }
        }
    }
}

@Composable
private fun GuideCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0x1A0F172A)),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("使用建议", style = MaterialTheme.typography.titleMedium)
            Text("• 仅提供悬浮覆盖形态的手动记录", style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
            Text("• 不联网，不做自动识别", style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
            Text("• 建议先授权后开启，避免频繁跳转设置页", style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
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
