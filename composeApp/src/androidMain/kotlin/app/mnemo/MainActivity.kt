package app.mnemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.platform.appContext
import app.mnemo.platform.appDataDir
import app.mnemo.ui.theme.ThemeState
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = applicationContext
        val graph = graphHolder ?: AppGraph.create(DriverFactory(applicationContext), appDataDir() + "/mnemo.db").also {
            runBlocking { it.seed() }
            graphHolder = it
        }
        setContent { App(graph, ThemeState.fromPrefs()) }
    }

    companion object {
        private var graphHolder: AppGraph? = null
    }
}
