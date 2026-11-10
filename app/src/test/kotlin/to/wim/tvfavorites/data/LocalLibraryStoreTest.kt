package to.wim.tvfavorites.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import to.wim.tvfavorites.model.Favorite
import to.wim.tvfavorites.model.ServiceOverride
import java.io.File
import java.nio.file.Files

class LocalLibraryStoreTest {
    private val dir: File = Files.createTempDirectory("store").toFile()
    private val file = File(dir, "favorites.json")

    private fun fav(id: String, position: Long) = Favorite(id = id, title = "T$id", url = "https://x.org/$id", serviceId = "x.org", position = position)

    @Test
    fun `changes are kept in the file`() =
        runBlocking {
            val store = LocalLibraryStore(file)
            store.add(listOf(fav("a", 0), fav("b", 1)))
            store.update(fav("a", 0).copy(title = "Renamed"))
            store.updatePositions(listOf(fav("b", -1)))
            store.delete("missing")
            store.setOverride("x.org", ServiceOverride(packageName = "org.x"))
            store.setGroupOrder(listOf("x.org", "netflix"))

            val reopened = LocalLibraryStore(file).library.value
            assertEquals(listOf("Renamed", "Tb"), reopened.favorites.map { it.title })
            assertEquals(-1L, reopened.favorites.first { it.id == "b" }.position)
            assertEquals("org.x", reopened.overrides["x.org"]?.packageName)
            assertEquals(listOf("x.org", "netflix"), reopened.groupOrder)
        }

    @Test
    fun `an empty override is removed, and clear empties the list`() =
        runBlocking {
            val store = LocalLibraryStore(file)
            store.add(listOf(fav("a", 0)))
            store.setOverride("x.org", ServiceOverride(linkTemplate = "{url}"))
            store.setOverride("x.org", ServiceOverride())
            assertTrue(store.library.value.overrides.isEmpty())
            store.clear()
            assertTrue(LocalLibraryStore(file).library.value.favorites.isEmpty())
        }

    @Test
    fun `a damaged file gives an empty list instead of a crash`() {
        file.writeText("{not json")
        assertTrue(LocalLibraryStore(file).library.value.favorites.isEmpty())
    }

    @Test
    fun `channel ids are stored as text and read back`() {
        val map = mapOf("all" to 3L, "vrtmax" to 12L)
        assertEquals(map, SettingsStore.decodeChannels(SettingsStore.encodeChannels(map)))
        assertEquals(emptyMap<String, Long>(), SettingsStore.decodeChannels("garbage;=;x=y"))
    }
}
