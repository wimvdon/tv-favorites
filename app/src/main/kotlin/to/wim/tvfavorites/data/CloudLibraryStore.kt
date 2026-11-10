package to.wim.tvfavorites.data

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.suspendCancellableCoroutine
import to.wim.tvfavorites.logic.Household
import to.wim.tvfavorites.model.Favorite
import to.wim.tvfavorites.model.Library
import to.wim.tvfavorites.model.ServiceOverride
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The list shared by a TV and its paired phones, in Firestore under `households/<id>`
 * (see `firebase/firestore.rules`). Firestore keeps a copy on the device, so the list shows
 * and changes offline too and catches up later.
 */
class CloudLibraryStore(
    private val db: FirebaseFirestore,
    private val household: Household,
) : LibraryStore {
    private val root get() = db.collection("households").document(household.id)
    private val favorites get() = root.collection("favorites")
    private val services get() = root.collection("settings").document("services")
    private val groups get() = root.collection("settings").document("groups")

    override val library: Flow<Library> =
        combine(favoritesFlow(), overridesFlow(), groupOrderFlow()) { items, overrides, order -> Library(items, overrides, order) }

    private fun groupOrderFlow(): Flow<List<String>> =
        callbackFlow {
            val registration =
                groups.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend((snapshot?.get("order") as? List<*>)?.filterIsInstance<String>().orEmpty())
                }
            awaitClose { registration.remove() }
        }

    override suspend fun setGroupOrder(order: List<String>) {
        groups.set(mapOf("order" to order)).await()
    }

    private fun favoritesFlow(): Flow<List<Favorite>> =
        callbackFlow {
            val registration =
                favorites.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull(::toFavorite))
                }
            awaitClose { registration.remove() }
        }

    private fun overridesFlow(): Flow<Map<String, ServiceOverride>> =
        callbackFlow {
            val registration =
                services.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(toOverrides(snapshot))
                }
            awaitClose { registration.remove() }
        }

    override suspend fun add(items: List<Favorite>) {
        if (items.isEmpty()) return
        items.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.set(favorites.document(it.id), fromFavorite(it)) }
            batch.commit().await()
        }
    }

    override suspend fun update(item: Favorite) {
        favorites.document(item.id).set(fromFavorite(item)).await()
    }

    override suspend fun updatePositions(items: List<Favorite>) {
        items.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.update(favorites.document(it.id), "position", it.position) }
            batch.commit().await()
        }
    }

    override suspend fun delete(id: String) {
        favorites.document(id).delete().await()
    }

    override suspend fun setOverride(serviceId: String, value: ServiceOverride) {
        val entry: Any = if (value.isEmpty) FieldValue.delete() else mapOf("packageName" to value.packageName, "linkTemplate" to value.linkTemplate)
        services.set(mapOf(serviceId to entry), SetOptions.merge()).await()
    }

    companion object {
        /** Creates a new household (on the TV) with this device as its first member. */
        suspend fun create(db: FirebaseFirestore, auth: FirebaseAuth, household: Household) {
            val uid = signIn(auth)
            val root = db.collection("households").document(household.id)
            root.set(mapOf("joinKey" to household.joinKey, "createdAt" to FieldValue.serverTimestamp())).await()
            root.collection("members").document(uid).set(mapOf("key" to household.joinKey)).await()
        }

        /** Joins an existing household with the key from its QR code. */
        suspend fun join(db: FirebaseFirestore, auth: FirebaseAuth, household: Household) {
            val uid = signIn(auth)
            db
                .collection("households")
                .document(household.id)
                .collection("members")
                .document(uid)
                .set(mapOf("key" to household.joinKey))
                .await()
        }

        /** Leaves a household: this device can no longer read or change its list. */
        suspend fun leave(db: FirebaseFirestore, auth: FirebaseAuth, household: Household) {
            val uid = auth.currentUser?.uid ?: return
            db.collection("households").document(household.id).collection("members").document(uid).delete().await()
        }

        private suspend fun signIn(auth: FirebaseAuth): String =
            auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid ?: error("Anonymous sign-in returned no user")

        fun toFavorite(doc: DocumentSnapshot): Favorite? {
            val title = doc.getString("title") ?: return null
            val url = doc.getString("url") ?: return null
            return Favorite(
                id = doc.id,
                title = title,
                url = url,
                serviceId = doc.getString("serviceId").orEmpty().ifEmpty { "other" },
                description = doc.getString("description").orEmpty(),
                // Favorites saved by 0.1.x may only have a (TMDB) poster.
                imageUrl = doc.getString("imageUrl").orEmpty().ifEmpty { doc.getString("posterUrl").orEmpty() },
                position = doc.getLong("position") ?: 0,
                addedAt = doc.getLong("addedAt") ?: 0,
                tvMazeId = doc.getLong("tvMazeId") ?: 0,
                lastAired = doc.getString("lastAired").orEmpty(),
                lastEpisode = doc.getString("lastEpisode").orEmpty(),
                checkedAt = doc.getLong("checkedAt") ?: 0,
            )
        }

        fun fromFavorite(f: Favorite): Map<String, Any> =
            mapOf(
                "title" to f.title,
                "url" to f.url,
                "serviceId" to f.serviceId,
                "description" to f.description,
                "imageUrl" to f.imageUrl,
                "position" to f.position,
                "addedAt" to f.addedAt,
                "tvMazeId" to f.tvMazeId,
                "lastAired" to f.lastAired,
                "lastEpisode" to f.lastEpisode,
                "checkedAt" to f.checkedAt,
            )

        private fun toOverrides(doc: DocumentSnapshot?): Map<String, ServiceOverride> {
            val data = doc?.data ?: return emptyMap()
            return data.mapNotNull { (key, value) ->
                val map = value as? Map<*, *> ?: return@mapNotNull null
                key to ServiceOverride(map["packageName"] as? String ?: "", map["linkTemplate"] as? String ?: "")
            }.toMap()
        }
    }
}

/** Waits for a Play services [Task] without blocking a thread. */
suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val error = task.exception
            when {
                error != null -> cont.resumeWithException(error)
                task.isCanceled -> cont.cancel()
                else -> cont.resume(task.result)
            }
        }
    }
