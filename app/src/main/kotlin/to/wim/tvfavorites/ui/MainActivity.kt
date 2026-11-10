package to.wim.tvfavorites.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.data.Settings
import to.wim.tvfavorites.logic.SharedLink
import to.wim.tvfavorites.logic.SharedText
import to.wim.tvfavorites.tv.HomeChannels
import to.wim.tvfavorites.ui.phone.PhoneApp
import to.wim.tvfavorites.ui.theme.AppTheme
import to.wim.tvfavorites.ui.tv.TvApp

/**
 * The one screen activity. On a TV it shows the TV screens; on a phone the phone screens, and
 * it receives links shared from streaming apps (Share › TV Favorites).
 */
class MainActivity : ComponentActivity() {
    private var shared by mutableStateOf<SharedLink?>(null)

    /** True when this activity was started by a share, so saving goes back to the streaming app. */
    private var startedByShare = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            shared = sharedLink(intent)
            startedByShare = shared != null
        }
        if (app.isTv) HomeChannels.schedule(this)
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())
            if (app.isTv) {
                TvApp()
            } else {
                AppTheme(mode = settings.theme, dynamicColor = settings.dynamicColor) {
                    PhoneApp(
                        shared = shared,
                        onSharedHandled = { saved ->
                            shared = null
                            if (startedByShare && saved) finish()
                            startedByShare = false
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedLink(intent)?.let {
            shared = it
            startedByShare = false
        }
    }

    private fun sharedLink(intent: Intent?): SharedLink? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val link = SharedText.parse(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
        if (link == null) Toast.makeText(this, R.string.share_no_link, Toast.LENGTH_LONG).show()
        return link
    }
}
