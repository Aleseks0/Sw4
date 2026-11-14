package cz.mojekoreny.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.mojekoreny.app.ui.theme.LocalAppColors
import cz.mojekoreny.gedcom.Person

/** Tmavohnědá lišta s oranžovým proužkem – stejná jako na webu. */
@Composable
fun AppHeader(title: String, onBack: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(c.header).windowInsetsPadding(WindowInsets.statusBars)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.stripe))
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = if (onBack == null) 16.dp else 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zpět", tint = c.onHeader) }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = c.onHeader,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Nadpis sekce: patkové písmo a tenká linka pod ním (jako na webu). */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Menší podnadpis (Rodiče, Prarodiče …). */
@Composable
fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 6.dp, bottom = 8.dp),
    )
}

/** Bílá karta s tenkým rámečkem. */
@Composable
fun WebCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val color = MaterialTheme.colorScheme.surfaceContainerLowest
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(content = content)
        }
    }
}

/** Zaoblený štítek (roky, „výchozí osoba“ …). */
@Composable
fun Pill(text: String, container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

fun yearsRange(p: Person): String = p.lifeSpan

/** Karta osoby: levý barevný proužek, jméno tučně, roky šedě. */
@Composable
fun PersonCard(
    person: Person,
    modifier: Modifier = Modifier,
    caption: String? = null,
    stripe: Color = LocalAppColors.current.tan,
    highlighted: Boolean = false,
    detail: String? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (highlighted) cs.primaryContainer else cs.surfaceContainerLowest,
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(stripe))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (caption != null) {
                    Text(caption, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
                Text(
                    person.name.full.ifEmpty { "(beze jména)" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(person.lifeSpan.ifEmpty { null }, detail).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Karty osob ve dvou sloupcích (stejně vysoké v řádku). */
@Composable
fun <T> TwoColumns(items: List<T>, content: @Composable (T, Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { content(it, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Velké číslo patkovým písmem a popisek pod ním (statistika na úvodu webu). */
@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            action()
        }
    }
}

/** Barva proužku podle linie: 10 = otec, 11 = matka, 0–3 = linie prarodičů, jinak výchozí. */
@Composable
fun lineStripe(line: Int): Color {
    val c = LocalAppColors.current
    return when (line) {
        10 -> c.fatherSoft
        11 -> c.motherSoft
        in 0..3 -> c.lineStrong[line]
        else -> c.tan
    }
}

@Composable
fun Modifier.roundedClip(radius: Int = 10): Modifier = this.clip(RoundedCornerShape(radius.dp))
