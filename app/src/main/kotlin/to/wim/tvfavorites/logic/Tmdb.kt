package to.wim.tvfavorites.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** A show or movie found on TMDB. */
data class TmdbTitle(
    val id: Long,
    val mediaType: String,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterPath: String,
    val backdropPath: String,
    val year: String,
) {
    val posterUrl: String get() = if (posterPath.isBlank()) "" else "https://image.tmdb.org/t/p/w500$posterPath"
    val backdropUrl: String get() = if (backdropPath.isBlank()) "" else "https://image.tmdb.org/t/p/w780$backdropPath"
}

/** The latest airing of a TMDB title. */
data class TmdbAiring(
    /**
     * yyyy-MM-dd from TMDB, or the exact moment (ISO 8601 with offset, from TVmaze) when
     * known; empty when unknown.
     */
    val date: String,
    /** "S2E5" for an episode, empty for a movie. */
    val episode: String,
    /** The show's IMDb id ("tt0903747"), to find its air times on TVmaze, or empty. */
    val imdbId: String = "",
)

object Tmdb {
    const val BASE_URL = "https://api.themoviedb.org/3"

    private val json = Json { ignoreUnknownKeys = true }

    /** The results of `/search/multi`, shows and movies only. */
    fun parseSearch(body: String): List<TmdbTitle> {
        val root = json.parseToJsonElement(body).jsonObject
        val results = root["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val type = o.string("media_type")
            if (type != "tv" && type != "movie") return@mapNotNull null
            TmdbTitle(
                id = o.string("id").toLongOrNull() ?: return@mapNotNull null,
                mediaType = type,
                title = o.string("name").ifEmpty { o.string("title") },
                originalTitle = o.string("original_name").ifEmpty { o.string("original_title") },
                overview = o.string("overview"),
                posterPath = o.string("poster_path"),
                backdropPath = o.string("backdrop_path"),
                year = o.string("first_air_date").ifEmpty { o.string("release_date") }.take(4),
            )
        }
    }

    /**
     * The latest airing from `/tv/<id>` (the last episode that aired) or `/movie/<id>` (the
     * release date).
     */
    fun parseAiring(body: String, mediaType: String): TmdbAiring {
        val o = json.parseToJsonElement(body).jsonObject
        if (mediaType == "movie") return TmdbAiring(o.string("release_date"), "")
        // With append_to_response=external_ids.
        val imdb = (o["external_ids"] as? JsonObject)?.string("imdb_id").orEmpty()
        val last = o["last_episode_to_air"] as? JsonObject
        if (last != null) {
            val season = last.string("season_number").toIntOrNull()
            val number = last.string("episode_number").toIntOrNull()
            val episode = if (season != null && number != null) "S${season}E$number" else ""
            return TmdbAiring(last.string("air_date").ifEmpty { o.string("last_air_date") }, episode, imdb)
        }
        return TmdbAiring(o.string("last_air_date"), "", imdb)
    }

    /**
     * The result that is clearly the same title as [title] (same name, ignoring case, accents
     * and punctuation), preferring one with a poster. Null when none is a clear match, so a
     * wrong poster is never filled in.
     */
    fun bestMatch(results: List<TmdbTitle>, title: String): TmdbTitle? {
        val wanted = Titles.normalize(title)
        if (wanted.isEmpty()) return null
        val same = results.filter { Titles.normalize(it.title) == wanted || Titles.normalize(it.originalTitle) == wanted }
        return same.firstOrNull { it.posterPath.isNotBlank() } ?: same.firstOrNull()
    }

    private fun JsonObject.string(key: String): String = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()

    /** Accepts the long "API Read Access Token" only (the short v3 key won't work as a bearer token). */
    fun looksLikeToken(token: String): Boolean = token.trim().let { it.length > 100 && it.count { c -> c == '.' } == 2 }
}

/** TVmaze (api.tvmaze.com, no key): the exact air time of a show's latest episode. */
object TvMaze {
    const val BASE_URL = "https://api.tvmaze.com"

    private val json = Json { ignoreUnknownKeys = true }

    /** The link to the show's latest aired episode, from `/lookup/shows?imdb=`, or empty. */
    fun previousEpisodeLink(showBody: String): String {
        val o = json.parseToJsonElement(showBody).jsonObject
        val links = o["_links"] as? JsonObject ?: return ""
        val previous = links["previousepisode"] as? JsonObject ?: return ""
        return (previous["href"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty().replace("http://", "https://")
    }

    /** The episode's moment ("airstamp", with offset) and number, or null without an airstamp. */
    fun parseEpisode(body: String): TmdbAiring? {
        val o = json.parseToJsonElement(body).jsonObject
        fun s(key: String) = (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull.orEmpty()
        val stamp = s("airstamp").ifEmpty { return null }
        val season = s("season").toIntOrNull()
        val number = s("number").toIntOrNull()
        return TmdbAiring(stamp, if (season != null && number != null) "S${season}E$number" else "")
    }
}

/** The details of a new favorite, before the user saves it. */
data class Draft(
    val url: String,
    val serviceId: String,
    val title: String,
    val description: String,
    val posterUrl: String,
    val imageUrl: String,
    val tmdbId: Long = 0,
    val tmdbType: String = "",
    val lastAired: String = "",
    val lastEpisode: String = "",
)

object DraftBuilder {
    /**
     * Combines what is known about a shared link: the page's own title, description and
     * picture first, then the title in the shared text, then the link's path. A TMDB [match]
     * adds a poster and fills an empty description or picture.
     */
    fun build(link: SharedLink, finalUrl: String, page: PageMetadata?, match: TmdbTitle? = null): Draft {
        val url = finalUrl.ifBlank { link.url }
        val service = ServiceCatalog.match(url) ?: ServiceCatalog.match(link.url)
        val serviceId = service?.id ?: ServiceCatalog.serviceIdFor(url)
        val serviceName = service?.name ?: ServiceCatalog.nameOf(serviceId)
        val title = titleFor(link, url, page, serviceName)
        // A page whose title is only the site's name (a sign-in wall or a landing page) has the
        // site's general description and picture too: those say nothing about the show.
        val usefulPage = page?.takeUnless { Titles.isGeneric(Titles.clean(it.title, serviceName, it.siteName), serviceName, it.siteName) }
        return Draft(
            url = url,
            serviceId = serviceId,
            title = title.ifBlank { match?.title.orEmpty() },
            description = usefulPage?.description.orEmpty().ifBlank { match?.overview.orEmpty() },
            posterUrl = match?.posterUrl.orEmpty(),
            imageUrl = usefulPage?.imageUrl.orEmpty().ifBlank { match?.backdropUrl.orEmpty() },
            tmdbId = match?.id ?: 0,
            tmdbType = match?.mediaType.orEmpty(),
        )
    }

    /** The title to search TMDB with, before any match is known. */
    fun titleFor(link: SharedLink, url: String, page: PageMetadata?, serviceName: String): String {
        val fromPage = page?.let { Titles.clean(it.title, serviceName, it.siteName) }.orEmpty()
        if (!Titles.isGeneric(fromPage, serviceName, page?.siteName.orEmpty())) return fromPage
        val fromShare = Titles.clean(link.titleHint, serviceName)
        if (!Titles.isGeneric(fromShare, serviceName)) return fromShare
        return SharedText.titleFromPath(url)
    }
}
