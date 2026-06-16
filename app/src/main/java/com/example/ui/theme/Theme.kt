package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Force a beautiful, clean White Biometric Theme by default
private val LightColorScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = LavenderPrimaryContainer,
    onPrimaryContainer = OnIndigoPrimaryContainer,
    
    secondary = VioletSecondary,
    onSecondary = Color.White,
    secondaryContainer = SoftVioletContainer,
    onSecondaryContainer = OnVioletContainer,
    
    background = CoolBg,
    onBackground = Color(0xFF0F172A), // Tailwinds cool dark gray Slate 900
    
    surface = SmoothSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = CoolBg,
    onSurfaceVariant = Color(0xFF475569), // Slate 600

    error = AlertRed,
    onError = Color.White,
    errorContainer = SoftErrorContainer,
    onErrorContainer = OnErrorContainer,
    
    outline = FineOutline
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
