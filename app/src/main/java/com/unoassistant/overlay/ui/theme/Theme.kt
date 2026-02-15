package com.unoassistant.overlay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 视觉风格目标：
 * - 亮色系、干净、信息层级明确
 * - 轻质感（更像工具而不是 Demo）
 *
 * 仅用于 UI 观感升级：不影响任何业务逻辑。
 */

private val Ink = Color(0xFF0F172A)        // 深墨色
private val InkMuted = Color(0xFF475569)   // 次级文字
private val Paper = Color(0xFFF4F7FC)      // 页面底
private val Card = Color(0xFFFFFFFF)       // 卡片底
private val Border = Color(0xFFD9E2EF)     // 细边
private val Accent = Color(0xFF1565C0)     // 主交互色

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Ink,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    outline = Border
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF101827),
    onSurface = Color(0xFFE5E7EB),
    outline = Color(0xFF243049)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, color = InkMuted)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
)

@Composable
fun UnoAssistantTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
