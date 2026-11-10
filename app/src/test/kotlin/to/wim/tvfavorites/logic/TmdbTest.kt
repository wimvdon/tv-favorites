package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class TmdbTest {
    private val search =
        """
        {"page":1,"results":[
          {"id":1,"media_type":"person","name":"Dark"},
          {"id":70523,"media_type":"tv","name":"Dark","original_name":"Dark","overview":"A missing child…","poster_path":"/p.jpg","backdrop_path":"/b.jpg","first_air_date":"2017-12-01"},
          {"id":99,"media_type":"movie","title":"Dark Waters","original_title":"Dark Waters","poster_path":"/w.jpg","release_date":"2019-11-22"}
        ]}
        """.trimIndent()

    @Test
    fun `parses shows and movies, not people`() {
        val results = Tmdb.parseSearch(search)
        assertEquals(listOf(70523L, 99L), results.map { it.id })
        assertEquals("https://image.tmdb.org/t/p/w500/p.jpg", results[0].posterUrl)
        assertEquals("2017", results[0].year)
    }

    @Test
    fun `only an exact title is a match`() {
        val results = Tmdb.parseSearch(search)
        assertEquals(70523L, Tmdb.bestMatch(results, "dark")?.id)
        assertNull(Tmdb.bestMatch(results, "Darkness"))
    }

    @Test
    fun `last aired episode of a show, with its IMDb id`() {
        val body = """{"last_air_date":"2020-06-27","last_episode_to_air":{"air_date":"2020-06-27","season_number":3,"episode_number":8},"external_ids":{"imdb_id":"tt5753856"}}"""
        assertEquals(TmdbAiring("2020-06-27", "S3E8", "tt5753856"), Tmdb.parseAiring(body, "tv"))
        assertEquals(TmdbAiring("2019-11-22", ""), Tmdb.parseAiring("""{"release_date":"2019-11-22"}""", "movie"))
    }

    @Test
    fun `tvmaze gives the exact moment of the previous episode`() {
        val show = """{"id":1,"_links":{"previousepisode":{"href":"http://api.tvmaze.com/episodes/42"}}}"""
        assertEquals("https://api.tvmaze.com/episodes/42", TvMaze.previousEpisodeLink(show))
        assertEquals("", TvMaze.previousEpisodeLink("""{"id":1,"_links":{}}"""))
        val episode = """{"id":42,"season":2,"number":5,"airdate":"2026-11-03","airstamp":"2026-11-04T02:30:00+00:00"}"""
        assertEquals(TmdbAiring("2026-11-04T02:30:00+00:00", "S2E5"), TvMaze.parseEpisode(episode))
        assertNull(TvMaze.parseEpisode("""{"id":42}"""))
    }

    @Test
    fun `only the long read access token is accepted`() {
        assertTrue(Tmdb.looksLikeToken("eyJ" + "a".repeat(60) + "." + "b".repeat(60) + "." + "c".repeat(40)))
        assertFalse(Tmdb.looksLikeToken("0123456789abcdef0123456789abcdef"))
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
        val match = TmdbTitle(5, "tv", "De Mol", "De Mol", "TMDB text", "/m.jpg", "/mb.jpg", "1999")
        val full = DraftBuilder.build(link, link.url, page, match)
        assertEquals("Wie is de mol?", full.description)
        assertEquals("https://vrt.be/mol.jpg", full.imageUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/m.jpg", full.posterUrl)
        assertEquals(5L, full.tmdbId)
    }
}
