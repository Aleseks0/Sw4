package cz.mojekoreny.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.mojekoreny.app.ui.theme.LocalFamilyColors
import cz.mojekoreny.gedcom.LifeKind
import cz.mojekoreny.gedcom.Person
import cz.mojekoreny.gedcom.Sex

/** Barvy podle pohlaví: (výrazná, na výrazné, světlý podklad). */
@Composable
fun sexColors(sex: Sex): Triple<Color, Color, Color> {
    val c = LocalFamilyColors.current
    return when (sex) {
        Sex.MALE -> Triple(c.male, c.onMale, c.maleContainer)
        Sex.FEMALE -> Triple(c.female, c.onFemale, c.femaleContainer)
        else -> Triple(c.unknown, Color.White, c.unknownContainer)
    }
}

fun initials(p: Person): String {
    val g = p.name.given.firstOrNull { it.isLetter() }
    val s = p.name.surname.firstOrNull { it.isLetter() }
    return listOfNotNull(g, s).joinToString("").uppercase().ifEmpty { "?" }
}

/** Kulatý avatar s iniciálami; barevný přechod podle pohlaví. */
@Composable
fun PersonAvatar(person: Person, size: Dp = 44.dp, ring: Boolean = false) {
    val (strong, onStrong, _) = sexColors(person.sex)
    Box(
        Modifier
            .size(size)
            .then(if (ring) Modifier.border(3.dp, MaterialTheme.colorScheme.surface, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(strong.copy(alpha = 0.75f), strong))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(person),
            color = onStrong,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
        )
    }
}

/** Karta s nadpisem a ikonou – základní stavební kámen obrazovek. */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.size(6.dp))
            content()
        }
    }
}

/** Malá karta osoby (rodina, výročí, naposledy prohlížené). */
@Composable
fun PersonTile(person: Person, label: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (_, _, container) = sexColors(person.sex)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = container.copy(alpha = 0.6f),
        modifier = modifier.width(116.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PersonAvatar(person, 38.dp)
            Spacer(Modifier.size(6.dp))
            if (label != null) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                person.name.full.ifEmpty { "(beze jména)" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (person.lifeSpan.isNotEmpty()) {
                Text(person.lifeSpan, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Řádek osoby v seznamu. */
@Composable
fun PersonRow(person: Person, supporting: String?, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.name.full.ifEmpty { "(beze jména)" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supporting.isNullOrEmpty()) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Barevná dlaždice s číslem (přehled na úvodní obrazovce). */
@Composable
fun StatTile(value: String, label: String, icon: ImageVector, accent: Int, modifier: Modifier = Modifier) {
    val (strong, container) = LocalFamilyColors.current.accents[accent % LocalFamilyColors.current.accents.size]
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(strong), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = strong, maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Prázdný stav s ikonou a textem. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (action != null) {
            Spacer(Modifier.size(24.dp))
            action()
        }
    }
}

fun LifeKind.icon(): ImageVector = when (this) {
    LifeKind.BIRTH -> Icons.Outlined.Cake
    LifeKind.BAPTISM -> Icons.Outlined.WaterDrop
    LifeKind.MARRIAGE -> Icons.Outlined.Favorite
    LifeKind.DIVORCE -> Icons.Outlined.HeartBroken
    LifeKind.CHILD -> Icons.Outlined.ChildCare
    LifeKind.OCCUPATION -> Icons.Outlined.Work
    LifeKind.RESIDENCE -> Icons.Outlined.Home
    LifeKind.RELIGION -> Icons.Outlined.Church
    LifeKind.EDUCATION -> Icons.Outlined.School
    LifeKind.MIGRATION -> Icons.Outlined.Sailing
    LifeKind.DEATH -> Icons.Outlined.Spa
    LifeKind.BURIAL -> Icons.Outlined.LocalFlorist
    LifeKind.OTHER -> Icons.Outlined.Star
}
