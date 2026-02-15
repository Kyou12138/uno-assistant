package com.unoassistant.overlay

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 前台服务启停入口，便于在 Activity/Overlay View 等不同位置复用。
 */
object OverlayServiceController {
    fun start(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, OverlayForegroundService::class.java).apply {
            action = OverlayForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, OverlayForegroundService::class.java).apply {
            action = OverlayForegroundService.ACTION_STOP
        }
        appContext.startService(intent)
    }
}

