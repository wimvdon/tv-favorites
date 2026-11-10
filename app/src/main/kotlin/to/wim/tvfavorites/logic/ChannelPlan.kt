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

object ChannelPlan {
    const val ALL = "all"

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
