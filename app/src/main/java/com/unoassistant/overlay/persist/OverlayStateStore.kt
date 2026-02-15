package com.unoassistant.overlay.persist

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.unoassistant.overlay.model.OverlayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "overlay_state")

/**
 * DataStore(Preferences) 存储：
 * - MVP 阶段采用“单 key JSON”策略，便于结构演进（新增字段可回退默认值）
 */
class OverlayStateStore(private val context: Context) {
    private val keyJson: Preferences.Key<String> = stringPreferencesKey("state_json_v1")

    val stateFlow: Flow<OverlayState> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                val raw = prefs[keyJson]
                OverlayStateJson.decodeOrDefault(raw)
            }

    suspend fun save(state: OverlayState) {
        context.dataStore.edit { prefs ->
            prefs[keyJson] = OverlayStateJson.encode(state)
        }
    }

    suspend fun reset() {
        context.dataStore.edit { prefs ->
            prefs.remove(keyJson)
        }
    }
}
