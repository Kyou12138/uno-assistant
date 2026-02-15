package com.unoassistant.overlay

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
        // 让测试具备“可见交互”：清空数据 -> 授权overlay -> 启动 -> 点击开启 -> 验证收起可展开 -> 验证默认3对手落位 -> 收起拖拽 -> 停止服务清理
        // 注意：不要在 instrumentation 进程内执行 pm clear，会导致测试进程被杀。
        // 这里用“应用内状态重置”保证测试可重复且不依赖历史拖拽持久化。
        val ctx = instrumentation.targetContext
        OverlayStateRepository.update(ctx) { OverlayState.default() }
        device.executeShellCommand("am startservice -n $pkg/.OverlayForegroundService -a com.unoassistant.overlay.action.STOP")
        device.executeShellCommand("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        device.executeShellCommand("am start -n $pkg/.MainActivity")

        try {
            device.waitForIdle()

            // 等首页稳定出现
            assertNotNull(device.wait(Until.findObject(By.text("UNO 记牌助手")), 8_000))

            // 点击开启悬浮
            val startBtn = device.wait(Until.findObject(By.text("开启悬浮面板")), 8_000)
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
