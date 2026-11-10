package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.tvfavorites.model.Favorite
import java.net.URI
import java.net.URLDecoder

class ChannelPlanTest {
    private val items =
        listOf(
            Favorite(id = "1", title = "Dark", url = "https://www.netflix.com/title/1", serviceId = "netflix", position = 1, description = "Time travel"),
            Favorite(id = "2", title = "De Mol", url = "https://www.vrt.be/vrtmax/a-z/de-mol/", serviceId = "vrtmax", position = 0, imageUrl = "https://i/2.jpg"),
        )

    @Test
    fun `one row for all and one per service, in the user's order`() {
        val plan = ChannelPlan.plan(items, "All")
        assertEquals(listOf("all", "vrtmax", "netflix"), plan.map { it.key })
        assertEquals(listOf("2", "1"), plan[0].programs.map { it.favoriteId })
        assertEquals("VRT MAX", plan[1].name)
    }

    @Test
    fun `the aired line comes before the description`() {
        val program = ChannelPlan.program(items[0], "Last aired 3 Nov")
        assertEquals("Last aired 3 Nov\nTime travel", program.description)
        assertEquals("Time travel", ChannelPlan.program(items[0]).description)
    }

    @Test
    fun `tiles use the service's picture and the user's row order`() {
        assertEquals("https://i/2.jpg", ChannelPlan.program(items[1]).imageUrl)
        assertEquals("", ChannelPlan.program(items[0]).imageUrl)
        assertEquals(listOf("all", "netflix", "vrtmax"), ChannelPlan.plan(items, "All", groupOrder = listOf("netflix")).map { it.key })
    }

    @Test
    fun `the tile's link carries the favorite`() {
        val uri = URI(ChannelPlan.openUri(items[1]))
        assertEquals("tvfavorites", uri.scheme)
        assertEquals("open", uri.host)
        val params = uri.rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals("https://www.vrt.be/vrtmax/a-z/de-mol/", params["url"])
        assertEquals("vrtmax", params["service"])
    }

    @Test
    fun `rows are capped`() {
        val many = (1..80).map { Favorite(id = "$it", title = "$it", url = "https://x.org/$it", serviceId = "x.org", position = it.toLong()) }
        assertEquals(50, ChannelPlan.plan(many, "All")[0].programs.size)
    }

    @Test
    fun `rows are found again by their stored id or the id they were written with`() {
        val pkg = "to.wim.tvfavorites"
        val existing =
            listOf(
                ExistingChannel(10, "$pkg/all", pkg),
                ExistingChannel(11, "$pkg/netflix", pkg),
                // Written by an older version with the bare key.
                ExistingChannel(12, "vrtmax", pkg),
                // A second "All" row, left over from a sync that failed half way.
                ExistingChannel(13, "$pkg/all", pkg),
                // Another app's row is never touched.
                ExistingChannel(14, "all", "com.other.app"),
            )
        val match = ChannelPlan.matchChannels(pkg, stored = mapOf("all" to 10L, "gone" to 99L), existing = existing)
        assertEquals(mapOf("all" to 10L, "netflix" to 11L, "vrtmax" to 12L), match.found)
        assertEquals(listOf(13L), match.extra)
        assertEquals("$pkg/all", ChannelPlan.providerId(pkg, "all"))
    }

    @Test
    fun `nothing stored still finds the rows`() {
        val pkg = "to.wim.tvfavorites.debug"
        val match = ChannelPlan.matchChannels(pkg, emptyMap(), listOf(ExistingChannel(5, "$pkg/all"), ExistingChannel(6, "to.wim.tvfavorites/all")))
        // The release app's rows (another package name) are not this app's.
        assertEquals(mapOf("all" to 5L), match.found)
        assertTrue(match.extra.isEmpty())
    }

    @Test
    fun `tiles are updated in place, removed when gone and added when new`() {
        val changes = ChannelPlan.programChanges(existing = mapOf("a" to listOf(1L), "b" to listOf(2L, 3L), "c" to listOf(4L)), wanted = listOf("b", "a", "d"))
        assertEquals(mapOf("b" to 2L, "a" to 1L), changes.update)
        assertEquals(listOf("d"), changes.insert)
        assertEquals(setOf(3L, 4L), changes.delete.toSet())
    }
}
