package to.wim.tvfavorites.logic

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** What a streaming app's share button sends: usually a sentence with a link in it. */
data class SharedLink(
    val url: String,
    /** A title found in the shared text itself (between quotes, or the text around the link), or empty. */
    val titleHint: String,
)

object SharedText {
    private val urlPattern = Regex("""https?://[^\s<>"'“”‘’«»]+""", RegexOption.IGNORE_CASE)
    private val quoted = Regex("""["“”'‘’«»„]\s*([^"“”‘’«»„]{2,120}?)\s*["“”'‘’«»]""")

    /** Query parameters that only track where a link was shared from. */
    private val trackingParams =
        setOf("fbclid", "gclid", "igshid", "si", "ref", "ref_", "share", "shared", "trackid", "trkid", "feature")

    /** The first web link in [text] (without tracking parameters), or null. */
    fun parse(text: String?, subject: String? = null): SharedLink? {
        if (text.isNullOrBlank()) return null
        val match = urlPattern.find(text) ?: return null
        val url = cleanUrl(match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']'))
        val hint =
            quoted.find(text.replace(match.value, " "))?.groupValues?.get(1)?.trim()
                ?: subject?.trim()?.takeIf { it.isNotEmpty() && urlPattern.find(it) == null }
                ?: text.replace(match.value, " ").trim().takeIf { it.length in 2..80 && !it.contains('\n') }
                ?: ""
        return SharedLink(url, hint)
    }

    fun cleanUrl(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val query =
            uri.rawQuery
                ?.split('&')
                ?.filter { part ->
                    val name = part.substringBefore('=').lowercase(Locale.ROOT)
                    name.isNotEmpty() && !name.startsWith("utm_") && name !in trackingParams
                }?.joinToString("&")
                ?.takeIf { it.isNotEmpty() }
        val base = url.substringBefore('#').substringBefore('?')
        return if (query != null) "$base?$query" else base
    }

    /**
     * A readable title from the link itself, as a last resort: the last meaningful part of the
     * path ("/a-z/de-mol/" gives "De Mol"). Numbers-only and very short parts are skipped.
     */
    fun titleFromPath(url: String): String {
        val path = runCatching { URI(url).rawPath }.getOrNull().orEmpty()
        val skip = setOf("watch", "title", "detail", "series", "serie", "show", "shows", "movie", "movies", "film", "films", "video", "videos", "a-z", "play", "afspelen", "programmas", "program", "programs", "vrtmax", "vtmgo", "nl", "fr", "en", "de", "be", "browse", "entity", "details")
        val segment =
            path
                .split('/')
                .map { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                .map { it.substringBeforeLast('.') }
                .lastOrNull { part ->
                    part.length > 2 &&
                        part.lowercase(Locale.ROOT) !in skip &&
                        part.any { it.isLetter() } &&
                        !looksLikeId(part)
                } ?: return ""
        return segment
            .split('-', '_', '+', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
    }

    /** Ids like "tt1234567", "80100172", "b0f3c1a2-..." or "GYVnMrAJbZ8KpwgEAAAAJ". */
    private fun looksLikeId(part: String): Boolean {
        val digits = part.count { it.isDigit() }
        if (digits >= part.length / 2) return true
        if (!part.contains('-') && part.length >= 12 && part.any { it.isUpperCase() } && part.any { it.isLowerCase() }) return true
        return Regex("""[0-9a-f]{8}-[0-9a-f]{4}-.*""", RegexOption.IGNORE_CASE).matches(part)
    }
}
