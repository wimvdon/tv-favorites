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
            Favorite(id = "1", title = "Dark", url = "https://www.netflix.com/title/1", serviceId = "netflix", position = 1, description = "Time travel", posterUrl = "https://p/1.jpg"),
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
    fun `landscape pictures are preferred, posters are marked portrait`() {
        assertTrue(ChannelPlan.program(items[0]).portrait)
        assertEquals("https://i/2.jpg", ChannelPlan.program(items[1]).imageUrl)
        assertEquals(false, ChannelPlan.program(items[1]).portrait)
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
}
