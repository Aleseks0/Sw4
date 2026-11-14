package cz.mojekoreny.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cz.mojekoreny.app.ui.MojeKorenyApp
import cz.mojekoreny.app.ui.theme.MojeKorenyTheme

class MainActivity : ComponentActivity() {
    private val vm: TreeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MojeKorenyTheme {
                MojeKorenyApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Soubor .ged otevřený z jiné aplikace. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let(vm::import)
    }
}
