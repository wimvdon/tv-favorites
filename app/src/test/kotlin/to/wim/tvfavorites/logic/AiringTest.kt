package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.tvfavorites.model.Favorite
import java.time.ZoneId
import java.util.Locale

class AiringTest {
    private val base = Favorite(id = "1", title = "Show", url = "https://x.org", serviceId = "x.org")

    @Test
    fun `an exact moment is shown in the TV's time zone`() {
        // 02:30 UTC on 4 November is 21:30 on 3 November in New York and 03:30 on 4 November in Brussels.
        val item = base.copy(lastAired = "2026-11-04T02:30:00+00:00", lastEpisode = "S2E5")
        assertEquals("Nov 3, 2026, 9:30 PM · S2E5", Airing.dateAndEpisode(item, Locale.US, ZoneId.of("America/New_York")).replace('\u202f', ' '))
        assertTrue(Airing.dateAndEpisode(item, Locale.forLanguageTag("nl-BE"), ZoneId.of("Europe/Brussels")).contains("03:30"))
    }

    @Test
    fun `a date alone is shown without a time`() {
        assertEquals("Nov 3, 2026", Airing.dateAndEpisode(base.copy(lastAired = "2026-11-03"), Locale.US))
        assertEquals("", Airing.dateAndEpisode(base, Locale.US))
        assertEquals("", Airing.dateAndEpisode(base.copy(lastAired = "soon"), Locale.US))
    }

    @Test
    fun `refreshed once a day, only for TMDB matches`() {
        val day = Airing.REFRESH_AFTER_MS
        assertFalse(Airing.isDue(base, now = 10 * day))
        assertTrue(Airing.isDue(base.copy(tmdbId = 5, checkedAt = 0), now = day))
        assertFalse(Airing.isDue(base.copy(tmdbId = 5, checkedAt = day), now = day + 1000))
    }

    @Test
    fun `TVmaze's moment is used unless it is older than TMDB's date`() {
        val tmdb = TmdbAiring("2026-11-03", "S2E5", "tt1")
        val exact = TmdbAiring("2026-11-04T02:30:00+00:00", "S2E5")
        assertEquals(exact.copy(imdbId = "tt1"), Airing.pick(tmdb, exact))
        // A day earlier in UTC is still the same episode (aired in the evening somewhere east).
        val east = TmdbAiring("2026-11-02T20:00:00+00:00", "S2E5")
        assertEquals(east.copy(imdbId = "tt1"), Airing.pick(tmdb, east))
        // TVmaze lagging a whole season behind: keep TMDB's.
        assertEquals(tmdb, Airing.pick(tmdb, TmdbAiring("2025-05-01T20:00:00+00:00", "S1E8")))
        assertEquals(tmdb, Airing.pick(tmdb, null))
    }
}
