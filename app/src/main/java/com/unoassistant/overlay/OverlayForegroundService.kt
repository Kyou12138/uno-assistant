package com.unoassistant.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 悬浮面板启用期间的前台服务：
 * - 创建通知渠道
 * - 展示常驻通知，避免后台被系统回收
 * - 关闭时停止自身并尽量清理悬浮面板（兜底）
 */
class OverlayForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // 关闭路径兜底：防止残留悬浮与通知
                OverlayPanelManager.hide()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> return handleStart()
            else -> return handleStart()
        }
    }

    private fun handleStart(): Int {
        startInForeground()
        // 在服务内添加 overlay，确保脱离 Activity 生命周期。
        val ok = OverlayPanelManager.show(this)
        if (!ok) {
            // 未授权时不应启动成功：清理通知并停止服务，避免残留“已启用”假象。
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startInForeground() {
        ensureChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "悬浮标记面板",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "悬浮标记面板启用期间的常驻通知"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("UNO 悬浮标记面板已启用")
            .setContentText("点此通知可快速关闭悬浮面板")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "关闭", stopPendingIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.unoassistant.overlay.action.START"
        const val ACTION_STOP = "com.unoassistant.overlay.action.STOP"

        private const val CHANNEL_ID = "overlay_marker"
        private const val NOTIFICATION_ID = 1001
    }
}
