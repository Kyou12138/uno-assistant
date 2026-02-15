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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.unoassistant.overlay.persist.OverlayStateRepository
import com.unoassistant.overlay.model.Opponent
import com.unoassistant.overlay.model.OverlayState
import com.unoassistant.overlay.model.UnoColor
import java.util.UUID

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

        // 启动状态仓库并读取初始配置（位置/透明度/锁定等后续逐步接入）
        val state = OverlayStateRepository.get(appContext)

        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = buildOverlayView(appContext, state)
        view.alpha = state.alpha
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = state.overlayX
            y = state.overlayY
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

    private fun buildOverlayView(context: Context, initialState: OverlayState): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // 轻量白底，用于确认“可见且可点击”
            setBackgroundColor(0xCCFFFFFF.toInt())
            setPadding(20, 16, 20, 16)
        }

        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val addBtn = Button(context).apply {
            text = "添加对手"
        }

        val closeBtn = Button(context).apply {
            text = "关闭"
            setOnClickListener {
                // 关闭入口需要同时停止前台服务，避免通知残留
                OverlayServiceController.stop(context)
                hide()
            }
        }

        toolbar.addView(addBtn)
        toolbar.addView(closeBtn)

        val title = TextView(context).apply {
            text = "UNO 悬浮标记面板"
            textSize = 14f
        }

        val listScroll = ScrollView(context)
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        listScroll.addView(listContainer)

        fun render(state: OverlayState) {
            listContainer.removeAllViews()
            if (state.opponents.isEmpty()) {
                val empty = TextView(context).apply {
                    text = "暂无对手，点击“添加对手”开始。"
                    textSize = 12f
                }
                listContainer.addView(empty)
                return
            }
            state.opponents.forEach { opponent ->
                listContainer.addView(
                    buildOpponentRow(context, opponent) {
                        render(OverlayStateRepository.get(context))
                    }
                )
            }
        }

        fun addOpponent() {
            OverlayStateRepository.update(context) { cur ->
                val nextIndex = nextOpponentIndex(cur.opponents)
                val id = UUID.randomUUID().toString()
                val name = "对手 $nextIndex"
                cur.copy(opponents = cur.opponents + Opponent.default(id, name))
            }
            render(OverlayStateRepository.get(context))
        }

        addBtn.setOnClickListener { addOpponent() }

        root.addView(toolbar)
        root.addView(title)
        root.addView(listScroll)

        render(initialState)
        return root
    }

    private fun buildOpponentRow(
        context: Context,
        opponent: Opponent,
        onChanged: () -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }

        val name = TextView(context).apply {
            text = opponent.name
            textSize = 12f
            setPadding(0, 0, 12, 0)
        }

        val deleteBtn = Button(context).apply {
            text = "删"
            setOnClickListener {
                OverlayStateRepository.update(context) { cur ->
                    cur.copy(opponents = cur.opponents.filterNot { it.id == opponent.id })
                }
                onChanged()
                Toast.makeText(context, "已删除 ${opponent.name}", Toast.LENGTH_SHORT).show()
            }
        }

        val colors = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        colors.addView(colorButton(context, UnoColor.Red))
        colors.addView(colorButton(context, UnoColor.Yellow))
        colors.addView(colorButton(context, UnoColor.Blue))
        colors.addView(colorButton(context, UnoColor.Green))

        row.addView(name)
        row.addView(colors)
        row.addView(deleteBtn)
        return row
    }

    private fun colorButton(context: Context, color: UnoColor): View {
        // OCM-05-01 仅要求默认四色为“彩色(未排除态)”，交互切换在 OCM-05-02 实现。
        val btn = Button(context).apply {
            text = when (color) {
                UnoColor.Red -> "R"
                UnoColor.Yellow -> "Y"
                UnoColor.Blue -> "B"
                UnoColor.Green -> "G"
            }
            val bg = when (color) {
                UnoColor.Red -> 0xFFFF5252.toInt()
                UnoColor.Yellow -> 0xFFFFD740.toInt()
                UnoColor.Blue -> 0xFF448AFF.toInt()
                UnoColor.Green -> 0xFF69F0AE.toInt()
            }
            setBackgroundColor(bg)
            setOnClickListener {
                Toast.makeText(context, "颜色切换将在后续步骤实现", Toast.LENGTH_SHORT).show()
            }
        }
        return btn
    }

    private fun nextOpponentIndex(opponents: List<Opponent>): Int {
        // 默认命名：对手 1/2/...，删除后不重排已存在的名称；新增取 max+1。
        var max = 0
        opponents.forEach { o ->
            val m = Regex("^对手\\s+(\\d+)$").find(o.name)
            val n = m?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (n != null && n > max) max = n
        }
        return max + 1
    }

    private fun canDrawOverlays(context: Context): Boolean {
        // minSdk=26，仍保留 M 以下判断以便复用与可读性
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}
