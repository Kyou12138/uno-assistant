package com.unoassistant.overlay.persist

import android.content.Context
import com.unoassistant.overlay.model.OverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 进程内的“状态仓库”：
 * - 以 DataStore 为唯一持久化来源
 * - 暴露最新状态给 overlay/service/UI 使用
 *
 * MVP 策略：不做复杂同步，后续如出现高频写入再加 debounce。
 */
object OverlayStateRepository {
    @Volatile
    private var started = false

    @Volatile
    var latestState: OverlayState = OverlayState.default()
        private set

    private var store: OverlayStateStore? = null
    private var scope: CoroutineScope? = null

    fun ensureStarted(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            val appContext = context.applicationContext
            store = OverlayStateStore(appContext)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // 首次启动先同步读取一次，避免 UI 初次拿到“全默认”而覆盖真实存储。
            latestState = runBlocking(Dispatchers.IO) {
                store!!.stateFlow.first()
            }
            scope!!.launch {
                store!!.stateFlow.collectLatest { state ->
                    latestState = state
                }
            }
            started = true
        }
    }

    fun get(context: Context): OverlayState {
        ensureStarted(context)
        return latestState
    }

    fun update(context: Context, transform: (OverlayState) -> OverlayState) {
        ensureStarted(context)
        val next = transform(latestState)
        latestState = next
        scope?.launch {
            store?.save(next)
        }
    }
}
