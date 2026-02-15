package com.unoassistant.overlay.model

/**
 * 颜色枚举：固定红/黄/蓝/绿。
 */
enum class UnoColor {
    Red, Yellow, Blue, Green
}

/**
 * 单个对手的颜色排除态。
 * excluded=true 表示“排除态(灰)”：用户认为该对手可能没有该颜色。
 */
data class Opponent(
    val id: String,
    val name: String,
    val excluded: Map<UnoColor, Boolean>
) {
    companion object {
        fun default(id: String, name: String): Opponent {
            val excluded = UnoColor.entries.associateWith { false }
            return Opponent(id = id, name = name, excluded = excluded)
        }
    }
}

/**
 * 悬浮面板配置状态（持久化）。
 */
data class OverlayState(
    val overlayX: Int,
    val overlayY: Int,
    val alpha: Float,
    val locked: Boolean,
    val opponents: List<Opponent>
) {
    companion object {
        fun default(): OverlayState {
            return OverlayState(
                overlayX = 0,
                overlayY = 200,
                alpha = 1.0f,
                locked = true,
                opponents = emptyList()
            )
        }
    }
}

