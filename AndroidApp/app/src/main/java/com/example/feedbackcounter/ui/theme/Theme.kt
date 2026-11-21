package com.example.feedbackcounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ✅ ライトモード固定（端末がダーク設定でもアプリはライト）
// 背景は「白」に寄せて固定
private val LightColors = lightColorScheme(
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F2),
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
