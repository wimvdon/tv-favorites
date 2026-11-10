package to.wim.tvfavorites.logic

import to.wim.tvfavorites.model.Favorite

/** Favorites of one service, in the user's order. */
data class ServiceGroup(
    val serviceId: String,
    val name: String,
    val items: List<Favorite>,
)

object Ordering {
    /** The list in the user's order (oldest first when positions are equal). */
    fun sorted(items: List<Favorite>): List<Favorite> = items.sortedWith(compareBy<Favorite>({ it.position }, { it.addedAt }, { it.id }))

    /** The position for a new favorite: before everything else, so it shows first. */
    fun positionForNew(items: List<Favorite>): Long = (items.minOfOrNull { it.position } ?: 1L) - 1

    /**
     * Moves the item at [from] to [to] (indexes in the sorted list) and numbers the list again.
     * Returns only the items whose position changed, ready to store.
     */
    fun move(items: List<Favorite>, from: Int, to: Int): List<Favorite> {
        val list = sorted(items).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return emptyList()
        list.add(to, list.removeAt(from))
        return renumber(items, list)
    }

    /**
     * Moves an item within one service's group ([from] and [to] are indexes inside the group).
     * The group's items keep the places they had in the full list; only their order among
     * those places changes, so the other services' items don't move.
     */
    fun moveWithin(items: List<Favorite>, serviceId: String, from: Int, to: Int): List<Favorite> {
        val list = sorted(items)
        val slots = list.indices.filter { list[it].serviceId == serviceId }
        if (from !in slots.indices || to !in slots.indices || from == to) return emptyList()
        val group = slots.map { list[it] }.toMutableList()
        group.add(to, group.removeAt(from))
        val result = list.toMutableList()
        slots.forEachIndexed { i, slot -> result[slot] = group[i] }
        return renumber(items, result)
    }

    private fun renumber(original: List<Favorite>, ordered: List<Favorite>): List<Favorite> {
        val before = original.associate { it.id to it.position }
        return ordered.mapIndexedNotNull { index, item ->
            val position = index.toLong()
            if (before[item.id] == position) null else item.copy(position = position)
        }
    }

    /** One group per service, in the order its first favorite appears. */
    fun groups(items: List<Favorite>, nameOf: (String) -> String = ServiceCatalog::nameOf): List<ServiceGroup> =
        sorted(items)
            .groupBy { it.serviceId }
            .map { (id, list) -> ServiceGroup(id, nameOf(id), list) }

    /** [incoming] without the favorites whose link is already in [existing]. */
    fun withoutDuplicates(existing: List<Favorite>, incoming: List<Favorite>): List<Favorite> {
        val urls = existing.map { it.url.trimEnd('/') }.toMutableSet()
        return incoming.filter { urls.add(it.url.trimEnd('/')) }
    }
}
