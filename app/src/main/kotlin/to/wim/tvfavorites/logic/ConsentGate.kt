package to.wim.tvfavorites.logic

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/**
 * Cookie and privacy walls ("consent gates") that sites put in front of their pages for
 * visitors without a consent cookie. The app never accepts consent on the user's behalf: it
 * recognises such a page, ignores what it says, and keeps the link that was shared (or the one
 * the wall would send back to). Nothing here is specific to one site.
 */
object ConsentGate {
    /** Words in a host or path that consent walls use. */
    private val urlWords =
        listOf(
            "consent", "privacy-gate", "privacygate", "privacy_gate", "cookiewall", "cookie-wall", "cookie_wall",
            "cookies-wall", "cookieconsent", "cookie-consent", "gdpr", "/privacy", "myprivacy", "cmp.", "/cmp/", "/cookies",
            "toestemming", "einwilligung", "consentement", "consentimiento", "consenso",
        )

    /** Words in a page title that consent walls use, in the app's languages. */
    private val titleWords =
        Regex(
            """\b(privacy|cookies?|consent|gdpr|toestemming|privacyinstellingen|datenschutz|einwilligung|""" +
                """confidentialit\w*|consentement|privacidad|consentimiento|riservatezza|consenso)\b""",
            RegexOption.IGNORE_CASE,
        )

    /** True when [url] itself is a consent wall's address. */
    fun isGateUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val hostAndPath = (uri.host.orEmpty() + uri.rawPath.orEmpty()).lowercase(Locale.ROOT)
        return urlWords.any { hostAndPath.contains(it) }
    }

    /**
     * True when the page at [finalUrl] is a consent wall rather than the page that was shared:
     * its address looks like one, or its title is about privacy or cookies and it has no picture
     * (a show's page has one; a wall rarely does).
     */
    fun isGate(finalUrl: String, page: PageMetadata?): Boolean {
        if (isGateUrl(finalUrl)) return true
        if (page == null) return false
        return titleWords.containsMatchIn(page.title) && page.imageUrl.isBlank()
    }

    /**
     * The page a consent wall at [gateUrl] would send the visitor back to: any query parameter
     * (whatever its name: callbackUrl, redirect_uri, continue, …) holding a web link, followed
     * through nested links, preferring one on the same site as [originalUrl]. Null when none.
     */
    fun returnUrl(gateUrl: String, originalUrl: String): String? {
        val wanted = siteOf(originalUrl)
        val found = mutableListOf<String>()

        fun collect(url: String, depth: Int) {
            if (depth > 3) return
            val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return
            for (part in query.split('&')) {
                val value = runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrNull() ?: continue
                if (value.startsWith("http://") || value.startsWith("https://")) {
                    found += value
                    collect(value, depth + 1)
                }
            }
        }
        collect(gateUrl, 0)
        val candidates = found.filterNot(::isGateUrl)
        return candidates.lastOrNull { wanted != null && siteOf(it) == wanted } ?: candidates.lastOrNull()
    }

    /** "vtmgo.be" for "https://link.vtmgo.be/…": the last two parts of the host (good enough to compare sites). */
    fun siteOf(url: String): String? {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase(Locale.ROOT) ?: return null
        val parts = host.split('.')
        val twoPartSuffix = parts.size >= 3 && parts[parts.size - 2] in setOf("co", "com", "org", "net", "gov", "ac")
        return parts.takeLast(if (twoPartSuffix) 3 else 2).joinToString(".")
    }

    /**
     * The link to keep for a favorite: the page's own declared link ([canonical]: its app or
     * og:url link) or the address after redirects ([final]), when they are on the same site as
     * the shared link and not a consent wall; otherwise the shared link itself.
     */
    fun chooseLink(shared: String, final: String, canonical: String): String {
        val site = siteOf(shared)
        return listOf(canonical, final)
            .map { SharedText.cleanUrl(it.trim()) }
            .firstOrNull { it.startsWith("https://") && !isGateUrl(it) && siteOf(it) == site }
            ?: shared
    }
}
