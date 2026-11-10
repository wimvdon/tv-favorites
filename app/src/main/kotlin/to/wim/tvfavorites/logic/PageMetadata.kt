package to.wim.tvfavorites.logic

import java.net.URI
import java.util.Locale

/** What a web page says about itself in its `<head>` (Open Graph, Twitter cards, `<title>`). */
data class PageMetadata(
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val siteName: String = "",
)

object PageMetadataParser {
    private val metaTag = Regex("""<meta\b[^>]*>""", setOf(RegexOption.IGNORE_CASE))
    private val attribute = Regex("""([a-zA-Z:_-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
    private val titleTag = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)

    /** Reads the page's own description of itself. Relative image links are made absolute with [pageUrl]. */
    fun parse(html: String, pageUrl: String): PageMetadata {
        val head = html.substringBefore("</head>", html)
        val meta = mutableMapOf<String, String>()
        for (tag in metaTag.findAll(head)) {
            val attrs =
                attribute.findAll(tag.value).associate { m ->
                    m.groupValues[1].lowercase(Locale.ROOT) to (m.groups[3]?.value ?: m.groups[4]?.value.orEmpty())
                }
            val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase(Locale.ROOT) ?: continue
            val content = attrs["content"] ?: continue
            if (content.isNotBlank()) meta.putIfAbsent(key, decodeEntities(content).trim())
        }
        val title =
            meta["og:title"] ?: meta["twitter:title"] ?: meta["title"]
                ?: titleTag.find(head)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
                ?: ""
        val description = meta["og:description"] ?: meta["twitter:description"] ?: meta["description"] ?: ""
        val image = meta["og:image"] ?: meta["og:image:url"] ?: meta["og:image:secure_url"] ?: meta["twitter:image"] ?: meta["twitter:image:src"] ?: ""
        return PageMetadata(
            title = title.collapseSpaces(),
            description = description.collapseSpaces(),
            imageUrl = absolute(image, pageUrl),
            siteName = meta["og:site_name"].orEmpty(),
        )
    }

    private fun absolute(link: String, base: String): String {
        if (link.isBlank()) return ""
        if (link.startsWith("//")) return "https:$link"
        return runCatching { URI(base).resolve(link.replace(" ", "%20")).toString() }.getOrDefault(link)
    }

    fun decodeEntities(text: String): String =
        Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""").replace(text) { m ->
            val code = m.groupValues[1]
            when {
                code.startsWith("#x", ignoreCase = true) -> code.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                code.startsWith("#") -> code.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> namedEntities[code]
            } ?: m.value
        }

    private val namedEntities =
        mapOf(
            "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
            "eacute" to "é", "egrave" to "è", "ecirc" to "ê", "euml" to "ë", "aacute" to "á", "agrave" to "à",
            "acirc" to "â", "auml" to "ä", "iuml" to "ï", "ouml" to "ö", "uuml" to "ü", "ccedil" to "ç",
            "ntilde" to "ñ", "oacute" to "ó", "uacute" to "ú", "iacute" to "í", "szlig" to "ß",
            "hellip" to "…", "ndash" to "–", "mdash" to "—", "rsquo" to "’", "lsquo" to "‘",
            "rdquo" to "”", "ldquo" to "“", "laquo" to "«", "raquo" to "»",
        )

    private fun String.collapseSpaces() = replace(Regex("""\s+"""), " ").trim()
}

object Titles {
    /** Words around a title that streaming sites add to their page titles. */
    private val prefixes =
        listOf(
            "watch ", "kijk ", "bekijk ", "regarder ", "ansehen: ", "guarda ", "ver ", "mira ",
        )

    /**
     * Strips the site's own name and filler from a page title: "Watch Dark | Netflix Official
     * Site" gives "Dark", "De Mol - VRT MAX" gives "De Mol".
     */
    fun clean(title: String, serviceName: String, siteName: String = ""): String {
        var t = title.trim()
        val names = listOf(serviceName, siteName).filter { it.isNotBlank() }
        for (separator in listOf(" | ", " - ", " – ", " — ", " · ", " :: ")) {
            val parts = t.split(separator)
            if (parts.size > 1) {
                val kept = parts.filterNot { part -> names.any { part.contains(it, ignoreCase = true) } }
                t = (kept.ifEmpty { parts.take(1) }).joinToString(separator)
            }
        }
        for (name in names) {
            for (suffix in listOf(" on $name", " op $name", " sur $name", " auf $name", " su $name", " en $name")) {
                if (t.endsWith(suffix, ignoreCase = true)) t = t.dropLast(suffix.length)
            }
        }
        prefixes.firstOrNull { t.startsWith(it, ignoreCase = true) }?.let { t = t.drop(it.length) }
        t = t.trim().trim('"', '“', '”', '\'', '‘', '’').trim()
        return t
    }

    /** True when [title] says nothing about the show (the page title is just the site's name). */
    fun isGeneric(title: String, serviceName: String, siteName: String = ""): Boolean {
        val n = normalize(title)
        if (n.isEmpty()) return true
        return listOf(serviceName, siteName).filter { it.isNotBlank() }.any { normalize(it) == n } ||
            n in setOf("home", "homepage", "startpagina", "accueil", "startseite", "inicio", "404", "not found", "page not found")
    }

    /** Lower case, accents and punctuation removed, for comparing titles. */
    fun normalize(text: String): String =
        java.text.Normalizer
            .normalize(text, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase(Locale.ROOT)
            .replace("&", "and")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
}
