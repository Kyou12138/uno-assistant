package com.unoassistant.overlay.persist

import com.unoassistant.overlay.model.Opponent
import com.unoassistant.overlay.model.OverlayState
import com.unoassistant.overlay.model.UnoColor

/**
 * 轻量 JSON 编解码：
 * - 不引入额外序列化依赖（KISS）
 * - 格式只要“可读写 + 可演进”，无需追求通用性
 *
 * 注意：这是 MVP 工具型实现，后续如引入 kotlinx.serialization 可替换。
 */
object OverlayStateJson {
    fun encode(state: OverlayState): String {
        // 采用非常保守的键值拼接格式，避免复杂 JSON 依赖。
        // 格式：
        // x=0;y=200;alpha=1.0;locked=1;max=4;opponents=[id|name|R0Y0B0G0|ox|oy,...]
        val opponents = state.opponents.joinToString(separator = ",") { o ->
            val flags = buildString {
                append("R"); append(if (o.excluded[UnoColor.Red] == true) 1 else 0)
                append("Y"); append(if (o.excluded[UnoColor.Yellow] == true) 1 else 0)
                append("B"); append(if (o.excluded[UnoColor.Blue] == true) 1 else 0)
                append("G"); append(if (o.excluded[UnoColor.Green] == true) 1 else 0)
            }
            "${escape(o.id)}|${escape(o.name)}|$flags|${o.offsetX}|${o.offsetY}"
        }
        return "x=${state.overlayX};y=${state.overlayY};alpha=${state.alpha};locked=${if (state.locked) 1 else 0};max=${state.maxOpponents};opponents=[$opponents]"
    }

    fun decodeOrDefault(raw: String?): OverlayState {
        if (raw.isNullOrBlank()) return OverlayState.default()
        return try {
            decode(raw)
        } catch (_: Throwable) {
            OverlayState.default()
        }
    }

    private fun decode(raw: String): OverlayState {
        val parts = raw.split(";")
        val map = parts.mapNotNull { kv ->
            val idx = kv.indexOf("=")
            if (idx <= 0) null else kv.substring(0, idx) to kv.substring(idx + 1)
        }.toMap()

        val x = map["x"]?.toIntOrNull() ?: 0
        val y = map["y"]?.toIntOrNull() ?: 200
        val alpha = map["alpha"]?.toFloatOrNull() ?: 1.0f
        val locked = (map["locked"]?.trim() == "1")
        val maxOpponents = map["max"]?.toIntOrNull()?.coerceIn(1, 12) ?: 4

        val opponentsRaw = map["opponents"] ?: "[]"
        val opponents = parseOpponents(opponentsRaw)

        return OverlayState(
            overlayX = x,
            overlayY = y,
            alpha = alpha.coerceIn(0.2f, 1.0f),
            locked = locked,
            maxOpponents = maxOpponents,
            opponents = opponents
        )
    }

    private fun parseOpponents(raw: String): List<Opponent> {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
        val inner = trimmed.substring(1, trimmed.length - 1)
        if (inner.isBlank()) return emptyList()

        return inner.split(",").mapNotNull { item ->
            val seg = item.split('|')
            if (seg.size < 3) return@mapNotNull null
            val id = unescape(seg[0])
            val name = unescape(seg[1])
            val flags = seg[2]
            val ox = seg.getOrNull(3)?.toIntOrNull() ?: 0
            val oy = seg.getOrNull(4)?.toIntOrNull() ?: 0
            Opponent(
                id = id,
                name = name,
                excluded = mapOf(
                    UnoColor.Red to readFlag(flags, 'R'),
                    UnoColor.Yellow to readFlag(flags, 'Y'),
                    UnoColor.Blue to readFlag(flags, 'B'),
                    UnoColor.Green to readFlag(flags, 'G')
                ),
                offsetX = ox,
                offsetY = oy
            )
        }
    }

    private fun readFlag(flags: String, c: Char): Boolean {
        val idx = flags.indexOf(c)
        if (idx < 0 || idx + 1 >= flags.length) return false
        return flags[idx + 1] == '1'
    }

    private fun escape(s: String): String =
        s.replace("%", "%25").replace("|", "%7C").replace(",", "%2C").replace(";", "%3B").replace("[", "%5B").replace("]", "%5D")

    private fun unescape(s: String): String =
        s.replace("%5D", "]").replace("%5B", "[").replace("%3B", ";").replace("%2C", ",").replace("%7C", "|").replace("%25", "%")
}
