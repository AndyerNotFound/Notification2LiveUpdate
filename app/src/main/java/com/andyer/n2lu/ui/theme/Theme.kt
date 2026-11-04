package com.andyer.n2lu.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF4338CA),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF9AA4B2),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF00312B),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFA5B4FC),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF111823),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1B2634),
    onSurfaceVariant = Color(0xFF9CA3AF),
    outline = Color(0xFF64748B),
    outlineVariant = Color(0xFF1F2937),
    error = Color(0xFFF87171),
)

@Composable
fun N2LuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
