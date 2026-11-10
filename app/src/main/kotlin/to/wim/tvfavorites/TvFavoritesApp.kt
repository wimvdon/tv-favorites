package to.wim.tvfavorites

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import okhttp3.Cache
import okhttp3.OkHttpClient
import to.wim.tvfavorites.data.DetailsFetcher
import to.wim.tvfavorites.data.LibraryRepository
import to.wim.tvfavorites.data.LocalLibraryStore
import to.wim.tvfavorites.data.SettingsStore
import java.io.File
import java.util.concurrent.TimeUnit

/** Creates the shared objects once. Screens reach them through [Context.app]. */
class TvFavoritesApp : Application(), SingletonImageLoader.Factory {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .cache(Cache(File(cacheDir, "http"), 50L * 1024 * 1024))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val settings by lazy { SettingsStore(this) }

    val details by lazy { DetailsFetcher(http) }

    private val firebase: FirebaseApp? by lazy {
        if (BuildConfig.FIREBASE_API_KEY.isBlank()) return@lazy null
        runCatching {
            FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(
                this,
                FirebaseOptions
                    .Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .build(),
            )
        }.onFailure { Log.w("TvFavoritesApp", "Firebase could not start", it) }.getOrNull()
    }

    val library by lazy {
        LibraryRepository(
            scope = scope,
            settings = settings,
            local = LocalLibraryStore(File(filesDir, "favorites.json")),
            firestore = { firebase?.let { FirebaseFirestore.getInstance(it) } },
            auth = { firebase?.let { FirebaseAuth.getInstance(it) } },
        )
    }

    /** True on an Android TV (or any device without a touch screen that runs the TV launcher). */
    val isTv: Boolean by lazy { isTelevision(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()

    companion object {
        fun isTelevision(context: Context): Boolean {
            val uiMode = context.getSystemService(UiModeManager::class.java)?.currentModeType
            return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        }
    }
}

val Context.app: TvFavoritesApp get() = applicationContext as TvFavoritesApp
