package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.tvfavorites.model.Favorite

class OrderingTest {
    private fun fav(id: String, service: String, position: Long) = Favorite(id = id, title = id, url = "https://x/$id", serviceId = service, position = position)

    private val list = listOf(fav("a", "netflix", 0), fav("b", "vrtmax", 1), fav("c", "netflix", 2), fav("d", "vrtmax", 3))

    private fun apply(changes: List<Favorite>): List<String> {
        val byId = changes.associateBy { it.id }
        return Ordering.sorted(list.map { byId[it.id] ?: it }).map { it.id }
    }

    @Test
    fun `move renumbers and returns only changed items`() {
        val changes = Ordering.move(list, 3, 0)
        assertEquals(listOf("d", "a", "b", "c"), apply(changes))
        assertEquals(4, changes.size)
        assertTrue(Ordering.move(list, 1, 1).isEmpty())
    }

    @Test
    fun `moving within a service keeps the other services in place`() {
        val changes = Ordering.moveWithin(list, "netflix", 1, 0)
        assertEquals(listOf("c", "b", "a", "d"), apply(changes))
        assertTrue(changes.none { it.serviceId == "vrtmax" })
    }

    @Test
    fun `new items go first`() {
        assertEquals(-1L, Ordering.positionForNew(list))
        assertEquals(0L, Ordering.positionForNew(emptyList()))
    }

    @Test
    fun `groups follow the first favorite of each service`() {
        val groups = Ordering.groups(list) { it.uppercase() }
        assertEquals(listOf("netflix", "vrtmax"), groups.map { it.serviceId })
        assertEquals(listOf("a", "c"), groups[0].items.map { it.id })
        assertEquals("NETFLIX", groups[0].name)
    }

    @Test
    fun `duplicates are recognised by link`() {
        val incoming = listOf(fav("x", "netflix", 0).copy(url = "https://x/a/"), fav("y", "netflix", 0).copy(url = "https://x/new"))
        assertEquals(listOf("y"), Ordering.withoutDuplicates(list, incoming).map { it.id })
    }
}
