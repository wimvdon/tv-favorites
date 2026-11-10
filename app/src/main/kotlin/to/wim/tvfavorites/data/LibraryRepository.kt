package to.wim.tvfavorites.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import to.wim.tvfavorites.logic.Airing
import to.wim.tvfavorites.logic.Draft
import to.wim.tvfavorites.logic.Household
import to.wim.tvfavorites.logic.Ordering
import to.wim.tvfavorites.logic.Pairing
import to.wim.tvfavorites.model.Favorite
import to.wim.tvfavorites.model.Library
import to.wim.tvfavorites.model.ServiceOverride

/** Whether this device shares its list with paired devices. */
sealed interface Connection {
    /** The build has no Firebase key: the list stays on this device. */
    data object Unavailable : Connection

    /** Not paired: the list is on this device only. */
    data object Local : Connection

    data class Shared(val household: Household) : Connection
}

/** The paired list stopped working (the TV left, or the data was removed). */
class SyncProblem(message: String) : Exception(message)

/**
 * The favorites the screens show, and every change to them. Uses the shared cloud list once
 * this device belongs to a household, the local file otherwise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRepository(
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val local: LocalLibraryStore,
    private val firestore: () -> FirebaseFirestore?,
    private val auth: () -> FirebaseAuth?,
) {
    private val _problem = MutableStateFlow<String?>(null)

    /** A sync error worth showing, or null. */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    val connection: StateFlow<Connection> =
        settings.device
            .map { d ->
                when {
                    firestore() == null -> Connection.Unavailable
                    d.household != null -> Connection.Shared(d.household)
                    else -> Connection.Local
                }
            }.distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, Connection.Local)

    private val store: StateFlow<LibraryStore> =
        connection
            .map { c -> storeFor(c) }
            .stateIn(scope, SharingStarted.Eagerly, local)

    /** Null until the list has loaded for the first time. */
    val library: StateFlow<Library?> =
        store
            .flatMapLatest { s ->
                s.library
                    .onEach { _problem.value = null }
                    .catch { e ->
                        Log.w(TAG, "List could not be read", e)
                        _problem.value = e.message ?: e.javaClass.simpleName
                        emit(Library())
                    }
            }.stateIn(scope, SharingStarted.Eagerly, null)

    private fun storeFor(c: Connection): LibraryStore {
        val db = firestore()
        return if (c is Connection.Shared && db != null) CloudLibraryStore(db, c.household) else local
    }

    private fun current(): List<Favorite> = library.value?.favorites.orEmpty()

    private fun launchChange(block: suspend LibraryStore.() -> Unit) {
        val target = store.value
        scope.launch {
            runCatching { target.block() }.onFailure { e ->
                Log.w(TAG, "Change failed", e)
                _problem.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    fun add(draft: Draft): Favorite {
        val item =
            Favorite(
                id = Pairing.randomId(length = 20),
                title = draft.title.trim(),
                url = draft.url.trim(),
                serviceId = draft.serviceId,
                description = draft.description.trim(),
                posterUrl = draft.posterUrl.trim(),
                imageUrl = draft.imageUrl.trim(),
                position = Ordering.positionForNew(current()),
                addedAt = System.currentTimeMillis(),
                tmdbId = draft.tmdbId,
                tmdbType = draft.tmdbType,
                lastAired = draft.lastAired,
                lastEpisode = draft.lastEpisode,
                checkedAt = if (draft.tmdbId > 0) System.currentTimeMillis() else 0,
            )
        launchChange { add(listOf(item)) }
        return item
    }

    fun update(item: Favorite) = launchChange { update(item) }

    fun delete(id: String) = launchChange { delete(id) }

    /** Restores a deleted favorite (Undo). */
    fun restore(item: Favorite) = launchChange { add(listOf(item)) }

    fun move(from: Int, to: Int) {
        val changed = Ordering.move(current(), from, to)
        if (changed.isNotEmpty()) launchChange { updatePositions(changed) }
    }

    fun moveWithin(serviceId: String, from: Int, to: Int) {
        val changed = Ordering.moveWithin(current(), serviceId, from, to)
        if (changed.isNotEmpty()) launchChange { updatePositions(changed) }
    }

    /**
     * Looks up the last aired episode again for favorites matched on TMDB, once a day each
     * (on the phone, which has the TMDB token). The TV's home screen rows follow the change.
     */
    suspend fun refreshAiring(fetcher: DetailsFetcher, token: String, now: Long = System.currentTimeMillis()) {
        if (token.isBlank() || !refreshing.tryLock()) return
        try {
            for (item in current().filter { Airing.isDue(it, now) }.take(30)) {
                val airing = fetcher.airing(item.tmdbId, item.tmdbType, token)
                val latest = current().firstOrNull { it.id == item.id } ?: continue
                // Also when TMDB couldn't be asked, so a failing title is tried again tomorrow, not every minute.
                val next = if (airing == null) latest.copy(checkedAt = now) else latest.copy(lastAired = airing.date, lastEpisode = airing.episode, checkedAt = now)
                store.value.update(next)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Air dates could not be refreshed", e)
        } finally {
            refreshing.unlock()
        }
    }

    private val refreshing = Mutex()

    fun setOverride(serviceId: String, value: ServiceOverride) = launchChange { setOverride(serviceId, value) }

    /** Saves the TMDB token on this device and in the shared list, for the paired devices. */
    suspend fun setTmdbToken(token: String) {
        settings.setTmdbToken(token)
        launchChange { setTmdbToken(token) }
    }

    /** This device's token, or the one shared by a paired device. */
    suspend fun tmdbToken(): String = settings.currentDevice().tmdbToken.ifBlank { library.value?.tmdbToken.orEmpty() }

    /**
     * On the TV: makes sure there is a household to show in the QR code, creating one (and
     * moving the local list into it) the first time. Null without network or Firebase.
     */
    suspend fun ensureHousehold(): Household? {
        settings.currentDevice().household?.let { return it }
        val db = firestore() ?: return null
        val auth = auth() ?: return null
        val household = Pairing.newHousehold()
        CloudLibraryStore.create(db, auth, household)
        moveLocalInto(CloudLibraryStore(db, household))
        settings.setHousehold(household)
        return household
    }

    /**
     * On the phone: joins the TV's household from its QR code. The favorites on this phone
     * (local, or of an earlier TV) are added to the TV's list, without duplicates.
     */
    suspend fun join(household: Household) {
        val db = firestore() ?: throw SyncProblem("Sync is not available in this build")
        val auth = auth() ?: throw SyncProblem("Sync is not available in this build")
        val previous = settings.currentDevice().household
        if (previous == household) return
        val mine = library.value ?: Library()
        CloudLibraryStore.join(db, auth, household)
        val target = CloudLibraryStore(db, household)
        val theirs = withTimeoutOrNull(15_000) { target.library.first() } ?: Library()
        val start = Ordering.positionForNew(theirs.favorites) - mine.favorites.size
        val copies =
            Ordering.withoutDuplicates(theirs.favorites, Ordering.sorted(mine.favorites))
                .mapIndexed { i, f -> f.copy(position = start + i) }
        target.add(copies)
        mine.overrides.filterKeys { it !in theirs.overrides }.forEach { (k, v) -> target.setOverride(k, v) }
        val token = settings.currentDevice().tmdbToken.ifBlank { mine.tmdbToken }
        if (token.isNotBlank() && theirs.tmdbToken.isBlank()) target.setTmdbToken(token)
        if (previous == null) local.clear() else runCatching { CloudLibraryStore.leave(db, auth, previous) }
        settings.setHousehold(household)
    }

    /** Stops sharing: the current list is kept on this device only. */
    suspend fun leave() {
        val household = settings.currentDevice().household ?: return
        val mine = library.value ?: Library()
        local.clear()
        local.add(mine.favorites)
        mine.overrides.forEach { (k, v) -> local.setOverride(k, v) }
        if (mine.tmdbToken.isNotBlank()) local.setTmdbToken(mine.tmdbToken)
        val db = firestore()
        val auth = auth()
        if (db != null && auth != null) runCatching { CloudLibraryStore.leave(db, auth, household) }
        settings.setHousehold(null)
    }

    private suspend fun moveLocalInto(target: CloudLibraryStore) {
        val mine = local.library.value
        if (mine.favorites.isEmpty() && mine.overrides.isEmpty() && mine.tmdbToken.isBlank()) return
        target.add(mine.favorites)
        mine.overrides.forEach { (k, v) -> target.setOverride(k, v) }
        if (mine.tmdbToken.isNotBlank()) target.setTmdbToken(mine.tmdbToken)
        local.clear()
    }

    companion object {
        private const val TAG = "LibraryRepository"
    }
}
