package to.wim.tvfavorites.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import to.wim.tvfavorites.BuildConfig
import to.wim.tvfavorites.logic.Draft
import to.wim.tvfavorites.logic.ConsentGate
import to.wim.tvfavorites.logic.DraftBuilder
import to.wim.tvfavorites.logic.PageMetadata
import to.wim.tvfavorites.logic.PageMetadataParser
import to.wim.tvfavorites.logic.ServiceCatalog
import to.wim.tvfavorites.logic.LastAired
import to.wim.tvfavorites.logic.SharedLink
import to.wim.tvfavorites.logic.TvMaze
import to.wim.tvfavorites.logic.TvMazeShow
import to.wim.tvfavorites.logic.Titles
import java.util.Locale

/**
 * Looks up a shared link: the title, description and picture the service's own page gives
 * (its preview tags), and the show on TVmaze for when its latest episode aired.
 */
class DetailsFetcher(private val http: OkHttpClient) {
    suspend fun fetch(link: SharedLink): Draft =
        withContext(Dispatchers.IO) {
            val (finalUrl, page) = readPage(link.url)
            val draft = DraftBuilder.build(link, finalUrl, page)
            if (draft.title.isBlank()) return@withContext draft
            val service = ServiceCatalog.byId(draft.serviceId)
            val show = runCatching { findShow(draft.title, service?.countries.orEmpty(), service?.languages.orEmpty()) }.getOrNull()
                ?: return@withContext draft
            DraftBuilder.withShow(draft, show, runCatching { lastAired(show.id) }.getOrNull())
        }

    /** The show on TVmaze for [title] (null when there is no clear match or TVmaze couldn't be asked). */
    suspend fun show(title: String, serviceId: String): TvMazeShow? =
        withContext(Dispatchers.IO) {
            val service = ServiceCatalog.byId(serviceId)
            runCatching { findShow(title, service?.countries.orEmpty(), service?.languages.orEmpty()) }.getOrNull()
        }

    /** When the latest episode of TVmaze show [id] aired; null when TVmaze couldn't be asked or knows none. */
    suspend fun airing(id: Long): LastAired? = withContext(Dispatchers.IO) { runCatching { lastAired(id) }.getOrNull() }

    private fun findShow(
        title: String,
        countries: Set<String>,
        languages: Set<String>,
    ): TvMazeShow? {
        val url = "${TvMaze.BASE_URL}/search/shows".toHttpUrl().newBuilder().addQueryParameter("q", title).build()
        return plainGet(url)?.let { TvMaze.bestMatch(TvMaze.parseSearch(it), title, countries, languages) }
    }

    private fun lastAired(id: Long): LastAired? {
        val url = "${TvMaze.BASE_URL}/shows/$id".toHttpUrl().newBuilder().addQueryParameter("embed", "previousepisode").build()
        return plainGet(url)?.let(TvMaze::parsePreviousEpisode)
    }

    private fun plainGet(url: okhttp3.HttpUrl): String? =
        http
            .newCall(Request.Builder().url(url).header("User-Agent", "tv-favorites/${BuildConfig.VERSION_NAME}").build())
            .execute()
            .use { if (it.isSuccessful) it.body.string() else null }

    /**
     * The page after redirects, and what it says about itself (null when it couldn't be read, or
     * only a cookie or privacy wall answered). Link-preview bots are asked first: sites fill in
     * their preview tags for them (that is what the tags are for) and usually let them past their
     * consent walls. The app never accepts consent itself; past a wall it only tries the page the
     * wall would return to.
     */
    private fun readPage(url: String): Pair<String, PageMetadata?> {
        val preview = "facebookexternalhit/1.1 (compatible; TV Favorites/${BuildConfig.VERSION_NAME})"
        val browser = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
        var best: Pair<String, PageMetadata?> = url to null
        for (agent in listOf(preview, browser)) {
            val (finalUrl, page) = readOne(url, agent) ?: continue
            val usable =
                if (ConsentGate.isGate(finalUrl, page)) {
                    ConsentGate.returnUrl(finalUrl, url)?.let { back -> readOne(back, agent) }?.takeUnless { (f, p) -> ConsentGate.isGate(f, p) }
                } else {
                    finalUrl to page
                } ?: continue
            best = usable
            val (usableUrl, usablePage) = usable
            val serviceName = ServiceCatalog.match(usableUrl)?.name ?: ServiceCatalog.nameOf(ServiceCatalog.serviceIdFor(usableUrl))
            if (usablePage != null && !Titles.isGeneric(Titles.clean(usablePage.title, serviceName, usablePage.siteName), serviceName, usablePage.siteName)) break
        }
        return best
    }

    private fun readOne(url: String, agent: String): Pair<String, PageMetadata?>? {
        val (finalUrl, html) = runCatching { get(url, agent) }.getOrNull() ?: return null
        return finalUrl to PageMetadataParser.parse(html, finalUrl)
    }

    private fun get(url: String, agent: String): Pair<String, String>? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", agent)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", Locale.getDefault().toLanguageTag() + ",en;q=0.5")
                .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.source()
            body.request(MAX_PAGE_BYTES)
            val bytes = body.buffer.readByteArray(minOf(body.buffer.size, MAX_PAGE_BYTES))
            return response.request.url.toString() to String(bytes, Charsets.UTF_8)
        }
    }

    companion object {
        private const val MAX_PAGE_BYTES = 1_500_000L
    }
}
