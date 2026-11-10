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
    const val SEARCH_AGAIN_AFTER_MS = 7 * REFRESH_AFTER_MS

    /**
     * True when [item]'s last aired episode should be looked up: once a day for a show found on
     * TVmaze, once a week for one not found yet (TVmaze may add it; also fills in favorites
     * saved before air dates existed).
     */
    fun isDue(item: Favorite, now: Long): Boolean =
        now - item.checkedAt >= if (item.tvMazeId > 0) REFRESH_AFTER_MS else SEARCH_AGAIN_AFTER_MS

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
