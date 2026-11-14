package cz.mojekoreny.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.captureRoboImage
import cz.mojekoreny.app.ui.Tab
import cz.mojekoreny.app.ui.BottomTabs
import cz.mojekoreny.app.ui.components.AppHeader
import cz.mojekoreny.app.ui.screens.HomeScreen
import cz.mojekoreny.app.ui.screens.MoreScreen
import cz.mojekoreny.app.ui.screens.PeopleScreen
import cz.mojekoreny.app.ui.screens.PersonScreen
import cz.mojekoreny.app.ui.screens.TreeScreen
import cz.mojekoreny.app.ui.theme.MojeKorenyTheme
import cz.mojekoreny.gedcom.GedcomReader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Snímky obrazovek bez emulátoru (Robolectric + Roborazzi) na smyšleném rodokmenu.
 * Spuštění: ./gradlew :app:testDebugUnitTest → obrázky v app/build/snimky/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val tree = GedcomReader.read(javaClass.getResourceAsStream("/ukazkovy-rod.ged")!!)
    private val home = tree.person("I1")!!
    private val today = LocalDate.of(2026, 11, 14)
    private val recent = listOf("I2", "I3", "I5").mapNotNull(tree::person)

    /** Obrazovka v rámu skutečné aplikace: hnědá lišta nahoře, záložky dole. */
    private fun frame(
        name: String,
        tab: Tab? = Tab.HOME,
        dark: Boolean = false,
        fontScale: Float = 1f,
        back: Boolean = false,
        before: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                MojeKorenyTheme(dark = dark) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        AppHeader("Rodokmen Dvořák", onBack = if (back) ({}) else null)
                        Box(Modifier.weight(1f)) { content() }
                        if (tab != null) BottomTabs(tab) {}
                    }
                }
            }
        }
        before()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/snimky/$name.png")
    }

    @Test fun domu() = frame("1-domu") { HomeScreen(tree, "Rodokmen Dvořák", home, recent, today, {}, {}, {}) }
    @Test fun domuTmavy() = frame("1-domu-tmavy", dark = true) { HomeScreen(tree, "Rodokmen Dvořák", home, recent, today, {}, {}, {}) }
    @Test fun osoby() = frame("2-osoby", Tab.PEOPLE) { PeopleScreen(tree, home, false) {} }
    @Test fun detail() = frame("3-detail", null, back = true) { PersonScreen(tree, tree.person("I2")!!, false, {}, {}, {}) }
    @Test fun detailTmavy() = frame("3-detail-tmavy", null, dark = true, back = true) { PersonScreen(tree, tree.person("I3")!!, false, {}, {}, {}) }
    @Test fun strom() = frame("4-strom", Tab.TREE) { TreeScreen(tree, home, {}, {}) }
    @Test fun potomci() = frame(
        "4-potomci", Tab.TREE,
        before = { compose.onNodeWithText("Potomci").performClick() },
    ) { TreeScreen(tree, tree.person("I20") ?: home, {}, {}) }
    @Test fun vejir() = frame("4-vejir", Tab.TREE, before = { compose.onNodeWithText("Vějíř").performClick() }) {
        TreeScreen(tree, home, {}, {})
    }
    @Test fun vejirTmavy() = frame("4-vejir-tmavy", Tab.TREE, dark = true, before = { compose.onNodeWithText("Vějíř").performClick() }) {
        TreeScreen(tree, home, {}, {})
    }
    @Test fun vice() = frame("5-vice", Tab.MORE) { MoreScreen(tree, "ukazkovy-rod.ged", "0.4.0", {}, {}, {}) }

    // Zátěž: velké písmo systému (130 %) – nic se nesmí překrývat ani ořezávat.
    @Test fun domuVelkePismo() = frame("9-domu-pismo130", fontScale = 1.3f) { HomeScreen(tree, "Rodokmen Dvořák", home, recent, today, {}, {}, {}) }
    @Test fun detailVelkePismo() = frame("9-detail-pismo130", null, fontScale = 1.3f, back = true) { PersonScreen(tree, tree.person("I2")!!, false, {}, {}, {}) }

    @Test fun ikona() {
        compose.setContent {
            Box(Modifier.size(192.dp).background(Color.White)) {
                AndroidView(factory = { ctx -> android.widget.ImageView(ctx).apply { setImageResource(R.mipmap.ic_launcher) } }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.onRoot().captureRoboImage("build/snimky/0-ikona.png")
    }
}
