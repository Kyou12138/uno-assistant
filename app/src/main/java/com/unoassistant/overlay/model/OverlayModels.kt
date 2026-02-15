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
    val excluded: Map<UnoColor, Boolean>,
    // 对手卡片相对位移（用于自定义布局拖动）
    val offsetX: Int,
    val offsetY: Int
) {
    companion object {
        fun default(id: String, name: String): Opponent {
            val excluded = UnoColor.entries.associateWith { false }
            return Opponent(id = id, name = name, excluded = excluded, offsetX = 0, offsetY = 0)
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
    // 对手窗透明度（单独配置）
    val opponentAlpha: Float,
    val locked: Boolean,
    // 控制条是否收起到侧边（半隐藏）
    val controlCollapsed: Boolean,
    // 可新增对手的最大数量（配置项，默认 3）
    val maxOpponents: Int,
    val opponents: List<Opponent>
) {
    companion object {
        fun default(): OverlayState {
            return OverlayState(
                overlayX = 0,
                overlayY = 200,
                alpha = 1.0f,
                opponentAlpha = 0.92f,
                locked = true,
                // 初次体验：默认收起控制条，减少遮挡
                controlCollapsed = true,
                maxOpponents = 3,
                opponents = emptyList()
            )
        }
    }
}
