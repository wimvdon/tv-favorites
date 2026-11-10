package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LookupTest {
    private val search =
        """
        [
          {"score":0.9,"show":{"id":1,"name":"De Mol","language":"Dutch","network":{"country":{"code":"NL"}},"summary":"<p>Dutch version.</p>"}},
          {"score":0.8,"show":{"id":2,"name":"De Mol","language":"Dutch","network":{"country":{"code":"BE"}},"summary":"<p>Wie is de <b>mol</b>?</p>"}},
          {"score":0.5,"show":{"id":3,"name":"The Mole","language":"English","webChannel":{"country":{"code":"US"}}}}
        ]
        """.trimIndent()

    @Test
    fun `search results carry language, countries and a plain summary`() {
        val r = TvMaze.parseSearch(search)
        assertEquals(listOf(1L, 2L, 3L), r.map { it.id })
        assertEquals(listOf("BE"), r[1].countries)
        assertEquals(listOf("US"), r[2].countries)
        assertEquals("Wie is de mol ?", r[1].summary)
    }

    @Test
    fun `a regional service gets its own country's show`() {
        val r = TvMaze.parseSearch(search)
        assertEquals(2L, TvMaze.bestMatch(r, "De Mol", setOf("BE"), setOf("nl"))?.id)
        // Same language when no show is from that country.
        assertEquals(1L, TvMaze.bestMatch(r.filter { it.id != 2L }, "De Mol", setOf("BE"), setOf("nl"))?.id)
        // International services take the first exact name.
        assertEquals(1L, TvMaze.bestMatch(r, "de mol")?.id)
        assertNull(TvMaze.bestMatch(r, "De Mol 2"))
    }

    @Test
    fun `several same-named foreign shows are too unsure`() {
        val shows = listOf(TvMazeShow(1, "Thuis", "English", listOf("US"), ""), TvMazeShow(2, "Thuis", "English", listOf("GB"), ""))
        assertNull(TvMaze.bestMatch(shows, "Thuis", setOf("BE"), setOf("nl")))
        // One foreign show of that name is fine (a British series on VRT MAX).
        assertEquals(1L, TvMaze.bestMatch(shows.take(1), "Thuis", setOf("BE"), setOf("nl"))?.id)
    }

    @Test
    fun `the previous episode gives the exact moment and number`() {
        val body = """{"id":2,"_embedded":{"previousepisode":{"season":12,"number":4,"airdate":"2026-11-08","airstamp":"2026-11-08T19:35:00+00:00"}}}"""
        assertEquals(LastAired("2026-11-08T19:35:00+00:00", "S12E4"), TvMaze.parsePreviousEpisode(body))
        assertEquals(LastAired("2026-11-08", ""), TvMaze.parsePreviousEpisode("""{"_embedded":{"previousepisode":{"airdate":"2026-11-08"}}}"""))
        assertNull(TvMaze.parsePreviousEpisode("""{"id":2}"""))
    }

    @Test
    fun `draft prefers the page, then the share text, then the path`() {
        val link = SharedLink("https://www.vrt.be/vrtmax/a-z/de-mol/", "De Mol")
        val generic = PageMetadata(title = "VRT MAX", description = "Kijk gratis naar alles van VRT.", imageUrl = "https://vrt.be/logo.png")
        val draft = DraftBuilder.build(link, link.url, generic)
        assertEquals("De Mol", draft.title)
        assertEquals("vrtmax", draft.serviceId)
        // The landing page's general description and logo say nothing about the show.
        assertEquals("", draft.description)
        assertEquals("", draft.imageUrl)

        val page = PageMetadata(title = "De Mol | VRT MAX", description = "Wie is de mol?", imageUrl = "https://vrt.be/mol.jpg")
        val full = DraftBuilder.build(link, link.url, page)
        assertEquals("Wie is de mol?", full.description)
        assertEquals("https://vrt.be/mol.jpg", full.imageUrl)
    }

    @Test
    fun `a TVmaze show adds its id, air date and a missing description`() {
        val draft = Draft("https://x.be/a", "vrtmax", "De Mol", "", "https://x.be/a.jpg")
        val show = TvMazeShow(2, "De Mol", "Dutch", listOf("BE"), "Wie is de mol?")
        val withShow = DraftBuilder.withShow(draft, show, LastAired("2026-11-08T19:35:00+00:00", "S12E4"))
        assertEquals(2L, withShow.tvMazeId)
        assertEquals("Wie is de mol?", withShow.description)
        assertEquals("S12E4", withShow.lastEpisode)
        assertEquals("https://x.be/a.jpg", withShow.imageUrl)
        assertEquals("Own text", DraftBuilder.withShow(draft.copy(description = "Own text"), show, null).description)
    }

    @Test
    fun `the landscape picture is preferred over a portrait one`() {
        val html =
            """
            <head>
            <meta property="og:title" content="De Mol">
            <meta property="og:image" content="https://img.vrt.be/tall.jpg">
            <meta property="og:image:width" content="600"><meta property="og:image:height" content="900">
            <meta property="og:image" content="https://img.vrt.be/wide.jpg">
            <meta property="og:image:width" content="1920"><meta property="og:image:height" content="1080">
            </head>
            """.trimIndent()
        assertEquals("https://img.vrt.be/wide.jpg", PageMetadataParser.parse(html, "https://www.vrt.be/").imageUrl)
        // A picture without a size is used as it is; schema.org pictures count too.
        val ld = """<head><meta property="og:title" content="Thuis"></head><body><script type="application/ld+json">{"image":"https:\/\/cdn.x.be\/thuis.jpg"}</script></body>"""
        assertEquals("https://cdn.x.be/thuis.jpg", PageMetadataParser.parse(ld, "https://www.x.be/").imageUrl)
        // Only a portrait picture: still better than none.
        val tall = """<head><meta property="og:image" content="https://a/t.jpg"><meta property="og:image:width" content="2"><meta property="og:image:height" content="3"></head>"""
        assertEquals("https://a/t.jpg", PageMetadataParser.parse(tall, "https://a/").imageUrl)
    }
}
