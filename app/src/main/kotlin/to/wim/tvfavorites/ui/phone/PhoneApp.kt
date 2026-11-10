package to.wim.tvfavorites.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.wim.tvfavorites.app
import to.wim.tvfavorites.data.DeviceState
import to.wim.tvfavorites.data.Settings
import to.wim.tvfavorites.logic.SharedLink
import to.wim.tvfavorites.ui.theme.Motion
import to.wim.tvfavorites.ui.theme.rememberReducedMotion

/** The phone's pages. */
sealed interface Route {
    data object Home : Route

    /** A new favorite from a shared or pasted link. */
    data class Add(val link: SharedLink) : Route

    data class Edit(val id: String) : Route

    data object Settings : Route

    data object Services : Route
}

/**
 * The phone app: the list, adding and editing favorites, and settings. [shared] is a link that
 * came in through Share; [onSharedHandled] is told whether it was saved.
 */
@Composable
fun PhoneApp(
    shared: SharedLink?,
    onSharedHandled: (saved: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val settings by app.settings.settings.collectAsStateWithLifecycle(Settings())
    val device by app.settings.device.collectAsStateWithLifecycle(DeviceState())
    val library by app.library.library.collectAsStateWithLifecycle()

    fun push(route: Route) {
        stack.add(route)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    LaunchedEffect(shared) {
        if (shared != null) {
            stack.removeAll { it is Route.Add }
            push(Route.Add(shared))
        }
    }
    // The last aired episode of shows matched on TMDB, at most once a day each.
    LaunchedEffect(Unit) {
        app.library.library.collect { lib -> if (lib != null) app.library.refreshAiring(app.details, app.library.tmdbToken()) }
    }

    BackHandler(enabled = stack.size > 1) {
        val top = stack.last()
        pop()
        if (top is Route.Add) onSharedHandled(false)
    }

    val reduced = rememberReducedMotion()
    val offset = with(LocalDensity.current) { 30.dp.roundToPx() }
    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = { Motion.sharedAxisX(forward = stack.contains(initialState), offsetPx = if (reduced) 0 else offset) },
        label = "screens",
    ) { route ->
        when (route) {
            Route.Home ->
                HomeScreen(
                    settings = settings,
                    onAdd = { push(Route.Add(it)) },
                    onEdit = { push(Route.Edit(it.id)) },
                    onSettings = { push(Route.Settings) },
                )
            is Route.Add ->
                AddScreen(
                    link = route.link,
                    tmdbToken = device.tmdbToken.ifBlank { library?.tmdbToken.orEmpty() },
                    onBack = {
                        pop()
                        onSharedHandled(false)
                    },
                    onSaved = {
                        pop()
                        onSharedHandled(true)
                    },
                )
            is Route.Edit -> EditScreen(id = route.id, tmdbToken = device.tmdbToken.ifBlank { library?.tmdbToken.orEmpty() }, onBack = ::pop)
            Route.Settings ->
                SettingsScreen(
                    settings = settings,
                    device = device,
                    onBack = ::pop,
                    onServices = { push(Route.Services) },
                )
            Route.Services -> ServicesScreen(onBack = ::pop)
        }
    }
}
