package cz.mojekoreny.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import com.github.takahirom.roborazzi.captureRoboImage
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

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            MojeKorenyTheme(dark = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.onRoot().captureRoboImage("build/snimky/$name.png")
    }

    @Test fun domu() = shot("1-domu") {
        HomeScreen(tree, "Rodokmen Dvořák", home, listOf("I2", "I3", "I5").mapNotNull(tree::person), today, {}, {}, {})
    }

    @Test fun domuTmavy() = shot("1-domu-tmavy", dark = true) {
        HomeScreen(tree, "Rodokmen Dvořák", home, listOf("I2", "I3", "I5").mapNotNull(tree::person), today, {}, {}, {})
    }

    @Test fun osoby() = shot("2-osoby") { PeopleScreen(tree, home, false) {} }

    @Test fun detail() = shot("3-detail") {
        PersonScreen(tree, tree.person("I2")!!, isHome = false, {}, {}, {}, {})
    }

    @Test fun strom() = shot("4-strom") { TreeScreen(tree, home, {}, {}) }

    @Test fun detailTmavy() = shot("3-detail-tmavy", dark = true) {
        PersonScreen(tree, tree.person("I3")!!, isHome = false, {}, {}, {}, {})
    }

    @Test fun vejir() {
        compose.setContent {
            MojeKorenyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TreeScreen(tree, home, {}, {}) }
            }
        }
        compose.onNodeWithText("Vějíř").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/snimky/4-vejir.png")
    }

    @Test fun vejirTmavy() {
        compose.setContent {
            MojeKorenyTheme(dark = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { TreeScreen(tree, home, {}, {}) }
            }
        }
        compose.onNodeWithText("Vějíř").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/snimky/4-vejir-tmavy.png")
    }

    @Test fun ikona() {
        compose.setContent {
            androidx.compose.foundation.layout.Box(Modifier.size(192.dp).background(Color.White)) {
                AndroidView(factory = { ctx ->
                    android.widget.ImageView(ctx).apply { setImageResource(R.mipmap.ic_launcher) }
                }, modifier = Modifier.fillMaxSize())
            }
        }
        compose.onRoot().captureRoboImage("build/snimky/0-ikona.png")
    }

    @Test fun vice() = shot("5-vice") { MoreScreen(tree, "ukazkovy-rod.ged", "0.2.0", {}, {}, {}) }
}
