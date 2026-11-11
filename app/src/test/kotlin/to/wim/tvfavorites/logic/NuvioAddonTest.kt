package to.wim.tvfavorites.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NuvioAddonTest {
    @Test
    fun `the address carries the household and its key`() {
        val household = Household("ABCDEFGHIJKLMNOPQRSTUVWX", "abcdefghijklmnopqrstuvwxyz012345")
        assertEquals(
            "https://tv-favorites-nuvio.wim-8af.workers.dev/ABCDEFGHIJKLMNOPQRSTUVWX~abcdefghijklmnopqrstuvwxyz012345/manifest.json",
            NuvioAddon.address(household),
        )
    }

    @Test
    fun `the add-on names the services as the app does`() {
        // nuvio-addon/src/services.json is the add-on's copy of the service names.
        val file = listOf(File("../nuvio-addon/src/services.json"), File("nuvio-addon/src/services.json")).first { it.exists() }
        val names = (Json.parseToJsonElement(file.readText()) as JsonObject).mapValues { it.value.jsonPrimitive.content }
        val expected = ServiceCatalog.distinct.associate { it.id to it.name }
        assertEquals(expected, names)
        assertTrue(names.isNotEmpty())
    }

    @Test
    fun `the add-on opens favorites with the home screen tiles' link`() {
        // nuvio-addon/src/addon.js builds the same tvfavorites://open link as ChannelPlan.openUri.
        val file = listOf(File("../nuvio-addon/src/addon.js"), File("nuvio-addon/src/addon.js")).first { it.exists() }
        assertTrue(file.readText().contains("tvfavorites://open?"))
        assertTrue(ChannelPlan.openUri(to.wim.tvfavorites.model.Favorite("1", "t", "https://x.org", "x.org")).startsWith("tvfavorites://open?"))
    }
}
