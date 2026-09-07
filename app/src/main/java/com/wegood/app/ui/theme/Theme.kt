package com.wegood.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// iOS 系统色（高饱和、高对比）
val Pink = Color(0xFFFF2D55)      // 爱心
val PinkLight = Color(0xFFFF6482)
val Orange = Color(0xFFFF9500)    // 纪念日
val Indigo = Color(0xFF5E5CE6)    // 游戏
val Purple = Color(0xFFAF52DE)    // 井字棋
val Teal = Color(0xFF30B0C7)      // 每日一问
val Yellow = Color(0xFFFF9F0A)    // 真心话大冒险
val Green = Color(0xFF30D158)     // 在线
val Bg = Color(0xFFF2F2F7)        // 分组背景
val TextGray = Color(0xFF8E8E93)

val PinkGradient = Brush.linearGradient(listOf(PinkLight, Pink))
val OrangeGradient = Brush.linearGradient(listOf(Color(0xFFFFB340), Orange))
val IndigoGradient = Brush.linearGradient(listOf(Color(0xFF7D7AFF), Indigo))
val TealGradient = Brush.linearGradient(listOf(Color(0xFF5AC8FA), Teal))
val YellowGradient = Brush.linearGradient(listOf(Color(0xFFFFD60A), Yellow))
val PurpleGradient = Brush.linearGradient(listOf(Color(0xFFDA70F0), Purple))

private val LightColors = lightColorScheme(
    primary = Pink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3EA),
    onPrimaryContainer = Pink,
    secondary = Indigo,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    onSurfaceVariant = TextGray,
    error = Color(0xFFFF3B30),
)

@Composable
fun WeGoodTheme(content: @Composable () -> Unit) {
    // 遵循 iOS 健康风格：固定浅色（健康 App 也无深色摘要卡片语言），保持卡片色彩辨识度
    MaterialTheme(colorScheme = LightColors, content = content)
}
