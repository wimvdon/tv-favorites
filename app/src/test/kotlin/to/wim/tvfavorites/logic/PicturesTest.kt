package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PicturesTest {
    @Test
    fun `a portrait og image is the poster, a landscape one the picture`() {
        val html =
            """
            <head>
            <meta property="og:title" content="De Mol">
            <meta property="og:image" content="https://img.vrt.be/wide.jpg">
            <meta property="og:image:width" content="1920"><meta property="og:image:height" content="1080">
            <meta property="og:image" content="https://img.vrt.be/tall.jpg">
            <meta property="og:image:secure_url" content="https://img.vrt.be/tall.jpg">
            <meta property="og:image:width" content="600"><meta property="og:image:height" content="900">
            </head>
            """.trimIndent()
        val page = PageMetadataParser.parse(html, "https://www.vrt.be/vrtmax/a-z/de-mol/")
        assertEquals("https://img.vrt.be/wide.jpg", page.imageUrl)
        assertEquals("https://img.vrt.be/tall.jpg", page.posterUrl)
    }

    @Test
    fun `schema dot org data and poster-like names count too`() {
        val html =
            """
            <head><meta property="og:title" content="Thuis"><meta property="og:image" content="https://cdn.x.be/thuis-16x9.jpg"></head>
            <body><script type="application/ld+json">{"@type":"TVSeries","name":"Thuis","image":{"@type":"ImageObject","url":"https:\/\/cdn.x.be\/thuis-poster.jpg"}}</script></body>
            """.trimIndent()
        val page = PageMetadataParser.parse(html, "https://www.x.be/thuis")
        assertEquals("https://cdn.x.be/thuis-16x9.jpg", page.imageUrl)
        assertEquals("https://cdn.x.be/thuis-poster.jpg", page.posterUrl)
        assertEquals("", PageMetadataParser.parse("""<head><meta property="og:image" content="https://a/b.jpg"></head>""", "https://a/").posterUrl)
    }

    @Test
    fun `the service's picture wins over TMDB's poster`() {
        val link = SharedLink("https://www.vrt.be/vrtmax/a-z/de-mol/", "")
        val match = TmdbTitle(1, "tv", "De Mol", "De Mol", "", "/wrong.jpg", "/wrongb.jpg", "1999")
        val withPicture = DraftBuilder.build(link, link.url, PageMetadata(title = "De Mol", imageUrl = "https://img.vrt.be/wide.jpg"), match)
        assertEquals("https://img.vrt.be/wide.jpg", withPicture.imageUrl)
        assertEquals("", withPicture.posterUrl)
        // Only without any picture of its own does TMDB fill in.
        val bare = DraftBuilder.build(link, link.url, PageMetadata(title = "De Mol"), match)
        assertEquals("https://image.tmdb.org/t/p/w500/wrong.jpg", bare.posterUrl)
        assertEquals("https://image.tmdb.org/t/p/w780/wrongb.jpg", bare.imageUrl)
    }

    @Test
    fun `a regional service prefers its own country, then language`() {
        fun t(id: Long, lang: String, vararg countries: String) = TmdbTitle(id, "tv", "De Mol", "De Mol", "", "/$id.jpg", "", "", lang, countries.toList())
        val results = listOf(t(1, "nl", "NL"), t(2, "nl", "BE"), t(3, "en", "US"))
        assertEquals(2L, Tmdb.bestMatch(results, "De Mol", setOf("BE"), setOf("nl"))?.id)
        assertEquals(1L, Tmdb.bestMatch(results.filter { it.id != 2L }, "De Mol", setOf("BE"), setOf("nl"))?.id)
        // Several same-named titles and none local: too unsure to fill anything in.
        assertNull(Tmdb.bestMatch(listOf(t(3, "en", "US"), t(4, "en", "GB")), "De Mol", setOf("BE"), setOf("nl")))
        // One foreign title of that name is fine (a British series on VRT MAX).
        assertEquals(3L, Tmdb.bestMatch(listOf(t(3, "en", "US")), "De Mol", setOf("BE"), setOf("nl"))?.id)
        // International services keep the first match with a poster.
        assertEquals(1L, Tmdb.bestMatch(results, "De Mol")?.id)
    }

    @Test
    fun `search results carry language and countries`() {
        val r = Tmdb.parseSearch("""{"results":[{"id":5,"media_type":"tv","name":"De Mol","original_language":"nl","origin_country":["BE"]}]}""")
        assertEquals("nl", r[0].originalLanguage)
        assertEquals(listOf("BE"), r[0].originCountries)
    }
}
