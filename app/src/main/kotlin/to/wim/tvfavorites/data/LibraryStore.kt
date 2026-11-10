package to.wim.tvfavorites.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import to.wim.tvfavorites.model.Favorite
import to.wim.tvfavorites.model.Library
import to.wim.tvfavorites.model.ServiceOverride
import java.io.File

/** Where the list lives: on this device only, or shared through the cloud with the paired devices. */
interface LibraryStore {
    val library: Flow<Library>

    suspend fun add(items: List<Favorite>)

    suspend fun update(item: Favorite)

    /** Stores new positions (from [to.wim.tvfavorites.logic.Ordering]). */
    suspend fun updatePositions(items: List<Favorite>)

    suspend fun delete(id: String)

    suspend fun setOverride(serviceId: String, value: ServiceOverride)

    suspend fun setGroupOrder(order: List<String>)
}

/**
 * The list on this device only, in one JSON file (used until the phone is paired with a TV,
 * and in builds without a Firebase key). Plain Kotlin and java.io, so it is tested on the JVM.
 */
class LocalLibraryStore(private val file: File) : LibraryStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private val state = MutableStateFlow(read())

    override val library: StateFlow<Library> = state.asStateFlow()

    private fun read(): Library =
        runCatching { if (file.exists()) json.decodeFromString(Library.serializer(), file.readText()) else Library() }
            .getOrElse { Library() }

    private suspend fun change(block: (Library) -> Library) =
        mutex.withLock {
            val next = block(state.value)
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(json.encodeToString(Library.serializer(), next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
            state.value = next
        }

    override suspend fun add(items: List<Favorite>) = change { lib -> lib.copy(favorites = lib.favorites + items) }

    override suspend fun update(item: Favorite) =
        change { lib -> lib.copy(favorites = lib.favorites.map { if (it.id == item.id) item else it }) }

    override suspend fun updatePositions(items: List<Favorite>) {
        val positions = items.associate { it.id to it.position }
        change { lib -> lib.copy(favorites = lib.favorites.map { f -> positions[f.id]?.let { f.copy(position = it) } ?: f }) }
    }

    override suspend fun delete(id: String) = change { lib -> lib.copy(favorites = lib.favorites.filterNot { it.id == id }) }

    override suspend fun setOverride(serviceId: String, value: ServiceOverride) =
        change { lib ->
            lib.copy(overrides = if (value.isEmpty) lib.overrides - serviceId else lib.overrides + (serviceId to value))
        }

    override suspend fun setGroupOrder(order: List<String>) = change { it.copy(groupOrder = order) }

    /** Empties the list (after its favorites were copied to a TV's list). */
    suspend fun clear() = change { Library() }
}
