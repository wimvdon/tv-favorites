package to.wim.tvfavorites.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import to.wim.tvfavorites.BuildConfig
import to.wim.tvfavorites.logic.Draft
import to.wim.tvfavorites.logic.Airing
import to.wim.tvfavorites.logic.DraftBuilder
import to.wim.tvfavorites.logic.PageMetadata
import to.wim.tvfavorites.logic.PageMetadataParser
import to.wim.tvfavorites.logic.ServiceCatalog
import to.wim.tvfavorites.logic.SharedLink
import to.wim.tvfavorites.logic.Tmdb
import to.wim.tvfavorites.logic.TmdbAiring
import to.wim.tvfavorites.logic.TmdbTitle
import to.wim.tvfavorites.logic.TvMaze
import to.wim.tvfavorites.logic.Titles
import java.util.Locale

/**
 * Looks up the title, description and pictures of a shared link: the page's own preview tags
 * first, TMDB (with the user's token) for a poster and anything missing.
 */
class DetailsFetcher(private val http: OkHttpClient) {
    suspend fun fetch(link: SharedLink, tmdbToken: String): Draft =
        withContext(Dispatchers.IO) {
            val (finalUrl, page) = readPage(link.url)
            val draft = DraftBuilder.build(link, finalUrl, page)
            if (tmdbToken.isBlank() || draft.title.isBlank()) return@withContext draft
            val match = runCatching { searchTmdb(draft.title, tmdbToken) }.getOrNull() ?: return@withContext draft
            val airing = runCatching { airingOf(match.id, match.mediaType, tmdbToken) }.getOrNull()
            DraftBuilder.build(link, finalUrl, page, match).copy(lastAired = airing?.date.orEmpty(), lastEpisode = airing?.episode.orEmpty())
        }

    /** When the latest episode of a TMDB show aired (or a movie came out), or null when TMDB couldn't be asked. */
    suspend fun airing(tmdbId: Long, mediaType: String, tmdbToken: String): TmdbAiring? =
        withContext(Dispatchers.IO) { runCatching { airingOf(tmdbId, mediaType, tmdbToken) }.getOrNull() }

    /** TMDB gives the date; TVmaze, when it knows the show, the exact moment of the episode. */
    private fun airingOf(tmdbId: Long, mediaType: String, token: String): TmdbAiring? {
        val type = if (mediaType == "movie") "movie" else "tv"
        val url = "${Tmdb.BASE_URL}/$type/$tmdbId".toHttpUrl().newBuilder().apply { if (type == "tv") addQueryParameter("append_to_response", "external_ids") }.build()
        val fromTmdb =
            tmdbRequest(url, token).use { response ->
                if (!response.isSuccessful) return null
                Tmdb.parseAiring(response.body.string(), type)
            }
        if (type != "tv" || fromTmdb.imdbId.isBlank()) return fromTmdb
        return Airing.pick(fromTmdb, runCatching { tvMazeAiring(fromTmdb.imdbId) }.getOrNull())
    }

    private fun tvMazeAiring(imdbId: String): TmdbAiring? {
        val show = "${TvMaze.BASE_URL}/lookup/shows".toHttpUrl().newBuilder().addQueryParameter("imdb", imdbId).build()
        val link = plainGet(show)?.let(TvMaze::previousEpisodeLink)?.takeIf { it.startsWith(TvMaze.BASE_URL) } ?: return null
        return plainGet(link.toHttpUrl())?.let(TvMaze::parseEpisode)
    }

    private fun plainGet(url: okhttp3.HttpUrl): String? =
        http
            .newCall(Request.Builder().url(url).header("User-Agent", "tv-favorites/${BuildConfig.VERSION_NAME}").build())
            .execute()
            .use { if (it.isSuccessful) it.body.string() else null }

    private fun tmdbRequest(url: okhttp3.HttpUrl, token: String) =
        http
            .newCall(
                Request
                    .Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${token.trim()}")
                    .header("Accept", "application/json")
                    .header("User-Agent", "tv-favorites/${BuildConfig.VERSION_NAME}")
                    .build(),
            ).execute()

    /** The page after redirects, and what it says about itself (null when it couldn't be read). */
    private fun readPage(url: String): Pair<String, PageMetadata?> {
        val browser = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
        // Sites built as single-page apps often only fill in their preview tags for link-preview bots.
        val preview = "facebookexternalhit/1.1 (compatible; TV Favorites/${BuildConfig.VERSION_NAME})"
        var best: Pair<String, PageMetadata?> = url to null
        for (agent in listOf(browser, preview)) {
            val result = runCatching { get(url, agent) }.getOrNull() ?: continue
            val (finalUrl, html) = result
            val page = PageMetadataParser.parse(html, finalUrl)
            val serviceName = ServiceCatalog.match(finalUrl)?.name ?: ServiceCatalog.nameOf(ServiceCatalog.serviceIdFor(finalUrl))
            best = finalUrl to page
            if (!Titles.isGeneric(Titles.clean(page.title, serviceName, page.siteName), serviceName, page.siteName)) break
        }
        return best
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

    private fun searchTmdb(title: String, token: String): TmdbTitle? {
        val locale = Locale.getDefault()
        val url =
            "${Tmdb.BASE_URL}/search/multi"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("query", title)
                .addQueryParameter("language", "${locale.language}-${locale.country.ifEmpty { locale.language.uppercase(Locale.ROOT) }}")
                .addQueryParameter("include_adult", "false")
                .build()
        tmdbRequest(url, token).use { response ->
            if (!response.isSuccessful) return null
            return Tmdb.bestMatch(Tmdb.parseSearch(response.body.string()), title)
        }
    }

    companion object {
        private const val MAX_PAGE_BYTES = 1_500_000L
    }
}
