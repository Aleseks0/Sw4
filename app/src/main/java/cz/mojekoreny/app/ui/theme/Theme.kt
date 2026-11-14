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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Moderní barevný styl: světlý neutrální základ, fialová jako hlavní barva,
// oranžová a tyrkysová jako doplňkové; každá „linie“ rodu má vlastní barvu (Accents).
private val Light = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF1A0F5C),
    secondary = Color(0xFFC2570C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3CC),
    onSecondaryContainer = Color(0xFF4A1D00),
    tertiary = Color(0xFF0E8C7E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDF3EC),
    onTertiaryContainer = Color(0xFF00382F),
    background = Color(0xFFF6F6FA),
    onBackground = Color(0xFF1B1B23),
    surface = Color(0xFFF6F6FA),
    onSurface = Color(0xFF1B1B23),
    surfaceVariant = Color(0xFFE6E6EE),
    onSurfaceVariant = Color(0xFF5D5E6C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F1F6),
    surfaceContainer = Color(0xFFECECF3),
    surfaceContainerHigh = Color(0xFFE6E6EE),
    surfaceContainerHighest = Color(0xFFE0E0E9),
    outline = Color(0xFF8A8A99),
    outlineVariant = Color(0xFFDADAE4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC5BCFF),
    onPrimary = Color(0xFF2A1C8A),
    primaryContainer = Color(0xFF3F31B5),
    onPrimaryContainer = Color(0xFFE6E1FF),
    secondary = Color(0xFFFFB68A),
    onSecondary = Color(0xFF4A1D00),
    secondaryContainer = Color(0xFF6E3300),
    onSecondaryContainer = Color(0xFFFFE3CC),
    tertiary = Color(0xFF6FDCCB),
    onTertiary = Color(0xFF00382F),
    tertiaryContainer = Color(0xFF005046),
    onTertiaryContainer = Color(0xFFCDF3EC),
    background = Color(0xFF121218),
    onBackground = Color(0xFFE5E4EE),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE5E4EE),
    surfaceVariant = Color(0xFF34343F),
    onSurfaceVariant = Color(0xFFC6C5D3),
    surfaceContainerLowest = Color(0xFF1C1C24),
    surfaceContainerLow = Color(0xFF17171E),
    surfaceContainer = Color(0xFF22222B),
    surfaceContainerHigh = Color(0xFF2B2B35),
    surfaceContainerHighest = Color(0xFF34343F),
    outline = Color(0xFF908F9C),
    outlineVariant = Color(0xFF43434F),
)

/** Barvy navíc: muži/ženy a pestrá paleta pro dlaždice a linie rodu. */
@Immutable
data class FamilyColors(
    val male: Color,
    val onMale: Color,
    val maleContainer: Color,
    val female: Color,
    val onFemale: Color,
    val femaleContainer: Color,
    val unknown: Color,
    val unknownContainer: Color,
    /** (výrazná barva, světlý podklad) – fialová, oranžová, tyrkysová, růžová, modrá, zelená. */
    val accents: List<Pair<Color, Color>>,
)

private val LightFamily = FamilyColors(
    male = Color(0xFF2F6FE4), onMale = Color.White, maleContainer = Color(0xFFDCE7FF),
    female = Color(0xFFD63E74), onFemale = Color.White, femaleContainer = Color(0xFFFFDDE8),
    unknown = Color(0xFF7C7C8A), unknownContainer = Color(0xFFE6E6EE),
    accents = listOf(
        Color(0xFF6A4CE0) to Color(0xFFEAE4FF),
        Color(0xFFE8720C) to Color(0xFFFFE6D1),
        Color(0xFF0E9F8E) to Color(0xFFD3F5EF),
        Color(0xFFD63E74) to Color(0xFFFFE0EB),
        Color(0xFF2F6FE4) to Color(0xFFDDE8FF),
        Color(0xFF3E9B3A) to Color(0xFFDDF3D9),
    ),
)
private val DarkFamily = FamilyColors(
    male = Color(0xFFA9C4FF), onMale = Color(0xFF0A1F4D), maleContainer = Color(0xFF1F3466),
    female = Color(0xFFFFB0C8), onFemale = Color(0xFF3F0A1E), femaleContainer = Color(0xFF5C1D36),
    unknown = Color(0xFFC6C5D3), unknownContainer = Color(0xFF34343F),
    accents = listOf(
        Color(0xFFC5B5FF) to Color(0xFF34286E),
        Color(0xFFFFB97F) to Color(0xFF5A2E07),
        Color(0xFF7FE3D3) to Color(0xFF0B4A42),
        Color(0xFFFFADC8) to Color(0xFF5C1D36),
        Color(0xFFA9C4FF) to Color(0xFF1F3466),
        Color(0xFFA5DDA0) to Color(0xFF1F4A1C),
    ),
)

val LocalFamilyColors = staticCompositionLocalOf { LightFamily }

// Bezpatkové písmo, o stupeň menší nadpisy než výchozí – víc informací na obrazovce.
private val Base = Typography()
private val AppTypography = Typography(
    headlineLarge = Base.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MojeKorenyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFamilyColors provides if (dark) DarkFamily else LightFamily) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
