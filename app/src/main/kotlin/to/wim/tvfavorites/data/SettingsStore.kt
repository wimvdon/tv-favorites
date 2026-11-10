package to.wim.tvfavorites.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import to.wim.tvfavorites.logic.Household

enum class ThemeMode { System, Light, Dark }

/** Settings the user would want back on a new phone (Android backs this file up). */
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val groupByService: Boolean = false,
)

/** This device only, never backed up: the paired household, the TMDB token, home screen channel ids. */
data class DeviceState(
    val household: Household? = null,
    val tmdbToken: String = "",
    /** Home screen channel id per channel key ("all" or a service id), as "key=id;key=id". */
    val channels: Map<String, Long> = emptyMap(),
)

// Two files, see docs/architecture.md "Backup": backed_up_settings is in the backup rules.
private val Context.backedUpStore by preferencesDataStore(
    name = "backed_up_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val Context.deviceStore by preferencesDataStore(
    name = "device_state",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val groupByService = booleanPreferencesKey("group_by_service")
        val householdId = stringPreferencesKey("household_id")
        val joinKey = stringPreferencesKey("join_key")
        val tmdbToken = stringPreferencesKey("tmdb_token")
        val channels = stringPreferencesKey("channels")
    }

    val settings: Flow<Settings> =
        context.backedUpStore.data.map { p ->
            Settings(
                theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.System,
                dynamicColor = p[Keys.dynamicColor] ?: false,
                groupByService = p[Keys.groupByService] ?: false,
            )
        }

    val device: Flow<DeviceState> = context.deviceStore.data.map(::toDeviceState)

    suspend fun currentDevice(): DeviceState = device.first()

    suspend fun setTheme(value: ThemeMode) = context.backedUpStore.edit { it[Keys.theme] = value.name }

    suspend fun setDynamicColor(value: Boolean) = context.backedUpStore.edit { it[Keys.dynamicColor] = value }

    suspend fun setGroupByService(value: Boolean) = context.backedUpStore.edit { it[Keys.groupByService] = value }

    suspend fun setHousehold(value: Household?) =
        context.deviceStore.edit {
            if (value == null) {
                it.remove(Keys.householdId)
                it.remove(Keys.joinKey)
            } else {
                it[Keys.householdId] = value.id
                it[Keys.joinKey] = value.joinKey
            }
        }

    suspend fun setTmdbToken(value: String) = context.deviceStore.edit { it[Keys.tmdbToken] = value.trim() }

    suspend fun setChannels(value: Map<String, Long>) = context.deviceStore.edit { it[Keys.channels] = encodeChannels(value) }

    companion object {
        fun encodeChannels(value: Map<String, Long>): String = value.entries.joinToString(";") { "${it.key}=${it.value}" }

        fun decodeChannels(value: String?): Map<String, Long> =
            value.orEmpty().split(';').mapNotNull { part ->
                val key = part.substringBefore('=', "")
                val id = part.substringAfter('=', "").toLongOrNull()
                if (key.isEmpty() || id == null) null else key to id
            }.toMap()

        private fun toDeviceState(p: Preferences): DeviceState {
            val id = p[Keys.householdId]
            val key = p[Keys.joinKey]
            return DeviceState(
                household = if (id != null && key != null) Household(id, key) else null,
                tmdbToken = p[Keys.tmdbToken].orEmpty(),
                channels = decodeChannels(p[Keys.channels]),
            )
        }
    }
}
