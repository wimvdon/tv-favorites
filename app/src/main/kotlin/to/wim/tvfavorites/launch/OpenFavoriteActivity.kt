package to.wim.tvfavorites.launch

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.logic.ServiceCatalog

/**
 * Opens a favorite from a tile on the TV's home screen
 * (`tvfavorites://open?id=…&service=…&url=…`), then closes. Shows nothing itself.
 */
class OpenFavoriteActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        val url = data?.getQueryParameter("url")
        if (data?.scheme != "tvfavorites" || url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
            finish()
            return
        }
        val serviceId = data.getQueryParameter("service") ?: ServiceCatalog.serviceIdFor(url)
        app.scope.launch {
            // The service's settings (a picked app, a link pattern) come with the list.
            val library = withTimeoutOrNull(3_000) { app.library.library.filterNotNull().first() }
            val result = Launcher(this@OpenFavoriteActivity).open(url, serviceId, library?.overrides?.get(serviceId))
            showResult(applicationContext, result, ServiceCatalog.nameOf(serviceId))
            finish()
        }
    }

    companion object {
        /** A short message when the show itself couldn't be opened. */
        fun showResult(context: Context, result: LaunchResult, serviceName: String) {
            val text =
                when (result) {
                    LaunchResult.Opened -> return
                    is LaunchResult.OpenedAppOnly -> context.getString(R.string.launch_opened_app_only, result.appName)
                    LaunchResult.NoApp -> context.getString(R.string.launch_no_app, serviceName)
                }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}
