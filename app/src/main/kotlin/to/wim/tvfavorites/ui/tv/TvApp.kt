package to.wim.tvfavorites.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.delay
import to.wim.tvfavorites.app
import to.wim.tvfavorites.data.Connection
import to.wim.tvfavorites.tv.HomeChannels
import to.wim.tvfavorites.ui.theme.AppTheme
import to.wim.tvfavorites.data.ThemeMode

/** The TV's pages. */
enum class TvRoute { Home, Pair, Settings, Services, Rows, RowOrder, Nuvio }

/**
 * The TV app: works on its own once set up. It keeps the shared list, the home screen rows and
 * the air dates up to date itself; the phone is only needed to add favorites.
 */
@Composable
fun TvApp() {
    val app = LocalContext.current.app
    val context = LocalContext.current
    var route by rememberSaveable { mutableStateOf(TvRoute.Home) }
    val connection by app.library.connection.collectAsStateWithLifecycle()
    val library by app.library.library.collectAsStateWithLifecycle()

    // A household to show in the QR code, from the first start on (needs the network once).
    LaunchedEffect(connection) {
        if (connection == Connection.Local) {
            while (runCatching { app.library.ensureHousehold() }.getOrNull() == null) delay(30_000)
        }
    }
    // Home screen rows and air dates follow every change of the list.
    LaunchedEffect(library) {
        val lib = library ?: return@LaunchedEffect
        HomeChannels(context).sync(lib.favorites, lib.groupOrder)
        app.library.refreshAiring(app.details)
    }

    BackHandler(enabled = route != TvRoute.Home) {
        route = if (route in setOf(TvRoute.Services, TvRoute.Rows, TvRoute.RowOrder, TvRoute.Nuvio)) TvRoute.Settings else TvRoute.Home
    }

    // Material 3 for dialogs and text fields, TV Material for focusable cards and buttons; TVs are dark.
    AppTheme(mode = ThemeMode.Dark) {
        val m3 = androidx.compose.material3.MaterialTheme.colorScheme
        MaterialTheme(
            colorScheme =
                darkColorScheme(
                    primary = m3.primary,
                    onPrimary = m3.onPrimary,
                    primaryContainer = m3.primaryContainer,
                    onPrimaryContainer = m3.onPrimaryContainer,
                    secondary = m3.secondary,
                    onSecondary = m3.onSecondary,
                    secondaryContainer = m3.secondaryContainer,
                    onSecondaryContainer = m3.onSecondaryContainer,
                    tertiary = m3.tertiary,
                    onTertiary = m3.onTertiary,
                    background = m3.background,
                    onBackground = m3.onBackground,
                    surface = m3.surface,
                    onSurface = m3.onSurface,
                    surfaceVariant = m3.surfaceContainerHigh,
                    onSurfaceVariant = m3.onSurfaceVariant,
                    inverseSurface = m3.inverseSurface,
                    inverseOnSurface = m3.inverseOnSurface,
                    error = m3.error,
                    onError = m3.onError,
                    border = m3.outline,
                    borderVariant = m3.outlineVariant,
                    scrim = Color.Black,
                ),
        ) {
            // TV Material's text is black unless something sets the colour: every TV screen and
            // dialog gets the light text of the dark theme (both libraries' Text).
            CompositionLocalProvider(
                androidx.tv.material3.LocalContentColor provides m3.onBackground,
                androidx.compose.material3.LocalContentColor provides m3.onBackground,
            ) {
                when (route) {
                    TvRoute.Home ->
                        TvHomeScreen(
                            onPair = { route = TvRoute.Pair },
                            onSettings = { route = TvRoute.Settings },
                        )
                    TvRoute.Pair -> TvPairScreen(onDone = { route = TvRoute.Home })
                    TvRoute.Settings ->
                        TvSettingsScreen(
                            onPair = { route = TvRoute.Pair },
                            onServices = { route = TvRoute.Services },
                            onRows = { route = TvRoute.Rows },
                            onRowOrder = { route = TvRoute.RowOrder },
                            onNuvio = { route = TvRoute.Nuvio },
                        )
                    TvRoute.Services -> TvServicesScreen()
                    TvRoute.Rows -> TvRowsScreen()
                    TvRoute.RowOrder -> TvRowOrderScreen()
                    TvRoute.Nuvio -> TvNuvioScreen(onDone = { route = TvRoute.Settings })
                }
            }
        }
    }
}
