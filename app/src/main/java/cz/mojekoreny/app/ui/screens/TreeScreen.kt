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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.mojekoreny.app.ui.components.EmptyState
import cz.mojekoreny.app.ui.components.lineStripe
import cz.mojekoreny.app.ui.theme.LocalAppColors
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

private const val CARD_W = 156f
private const val CARD_H = 56f
private const val SLOT = 64f
private const val GAP = 30f
private const val MARGIN = 12f

private data class TreeNode(val person: Person, val col: Int, val y: Float, val line: Int)
private data class TreeLayout(val nodes: List<TreeNode>, val edges: List<Pair<TreeNode, TreeNode>>, val cols: Int, val height: Float)

/**
 * Kompaktní rozložení: „listy“ dostanou řádky postupně a každý uzel je svisle
 * uprostřed svých větví. [next] vrací větve uzlu, [lineOf] barevnou linii větve.
 */
private fun tidyLayout(
    root: Person,
    maxGen: Int,
    next: (Person) -> List<Person>,
    lineOf: (parentLine: Int, gen: Int, index: Int) -> Int,
): TreeLayout {
    val nodes = mutableListOf<TreeNode>()
    val edges = mutableListOf<Pair<TreeNode, TreeNode>>()
    var slot = 0
    var cols = 1
    val seen = mutableSetOf<String>()
    fun place(p: Person, gen: Int, line: Int): TreeNode {
        seen += p.id
        cols = maxOf(cols, gen + 1)
        val branches = if (gen + 1 < maxGen) next(p).filter { it.id !in seen } else emptyList()
        val node = if (branches.isEmpty()) {
            TreeNode(p, gen, (slot++ + 0.5f) * SLOT, line)
        } else {
            val bn = branches.mapIndexed { i, b -> place(b, gen + 1, lineOf(line, gen + 1, i)) }
            val n = TreeNode(p, gen, (bn.first().y + bn.last().y) / 2, line)
            bn.forEach { edges += n to it }
            n
        }
        nodes += node
        return node
    }
    place(root, 0, -1)
    return TreeLayout(nodes, edges, cols, slot * SLOT)
}

private fun ancestorsLayout(tree: GedcomTree, root: Person) = tidyLayout(
    root, 6,
    next = { listOfNotNull(tree.fatherOf(it), tree.motherOf(it)) },
    // 1. generace: otec / matka; od 2.: čtyři linie prarodičů
    lineOf = { pl, gen, i -> when (gen) { 1 -> 10 + i; 2 -> 2 * (pl - 10) + i; else -> pl } },
)

private fun descendantsLayout(tree: GedcomTree, root: Person) = tidyLayout(
    root, 5,
    next = { p -> tree.childrenOf(p).sortedBy { it.birth?.parsedDate?.sortKey ?: Int.MAX_VALUE } },
    lineOf = { pl, gen, i -> if (gen == 1) i % 4 else pl },
)

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
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val zoom = remember(mode, root?.id) { ZoomController() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)) {
            Text(
                when (mode) { TreeMode.ANCESTORS -> "Předci"; TreeMode.DESCENDANTS -> "Potomci"; TreeMode.FAN -> "Vějíř předků" },
                style = MaterialTheme.typography.headlineLarge,
            )
            if (root != null) {
                Text(root.name.full, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                TreeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = modeIndex == i,
                        onClick = { modeIndex = i },
                        shape = SegmentedButtonDefaults.itemShape(i, TreeMode.entries.size),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = cs.primaryContainer,
                            activeContentColor = cs.onPrimaryContainer,
                            inactiveContainerColor = cs.surfaceContainerLowest,
                            activeBorderColor = cs.primary,
                            inactiveBorderColor = cs.outlineVariant,
                        ),
                    ) { Text(m.label, style = MaterialTheme.typography.labelLarge) }
                }
            }
            // Ovládání nad plochou stromu – nikdy se nepřekrývá s obsahem.
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                ToolButton("+") { scope.launch { zoom.zoomBy(1.3f) } }
                ToolButton("−") { scope.launch { zoom.zoomBy(1 / 1.3f) } }
                ToolButton("Celý") { scope.launch { zoom.reset(animated = true) } }
            }
        }
        androidx.compose.material3.HorizontalDivider(color = cs.outlineVariant)

        if (root == null) {
            EmptyState(Icons.Outlined.AccountTree, "Strom je prázdný", "Načtěte rodokmen v záložce Více.")
            return@Column
        }
        when (mode) {
            TreeMode.FAN -> FanChart(zoom, tree, root, onFocus, onOpenPerson)
            else -> {
                val layout = remember(tree, root, mode) {
                    if (mode == TreeMode.ANCESTORS) ancestorsLayout(tree, root) else descendantsLayout(tree, root)
                }
                CardTree(zoom, layout, root, onFocus, onOpenPerson)
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, cs.outlineVariant),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = cs.surfaceContainerLowest,
            contentColor = cs.onSurface,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        modifier = Modifier.height(38.dp),
    ) { Text(label, style = MaterialTheme.typography.titleSmall) }
}

// ------------------------------------------------------------------ přibližování a posun

/** Stav přiblížení a posunu; ovládá se z tlačítek nad plochou i dvěma prsty. */
@Stable
private class ZoomController {
    val scale = Animatable(1f)
    val ox = Animatable(0f)
    val oy = Animatable(0f)
    var viewW = 0f
    var viewH = 0f
    var fit: (Float, Float) -> Triple<Float, Float, Float> = { _, _ -> Triple(1f, 0f, 0f) }

    suspend fun reset(animated: Boolean) = coroutineScope {
        val (s, x, y) = fit(viewW, viewH)
        if (animated) {
            launch { scale.animateTo(s) }
            launch { ox.animateTo(x) }
            oy.animateTo(y)
        } else {
            scale.snapTo(s); ox.snapTo(x); oy.snapTo(y)
        }
    }

    suspend fun zoomBy(f: Float) = coroutineScope {
        val old = scale.value
        val new = (old * f).coerceIn(0.3f, 3f)
        val k = new / old
        launch { scale.animateTo(new) }
        launch { ox.animateTo((ox.value - viewW / 2) * k + viewW / 2) }
        oy.animateTo((oy.value - viewH / 2) * k + viewH / 2)
    }

    suspend fun gesture(centroid: Offset, pan: Offset, zoom: Float) {
        val old = scale.value
        val new = (old * zoom).coerceIn(0.3f, 3f)
        val f = new / old
        scale.snapTo(new)
        ox.snapTo((ox.value - centroid.x) * f + centroid.x + pan.x)
        oy.snapTo((oy.value - centroid.y) * f + centroid.y + pan.y)
    }
}

/** Oříznutá plocha s přiblížením a posunem; obsah nikdy nepřesáhne své hranice. */
@Composable
private fun ZoomPanArea(
    zoom: ZoomController,
    contentWidthDp: Float,
    contentHeightDp: Float,
    key: Any,
    fit: Density.(viewW: Float, viewH: Float) -> Triple<Float, Float, Float>,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    zoom.fit = { w, h -> with(density) { fit(w, h) } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(key) {
                detectTransformGestures { centroid, pan, z, _ -> scope.launch { zoom.gesture(centroid, pan, z) } }
            },
    ) {
        zoom.viewW = constraints.maxWidth.toFloat()
        zoom.viewH = constraints.maxHeight.toFloat()
        LaunchedEffect(key, zoom.viewW, zoom.viewH) { zoom.reset(animated = false) }

        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(contentWidthDp.dp, contentHeightDp.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = zoom.ox.value
                    translationY = zoom.oy.value
                    scaleX = zoom.scale.value
                    scaleY = zoom.scale.value
                },
        ) { content() }
    }
}

// ------------------------------------------------------------------ strom z karet

@Composable
private fun CardTree(zoom: ZoomController, layout: TreeLayout, root: Person, onFocus: (String) -> Unit, onOpen: (String) -> Unit) {
    val widthDp = layout.cols * (CARD_W + GAP) - GAP + 2 * MARGIN
    val heightDp = layout.height + 2 * MARGIN
    val rootNode = layout.nodes.first { it.person.id == root.id }
    val lineColor = LocalAppColors.current.tan

    ZoomPanArea(zoom, widthDp, heightDp, key = layout, fit = { viewW, viewH ->
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
                drawPath(Path().apply { moveTo(x1, y1); cubicTo(mx, y1, mx, y2, x2, y2) }, lineColor, style = stroke)
            }
        }
        for (n in layout.nodes) {
            TreeCard(
                n.person,
                stripe = lineStripe(n.line),
                isRoot = n.person.id == root.id,
                modifier = Modifier.offset((MARGIN + n.col * (CARD_W + GAP)).dp, (MARGIN + n.y - CARD_H / 2).dp),
                onClick = { if (n.person.id == root.id) onOpen(n.person.id) else onFocus(n.person.id) },
            )
        }
    }
}

@Composable
private fun TreeCard(p: Person, stripe: Color, isRoot: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isRoot) cs.primaryContainer else cs.surfaceContainerLowest,
        border = BorderStroke(if (isRoot) 2.dp else 1.dp, if (isRoot) cs.primary else cs.outlineVariant),
        modifier = modifier.size(CARD_W.dp, CARD_H.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(stripe))
            Column(Modifier.padding(horizontal = 9.dp)) {
                Text(
                    p.name.given.ifEmpty { "?" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    p.birthSurname ?: p.name.surname,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.lifeSpan.isNotEmpty()) {
                    Text(p.lifeSpan, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ vějířový graf

private const val FAN_START = 90f      // 0° = vpravo, po směru hodin; začátek dole → otec vlevo, matka vpravo
private const val FAN_GENS = 5
private const val FAN_CENTER_R = 40f
private val FAN_RINGS = floatArrayOf(46f, 46f, 50f, 56f, 62f) // šířky prstenců (dp) od středu ven

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
private fun FanChart(zoom: ZoomController, tree: GedcomTree, root: Person, onFocus: (String) -> Unit, onOpen: (String) -> Unit) {
    val people = remember(tree, root) { fanPeople(tree, root) }
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val app = LocalAppColors.current
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val radius = ringOuter(FAN_GENS)
    val pad = 8f
    val sizeDp = 2 * (radius + pad)

    fun hit(pos: Offset): Person? = with(density) {
        val dx = pos.x - sizeDp.dp.toPx() / 2
        val dy = pos.y - sizeDp.dp.toPx() / 2
        val r = hypot(dx, dy).toDp().value
        if (r < FAN_CENTER_R) return root
        val g = (1..FAN_GENS).firstOrNull { r >= ringInner(it) && r < ringOuter(it) } ?: return null
        val angle = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
        val rel = (angle - FAN_START + 720) % 360
        val i = floor(rel / (360f / (1 shl g))).toInt().coerceIn(0, (1 shl g) - 1)
        people[g][i]
    }

    ZoomPanArea(zoom, sizeDp, sizeDp, key = root.id, fit = { viewW, viewH ->
        val s = minOf(viewW / sizeDp.dp.toPx(), viewH / sizeDp.dp.toPx()).coerceIn(0.3f, 1.2f)
        Triple(s, (viewW - sizeDp.dp.toPx() * s) / 2, (viewH - sizeDp.dp.toPx() * s) / 2)
    }) {
        Canvas(
            Modifier
                .requiredSize(sizeDp.dp, sizeDp.dp)
                .pointerInput(root.id) {
                    detectTapGestures(
                        onTap = { pos -> hit(pos)?.let { if (it.id == root.id) onOpen(it.id) else onFocus(it.id) } },
                        onLongPress = { pos -> hit(pos)?.let { onOpen(it.id) } },
                    )
                },
        ) {
            val c = Offset(size.width / 2, size.height / 2)
            val gapColor = cs.background

            for (g in 1..FAN_GENS) {
                val n = 1 shl g
                val seg = 360f / n
                val rIn = ringInner(g).dp.toPx()
                val rOut = ringOuter(g).dp.toPx()
                for (i in 0 until n) {
                    val p = people[g][i]
                    val start = FAN_START + i * seg
                    // Barva: 1. prstenec otec/matka, dál čtyři linie prarodičů; střídání odstínu po generacích.
                    val line = if (g == 1) -1 else i shr (g - 2)
                    val base = when {
                        g == 1 -> if (i == 0) app.fatherSoft else app.motherSoft
                        else -> app.lineSoft[line % 4]
                    }
                    val fill = when {
                        p == null -> app.unknownFill
                        g >= 3 && g % 2 == 1 -> lerp(base, cs.surfaceContainerLowest, 0.35f)
                        else -> base
                    }
                    val path = Path().apply {
                        arcTo(Rect(c, rOut), start, seg, forceMoveTo = true)
                        arcTo(Rect(c, rIn), start + seg, -seg, forceMoveTo = false)
                        close()
                    }
                    drawPath(path, fill)
                    drawPath(path, gapColor, style = Stroke(width = 1.5.dp.toPx()))
                    if (p != null) {
                        val mid = Math.toRadians((start + seg / 2).toDouble())
                        val rm = (rIn + rOut) / 2
                        val pos = Offset(c.x + rm * cos(mid).toFloat(), c.y + rm * sin(mid).toFloat())
                        val arcLen = (rm * seg * PI / 180).toFloat()
                        val ring = rOut - rIn
                        // Vnitřní prstence: text po obvodu; vnější: paprskovitě.
                        val tangential = g <= 3
                        val boxW = (if (tangential) arcLen else ring) * 0.9f
                        val boxH = (if (tangential) ring else arcLen) * 0.9f
                        val fs = when (g) { 1 -> 13f; 2 -> 12f; 3 -> 11f; 4 -> 10f; else -> 9f }
                        val text = fitText(measurer, p, boxW.toDp().value, boxH.toDp().value, fs, cs.onSurface)
                        var rot = (((if (tangential) Math.toDegrees(mid) + 90 else Math.toDegrees(mid)) % 360) + 360) % 360
                        if (rot > 90 && rot < 270) rot -= 180
                        if (text != null) {
                            rotate(rot.toFloat(), pivot = pos) {
                                drawText(text, topLeft = Offset(pos.x - text.size.width / 2f, pos.y - text.size.height / 2f))
                            }
                        }
                    }
                }
            }

            // Střed: výchozí osoba na hnědém podkladu jako lišta webu
            val rc = FAN_CENTER_R.dp.toPx()
            drawCircle(app.header, rc, c)
            val rootText = fitText(measurer, root, FAN_CENTER_R * 1.6f, FAN_CENTER_R * 1.5f, 12f, app.onHeader, bold = true)
            if (rootText != null) {
                drawText(rootText, topLeft = Offset(c.x - rootText.size.width / 2f, c.y - rootText.size.height / 2f))
            }
        }
    }
}

/** Najde nejbohatší popisek (jméno, příjmení, roky), který se vejde do obdélníku (rozměry v dp). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.fitText(
    measurer: TextMeasurer,
    p: Person,
    maxWdp: Float,
    maxHdp: Float,
    fontSize: Float,
    color: Color,
    bold: Boolean = false,
): TextLayoutResult? = fitTextPx(measurer, p, maxWdp.dp.toPx(), maxHdp.dp.toPx(), fontSize, color, bold)

private fun fitTextPx(
    measurer: TextMeasurer,
    p: Person,
    maxW: Float,
    maxH: Float,
    fontSize: Float,
    color: Color,
    bold: Boolean,
): TextLayoutResult? {
    if (maxW < 8 || maxH < 8) return null
    val given = p.name.given.ifEmpty { "?" }
    val surname = p.birthSurname ?: p.name.surname
    val years = p.lifeSpan
    val variants = listOf(
        listOf(given, surname, years).filter { it.isNotEmpty() }.joinToString("\n"),
        listOf(given, surname).filter { it.isNotEmpty() }.joinToString("\n"),
        "$given $surname".trim(),
        given,
    ).distinct()
    for (size in listOf(fontSize, fontSize * 0.88f, fontSize * 0.78f)) {
        val style = TextStyle(
            fontSize = size.sp,
            lineHeight = (size * 1.15f).sp,
            color = color,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.SansSerif,
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
