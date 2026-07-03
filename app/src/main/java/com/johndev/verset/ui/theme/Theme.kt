package com.johndev.verset.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VersetNavy = Color(0xFF1B2A4A)
val VersetCream = Color(0xFFF4E9CD)
val VersetGold = Color(0xFFC9A24B)

private val LightColors = lightColorScheme(
    primary = VersetNavy,
    secondary = VersetGold,
    background = VersetCream,
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = VersetGold,
    secondary = VersetNavy,
    background = Color(0xFF10182B),
    surface = Color(0xFF1B2A4A)
)

/** Reader font scale is user-adjustable in Settings and stored in Prefs. */
fun readerTextStyle(scale: Float): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal,
    fontSize = (18 * scale).sp,
    lineHeight = (28 * scale).sp
)

@Composable
fun VersetTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
