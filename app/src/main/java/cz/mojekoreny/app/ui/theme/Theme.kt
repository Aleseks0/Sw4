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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta: lesní zeleň (koruna stromu), ořechová hněď (kořeny), pergamen (matriky).
private val Light = lightColorScheme(
    primary = Color(0xFF2F5D45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8D3),
    onPrimaryContainer = Color(0xFF0A2116),
    secondary = Color(0xFF7A5534),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3DEC8),
    onSecondaryContainer = Color(0xFF2C1906),
    tertiary = Color(0xFFA2553F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD0),
    onTertiaryContainer = Color(0xFF3B0A00),
    background = Color(0xFFFAF6EF),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFAF6EF),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFECE3D5),
    onSurfaceVariant = Color(0xFF4E4639),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F0E6),
    surfaceContainer = Color(0xFFF1EADF),
    surfaceContainerHigh = Color(0xFFEBE4D8),
    surfaceContainerHighest = Color(0xFFE5DED2),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD1C6B4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FD2B1),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF16452F),
    onPrimaryContainer = Color(0xFFCDE8D3),
    secondary = Color(0xFFE6C0A0),
    onSecondary = Color(0xFF452A0F),
    secondaryContainer = Color(0xFF5E3F21),
    onSecondaryContainer = Color(0xFFF3DEC8),
    tertiary = Color(0xFFFFB4A0),
    onTertiary = Color(0xFF5D1A06),
    tertiaryContainer = Color(0xFF7F3D2A),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF15130F),
    onBackground = Color(0xFFE9E1D6),
    surface = Color(0xFF15130F),
    onSurface = Color(0xFFE9E1D6),
    surfaceVariant = Color(0xFF4E4639),
    onSurfaceVariant = Color(0xFFD1C6B4),
    // V tmavém režimu jsou karty o stupeň světlejší než pozadí (jinak by splývaly).
    surfaceContainerLowest = Color(0xFF24211C),
    surfaceContainerLow = Color(0xFF1B1915),
    surfaceContainer = Color(0xFF221F1B),
    surfaceContainerHigh = Color(0xFF2D2925),
    surfaceContainerHighest = Color(0xFF38342F),
    outline = Color(0xFF9A9080),
    outlineVariant = Color(0xFF4E4639),
)

/** Barvy navíc: odlišení mužů a žen v seznamu i ve stromu. */
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
)

private val LightFamily = FamilyColors(
    male = Color(0xFF3B6A8C), onMale = Color.White, maleContainer = Color(0xFFD6E6F4),
    female = Color(0xFFA2553F), onFemale = Color.White, femaleContainer = Color(0xFFF8DDD3),
    unknown = Color(0xFF6F675B), unknownContainer = Color(0xFFE6DFD3),
)
private val DarkFamily = FamilyColors(
    male = Color(0xFF9CC7EA), onMale = Color(0xFF0B3450), maleContainer = Color(0xFF223E54),
    female = Color(0xFFFFB4A0), onFemale = Color(0xFF5D1A06), femaleContainer = Color(0xFF5A2C1F),
    unknown = Color(0xFFCFC5B6), unknownContainer = Color(0xFF3A352E),
)

val LocalFamilyColors = staticCompositionLocalOf { LightFamily }

// Nadpisy patkovým písmem (jako staré matriky), text bezpatkovým kvůli čitelnosti.
private val Serif = FontFamily.Serif
private val Base = Typography()
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, lineHeight = 16.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
