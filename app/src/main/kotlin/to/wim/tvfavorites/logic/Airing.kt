package to.wim.tvfavorites.logic

import to.wim.tvfavorites.model.Favorite
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Airing {
    const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000

    /** True when the last aired date of [item] should be looked up again (once a day for TMDB matches). */
    fun isDue(item: Favorite, now: Long): Boolean = item.tmdbId > 0 && now - item.checkedAt >= REFRESH_AFTER_MS

    /**
     * TVmaze's exact moment when it is about the same episode as TMDB's date or a later one
     * (a day earlier is allowed: TVmaze gives the moment in UTC), TMDB's date otherwise, so a
     * show TVmaze tracks less well never goes back in time.
     */
    fun pick(tmdb: TmdbAiring, tvMaze: TmdbAiring?): TmdbAiring {
        if (tvMaze == null) return tmdb
        val exact = runCatching { OffsetDateTime.parse(tvMaze.date).toLocalDate() }.getOrNull() ?: return tmdb
        val day = runCatching { LocalDate.parse(tmdb.date) }.getOrNull() ?: return tvMaze.copy(imdbId = tmdb.imdbId)
        return if (!exact.isBefore(day.minusDays(1))) tvMaze.copy(imdbId = tmdb.imdbId) else tmdb
    }

    /**
     * "3 Nov 2026, 21:30 · S2E5" in the reader's [locale], with the time in [zone] (the TV's own
     * time zone), or only the date when the exact moment is unknown. The label "Last aired" is
     * added by the caller from the strings. Empty when the date is unknown.
     */
    fun dateAndEpisode(
        item: Favorite,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val moment = runCatching { OffsetDateTime.parse(item.lastAired) }.getOrNull()
        val text =
            if (moment != null) {
                moment.atZoneSameInstant(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
            } else {
                val date = runCatching { LocalDate.parse(item.lastAired) }.getOrNull() ?: return ""
                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            }
        return if (item.lastEpisode.isBlank()) text else "$text · ${item.lastEpisode}"
    }
}
