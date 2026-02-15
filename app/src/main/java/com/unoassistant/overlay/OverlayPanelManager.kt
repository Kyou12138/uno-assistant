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

    private val panelBgColor = 0xD9FFFFFF.toInt()
    private val excludedColorBg = 0xFF757575.toInt()

    private const val controlPaddingHorizontalPx = 10
    private const val controlPaddingVerticalPx = 8
    private const val opponentPaddingPx = 10
    private const val opponentHeaderBottomPaddingPx = 8
    private const val opponentNameEndPaddingPx = 8

    private const val addBtnWidthDp = 48
    private const val closeBtnWidthDp = 48
    private const val lockResetBtnWidthDp = 64
    private const val controlBtnHeightDp = 40
    private const val controlBtnMarginEndDp = 6

    private const val deleteBtnWidthDp = 56
    private const val deleteBtnHeightDp = 32
    private const val deleteBtnTextSizeSp = 11f

    private const val colorBtnWidthDp = 64
    private const val colorBtnHeightDp = 40
    private const val colorBtnMarginEndDp = 4
    private const val colorBtnMarginBottomDp = 4

    private const val defaultControlHeightDp = 56
    private const val opponentOffsetFromControlDp = 12
    private const val opponentGridColGapDp = 220
    private const val opponentGridRowGapDp = 190

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
            // 确保 measuredHeight 可用，便于对手窗口默认摆放避让控制条
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
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
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(panelBgColor)
            setPadding(
                controlPaddingHorizontalPx,
                controlPaddingVerticalPx,
                controlPaddingHorizontalPx,
                controlPaddingVerticalPx
            )
        }

        val addBtn = Button(context).apply { text = "+" }
        val lockBtn = Button(context)
        val resetBtn = Button(context).apply { text = "重置" }
        val closeBtn = Button(context).apply { text = "关" }

        addBtn.setOnClickListener {
            OverlayStateRepository.update(context) { cur ->
                val nextIndex = nextOpponentIndex(cur.opponents)
                val id = UUID.randomUUID().toString()
                val name = "对手 $nextIndex"
                val pos = defaultOpponentPosition(context, cur.opponents.size, controlView, cur)
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

        // 控制条尽量紧凑，减少遮挡游戏区域
        addBtn.layoutParams = buttonLayoutParams(context, addBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        lockBtn.layoutParams = buttonLayoutParams(context, lockResetBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        resetBtn.layoutParams = buttonLayoutParams(context, lockResetBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        closeBtn.layoutParams = buttonLayoutParams(context, closeBtnWidthDp, controlBtnHeightDp)

        root.addView(addBtn)
        root.addView(lockBtn)
        root.addView(resetBtn)
        root.addView(closeBtn)

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
                // 默认摆放避让控制条区域，避免遮挡操作按钮
                defaultOpponentPosition(context, index, controlView, state)
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
            setPadding(opponentPaddingPx, opponentPaddingPx, opponentPaddingPx, opponentPaddingPx)
            setBackgroundColor(panelBgColor)
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, opponentHeaderBottomPaddingPx)
        }

        val name = TextView(context).apply {
            text = "${opponent.name} ↕"
            textSize = 12f
            setPadding(0, 0, opponentNameEndPaddingPx, 0)
        }

        val deleteBtn = Button(context).apply {
            // 按钮尽量小，避免遮挡对手信息与色块区域
            text = "X"
            textSize = deleteBtnTextSizeSp
            layoutParams = buttonLayoutParams(context, deleteBtnWidthDp, deleteBtnHeightDp)
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
            text = colorLabel(color)
            layoutParams = buttonLayoutParams(
                context = context,
                widthDp = colorBtnWidthDp,
                heightDp = colorBtnHeightDp,
                marginEndDp = colorBtnMarginEndDp,
                bottomMarginDp = colorBtnMarginBottomDp
            )

            val activeBg = activeColor(color)
            val activeText = activeTextColor(color)

            setBackgroundColor(if (isExcluded) excludedColorBg else activeBg)
            setTextColor(if (isExcluded) 0xFFFFFFFF.toInt() else activeText)

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

    private fun defaultOpponentPosition(
        context: Context,
        index: Int,
        control: View?,
        state: OverlayState
    ): Pair<Int, Int> {
        // 控制条默认位于 (overlayX, overlayY)。对手窗默认从控制条下方开始排布，避免遮挡控制按钮。
        val baseX = state.overlayX
        val controlH = control?.measuredHeight?.takeIf { it > 0 } ?: dp(context, defaultControlHeightDp)
        val baseY = state.overlayY + controlH + dp(context, opponentOffsetFromControlDp)

        val col = index % 2
        val row = index / 2
        val x = baseX + col * dp(context, opponentGridColGapDp)
        val y = baseY + row * dp(context, opponentGridRowGapDp)
        return x to y
    }

    private fun buttonLayoutParams(
        context: Context,
        widthDp: Int,
        heightDp: Int,
        marginEndDp: Int = 0,
        bottomMarginDp: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(dp(context, widthDp), dp(context, heightDp)).apply {
            if (marginEndDp > 0) marginEnd = dp(context, marginEndDp)
            if (bottomMarginDp > 0) bottomMargin = dp(context, bottomMarginDp)
        }
    }

    private fun colorLabel(color: UnoColor): String {
        return when (color) {
            UnoColor.Red -> "R"
            UnoColor.Yellow -> "Y"
            UnoColor.Blue -> "B"
            UnoColor.Green -> "G"
        }
    }

    private fun activeColor(color: UnoColor): Int {
        return when (color) {
            UnoColor.Red -> 0xFFFF5252.toInt()
            UnoColor.Yellow -> 0xFFFFD740.toInt()
            UnoColor.Blue -> 0xFF448AFF.toInt()
            // 用更亮的绿色，避免在部分机型/亮度下“绿色看不清”
            UnoColor.Green -> 0xFF00C853.toInt()
        }
    }

    private fun activeTextColor(color: UnoColor): Int {
        return when (color) {
            UnoColor.Yellow -> 0xFF1A1A1A.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
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
