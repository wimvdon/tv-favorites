package to.wim.tvfavorites.model

import kotlinx.serialization.Serializable

/**
 * One saved show or movie. [serviceId] is a [to.wim.tvfavorites.logic.StreamingService] id, or
 * the link's host for services the app doesn't know. Lower [position] comes first.
 */
@Serializable
data class Favorite(
    val id: String,
    val title: String,
    val url: String,
    val serviceId: String,
    val description: String = "",
    /** The service's own picture (landscape, from the page's preview tags), or empty. */
    val imageUrl: String = "",
    val position: Long = 0,
    val addedAt: Long = 0,
    /** The matching TVmaze show, or 0 when none is known (yet). */
    val tvMazeId: Long = 0,
    /** When the latest episode aired (a movie: its release), as yyyy-MM-dd, or empty. */
    val lastAired: String = "",
    /** That episode as "S2E5", or empty. */
    val lastEpisode: String = "",
    /** When [lastAired] was last looked up (milliseconds), so it is refreshed once a day at most. */
    val checkedAt: Long = 0,
)

/** Hand-made changes to how a service is opened on the TV (Settings › Streaming apps). */
@Serializable
data class ServiceOverride(
    /** The Android TV app to open, or empty to find it automatically. */
    val packageName: String = "",
    /** A link pattern with {url}, {id}, {path} or {query}, or empty to use the shared link. */
    val linkTemplate: String = "",
) {
    val isEmpty: Boolean get() = packageName.isBlank() && linkTemplate.isBlank()
}

/** Everything the app stores about the list: the favorites and the per-service changes. */
@Serializable
data class Library(
    val favorites: List<Favorite> = emptyList(),
    val overrides: Map<String, ServiceOverride> = emptyMap(),
    /** The user's order of the services (groups on the phone, rows on the TV); others follow. */
    val groupOrder: List<String> = emptyList(),
)
