package com.ridelink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RideLinkColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF1976D2),
)

@Composable
fun RideLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RideLinkColorScheme,
        content = content,
    )
}
