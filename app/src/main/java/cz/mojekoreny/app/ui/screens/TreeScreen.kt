package cz.mojekoreny.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.mojekoreny.app.ui.components.EmptyState
import cz.mojekoreny.app.ui.components.PersonAvatar
import cz.mojekoreny.app.ui.components.sexColors
import cz.mojekoreny.app.ui.theme.LocalFamilyColors
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.Person
import cz.mojekoreny.gedcom.fatherOf
import cz.mojekoreny.gedcom.motherOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

// ------------------------------------------------------------------ rozvržení karet

private const val CARD_W = 150f
private const val CARD_H = 54f
private const val SLOT = 62f
private const val GAP = 34f
private const val MARGIN = 12f

private data class TreeNode(val person: Person, val col: Int, val y: Float)
private data class TreeLayout(val nodes: List<TreeNode>, val edges: List<Pair<TreeNode, TreeNode>>, val cols: Int, val height: Float)

/**
 * Kompaktní rozložení stromu: „listy“ (nejvzdálenější známé osoby) dostanou řádky
 * postupně a každý uzel je svisle uprostřed svých větví. [next] vrací větve uzlu –
 * u předků rodiče, u potomků děti.
 */
private fun tidyLayout(root: Person, maxGen: Int, next: (Person) -> List<Person>): TreeLayout {
    val nodes = mutableListOf<TreeNode>()
    val edges = mutableListOf<Pair<TreeNode, TreeNode>>()
    var slot = 0
    var cols = 1
    val seen = mutableSetOf<String>()
    fun place(p: Person, gen: Int): TreeNode {
        seen += p.id
        cols = maxOf(cols, gen + 1)
        val branches = if (gen + 1 < maxGen) next(p).filter { it.id !in seen } else emptyList()
        val node = if (branches.isEmpty()) {
            TreeNode(p, gen, (slot++ + 0.5f) * SLOT)
        } else {
            val bn = branches.map { place(it, gen + 1) }
            val n = TreeNode(p, gen, (bn.first().y + bn.last().y) / 2)
            bn.forEach { edges += n to it }
            n
        }
        nodes += node
        return node
    }
    place(root, 0)
    return TreeLayout(nodes, edges, cols, slot * SLOT)
}

private fun ancestorsLayout(tree: GedcomTree, root: Person) =
    tidyLayout(root, 6) { listOfNotNull(tree.fatherOf(it), tree.motherOf(it)) }

private fun descendantsLayout(tree: GedcomTree, root: Person) =
    tidyLayout(root, 5) { p -> tree.childrenOf(p).sortedBy { it.birth?.parsedDate?.sortKey ?: Int.MAX_VALUE } }

// ------------------------------------------------------------------ obrazovka

private enum class TreeMode(val label: String) { ANCESTORS("Předci"), DESCENDANTS("Potomci"), FAN("Vějíř") }

@Composable
fun TreeScreen(
    tree: GedcomTree,
    root: Person?,
    onFocus: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    var modeIndex by rememberSaveable { mutableIntStateOf(0) }
    val mode = TreeMode.entries[modeIndex]
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 10.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Strom", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                if (root != null) {
                    Surface(
                        onClick = { onOpenPerson(root.id) },
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            PersonAvatar(root, 26.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(root.name.full, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 6.dp)) {
                TreeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = modeIndex == i,
                        onClick = { modeIndex = i },
                        shape = SegmentedButtonDefaults.itemShape(i, TreeMode.entries.size),
                        icon = {},
                    ) { Text(m.label, style = MaterialTheme.typography.labelLarge) }
                }
            }
            Text(
                if (mode == TreeMode.FAN) "Klepnutím na výseč osobu přesunete do středu, podržením otevřete její příběh."
                else "Klepnutím na osobu ji přesunete na začátek stromu, klepnutím na zvýrazněnou otevřete příběh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
        if (root == null) {
            EmptyState(Icons.Outlined.AccountTree, "Strom je prázdný", "Načtěte rodokmen v záložce Více.")
            return@Column
        }
        when (mode) {
            TreeMode.FAN -> FanChart(tree, root, onFocus, onOpenPerson)
            else -> {
                val layout = remember(tree, root, mode) {
                    if (mode == TreeMode.ANCESTORS) ancestorsLayout(tree, root) else descendantsLayout(tree, root)
                }
                CardTree(layout, root, onFocus, onOpenPerson)
            }
        }
    }
}

// ------------------------------------------------------------------ přibližování a posun

/**
 * Plocha s přiblížením (dva prsty) a posunem; obsah je oříznutý, takže nikdy
 * nepřekryje záhlaví ani spodní lištu. Vpravo dole tlačítka +, − a vycentrovat.
 * [fit] spočítá výchozí měřítko a posun z velikosti výřezu (v px).
 */
@Composable
private fun ZoomPanArea(
    contentWidthDp: Float,
    contentHeightDp: Float,
    key: Any,
    fit: Density.(viewW: Float, viewH: Float) -> Triple<Float, Float, Float>,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val cs = MaterialTheme.colorScheme

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(cs.surfaceContainerLow)
            .pointerInput(key) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val old = scale.value
                    val new = (old * zoom).coerceIn(0.3f, 3f)
                    val f = new / old
                    scope.launch {
                        scale.snapTo(new)
                        offsetX.snapTo((offsetX.value - centroid.x) * f + centroid.x + pan.x)
                        offsetY.snapTo((offsetY.value - centroid.y) * f + centroid.y + pan.y)
                    }
                }
            },
    ) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()

        suspend fun reset(animated: Boolean) = coroutineScope {
            val (s, x, y) = with(density) { fit(viewW, viewH) }
            if (animated) {
                launch { scale.animateTo(s) }
                launch { offsetX.animateTo(x) }
                offsetY.animateTo(y)
            } else {
                scale.snapTo(s); offsetX.snapTo(x); offsetY.snapTo(y)
            }
        }

        fun zoomBy(f: Float) = scope.launch {
            val old = scale.value
            val new = (old * f).coerceIn(0.3f, 3f)
            val k = new / old
            launch { scale.animateTo(new) }
            launch { offsetX.animateTo((offsetX.value - viewW / 2) * k + viewW / 2) }
            offsetY.animateTo((offsetY.value - viewH / 2) * k + viewH / 2)
        }

        LaunchedEffect(key, viewW, viewH) { reset(animated = false) }

        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(contentWidthDp.dp, contentHeightDp.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = offsetX.value
                    translationY = offsetY.value
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) { content() }

        Column(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
            SmallFloatingActionButton(onClick = { zoomBy(1.3f) }) { Icon(Icons.Outlined.Add, "Přiblížit") }
            SmallFloatingActionButton(onClick = { zoomBy(1 / 1.3f) }) { Icon(Icons.Outlined.Remove, "Oddálit") }
            SmallFloatingActionButton(
                onClick = { scope.launch { reset(animated = true) } },
                containerColor = cs.primary,
                contentColor = cs.onPrimary,
            ) { Icon(Icons.Outlined.CenterFocusStrong, "Vycentrovat") }
        }
    }
}

// ------------------------------------------------------------------ strom z karet

@Composable
private fun CardTree(layout: TreeLayout, root: Person, onFocus: (String) -> Unit, onOpen: (String) -> Unit) {
    val widthDp = layout.cols * (CARD_W + GAP) - GAP + 2 * MARGIN
    val heightDp = layout.height + 2 * MARGIN
    val rootNode = layout.nodes.first { it.person.id == root.id }
    val lineColor = MaterialTheme.colorScheme.outline
    val accents = LocalFamilyColors.current.accents

    ZoomPanArea(widthDp, heightDp, key = layout, fit = { viewW, viewH ->
        // Celé na šířku i výšku, pokud to jde; jinak nejvýš 100 % a nejméně 55 %.
        val s = minOf(viewW / widthDp.dp.toPx(), viewH / heightDp.dp.toPx(), 1f).coerceAtLeast(0.55f)
        val contentH = heightDp.dp.toPx() * s
        val y = if (contentH <= viewH) (viewH - contentH) / 2
        else (viewH / 2 - (rootNode.y + MARGIN).dp.toPx() * s).coerceIn(viewH - contentH, 0f)
        val contentW = widthDp.dp.toPx() * s
        val x = if (contentW <= viewW) (viewW - contentW) / 2 else 0f
        Triple(s, x, y)
    }) {
        Canvas(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
            val stroke = Stroke(width = 2.dp.toPx())
            for ((left, right) in layout.edges) {
                val x1 = (MARGIN + left.col * (CARD_W + GAP) + CARD_W).dp.toPx()
                val y1 = (MARGIN + left.y).dp.toPx()
                val x2 = (MARGIN + right.col * (CARD_W + GAP)).dp.toPx()
                val y2 = (MARGIN + right.y).dp.toPx()
                val mx = (x1 + x2) / 2
                val path = Path().apply { moveTo(x1, y1); cubicTo(mx, y1, mx, y2, x2, y2) }
                // Barva spojnice podle generace – stromy se v ní lépe čtou.
                val c = accents[right.col % accents.size].first
                drawPath(path, lerp(lineColor, c, 0.55f).copy(alpha = 0.8f), style = stroke)
            }
        }
        for (n in layout.nodes) {
            TreeCard(
                n.person,
                isRoot = n.person.id == root.id,
                modifier = Modifier.offset((MARGIN + n.col * (CARD_W + GAP)).dp, (MARGIN + n.y - CARD_H / 2).dp),
                onClick = { if (n.person.id == root.id) onOpen(n.person.id) else onFocus(n.person.id) },
            )
        }
    }
}

@Composable
private fun TreeCard(p: Person, isRoot: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (strong, _, container) = sexColors(p.sex)
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isRoot) container else cs.surfaceContainerLowest,
        border = if (isRoot) BorderStroke(2.dp, strong) else null,
        shadowElevation = if (isRoot) 3.dp else 1.dp,
        modifier = modifier.size(CARD_W.dp, CARD_H.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(strong))
            Spacer(Modifier.width(8.dp))
            PersonAvatar(p, 30.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.padding(end = 6.dp)) {
                Text(
                    p.name.given.ifEmpty { "?" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    p.birthSurname ?: p.name.surname,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.lifeSpan.isNotEmpty()) {
                    Text(p.lifeSpan, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ vějířový graf

private const val FAN_START = 150f   // úhel začátku (dole vlevo), 0° = vpravo, po směru hodin
private const val FAN_SWEEP = 240f
private const val FAN_GENS = 5       // počet prstenců předků
private val FAN_CENTER_R = 44f
private val FAN_RINGS = floatArrayOf(46f, 46f, 50f, 60f, 66f) // šířky prstenců (dp) od středu ven

private fun ringInner(g: Int): Float = FAN_CENTER_R + FAN_RINGS.take(g - 1).sum()
private fun ringOuter(g: Int): Float = ringInner(g) + FAN_RINGS[g - 1]

/** Předci podle Ahnentafel: generace g, index i; otec = 2i, matka = 2i + 1. */
private fun fanPeople(tree: GedcomTree, root: Person): List<List<Person?>> {
    val gens = mutableListOf<List<Person?>>(listOf(root))
    for (g in 1..FAN_GENS) {
        gens += gens.last().flatMap { p -> listOf(p?.let(tree::fatherOf), p?.let(tree::motherOf)) }
    }
    return gens
}

@Composable
private fun FanChart(tree: GedcomTree, root: Person, onFocus: (String) -> Unit, onOpen: (String) -> Unit) {
    val people = remember(tree, root) { fanPeople(tree, root) }
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val accents = LocalFamilyColors.current.accents
    val family = LocalFamilyColors.current
    val cs = MaterialTheme.colorScheme
    val radius = ringOuter(FAN_GENS)
    val pad = 10f
    val widthDp = 2 * (radius + pad)
    val cyDp = radius + pad
    val heightDp = cyDp + radius * sin(Math.toRadians(FAN_START.toDouble())).toFloat() + pad

    fun hit(pos: Offset, density: Density): Person? = with(density) {
        val dx = pos.x - widthDp.dp.toPx() / 2
        val dy = pos.y - cyDp.dp.toPx()
        val r = hypot(dx, dy).toDp().value
        if (r < FAN_CENTER_R) return root
        val g = (1..FAN_GENS).firstOrNull { r >= ringInner(it) && r < ringOuter(it) } ?: return null
        val angle = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
        val rel = (angle - FAN_START + 720) % 360
        if (rel > FAN_SWEEP) return null
        val i = floor(rel / (FAN_SWEEP / (1 shl g))).toInt().coerceIn(0, (1 shl g) - 1)
        people[g][i]
    }

    val density = LocalDensity.current
    ZoomPanArea(widthDp, heightDp, key = root.id, fit = { viewW, viewH ->
        val s = minOf(viewW / widthDp.dp.toPx(), viewH / heightDp.dp.toPx()).coerceIn(0.4f, 1.2f)
        Triple(s, (viewW - widthDp.dp.toPx() * s) / 2, ((viewH - heightDp.dp.toPx() * s) / 2).coerceAtLeast(0f))
    }) {
        Canvas(
            Modifier
                .requiredSize(widthDp.dp, heightDp.dp)
                .pointerInput(root.id) {
                    detectTapGestures(
                        onTap = { pos -> hit(pos, density)?.let { if (it.id == root.id) onOpen(it.id) else onFocus(it.id) } },
                        onLongPress = { pos -> hit(pos, density)?.let { onOpen(it.id) } },
                    )
                },
        ) {
            val cx = size.width / 2
            val cy = cyDp.dp.toPx()
            val center = Offset(cx, cy)
            val gapColor = cs.surfaceContainerLow

            for (g in 1..FAN_GENS) {
                val n = 1 shl g
                val segSweep = FAN_SWEEP / n
                val rIn = ringInner(g).dp.toPx()
                val rOut = ringOuter(g).dp.toPx()
                for (i in 0 until n) {
                    val p = people[g][i]
                    val start = FAN_START + i * segSweep
                    // Barva linie: u rodičů muž/žena, dál podle čtyř prarodičů.
                    val (strong, light) = when {
                        g == 1 -> if (i == 0) family.male to family.maleContainer else family.female to family.femaleContainer
                        else -> accents[listOf(0, 1, 2, 3)[(i shr (g - 2)) % 4]]
                    }
                    val fill = when {
                        p == null -> cs.surfaceContainerHigh.copy(alpha = 0.5f)
                        else -> lerp(light, strong, if (g % 2 == 0) 0.12f else 0.04f)
                    }
                    val path = Path().apply {
                        arcTo(Rect(center, rOut), start, segSweep, forceMoveTo = true)
                        arcTo(Rect(center, rIn), start + segSweep, -segSweep, forceMoveTo = false)
                        close()
                    }
                    drawPath(path, fill)
                    drawPath(path, gapColor, style = Stroke(width = 1.5.dp.toPx()))
                    if (p != null) {
                        // Barevný proužek na vnějším okraji výseče
                        drawArc(
                            strong, start + 0.6f, segSweep - 1.2f, useCenter = false,
                            topLeft = Offset(cx - rOut + 2.dp.toPx(), cy - rOut + 2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size((rOut - 2.dp.toPx()) * 2, (rOut - 2.dp.toPx()) * 2),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                        val mid = Math.toRadians((start + segSweep / 2).toDouble())
                        val rm = (rIn + rOut) / 2
                        val pos = Offset(cx + rm * cos(mid).toFloat(), cy + rm * sin(mid).toFloat())
                        val arcLen = (rm * segSweep * PI / 180).toFloat()
                        val ring = rOut - rIn
                        // Vnitřní prstence: text po obvodu; vnější: text paprskovitě.
                        val tangential = g <= 2
                        val boxW = (if (tangential) arcLen else ring) * 0.88f
                        val boxH = (if (tangential) ring else arcLen) * 0.9f
                        val fs = when (g) { 1, 2 -> 11.5f; 3 -> 10.5f; 4 -> 10f; else -> 9f }
                        val layout = fitText(measurer, p, boxW, boxH, fs, cs.onSurface)
                        var rot = ((if (tangential) Math.toDegrees(mid) + 90 else Math.toDegrees(mid)) % 360 + 360) % 360
                        if (rot > 90 && rot < 270) rot -= 180
                        if (layout != null) {
                            rotate(rot.toFloat(), pivot = pos) {
                                drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
                            }
                        }
                    }
                }
            }

            // Střed: výchozí osoba
            val rc = FAN_CENTER_R.dp.toPx()
            drawCircle(Brush.linearGradient(listOf(Color(0xFF7A4FE6), Color(0xFF2F6FE4)), center - Offset(rc, rc), center + Offset(rc, rc)), rc, center)
            val rootText = fitText(measurer, root, rc * 1.6f, rc * 1.5f, 11.5f, Color.White, bold = true)
            if (rootText != null) {
                drawText(rootText, topLeft = Offset(cx - rootText.size.width / 2f, cy - rootText.size.height / 2f))
            }
        }
    }
}

/** Najde nejbohatší popisek (jméno, příjmení, roky), který se vejde do obdélníku. */
private fun fitText(
    measurer: androidx.compose.ui.text.TextMeasurer,
    p: Person,
    maxW: Float,
    maxH: Float,
    fontSize: Float,
    color: Color,
    bold: Boolean = false,
): TextLayoutResult? {
    if (maxW < 8 || maxH < 8) return null
    val given = p.name.given.ifEmpty { "?" }
    val surname = p.birthSurname ?: p.name.surname
    val years = p.lifeSpan
    val variants = listOfNotNull(
        listOf(given, surname, years).filter { it.isNotEmpty() }.joinToString("\n"),
        listOf(given, surname).filter { it.isNotEmpty() }.joinToString("\n"),
        "$given $surname".trim(),
        given,
    )
    for (size in listOf(fontSize, fontSize * 0.88f, fontSize * 0.78f)) {
        val style = TextStyle(
            fontSize = size.sp, lineHeight = (size * 1.15f).sp, color = color, textAlign = TextAlign.Center,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
        for (v in variants) {
            val l = measurer.measure(
                v, style,
                overflow = TextOverflow.Ellipsis,
                maxLines = v.count { it == '\n' } + 1,
                constraints = Constraints(maxWidth = maxW.toInt()),
            )
            if (l.size.height <= maxH && !l.hasVisualOverflow) return l
        }
    }
    return null
}
