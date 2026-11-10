package to.wim.tvfavorites.logic

import to.wim.tvfavorites.model.Favorite
import java.net.URLEncoder

/** A row on the Android TV home screen. [key] is "all" or a service id. */
data class PlannedChannel(
    val key: String,
    val name: String,
    val programs: List<PlannedProgram>,
)

/** A tile in a home screen row. [intentUri] opens the favorite through the app. */
data class PlannedProgram(
    val favoriteId: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val intentUri: String,
)

/** A row already in Android's list of home screen rows. */
data class ExistingChannel(
    val id: Long,
    val providerId: String?,
    val packageName: String? = null,
)

/** Which existing rows belong to which key, and leftovers (duplicates) to remove. */
data class ChannelMatch(
    val found: Map<String, Long>,
    val extra: List<Long>,
)

/** How to bring a row's tiles up to date: delete these rows, update these keys' rows in place, insert these keys. */
data class ProgramChanges(
    val delete: List<Long>,
    val update: Map<String, Long>,
    val insert: List<String>,
)

object ChannelPlan {
    const val ALL = "all"

    /** The id a row is written with, so the app finds its rows again even when it lost their ids. */
    fun providerId(packageName: String, key: String): String = "$packageName/$key"

    /**
     * Matches the rows Android has for this app ([existing]) to keys: first by the ids the app
     * stored, then by the id each row was written with ([providerId]; versions before 0.1.3 used the
     * bare key). Rows of other apps are never touched. A second row for the same key is a leftover.
     */
    fun matchChannels(
        packageName: String,
        stored: Map<String, Long>,
        existing: List<ExistingChannel>,
    ): ChannelMatch {
        val ours = existing.filter { it.packageName == null || it.packageName == packageName }
        val byId = ours.associateBy { it.id }
        val found = linkedMapOf<String, Long>()
        for ((key, id) in stored) if (id in byId && id !in found.values) found[key] = id
        val extra = mutableListOf<Long>()
        for (channel in ours) {
            if (channel.id in found.values) continue
            val key = keyOf(packageName, channel.providerId) ?: continue
            if (key in found) extra += channel.id else found[key] = channel.id
        }
        return ChannelMatch(found, extra)
    }

    private fun keyOf(packageName: String, providerId: String?): String? {
        val id = providerId?.takeIf { it.isNotBlank() } ?: return null
        val prefix = "$packageName/"
        return when {
            id.startsWith(prefix) -> id.removePrefix(prefix).takeIf { it.isNotEmpty() }
            '/' !in id && '.' !in id -> id
            else -> null
        }
    }

    /**
     * Updates tiles in place where possible (the launcher keeps its place), removes the tiles of
     * favorites that left the row and duplicates, and inserts the new ones. [existing] maps a
     * favorite id to the tile rows written for it.
     */
    fun programChanges(
        existing: Map<String, List<Long>>,
        wanted: List<String>,
    ): ProgramChanges {
        val wantedSet = wanted.toSet()
        val delete = mutableListOf<Long>()
        val update = linkedMapOf<String, Long>()
        val insert = mutableListOf<String>()
        for ((key, rows) in existing) if (key !in wantedSet) delete += rows
        for (key in wanted.distinct()) {
            val rows = existing[key].orEmpty()
            if (rows.isEmpty()) {
                insert += key
            } else {
                update[key] = rows.first()
                delete += rows.drop(1)
            }
        }
        return ProgramChanges(delete, update, insert)
    }

    /**
     * One "All" row plus one row per service with favorites, each in the user's order. A
     * channel shows at most [limit] tiles, as the home screen gets slow with long rows.
     */
    fun plan(
        items: List<Favorite>,
        allName: String,
        aired: (Favorite) -> String = { "" },
        groupOrder: List<String> = emptyList(),
        limit: Int = 50,
    ): List<PlannedChannel> {
        val sorted = Ordering.sorted(items)
        val all = PlannedChannel(ALL, allName, sorted.take(limit).map { program(it, aired(it)) })
        val perService = Ordering.groups(sorted, order = groupOrder).map { g -> PlannedChannel(g.serviceId, g.name, g.items.take(limit).map { program(it, aired(it)) }) }
        return listOf(all) + perService
    }

    /** [airedLine] ("Last aired 3 Nov 2026 · S2E5") goes first, so it shows even when the description is cut off. */
    fun program(item: Favorite, airedLine: String = ""): PlannedProgram =
        PlannedProgram(
            favoriteId = item.id,
            title = item.title,
            description = listOf(airedLine, item.description).filter { it.isNotBlank() }.joinToString("\n"),
            imageUrl = item.imageUrl,
            intentUri = openUri(item),
        )

    /** The link a home screen tile opens: the app's own launch screen, which then opens the service. */
    fun openUri(item: Favorite): String =
        "tvfavorites://open?id=${enc(item.id)}&service=${enc(item.serviceId)}&url=${enc(item.url)}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
