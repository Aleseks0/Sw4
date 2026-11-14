package cz.mojekoreny.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Vzhled převzatý z rodinného webu: teplý papír, hnědý akcent, patkové nadpisy,
 * bílé karty s tenkým rámečkem, pastelové barvy linií rodu ve vějíři.
 * Hodnoty odpovídají CSS proměnným webu (--bg, --paper, --ink, --accent …).
 */

private val Light = lightColorScheme(
    primary = Color(0xFF8A5A2B),            // --accent
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E6D4),   // --accent-soft
    onPrimaryContainer = Color(0xFF3B2E22),
    secondary = Color(0xFF3F7D4A),          // --doc (doloženo)
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F0E3),
    onSecondaryContainer = Color(0xFF173A1E),
    tertiary = Color(0xFF3E6488),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6EEF6),
    onTertiaryContainer = Color(0xFF1B3550),
    background = Color(0xFFFBF8F2),         // --bg
    onBackground = Color(0xFF2B2620),       // --ink
    surface = Color(0xFFFBF8F2),
    onSurface = Color(0xFF2B2620),
    surfaceVariant = Color(0xFFEFE7DA),
    onSurfaceVariant = Color(0xFF6E6153),   // --muted
    surfaceContainerLowest = Color(0xFFFFFFFF), // --paper (karty)
    surfaceContainerLow = Color(0xFFF6F0E6),
    surfaceContainer = Color(0xFFF1EDE6),
    surfaceContainerHigh = Color(0xFFEFE7DA),
    surfaceContainerHighest = Color(0xFFE8DCCB),
    outline = Color(0xFF8A7660),
    outlineVariant = Color(0xFFE4DDD1),     // --line
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE0A765),
    onPrimary = Color(0xFF3B2300),
    primaryContainer = Color(0xFF4A3722),
    onPrimaryContainer = Color(0xFFF3E6D4),
    secondary = Color(0xFF8FCF98),
    onSecondary = Color(0xFF0E3A18),
    secondaryContainer = Color(0xFF244B2B),
    onSecondaryContainer = Color(0xFFD5EDD8),
    tertiary = Color(0xFF9EC3E6),
    onTertiary = Color(0xFF0E2E4A),
    tertiaryContainer = Color(0xFF28435E),
    onTertiaryContainer = Color(0xFFD7E7F6),
    background = Color(0xFF1D1914),
    onBackground = Color(0xFFEEE6D9),
    surface = Color(0xFF1D1914),
    onSurface = Color(0xFFEEE6D9),
    surfaceVariant = Color(0xFF3A3228),
    onSurfaceVariant = Color(0xFFC4B8A6),
    surfaceContainerLowest = Color(0xFF28221A),
    surfaceContainerLow = Color(0xFF231E17),
    surfaceContainer = Color(0xFF2D261D),
    surfaceContainerHigh = Color(0xFF362E24),
    surfaceContainerHighest = Color(0xFF40372C),
    outline = Color(0xFF9A8B76),
    outlineVariant = Color(0xFF433A2E),
)

/** Barvy navíc, které Material schéma nezná. */
@Immutable
data class AppColors(
    /** Tmavohnědá lišta nahoře a střed vějíře (web: #3b2e22). */
    val header: Color,
    val onHeader: Color,
    /** Oranžový proužek nad lištou (web: #e0952f). */
    val stripe: Color,
    /** Výchozí barva levého proužku karty osoby. */
    val tan: Color,
    /** Čtyři linie rodu (od čtyř prarodičů): výrazná barva a pastelová výplň. */
    val lineStrong: List<Color>,
    val lineSoft: List<Color>,
    /** První prstenec vějíře: otec (béžová) a matka (světle modrá). */
    val fatherSoft: Color,
    val motherSoft: Color,
    /** Pole bez známého předka. */
    val unknownFill: Color,
    val doc: Color,
    val probable: Color,
)

private val LightApp = AppColors(
    header = Color(0xFF3B2E22),
    onHeader = Color(0xFFFBF8F2),
    stripe = Color(0xFFE0952F),
    tan = Color(0xFFD8CFC2),
    lineStrong = listOf(Color(0xFFE0952F), Color(0xFFD96C8C), Color(0xFF5B9BD5), Color(0xFF2FB59C)),
    lineSoft = listOf(Color(0xFFF6DDB4), Color(0xFFF4C9D6), Color(0xFFCFE2F5), Color(0xFFC4EBE3)),
    fatherSoft = Color(0xFFE8DCCB),
    motherSoft = Color(0xFFD6E3EE),
    unknownFill = Color(0xFFEEEAE3),
    doc = Color(0xFF3F7D4A),
    probable = Color(0xFFB07A1E),
)

private val DarkApp = AppColors(
    header = Color(0xFF3B2E22),
    onHeader = Color(0xFFFBF8F2),
    stripe = Color(0xFFE0952F),
    tan = Color(0xFF5A4E40),
    lineStrong = listOf(Color(0xFFEFB45F), Color(0xFFE88BA6), Color(0xFF8FBCE8), Color(0xFF6CD0BC)),
    lineSoft = listOf(Color(0xFF5A4223), Color(0xFF5A2D3D), Color(0xFF2B4560), Color(0xFF1F5047)),
    fatherSoft = Color(0xFF4A3F31),
    motherSoft = Color(0xFF2F4152),
    unknownFill = Color(0xFF2A241C),
    doc = Color(0xFF8FCF98),
    probable = Color(0xFFE0B25F),
)

val LocalAppColors = staticCompositionLocalOf { LightApp }

private val Serif = FontFamily.Serif
private val Base = Typography()
private val AppTypography = Typography(
    headlineLarge = Base.headlineLarge.copy(fontFamily = Serif, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Serif, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Serif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    titleLarge = Base.titleLarge.copy(fontFamily = Serif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    titleMedium = Base.titleMedium.copy(fontFamily = Serif, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    titleSmall = Base.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = Base.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(999.dp),
)

@Composable
fun MojeKorenyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkApp else LightApp) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
