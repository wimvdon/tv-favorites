package to.wim.tvfavorites.launch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import to.wim.tvfavorites.logic.DeepLinks
import to.wim.tvfavorites.logic.InstalledApp
import to.wim.tvfavorites.logic.LaunchAttempt
import to.wim.tvfavorites.model.ServiceOverride

/** How opening a favorite went, for the message the screen shows. */
sealed interface LaunchResult {
    data object Opened : LaunchResult

    /** Only the app's start screen opened: the app didn't accept the link. */
    data class OpenedAppOnly(val appName: String) : LaunchResult

    /** No app for this service is installed (and nothing else took the link). */
    data object NoApp : LaunchResult
}

/** Opens favorites in the streaming apps, following [DeepLinks.plan]. */
class Launcher(private val context: Context) {
    private val pm: PackageManager get() = context.packageManager

    /** The apps on the TV's launcher (Android TV) or the phone's launcher. */
    fun installedApps(): List<InstalledApp> {
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        return categories
            .flatMap { category ->
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            }.map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appFor(serviceId: String, override: ServiceOverride?): InstalledApp? = DeepLinks.findApp(serviceId, override, installedApps())

    /** On the phone: the link goes to whichever app Android picks for it (the service's app when installed). */
    fun openOnPhone(url: String): LaunchResult =
        if (start(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            LaunchResult.Opened
        } else {
            LaunchResult.NoApp
        }

    fun open(url: String, serviceId: String, override: ServiceOverride?): LaunchResult {
        val app = appFor(serviceId, override)
        for (attempt in DeepLinks.plan(url, serviceId, app?.packageName, override)) {
            when (attempt) {
                is LaunchAttempt.View -> {
                    val intent = Intent(Intent.ACTION_VIEW, attempt.uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // With a package the app's own link filters decide; without one, only apps
                    // that take links from a browser (as on a phone) qualify.
                    if (attempt.packageName != null) intent.setPackage(attempt.packageName) else intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    // Netflix's TV app wants to know a link came from outside ("source" 30).
                    if (attempt.packageName == "com.netflix.ninja") intent.putExtra("source", "30")
                    if (start(intent)) return LaunchResult.Opened
                }
                is LaunchAttempt.OpenApp -> {
                    val intent =
                        pm.getLeanbackLaunchIntentForPackage(attempt.packageName)
                            ?: pm.getLaunchIntentForPackage(attempt.packageName)
                    if (intent != null && start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                        return LaunchResult.OpenedAppOnly(app?.label ?: attempt.packageName)
                    }
                }
            }
        }
        return LaunchResult.NoApp
    }

    private fun start(intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
