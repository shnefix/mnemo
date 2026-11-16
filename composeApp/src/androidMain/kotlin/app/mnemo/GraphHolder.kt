package app.mnemo

import android.content.Context
import app.mnemo.data.db.DriverFactory
import app.mnemo.di.AppGraph
import app.mnemo.platform.appContext
import app.mnemo.platform.appDataDir
import kotlinx.coroutines.runBlocking

/** The process-wide [AppGraph], shared by the activity and the reminder receiver. */
object GraphHolder {
    @Volatile private var graph: AppGraph? = null

    /** The graph if it is already built, without blocking. */
    fun peek(): AppGraph? = graph

    @Synchronized
    fun get(context: Context): AppGraph =
        graph ?: run {
            appContext = context.applicationContext
            AppGraph.create(DriverFactory(appContext), appDataDir() + "/mnemo.db").also {
                runBlocking { it.seed() }
                graph = it
            }
        }
}
