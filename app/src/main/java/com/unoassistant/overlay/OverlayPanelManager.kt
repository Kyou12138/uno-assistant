package com.unoassistant.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
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
    private var controlLayout: WindowManager.LayoutParams? = null
    private var controlLockButton: ImageButton? = null
    private var controlAddButton: ImageButton? = null
    private var controlRemoveButton: ImageButton? = null
    private var controlExpanded: View? = null
    private var controlCollapsedView: View? = null
    private val opponentViews = mutableMapOf<String, View>()
    private val opponentLayouts = mutableMapOf<String, WindowManager.LayoutParams>()
    private val opponentColorButtons = mutableMapOf<String, MutableMap<UnoColor, Button>>()

    // 更“产品化”的悬浮窗视觉：更高不透明度 + 更克制的描边
    private val panelBgColor = 0xF2FFFFFF.toInt()
    private val excludedColorBg = 0xFF757575.toInt()
    private val panelBorderColor = 0x1A0F172A

    private const val controlPaddingHorizontalPx = 10
    private const val controlPaddingVerticalPx = 8
    // 旧代码用“裸 px”会导致在高密度屏幕上过于拥挤，这里统一按 dp 计算。
    private const val opponentPaddingPx = 10
    private const val opponentHeaderBottomPaddingPx = 8
    private const val opponentNameEndPaddingPx = 6
    private const val opponentHandleEndPaddingPx = 8

    // 触控热区尽量满足 44dp 规则
    private const val controlBtnWidthDp = 44
    private const val controlBtnHeightDp = 44
    private const val controlBtnMarginEndDp = 6

    // “牌”样式：窄而高，单行 4 张横向排列
    private const val colorBtnWidthDp = 38
    private const val colorBtnHeightDp = 52
    private const val colorBtnMarginEndDp = 2
    private const val colorBtnMarginBottomDp = 0

    private const val defaultControlWidthDp = 210
    private const val defaultControlHeightDp = 56
    private const val opponentOffsetFromControlDp = 12
    private const val opponentEstimatedWidthDp = 230
    private const val opponentEstimatedHeightDp = 125
    private const val clockwiseSlotCount = 8
    private const val minTopInsetDp = 24
    private const val panelCornerRadiusDp = 16
    private const val panelBorderWidthDp = 1
    private const val collapsedHandleWidthDp = 46
    private const val collapsedHandleHeightDp = 46
    private const val collapsedPeekDp = 18
    private const val panelElevationDp = 10

    fun isShowing(): Boolean = controlView != null

    fun show(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!canDrawOverlays(appContext)) return false

        val wm = windowManager ?: (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also {
            windowManager = it
        }

        val state = OverlayStateRepository.get(appContext)

        if (controlView == null) {
            val panel = buildControlPanelView(appContext, OverlayStateRepository.get(appContext))
            panel.alpha = state.alpha
            // 先测量一次，便于根据“收起/展开”计算初始 x/y
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            val pos = initialControlPosition(appContext, state)
            val lp = newLayoutParams(pos.first, pos.second)
            wm.addView(panel, lp)
            controlView = panel
            controlLayout = lp
        }

        // 控制条创建完成后再补齐，确保默认落点可避开操作按钮区域（只补齐不裁剪）
        ensureOpponentCount(appContext, OverlayStateRepository.get(appContext).maxOpponents)

        syncOpponentWindows(appContext)
        updateLockButtonText(appContext)
        updateControlAddRemoveEnabled(appContext)
        applyControlCollapsedState(appContext)
        return true
    }

    fun hide() {
        val wm = windowManager ?: return

        opponentViews.values.forEach { view ->
            runCatching { wm.removeView(view) }
        }
        opponentViews.clear()
        opponentLayouts.clear()
        opponentColorButtons.clear()

        controlView?.let { runCatching { wm.removeView(it) } }
        controlView = null
        controlLayout = null
        controlLockButton = null
        controlAddButton = null
        controlRemoveButton = null
        controlExpanded = null
        controlCollapsedView = null
    }

    private fun buildControlPanelView(context: Context, state: OverlayState): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = panelBackground(context)
            setPadding(
                dp(context, controlPaddingHorizontalPx),
                dp(context, controlPaddingVerticalPx),
                dp(context, controlPaddingHorizontalPx),
                dp(context, controlPaddingVerticalPx)
            )
            elevation = dp(context, panelElevationDp).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        val expanded = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val collapsed = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        // 拖动手柄：只在展开态显示，避免与按钮点击冲突
        val dragHandle = TextView(context).apply {
            text = "⋮⋮"
            tooltipText = "拖动控制条"
            contentDescription = "拖动控制条手柄"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(0xFF0F172A.toInt())
            background = controlButtonBackground(context)
        }

        val addBtn = controlIconButton(context, android.R.drawable.ic_input_add, "增加对手")
        val removeBtn = controlIconButton(context, android.R.drawable.ic_delete, "减少对手")
        val lockBtn = controlIconButton(context, android.R.drawable.ic_lock_lock, "锁定拖动")
        val resetBtn = controlIconButton(context, android.R.drawable.ic_menu_rotate, "重置颜色")
        val collapseBtn = controlIconButton(context, android.R.drawable.ic_media_previous, "收起到侧边")
        val closeBtn = controlIconButton(context, android.R.drawable.ic_menu_close_clear_cancel, "关闭悬浮")

        addBtn.setOnClickListener {
            val curState = OverlayStateRepository.get(context)
            if (curState.opponents.size >= curState.maxOpponents) {
                Toast.makeText(context, "已达上限：最多 ${curState.maxOpponents} 名对手", Toast.LENGTH_SHORT).show()
                updateControlAddRemoveEnabled(context)
                return@setOnClickListener
            }
            OverlayStateRepository.update(context) { cur ->
                val nextIndex = nextOpponentIndex(cur.opponents)
                val id = UUID.randomUUID().toString()
                val name = "对手 $nextIndex"
                val pos = defaultOpponentPosition(context, cur.opponents.size, controlView, cur)
                cur.copy(opponents = cur.opponents + Opponent.default(id, name).copy(offsetX = pos.first, offsetY = pos.second))
            }
            syncOpponentWindows(context)
            updateControlAddRemoveEnabled(context)
        }

        removeBtn.setOnClickListener {
            val curState = OverlayStateRepository.get(context)
            if (curState.opponents.isEmpty()) {
                updateControlAddRemoveEnabled(context)
                return@setOnClickListener
            }
            OverlayStateRepository.update(context) { cur ->
                cur.copy(opponents = cur.opponents.dropLast(1))
            }
            syncOpponentWindows(context)
            updateControlAddRemoveEnabled(context)
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
            updateControlAddRemoveEnabled(context)
            Toast.makeText(context, "已重置所有颜色", Toast.LENGTH_SHORT).show()
        }

        closeBtn.setOnClickListener {
            OverlayServiceController.stop(context)
            hide()
        }

        collapseBtn.setOnClickListener {
            val lp = controlLayout ?: return@setOnClickListener
            val screenW = context.resources.displayMetrics.widthPixels
            val sideRight = lp.x > screenW / 2
            OverlayStateRepository.update(context) { cur ->
                cur.copy(controlCollapsed = true).let { it } // 仅切换状态，位置保持 overlayX/overlayY
            }
            // 收起后把控制条吸到侧边（不改变持久化 overlayX/overlayY，展开时可恢复）
            applyControlCollapsedState(context, forceSideRight = sideRight)
        }

        // 控制条尽量紧凑，减少遮挡游戏区域
        dragHandle.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, controlBtnHeightDp)).apply {
            marginEnd = dp(context, 6)
        }
        addBtn.layoutParams = buttonLayoutParams(context, controlBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        removeBtn.layoutParams = buttonLayoutParams(context, controlBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        lockBtn.layoutParams = buttonLayoutParams(context, controlBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        resetBtn.layoutParams = buttonLayoutParams(context, controlBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        collapseBtn.layoutParams = buttonLayoutParams(context, controlBtnWidthDp, controlBtnHeightDp, controlBtnMarginEndDp)
        closeBtn.layoutParams = buttonLayoutParams(context, controlBtnWidthDp, controlBtnHeightDp)

        expanded.addView(dragHandle)
        expanded.addView(addBtn)
        expanded.addView(removeBtn)
        expanded.addView(lockBtn)
        expanded.addView(resetBtn)
        expanded.addView(collapseBtn)
        expanded.addView(closeBtn)

        val expandBtn = controlIconButton(context, android.R.drawable.ic_media_next, "展开控制条")
        expandBtn.layoutParams = buttonLayoutParams(context, collapsedHandleWidthDp, collapsedHandleHeightDp)
        expandBtn.setOnClickListener {
            OverlayStateRepository.update(context) { cur -> cur.copy(controlCollapsed = false) }
            applyControlCollapsedState(context)
        }
        collapsed.addView(expandBtn)

        // 初始状态
        expanded.visibility = if (state.controlCollapsed) View.GONE else View.VISIBLE
        collapsed.visibility = if (state.controlCollapsed) View.VISIBLE else View.GONE

        root.addView(expanded)
        root.addView(collapsed)

        controlExpanded = expanded
        controlCollapsedView = collapsed

        controlLockButton = lockBtn
        controlAddButton = addBtn
        controlRemoveButton = removeBtn
        updateLockButtonText(context)
        updateControlAddRemoveEnabled(context)

        attachControlDrag(context, dragHandle, root)
        return root
    }

    private fun updateControlAddRemoveEnabled(context: Context) {
        val state = OverlayStateRepository.get(context)
        val add = controlAddButton
        val remove = controlRemoveButton
        if (add != null) {
            val enabled = state.opponents.size < state.maxOpponents
            add.isEnabled = enabled
            add.alpha = if (enabled) 1.0f else 0.35f
            add.imageAlpha = if (enabled) 255 else 110
        }
        if (remove != null) {
            val enabled = state.opponents.isNotEmpty()
            remove.isEnabled = enabled
            remove.alpha = if (enabled) 1.0f else 0.35f
            remove.imageAlpha = if (enabled) 255 else 110
        }
    }

    private fun ensureOpponentCount(context: Context, target: Int) {
        if (target <= 0) return
        val cur = OverlayStateRepository.get(context)
        if (cur.opponents.size >= target) return
        OverlayStateRepository.update(context) { s ->
            var opponents = s.opponents
            while (opponents.size < target) {
                val nextIndex = nextOpponentIndex(opponents)
                val id = UUID.randomUUID().toString()
                val name = "对手 $nextIndex"
                val pos = defaultOpponentPosition(context, opponents.size, controlView, s.copy(opponents = opponents))
                opponents = opponents + Opponent.default(id, name).copy(offsetX = pos.first, offsetY = pos.second)
            }
            s.copy(opponents = opponents)
        }
    }

    private fun initialControlPosition(context: Context, state: OverlayState): Pair<Int, Int> {
        if (!state.controlCollapsed) return state.overlayX to state.overlayY
        val screenW = context.resources.displayMetrics.widthPixels
        val y = state.overlayY
        val peek = dp(context, collapsedPeekDp)
        val x = screenW - peek
        return x to y
    }

    private fun applyControlCollapsedState(context: Context, forceSideRight: Boolean? = null) {
        val panel = controlView ?: return
        val lp = controlLayout ?: return
        val wm = windowManager ?: return
        val state = OverlayStateRepository.get(context)

        val expanded = controlExpanded
        val collapsed = controlCollapsedView
        if (expanded != null && collapsed != null) {
            expanded.visibility = if (state.controlCollapsed) View.GONE else View.VISIBLE
            collapsed.visibility = if (state.controlCollapsed) View.VISIBLE else View.GONE
        }

        // 重新测量（收起/展开宽度不同）
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        if (state.controlCollapsed) {
            val screenW = context.resources.displayMetrics.widthPixels
            val w = panel.measuredWidth.takeIf { it > 0 } ?: dp(context, collapsedHandleWidthDp)
            val peek = dp(context, collapsedPeekDp)
            val sideRight = forceSideRight ?: (lp.x > screenW / 2)
            lp.x = if (sideRight) (screenW - peek) else -(w - peek)
            // y 保持当前，便于用户拖到合适高度
        } else {
            lp.x = state.overlayX
            lp.y = state.overlayY
        }
        runCatching { wm.updateViewLayout(panel, lp) }
    }

    private fun attachControlDrag(context: Context, dragHandle: View, targetView: View) {
        var startRawX = 0f
        var startRawY = 0f
        var startX = 0
        var startY = 0

        dragHandle.setOnTouchListener { _, event ->
            val wm = windowManager ?: return@setOnTouchListener false
            val lp = controlLayout ?: return@setOnTouchListener false
            val state = OverlayStateRepository.get(context)
            // 收起态不允许通过展开态拖动手柄移动
            if (state.controlCollapsed) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (event.rawX - startRawX).toInt()
                    lp.y = startY + (event.rawY - startRawY).toInt()
                    runCatching { wm.updateViewLayout(targetView, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val x = lp.x
                    val y = lp.y
                    OverlayStateRepository.update(context) { cur -> cur.copy(overlayX = x, overlayY = y) }
                    true
                }
                else -> false
            }
        }
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
            opponentColorButtons.remove(id)
        }

        state.opponents.forEachIndexed { index, opponent ->
            val existingView = opponentViews[opponent.id]
            val existingLayout = opponentLayouts[opponent.id]
            if (existingView != null && existingLayout != null) {
                // 避免重建窗口导致闪烁：仅原地更新颜色按钮样式（必要时同步位置）
                updateOpponentColors(opponent)
                if (!(opponent.offsetX == 0 && opponent.offsetY == 0)) {
                    if (existingLayout.x != opponent.offsetX || existingLayout.y != opponent.offsetY) {
                        existingLayout.x = opponent.offsetX
                        existingLayout.y = opponent.offsetY
                        runCatching { wm.updateViewLayout(existingView, existingLayout) }
                    }
                }
                return@forEachIndexed
            }

            val pos = if (opponent.offsetX == 0 && opponent.offsetY == 0) {
                // 仅在首次创建时计算默认摆放，避免后续同步导致窗口“跳动”
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
            setPadding(
                dp(context, opponentPaddingPx),
                dp(context, opponentPaddingPx),
                dp(context, opponentPaddingPx),
                dp(context, opponentPaddingPx)
            )
            background = panelBackground(context)
            elevation = dp(context, panelElevationDp).toFloat()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(context, opponentHeaderBottomPaddingPx))
        }

        val dragHandle = TextView(context).apply {
            text = "⋮⋮"
            textSize = 14f
            tooltipText = "拖动对手窗"
            contentDescription = "拖动对手窗手柄"
            setPadding(0, 0, dp(context, opponentHandleEndPaddingPx), 0)
            setTextColor(0xFF0F172A.toInt())
        }

        val name = TextView(context).apply {
            text = opponent.name
            textSize = 12f
            setPadding(0, 0, dp(context, opponentNameEndPaddingPx), 0)
            setTextColor(0xFF0F172A.toInt())
        }

        header.addView(dragHandle)
        header.addView(name)
        // 删除入口移到控制条 +/-，避免对手窗内出现“叉”干扰视觉与误触

        val colors = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val colorButtons = mutableMapOf<UnoColor, Button>()
        fun addColor(color: UnoColor) {
            val btn = colorButton(context, opponent, color)
            colorButtons[color] = btn
            colors.addView(btn)
        }

        addColor(UnoColor.Red)
        addColor(UnoColor.Yellow)
        addColor(UnoColor.Blue)
        addColor(UnoColor.Green)

        root.addView(header)
        root.addView(colors)

        // 保存按钮引用，便于后续“原地更新样式”而不是重建窗口
        opponentColorButtons[opponent.id] = colorButtons

        attachOpponentWindowDrag(context, dragHandle, opponent.id, root, layout)
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

    private fun colorButton(context: Context, opponent: Opponent, color: UnoColor): Button {
        val isExcluded = opponent.excluded[color] == true
        return Button(context).apply {
            text = ""
            contentDescription = "${colorNameZh(color)}色牌"
            tooltipText = colorNameZh(color)
            layoutParams = buttonLayoutParams(
                context = context,
                widthDp = colorBtnWidthDp,
                heightDp = colorBtnHeightDp,
                marginEndDp = colorBtnMarginEndDp,
                bottomMarginDp = colorBtnMarginBottomDp
            )
            applyColorCardStyle(this, context, color, isExcluded)

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
        val btn = controlLockButton ?: return
        if (locked) {
            btn.setImageResource(android.R.drawable.ic_lock_lock)
            btn.contentDescription = "已锁定"
            btn.tooltipText = "已锁定（长按查看）"
        } else {
            btn.setImageResource(android.R.drawable.ic_menu_view)
            btn.contentDescription = "已解锁"
            btn.tooltipText = "已解锁（长按查看）"
        }
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
        // 按控制条四周“顺时针环绕”给新增对手分配默认落点（8 方位一圈，超出后外扩下一圈）。
        // 落点只在首次创建对手窗时生效，已持久化坐标不会被覆盖。
        val baseX = state.overlayX
        val baseY = state.overlayY

        val controlW = control?.measuredWidth?.takeIf { it > 0 } ?: dp(context, defaultControlWidthDp)
        val controlH = control?.measuredHeight?.takeIf { it > 0 } ?: dp(context, defaultControlHeightDp)

        val gap = dp(context, opponentOffsetFromControlDp)
        val opponentW = dp(context, opponentEstimatedWidthDp)
        val opponentH = dp(context, opponentEstimatedHeightDp)

        val ring = index / clockwiseSlotCount + 1
        val slot = index % clockwiseSlotCount

        val stepX = controlW + gap + (ring - 1) * (opponentW + gap)
        val stepY = controlH + gap + (ring - 1) * (opponentH + gap)

        val raw = when (slot) {
            0 -> (baseX + stepX) to baseY          // 右
            1 -> (baseX + stepX) to (baseY + stepY) // 右下
            2 -> baseX to (baseY + stepY)           // 下
            3 -> (baseX - stepX) to (baseY + stepY) // 左下
            4 -> (baseX - stepX) to baseY           // 左
            5 -> (baseX - stepX) to (baseY - stepY) // 左上
            6 -> baseX to (baseY - stepY)           // 上
            else -> (baseX + stepX) to (baseY - stepY) // 右上
        }

        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val minY = dp(context, minTopInsetDp)
        val clampedX = raw.first.coerceIn(0, maxOf(0, screenW - opponentW))
        val clampedY = raw.second.coerceIn(minY, maxOf(minY, screenH - opponentH))
        return clampedX to clampedY
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

    private fun controlIconButton(
        context: Context,
        iconRes: Int,
        tooltip: String
    ): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            contentDescription = tooltip
            tooltipText = tooltip
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = controlButtonBackground(context)
            imageTintList = ColorStateList.valueOf(0xFF0F172A.toInt())
            setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10))
        }
    }

    private fun controlButtonBackground(context: Context): RippleDrawable {
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 12).toFloat()
            setColor(0x0A0F172A)
            setStroke(dp(context, 1), 0x140F172A)
        }
        val ripple = ColorStateList.valueOf(0x140EA5A8)
        return RippleDrawable(ripple, content, null)
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

    private fun colorNameZh(color: UnoColor): String {
        return when (color) {
            UnoColor.Red -> "红"
            UnoColor.Yellow -> "黄"
            UnoColor.Blue -> "蓝"
            UnoColor.Green -> "绿"
        }
    }

    private fun applyColorCardStyle(btn: Button, context: Context, color: UnoColor, isExcluded: Boolean) {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 12).toFloat()
            if (isExcluded) {
                setColor(excludedColorBg)
                setStroke(dp(context, 2), activeColor(color))
            } else {
                setColor(activeColor(color))
                setStroke(dp(context, 1), 0x22000000)
            }
        }
        btn.background = drawable
        btn.alpha = if (isExcluded) 0.92f else 1.0f
    }

    private fun panelBackground(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, panelCornerRadiusDp).toFloat()
            setColor(panelBgColor)
            setStroke(dp(context, panelBorderWidthDp), panelBorderColor)
        }
    }

    private fun updateOpponentColors(opponent: Opponent) {
        val btns = opponentColorButtons[opponent.id] ?: return
        UnoColor.entries.forEach { c ->
            val btn = btns[c] ?: return@forEach
            val isExcluded = opponent.excluded[c] == true
            btn.contentDescription = "${colorNameZh(c)}色牌"
            btn.tooltipText = colorNameZh(c)
            applyColorCardStyle(btn, btn.context, c, isExcluded)
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
