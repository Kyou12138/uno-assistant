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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                OverlayControlPage()
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

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = canDrawOverlays(context)
                isOverlayShowing = OverlayPanelManager.isShowing()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "悬浮窗权限状态：${if (hasOverlayPermission) "已授权" else "未授权"}",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = "面板状态：${if (isOverlayShowing) "已开启" else "未开启"}",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = "说明：本应用仅提供“悬浮覆盖”形态的手动记录，不做联网或自动识别。",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { openOverlayPermissionSettings(context) }
        ) {
            Text("前往授权引导")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!hasOverlayPermission) {
                    openOverlayPermissionSettings(context)
                    Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val ok = OverlayPanelManager.show(context)
                isOverlayShowing = ok
                if (!ok) {
                    Toast.makeText(context, "未获得悬浮窗权限，无法开启", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                OverlayServiceController.start(context)
            }
        ) {
            Text("开启悬浮面板")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                OverlayPanelManager.hide()
                isOverlayShowing = false
                OverlayServiceController.stop(context)
                Toast.makeText(context, "已关闭悬浮面板", Toast.LENGTH_SHORT).show()
            }
        ) {
            Text("关闭悬浮面板")
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
    OverlayControlPage()
}
