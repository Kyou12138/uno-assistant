package com.unoassistant.overlay.persist

import android.content.Context
import com.unoassistant.overlay.model.OverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    private var hydrated = false

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
            // 异步水合：避免主线程阻塞导致偶发 ANR。
            scope!!.launch {
                store!!.stateFlow.collectLatest { state ->
                    latestState = state
                    hydrated = true
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
        val localStore = store ?: return
        val localScope = scope ?: return

        if (hydrated) {
            val next = transform(latestState)
            latestState = next
            localScope.launch {
                localStore.save(next)
            }
            return
        }

        // 尚未水合时先做乐观更新，避免交互“无响应感”；随后以持久化快照为基准再计算一次并落盘。
        val optimistic = transform(latestState)
        latestState = optimistic
        localScope.launch {
            val persisted = runCatching { localStore.stateFlow.first() }.getOrElse { latestState }
            val next = transform(persisted)
            latestState = next
            hydrated = true
            localStore.save(next)
        }
    }
}
