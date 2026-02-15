package com.unoassistant.overlay

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.unoassistant.overlay.model.OverlayState
import com.unoassistant.overlay.persist.OverlayStateRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityE2ETest {

    private val pkg = "com.unoassistant.overlay"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice = UiDevice.getInstance(instrumentation)

    @Test
    fun overlay_visibleFlow_collapsedExpand_positionsAndDrag() {
        // 保证用例之间不互相污染（横屏用例会覆盖 wm size）
        resetWmSizeOverride()

        // 让测试具备“可见交互”：清空数据 -> 授权overlay -> 启动 -> 点击开启 -> 验证收起可展开 -> 验证默认3对手落位 -> 收起拖拽 -> 停止服务清理
        // 注意：不要在 instrumentation 进程内执行 pm clear，会导致测试进程被杀。
        // 这里用“应用内状态重置”保证测试可重复且不依赖历史拖拽持久化。
        val ctx = instrumentation.targetContext
        OverlayStateRepository.update(ctx) { OverlayState.default() }
        device.executeShellCommand("am startservice -n $pkg/.OverlayForegroundService -a com.unoassistant.overlay.action.STOP")
        device.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        launchMainActivity(ctx)

        try {
            device.waitForIdle()

            // 点击开启悬浮
            val startBtn = findTextWithSwipeUp("开启悬浮面板", maxSwipes = 3, perTryTimeoutMs = 2_500)
            assertNotNull("未找到“开启悬浮面板”按钮", startBtn)
            startBtn!!.click()

            // 默认应是收起态：能看到“展开控制条”按钮（悬浮在侧边）
            val expandBtn = device.wait(Until.findObject(By.desc("展开控制条")), 10_000)
            assertNotNull("未找到收起态的“展开控制条”按钮（可能未显示overlay或权限未生效）", expandBtn)

            // 轻点展开，验证展开态按钮出现（覆盖“默认锁定下无法展开”的回归）
            expandBtn!!.click()
            val addBtn = device.wait(Until.findObject(By.desc("增加对手")), 8_000)
            assertNotNull("展开后未找到“增加对手”按钮", addBtn)

            // 默认maxOpponents=3且首次补齐：应出现对手1/2/3
            val o1 = device.wait(Until.findObject(By.text("对手 1")), 10_000)
            val o2 = device.wait(Until.findObject(By.text("对手 2")), 10_000)
            val o3 = device.wait(Until.findObject(By.text("对手 3")), 10_000)
            assertNotNull("未找到“对手 1”窗口", o1)
            assertNotNull("未找到“对手 2”窗口", o2)
            assertNotNull("未找到“对手 3”窗口", o3)

            // 校验默认落位（允许一定误差，只检查大致象限）
            val w = device.displayWidth.toFloat()
            val h = device.displayHeight.toFloat()
            assertNearRegion("对手1(左中)", o1!!, w, h, minX = 0.0f, maxX = 0.42f, minY = 0.32f, maxY = 0.82f)
            assertNearRegion("对手2(中上)", o2!!, w, h, minX = 0.30f, maxX = 0.70f, minY = 0.00f, maxY = 0.38f)
            assertNearRegion("对手3(右中)", o3!!, w, h, minX = 0.58f, maxX = 1.00f, minY = 0.32f, maxY = 0.82f)

            // 收起到侧边并验证收起态拖拽可改变位置（y变化）
            val collapseBtn = device.wait(Until.findObject(By.desc("收起到侧边")), 8_000)
            assertNotNull("未找到“收起到侧边”按钮", collapseBtn)
            collapseBtn!!.click()
            device.waitForIdle()

            val expandBtn2 = device.wait(Until.findObject(By.desc("展开控制条")), 8_000)
            assertNotNull("收起后未找到“展开控制条”按钮", expandBtn2)
            val beforeTop = expandBtn2!!.visibleBounds.top

            // 在可见区域内竖向拖动一段距离：使用 left+2 确保落在按钮可触达区域（按钮可能半隐藏，center可能不稳定）。
            val vb = expandBtn2.visibleBounds
            val sx = clamp(vb.left + 2, 2, device.displayWidth - 3)
            val sy = clamp(vb.centerY(), 2, device.displayHeight - 3)
            val ey = clamp(sy + 420, 2, device.displayHeight - 3)
            device.swipe(sx, sy, sx, ey, 40)
            device.waitForIdle()

            val expandBtn3 = device.wait(Until.findObject(By.desc("展开控制条")), 5_000)
            assertNotNull("拖拽后未找到“展开控制条”按钮", expandBtn3)
            val afterTop = expandBtn3!!.visibleBounds.top
            assertTrue("收起态拖拽未生效（top未变化）：before=$beforeTop after=$afterTop", kotlin.math.abs(afterTop - beforeTop) > 30)
        } finally {
            // 清理：停止前台服务，避免残留overlay影响后续测试
            device.executeShellCommand("am startservice -n $pkg/.OverlayForegroundService -a com.unoassistant.overlay.action.STOP")
            device.waitForIdle()
        }
    }

    @Test
    fun overlay_visibleFlow_landscape_defaultPositions() {
        // 该辅助器主要横屏使用：验证横屏下默认 3 对手落位为 左中/中中/右中（不沿用竖屏的“中上”）
        forceLandscapeByWmSize()

        val ctx = instrumentation.targetContext
        OverlayStateRepository.update(ctx) { OverlayState.default() }
        device.executeShellCommand("am startservice -n $pkg/.OverlayForegroundService -a com.unoassistant.overlay.action.STOP")
        device.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        launchMainActivity(ctx)

        try {
            device.waitForIdle()

            val startBtn = findTextWithSwipeUp("开启悬浮面板", maxSwipes = 4, perTryTimeoutMs = 2_500)
            assertNotNull("未找到“开启悬浮面板”按钮", startBtn)
            startBtn!!.click()

            val expandBtn = device.wait(Until.findObject(By.desc("展开控制条")), 10_000)
            assertNotNull("未找到收起态的“展开控制条”按钮（可能未显示overlay或权限未生效）", expandBtn)
            expandBtn!!.click()

            val o1 = device.wait(Until.findObject(By.text("对手 1")), 10_000)
            val o2 = device.wait(Until.findObject(By.text("对手 2")), 10_000)
            val o3 = device.wait(Until.findObject(By.text("对手 3")), 10_000)
            assertNotNull("未找到“对手 1”窗口", o1)
            assertNotNull("未找到“对手 2”窗口", o2)
            assertNotNull("未找到“对手 3”窗口", o3)

            val w = device.displayWidth.toFloat()
            val h = device.displayHeight.toFloat()
            assertTrue("期望横屏（displayWidth>displayHeight），实际 w=$w h=$h", w > h)

            // 横屏：左中 / 中中 / 右中
            assertNearRegion("对手1(左中)", o1!!, w, h, minX = 0.0f, maxX = 0.42f, minY = 0.25f, maxY = 0.85f)
            assertNearRegion("对手2(中中)", o2!!, w, h, minX = 0.30f, maxX = 0.70f, minY = 0.25f, maxY = 0.85f)
            assertNearRegion("对手3(右中)", o3!!, w, h, minX = 0.58f, maxX = 1.00f, minY = 0.25f, maxY = 0.85f)
        } finally {
            device.executeShellCommand("am startservice -n $pkg/.OverlayForegroundService -a com.unoassistant.overlay.action.STOP")
            resetWmSizeOverride()
            device.waitForIdle()
        }
    }

    private fun resetWmSizeOverride() {
        device.executeShellCommand("wm size reset")
        device.waitForIdle()
        // reset 后会触发一次配置刷新，给系统一点时间稳定到“竖屏”尺寸，避免后续 findObject 抖动。
        val deadline = SystemClock.uptimeMillis() + 6_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.displayWidth < device.displayHeight) break
            SystemClock.sleep(200)
        }
    }

    private fun forceLandscapeByWmSize() {
        // emulator/设备旋转在 instrumentation 环境可能不生效，用 wm size 覆盖来模拟横屏 display metrics（测试后会 reset）。
        resetWmSizeOverride()
        val w = device.displayWidth
        val h = device.displayHeight
        val max = maxOf(w, h)
        val min = minOf(w, h)
        device.executeShellCommand("wm size ${max}x${min}")
        device.waitForIdle()

        val deadline = SystemClock.uptimeMillis() + 6_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.displayWidth > device.displayHeight) return
            SystemClock.sleep(200)
        }
    }

    private fun launchMainActivity(ctx: android.content.Context) {
        val i = Intent(Intent.ACTION_MAIN).apply {
            setClassName(pkg, "$pkg.MainActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ctx.startActivity(i)
        val deadline = SystemClock.uptimeMillis() + 12_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (device.currentPackageName == pkg) return
            SystemClock.sleep(200)
        }
    }

    private fun findTextWithSwipeUp(
        text: String,
        maxSwipes: Int,
        perTryTimeoutMs: Long
    ): UiObject2? {
        for (i in 0..maxSwipes) {
            val obj = device.wait(Until.findObject(By.text(text)), perTryTimeoutMs)
            if (obj != null) return obj
            // 主页是可滚动的（Compose verticalScroll）；横屏高度较小，按钮可能在折叠区域外。
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.85f)
            device.waitForIdle()
        }
        return null
    }

    private fun assertNearRegion(
        label: String,
        obj: UiObject2,
        w: Float,
        h: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float
    ) {
        val b = obj.visibleBounds
        val cx = b.centerX() / w
        val cy = b.centerY() / h
        assertTrue("$label x不在范围：$cx not in [$minX,$maxX]", cx >= minX && cx <= maxX)
        assertTrue("$label y不在范围：$cy not in [$minY,$maxY]", cy >= minY && cy <= maxY)
    }

    private fun clamp(v: Int, min: Int, max: Int): Int {
        return v.coerceIn(min, max)
    }
}
