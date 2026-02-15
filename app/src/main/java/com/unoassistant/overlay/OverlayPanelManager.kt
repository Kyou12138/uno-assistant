package com.unoassistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 最小悬浮面板管理器：
 * - 仅负责 show/hide 与生命周期兜底
 * - 当前阶段用于权限引导闭环与“已授权可显示悬浮面板”的验收
 *
 * 后续（前台服务/Compose UI/拖动/持久化）会在其它 issue 中逐步完善。
 */
object OverlayPanelManager {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    fun isShowing(): Boolean = overlayView != null

    fun show(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!canDrawOverlays(appContext)) return false
        if (overlayView != null) return true

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = buildOverlayView(appContext)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        wm.addView(view, lp)
        windowManager = wm
        overlayView = view
        return true
    }

    fun hide() {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        try {
            wm.removeView(view)
        } finally {
            overlayView = null
            windowManager = null
        }
    }

    private fun buildOverlayView(context: Context): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 轻量白底，用于确认“可见且可点击”
            setBackgroundColor(0xCCFFFFFF.toInt())
            setPadding(20, 16, 20, 16)
        }

        val title = TextView(context).apply {
            text = "UNO 悬浮标记面板（MVP）"
            textSize = 16f
        }

        val desc = TextView(context).apply {
            text = "当前仅用于权限闭环验证，后续会替换为完整标记板 UI。"
            textSize = 12f
        }

        val closeBtn = Button(context).apply {
            text = "关闭悬浮面板"
            setOnClickListener { hide() }
        }

        container.addView(title)
        container.addView(desc)
        container.addView(closeBtn)
        return container
    }

    private fun canDrawOverlays(context: Context): Boolean {
        // minSdk=26，仍保留 M 以下判断以便复用与可读性
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}

