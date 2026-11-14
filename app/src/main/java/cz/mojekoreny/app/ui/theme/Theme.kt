package cz.mojekoreny.app.ui.theme

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

// Zemité barvy: zelená koruna, hnědé kořeny, papír matriky.
private val Light = lightColorScheme(
    primary = Color(0xFF3F6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3EDB4),
    onPrimaryContainer = Color(0xFF0B2108),
    secondary = Color(0xFF6B4A2B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DCC2),
    onSecondaryContainer = Color(0xFF261806),
    background = Color(0xFFFBF8F2),
    surface = Color(0xFFFBF8F2),
    surfaceVariant = Color(0xFFEAE3D6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA8D199),
    onPrimary = Color(0xFF13380F),
    primaryContainer = Color(0xFF285224),
    onPrimaryContainer = Color(0xFFC3EDB4),
    secondary = Color(0xFFDDC1A6),
    onSecondary = Color(0xFF3E2D17),
    secondaryContainer = Color(0xFF56432C),
    onSecondaryContainer = Color(0xFFF6DCC2),
    background = Color(0xFF15130F),
    surface = Color(0xFF15130F),
    surfaceVariant = Color(0xFF4B463D),
)

@Composable
fun MojeKorenyTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
