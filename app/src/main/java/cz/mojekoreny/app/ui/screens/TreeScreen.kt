package cz.mojekoreny.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.outlined.CenterFocusStrong
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.mojekoreny.app.ui.components.EmptyState
import cz.mojekoreny.app.ui.components.PersonAvatar
import cz.mojekoreny.app.ui.components.sexColors
import cz.mojekoreny.gedcom.GedcomTree
import cz.mojekoreny.gedcom.Person
import cz.mojekoreny.gedcom.fatherOf
import cz.mojekoreny.gedcom.motherOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val CARD_W = 176f
private const val CARD_H = 64f
private const val SLOT = 76f
private const val GAP = 44f

private data class TreeNode(val person: Person, val col: Int, val y: Float)
private data class TreeLayout(val nodes: List<TreeNode>, val edges: List<Pair<TreeNode, TreeNode>>, val cols: Int, val height: Float)

/**
 * Vývod: osoba vlevo, rodiče napravo od ní, každá generace v jednom sloupci.
 * Kompaktní rozložení – nejvzdálenější známí předci dostanou řádky postupně
 * a každé dítě je svisle uprostřed mezi svými rodiči, takže nevznikají prázdná místa.
 */
private fun ancestorsLayout(tree: GedcomTree, root: Person, maxGen: Int = 6): TreeLayout {
    val nodes = mutableListOf<TreeNode>()
    val edges = mutableListOf<Pair<TreeNode, TreeNode>>()
    var slot = 0
    var cols = 1
    val seen = mutableSetOf<String>()
    fun place(p: Person, gen: Int): TreeNode {
        seen += p.id
        cols = maxOf(cols, gen + 1)
        val parents = if (gen + 1 < maxGen) {
            listOfNotNull(tree.fatherOf(p), tree.motherOf(p)).filter { it.id !in seen }
        } else emptyList()
        val node = if (parents.isEmpty()) {
            TreeNode(p, gen, (slot++ + 0.5f) * SLOT)
        } else {
            val pn = parents.map { place(it, gen + 1) }
            val n = TreeNode(p, gen, (pn.first().y + pn.last().y) / 2)
            pn.forEach { edges += n to it }
            n
        }
        nodes += node
        return node
    }
    place(root, 0)
    return TreeLayout(nodes, edges, cols, slot * SLOT)
}

/** Rozrod: osoba vlevo, děti a vnoučata napravo; rodič je svisle uprostřed svých dětí. */
private fun descendantsLayout(tree: GedcomTree, root: Person, maxGen: Int = 4): TreeLayout {
    val nodes = mutableListOf<TreeNode>()
    val edges = mutableListOf<Pair<TreeNode, TreeNode>>()
    var slot = 0
    var cols = 1
    val seen = mutableSetOf<String>()
    fun place(p: Person, gen: Int): TreeNode {
        seen += p.id
        cols = maxOf(cols, gen + 1)
        val kids = if (gen + 1 < maxGen) {
            tree.childrenOf(p).filter { it.id !in seen }.sortedBy { it.birth?.parsedDate?.sortKey ?: Int.MAX_VALUE }
        } else emptyList()
        val node = if (kids.isEmpty()) {
            TreeNode(p, gen, (slot++ + 0.5f) * SLOT)
        } else {
            val childNodes = kids.map { place(it, gen + 1) }
            val n = TreeNode(p, gen, (childNodes.first().y + childNodes.last().y) / 2)
            childNodes.forEach { edges += n to it }
            n
        }
        nodes += node
        return node
    }
    place(root, 0)
    return TreeLayout(nodes, edges, cols, slot * SLOT)
}

@Composable
fun TreeScreen(
    tree: GedcomTree,
    root: Person?,
    onFocus: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = předci, 1 = potomci
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp)) {
            Text("Strom", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 20.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                listOf("Předci", "Potomci").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = mode == i,
                        onClick = { mode = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
        }
        if (root == null) {
            EmptyState(Icons.Outlined.AccountTree, "Strom je prázdný", "Načtěte rodokmen v záložce Více.")
            return@Column
        }
        val layout = remember(tree, root, mode) {
            if (mode == 0) ancestorsLayout(tree, root) else descendantsLayout(tree, root)
        }
        TreeCanvas(layout, root, onFocus, onOpenPerson)
    }
}

@Composable
private fun TreeCanvas(layout: TreeLayout, root: Person, onFocus: (String) -> Unit, onOpen: (String) -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val cs = MaterialTheme.colorScheme

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(cs.surfaceContainerLow)
            .pointerInput(layout) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.35f, 2.5f)
                    val f = newScale / scale
                    // Přiblížení kolem bodu mezi prsty
                    scope.launch {
                        offsetX.snapTo((offsetX.value - centroid.x) * f + centroid.x + pan.x)
                        offsetY.snapTo((offsetY.value - centroid.y) * f + centroid.y + pan.y)
                    }
                    scale = newScale
                }
            },
    ) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val rootNode = layout.nodes.first { it.person.id == root.id }

        suspend fun center(animated: Boolean) = coroutineScope {
            // Celý strom na výšku obrazovky, ale ne menší než 60 % (aby šlo číst).
            val contentH = with(density) { (layout.height + SLOT).dp.toPx() }
            val s = (viewH / contentH).coerceIn(0.6f, 1f)
            val tx = with(density) { 16.dp.toPx() }
            val ty = (viewH / 2 - with(density) { (rootNode.y).dp.toPx() } * s)
                .coerceIn(minOf(0f, viewH - contentH * s), with(density) { 8.dp.toPx() })
            scale = s
            if (animated) {
                launch { offsetX.animateTo(tx) }
                offsetY.animateTo(ty)
            } else {
                offsetX.snapTo(tx); offsetY.snapTo(ty)
            }
        }
        LaunchedEffect(layout) { center(animated = false) }

        val widthDp = layout.cols * (CARD_W + GAP)
        val heightDp = layout.height + SLOT
        val lineColor = cs.outline

        Box(
            Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(widthDp.dp, heightDp.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = offsetX.value
                    translationY = offsetY.value
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            Canvas(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                val stroke = Stroke(width = 2.dp.toPx())
                for ((left, right) in layout.edges) {
                    val x1 = (left.col * (CARD_W + GAP) + CARD_W).dp.toPx()
                    val y1 = left.y.dp.toPx()
                    val x2 = (right.col * (CARD_W + GAP)).dp.toPx()
                    val y2 = right.y.dp.toPx()
                    val mx = (x1 + x2) / 2
                    val path = Path().apply {
                        moveTo(x1, y1)
                        cubicTo(mx, y1, mx, y2, x2, y2)
                    }
                    drawPath(path, lineColor.copy(alpha = 0.6f), style = stroke)
                    drawCircle(lineColor, 3.dp.toPx(), Offset(x2, y2))
                }
            }
            for (n in layout.nodes) {
                TreeCard(
                    n.person,
                    isRoot = n.person.id == root.id,
                    modifier = Modifier.offset((n.col * (CARD_W + GAP)).dp, (n.y - CARD_H / 2).dp),
                    onClick = { if (n.person.id == root.id) onOpen(n.person.id) else onFocus(n.person.id) },
                )
            }
        }

        Text(
            "Klepnutím na osobu ji dáte na začátek stromu, klepnutím na zvýrazněnou otevřete její příběh.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 80.dp, bottom = 16.dp),
        )
        SmallFloatingActionButton(
            onClick = { scope.launch { center(animated = true) } },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Outlined.CenterFocusStrong, "Vycentrovat") }
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
        border = if (isRoot) BorderStroke(2.dp, strong) else BorderStroke(1.dp, cs.outlineVariant),
        shadowElevation = if (isRoot) 4.dp else 1.dp,
        modifier = modifier.size(CARD_W.dp, CARD_H.dp),
    ) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonAvatar(p, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    p.name.given.ifEmpty { "?" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
