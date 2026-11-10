package to.wim.tvfavorites.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** The latest airing of a show. */
data class LastAired(
    /** The exact moment (ISO 8601 with offset), or a date (yyyy-MM-dd); empty when unknown. */
    val date: String,
    /** "S2E5", or empty. */
    val episode: String,
)

/** A show found on TVmaze. */
data class TvMazeShow(
    val id: Long,
    val name: String,
    /** "Dutch", "English", … as TVmaze names languages. */
    val language: String,
    /** The country codes of its network or streaming service ("BE"). */
    val countries: List<String>,
    /** The show's description, without HTML. */
    val summary: String,
)

/** TVmaze (api.tvmaze.com, free, no key): finds shows by name and when their last episode aired. */
object TvMaze {
    const val BASE_URL = "https://api.tvmaze.com"

    private val json = Json { ignoreUnknownKeys = true }

    /** TVmaze names languages in English. */
    private val languageNames =
        mapOf("nl" to "Dutch", "fr" to "French", "de" to "German", "en" to "English", "it" to "Italian", "es" to "Spanish")

    /** The results of `/search/shows?q=`. */
    fun parseSearch(body: String): List<TvMazeShow> {
        val results = json.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val show = (element as? JsonObject)?.get("show") as? JsonObject ?: return@mapNotNull null
            val countries =
                listOf("network", "webChannel").mapNotNull { key ->
                    ((show[key] as? JsonObject)?.get("country") as? JsonObject)?.string("code")?.takeIf { it.isNotEmpty() }
                }
            TvMazeShow(
                id = show.string("id").toLongOrNull() ?: return@mapNotNull null,
                name = show.string("name"),
                language = show.string("language"),
                countries = countries,
                summary = stripHtml(show.string("summary")),
            )
        }
    }

    /**
     * The result that is clearly the same show as [title] (same name, ignoring case, accents and
     * punctuation). For a regional service ([countries], [languages]) a show from that country,
     * then in that language, wins over a same-named show from elsewhere; with several same-named
     * shows that are all foreign it gives up. Null when none is a clear match.
     */
    fun bestMatch(
        results: List<TvMazeShow>,
        title: String,
        countries: Set<String> = emptySet(),
        languages: Set<String> = emptySet(),
    ): TvMazeShow? {
        val wanted = Titles.normalize(title)
        if (wanted.isEmpty()) return null
        val same = results.filter { Titles.normalize(it.name) == wanted }
        val names = languages.mapNotNull { languageNames[it] }.toSet()
        val local = same.filter { s -> s.countries.any { it in countries } }.ifEmpty { same.filter { it.language in names } }
        return when {
            local.isNotEmpty() -> local.first()
            (countries.isNotEmpty() || languages.isNotEmpty()) && same.size > 1 -> null
            else -> same.firstOrNull()
        }
    }

    /** The latest aired episode from `/shows/<id>?embed=previousepisode`, or null when there is none. */
    fun parsePreviousEpisode(body: String): LastAired? {
        val o = json.parseToJsonElement(body).jsonObject
        val episode = ((o["_embedded"] as? JsonObject)?.get("previousepisode") as? JsonObject) ?: return null
        val date = episode.string("airstamp").ifEmpty { episode.string("airdate") }.ifEmpty { return null }
        val season = episode.string("season").toIntOrNull()
        val number = episode.string("number").toIntOrNull()
        return LastAired(date, if (season != null && number != null) "S${season}E$number" else "")
    }

    fun stripHtml(html: String): String =
        PageMetadataParser.decodeEntities(html.replace(Regex("<[^>]+>"), " ")).replace(Regex("""\s+"""), " ").trim()

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

/** The details of a new favorite, before the user saves it. */
data class Draft(
    val url: String,
    val serviceId: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val tvMazeId: Long = 0,
    val lastAired: String = "",
    val lastEpisode: String = "",
)

object DraftBuilder {
    /**
     * Combines what is known about a shared link: the page's own title, description and
     * picture first, then the title in the shared text, then the link's path.
     */
    fun build(link: SharedLink, finalUrl: String, page: PageMetadata?): Draft {
        // Never a consent wall's address: the page's own link or the shared one.
        val url = ConsentGate.chooseLink(link.url, finalUrl.ifBlank { link.url }, page?.canonicalUrl.orEmpty())
        val service = ServiceCatalog.match(url) ?: ServiceCatalog.match(link.url)
        val serviceId = service?.id ?: ServiceCatalog.serviceIdFor(url)
        val serviceName = service?.name ?: ServiceCatalog.nameOf(serviceId)
        // A page whose title is only the site's name (a sign-in wall or a landing page) has the
        // site's general description and picture too: those say nothing about the show.
        val usefulPage = page?.takeUnless { Titles.isGeneric(Titles.clean(it.title, serviceName, it.siteName), serviceName, it.siteName) }
        return Draft(
            url = url,
            serviceId = serviceId,
            title = titleFor(link, url, page, serviceName),
            description = usefulPage?.description.orEmpty(),
            imageUrl = usefulPage?.imageUrl.orEmpty(),
        )
    }

    /** A TVmaze match adds its id (for air dates), and its description when the page had none. */
    fun withShow(draft: Draft, show: TvMazeShow, aired: LastAired?): Draft =
        draft.copy(
            tvMazeId = show.id,
            description = draft.description.ifBlank { show.summary },
            lastAired = aired?.date.orEmpty(),
            lastEpisode = aired?.episode.orEmpty(),
        )

    /** The show's title: the page's, else the one in the shared text, else the link's path. */
    fun titleFor(link: SharedLink, url: String, page: PageMetadata?, serviceName: String): String {
        val fromPage = page?.let { Titles.clean(it.title, serviceName, it.siteName) }.orEmpty()
        if (!Titles.isGeneric(fromPage, serviceName, page?.siteName.orEmpty())) return fromPage
        val fromShare = Titles.clean(link.titleHint, serviceName)
        if (!Titles.isGeneric(fromShare, serviceName)) return fromShare
        return SharedText.titleFromPath(url)
    }
}
