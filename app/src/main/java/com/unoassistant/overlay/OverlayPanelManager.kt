package com.unoassistant.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.unoassistant.overlay.model.Opponent
import com.unoassistant.overlay.model.OverlayState
import com.unoassistant.overlay.model.UnoColor
import com.unoassistant.overlay.persist.OverlayStateRepository
import java.util.UUID

/**
 * 悬浮窗管理：
 * - 一个“控制条”悬浮窗（添加/锁定/重置/关闭）
 * - N 个“对手信息”独立悬浮窗（每个对手一个 WindowManager View）
 */
object OverlayPanelManager {
    private var windowManager: WindowManager? = null
    private var controlView: View? = null
    private var controlLockButton: Button? = null
    private val opponentViews = mutableMapOf<String, View>()
    private val opponentLayouts = mutableMapOf<String, WindowManager.LayoutParams>()

    fun isShowing(): Boolean = controlView != null

    fun show(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!canDrawOverlays(appContext)) return false

        val wm = windowManager ?: (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also {
            windowManager = it
        }

        val state = OverlayStateRepository.get(appContext)

        if (controlView == null) {
            val panel = buildControlPanelView(appContext)
            panel.alpha = state.alpha
            val lp = newLayoutParams(state.overlayX, state.overlayY)
            wm.addView(panel, lp)
            controlView = panel
        }

        syncOpponentWindows(appContext)
        updateLockButtonText(appContext)
        return true
    }

    fun hide() {
        val wm = windowManager ?: return

        opponentViews.values.forEach { view ->
            runCatching { wm.removeView(view) }
        }
        opponentViews.clear()
        opponentLayouts.clear()

        controlView?.let { runCatching { wm.removeView(it) } }
        controlView = null
        controlLockButton = null
    }

    private fun buildControlPanelView(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCCFFFFFF.toInt())
            setPadding(16, 12, 16, 12)
        }

        val title = TextView(context).apply {
            text = "UNO 悬浮控制条（对手为独立窗口）"
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }

        val toolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val addBtn = Button(context).apply { text = "添加对手" }
        val lockBtn = Button(context)
        val resetBtn = Button(context).apply { text = "重置颜色" }
        val closeBtn = Button(context).apply { text = "关闭" }

        addBtn.setOnClickListener {
            OverlayStateRepository.update(context) { cur ->
                val nextIndex = nextOpponentIndex(cur.opponents)
                val id = UUID.randomUUID().toString()
                val name = "对手 $nextIndex"
                val pos = defaultOpponentPosition(cur.opponents.size)
                cur.copy(opponents = cur.opponents + Opponent.default(id, name).copy(offsetX = pos.first, offsetY = pos.second))
            }
            syncOpponentWindows(context)
        }

        lockBtn.setOnClickListener {
            OverlayStateRepository.update(context) { cur ->
                cur.copy(locked = !cur.locked)
            }
            updateLockButtonText(context)
            val locked = OverlayStateRepository.get(context).locked
            Toast.makeText(context, if (locked) "已锁定：禁止拖动对手窗" else "已解锁：可拖动对手窗", Toast.LENGTH_SHORT).show()
        }

        resetBtn.setOnClickListener {
            OverlayStateRepository.update(context) { cur ->
                cur.copy(opponents = cur.opponents.map { it.copy(excluded = UnoColor.entries.associateWith { false }) })
            }
            syncOpponentWindows(context)
            Toast.makeText(context, "已重置所有颜色", Toast.LENGTH_SHORT).show()
        }

        closeBtn.setOnClickListener {
            OverlayServiceController.stop(context)
            hide()
        }

        toolbar.addView(addBtn)
        toolbar.addView(lockBtn)
        toolbar.addView(resetBtn)
        toolbar.addView(closeBtn)

        root.addView(title)
        root.addView(toolbar)

        controlLockButton = lockBtn
        updateLockButtonText(context)
        return root
    }

    private fun syncOpponentWindows(context: Context) {
        val wm = windowManager ?: return
        val state = OverlayStateRepository.get(context)

        val aliveIds = state.opponents.map { it.id }.toSet()
        val removedIds = opponentViews.keys.filter { it !in aliveIds }
        removedIds.forEach { id ->
            opponentViews[id]?.let { runCatching { wm.removeView(it) } }
            opponentViews.remove(id)
            opponentLayouts.remove(id)
        }

        state.opponents.forEachIndexed { index, opponent ->
            if (opponentViews.containsKey(opponent.id)) {
                opponentViews[opponent.id]?.let { runCatching { wm.removeView(it) } }
                opponentViews.remove(opponent.id)
                opponentLayouts.remove(opponent.id)
            }

            val pos = if (opponent.offsetX == 0 && opponent.offsetY == 0) {
                defaultOpponentPosition(index)
            } else {
                opponent.offsetX to opponent.offsetY
            }

            val layout = newLayoutParams(pos.first, pos.second)
            val view = buildOpponentWindow(context, opponent.copy(offsetX = pos.first, offsetY = pos.second), layout)
            wm.addView(view, layout)
            opponentViews[opponent.id] = view
            opponentLayouts[opponent.id] = layout
        }
    }

    private fun buildOpponentWindow(
        context: Context,
        opponent: Opponent,
        layout: WindowManager.LayoutParams
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10, 10, 10, 10)
            setBackgroundColor(0xD9FFFFFF.toInt())
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val name = TextView(context).apply {
            text = "${opponent.name} ↕"
            textSize = 12f
            setPadding(0, 0, 8, 0)
        }

        val deleteBtn = Button(context).apply {
            text = "删"
            setOnClickListener {
                OverlayStateRepository.update(context) { cur ->
                    cur.copy(opponents = cur.opponents.filterNot { it.id == opponent.id })
                }
                syncOpponentWindows(context)
            }
        }

        header.addView(name)
        header.addView(deleteBtn)

        val colors = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        top.addView(colorButton(context, opponent, UnoColor.Red))
        top.addView(colorButton(context, opponent, UnoColor.Yellow))
        bottom.addView(colorButton(context, opponent, UnoColor.Blue))
        bottom.addView(colorButton(context, opponent, UnoColor.Green))

        colors.addView(top)
        colors.addView(bottom)

        root.addView(header)
        root.addView(colors)

        attachOpponentWindowDrag(context, name, opponent.id, root, layout)
        return root
    }

    private fun attachOpponentWindowDrag(
        context: Context,
        dragHandle: View,
        opponentId: String,
        targetView: View,
        layout: WindowManager.LayoutParams
    ) {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0

        dragHandle.setOnTouchListener { _, event ->
            val locked = OverlayStateRepository.get(context).locked
            if (locked) return@setOnTouchListener false

            val wm = windowManager ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = layout.x
                    startY = layout.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layout.x = startX + (event.rawX - startRawX).toInt()
                    layout.y = startY + (event.rawY - startRawY).toInt()
                    wm.updateViewLayout(targetView, layout)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val x = layout.x
                    val y = layout.y
                    OverlayStateRepository.update(context) { cur ->
                        cur.copy(opponents = cur.opponents.map { o -> if (o.id == opponentId) o.copy(offsetX = x, offsetY = y) else o })
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun colorButton(context: Context, opponent: Opponent, color: UnoColor): View {
        val isExcluded = opponent.excluded[color] == true
        return Button(context).apply {
            text = when (color) {
                UnoColor.Red -> "R"
                UnoColor.Yellow -> "Y"
                UnoColor.Blue -> "B"
                UnoColor.Green -> "G"
            }
            layoutParams = LinearLayout.LayoutParams(dp(context, 64), dp(context, 40)).apply {
                marginEnd = dp(context, 4)
                bottomMargin = dp(context, 4)
            }

            val colorBg = when (color) {
                UnoColor.Red -> 0xFFFF5252.toInt()
                UnoColor.Yellow -> 0xFFFFD740.toInt()
                UnoColor.Blue -> 0xFF448AFF.toInt()
                UnoColor.Green -> 0xFF1B5E20.toInt()
            }
            val colorText = when (color) {
                UnoColor.Yellow -> 0xFF1A1A1A.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            val excludedBg = 0xFF757575.toInt()

            setBackgroundColor(if (isExcluded) excludedBg else colorBg)
            setTextColor(if (isExcluded) 0xFFFFFFFF.toInt() else colorText)

            setOnClickListener {
                OverlayStateRepository.update(context) { cur ->
                    cur.copy(
                        opponents = cur.opponents.map { o ->
                            if (o.id != opponent.id) o
                            else o.copy(excluded = o.excluded + (color to (o.excluded[color] != true)))
                        }
                    )
                }
                syncOpponentWindows(context)
            }
        }
    }

    private fun updateLockButtonText(context: Context) {
        val locked = OverlayStateRepository.get(context).locked
        controlLockButton?.text = if (locked) "已锁定" else "已解锁"
    }

    private fun nextOpponentIndex(opponents: List<Opponent>): Int {
        var max = 0
        opponents.forEach { o ->
            val m = Regex("^对手\\s+(\\d+)$").find(o.name)
            val n = m?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (n != null && n > max) max = n
        }
        return max + 1
    }

    private fun defaultOpponentPosition(index: Int): Pair<Int, Int> {
        val col = index % 2
        val row = index / 2
        val x = 40 + col * 220
        val y = 300 + row * 200
        return x to y
    }

    private fun newLayoutParams(x: Int, y: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}
