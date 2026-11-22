package com.personalkanban.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.personalkanban.app.domain.model.TaskPriority
import com.personalkanban.app.domain.model.TaskStatus
import com.personalkanban.app.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E5A88),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE8F6),
    onPrimaryContainer = Color(0xFF102A43),
    secondary = Color(0xFF475569),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF151C24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151C24),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF526071),
    outline = Color(0xFFD5DEE8),
    outlineVariant = Color(0xFFE6ECF2),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8BB8E8),
    onPrimary = Color(0xFF0B2540),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFDCE8F6),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF283548),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF042F2E),
    background = Color(0xFF0E131A),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF161D26),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFF9BA8B8),
    outline = Color(0xFF2E3A4B),
    outlineVariant = Color(0xFF232D3B),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun PersonalKanbanTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content
    )
}

fun TaskPriority.color(isDark: Boolean): Color = when (this) {
    TaskPriority.CRITICAL -> if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)
    TaskPriority.HIGH -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    TaskPriority.MEDIUM -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    TaskPriority.LOW -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
}

fun TaskStatus.accentColor(isDark: Boolean): Color = when (this) {
    TaskStatus.TODO -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    TaskStatus.IN_PROGRESS -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    TaskStatus.DONE -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
}
