package to.wim.tvfavorites.tv

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import to.wim.tvfavorites.app
import to.wim.tvfavorites.model.Library

/** Updates the home screen rows from the shared list while the app is closed. */
class HomeChannelsJob : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job =
            app.scope.launch {
                var latest: Library? = null
                // Firestore answers from its copy on the TV first, then with the server's list.
                withTimeoutOrNull(20_000) { app.library.library.filterNotNull().collect { latest = it } }
                if (latest != null) {
                    // The TV keeps its air dates up to date itself, without the phone.
                    app.library.refreshAiring(app.details)
                    withTimeoutOrNull(10_000) { app.library.library.filterNotNull().collect { latest = it } }
                }
                latest?.let { HomeChannels(this@HomeChannelsJob).sync(it.favorites, it.groupOrder) }
                jobFinished(params, false)
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }
}

/** Android TV asks a newly installed app to fill its home screen rows. */
class InitializeProgramsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        HomeChannels.schedule(context)
        context.app.scope.launch {
            val library = withTimeoutOrNull(8_000) { context.app.library.library.filterNotNull().first() }
            library?.let { HomeChannels(context).sync(it.favorites, it.groupOrder) }
            pending.finish()
        }
    }
}
