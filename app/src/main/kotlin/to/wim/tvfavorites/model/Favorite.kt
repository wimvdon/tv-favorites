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
    /** Portrait poster (TMDB), or empty. */
    val posterUrl: String = "",
    /** Landscape picture (the page's preview image or a TMDB backdrop), or empty. */
    val imageUrl: String = "",
    val position: Long = 0,
    val addedAt: Long = 0,
    /** The matching TMDB title ("tv" or "movie" and its id), or 0 when there is no clear match. */
    val tmdbId: Long = 0,
    val tmdbType: String = "",
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
    /**
     * The TMDB token shared with the paired devices, so the TV can look up air dates without
     * the phone. Only devices in the household can read it.
     */
    val tmdbToken: String = "",
)
