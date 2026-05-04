package com.durrr.first.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val LightColors = lightColorScheme(
    primary = Color(0xFF3147C7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF1B2A84),
    secondary = Color(0xFF5B6478),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9EDF8),
    onSecondaryContainer = Color(0xFF2A3348),
    tertiary = Color(0xFF18A36B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF8FAFF),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD8DDEA),
    outlineVariant = Color(0xFFE7EBF3),
    error = Color(0xFFD14343),
    errorContainer = Color(0xFFFDE9E9),
    onErrorContainer = Color(0xFF7D1D1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC0FF),
    onPrimary = Color(0xFF10205A),
    primaryContainer = Color(0xFF24348E),
    onPrimaryContainer = Color(0xFFE1E7FF),
    secondary = Color(0xFFB9C3DA),
    onSecondary = Color(0xFF233046),
    secondaryContainer = Color(0xFF374257),
    onSecondaryContainer = Color(0xFFE2E8F7),
    tertiary = Color(0xFF62D59E),
    onTertiary = Color(0xFF003821),
    background = Color(0xFF0E1320),
    onBackground = Color(0xFFE7EAF3),
    surface = Color(0xFF151C2C),
    onSurface = Color(0xFFE7EAF3),
    surfaceVariant = Color(0xFF1B2334),
    onSurfaceVariant = Color(0xFFA7B0C0),
    outline = Color(0xFF344055),
    outlineVariant = Color(0xFF263045),
    error = Color(0xFFFFB4B4),
    errorContainer = Color(0xFF621F1F),
    onErrorContainer = Color(0xFFFFE0E0),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
