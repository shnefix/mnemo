package app.mnemo

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.platform.appDataDir
import kotlinx.coroutines.runBlocking

fun main() {
    val dbPath = System.getenv("MNEMO_DB") ?: (appDataDir() + "/mnemo.db")
    val graph = AppGraph.create(DriverFactory(), dbPath)
    runBlocking { graph.seed() }

    application {
        val state =
            rememberWindowState(
                size = DpSize(1280.dp, 860.dp),
                position = WindowPosition.Aligned(Alignment.Center),
            )
        Window(onCloseRequest = ::exitApplication, title = "Mnemo", state = state) {
            App(graph)
        }
    }
}
