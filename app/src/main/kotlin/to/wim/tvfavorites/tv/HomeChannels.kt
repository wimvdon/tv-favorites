package to.wim.tvfavorites.tv

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.launch.OpenFavoriteActivity
import to.wim.tvfavorites.logic.Airing
import to.wim.tvfavorites.logic.ChannelPlan
import to.wim.tvfavorites.logic.ExistingChannel
import to.wim.tvfavorites.logic.PlannedChannel
import to.wim.tvfavorites.logic.PlannedProgram
import to.wim.tvfavorites.model.Favorite
import to.wim.tvfavorites.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * The rows on the Android TV home screen: "All" and one per service ([ChannelPlan]). Only the
 * "All" row is shown by itself; the others are offered in Settings › Home screen rows, as
 * Android asks the user to approve each extra row.
 *
 * Written the way apps whose rows do show up write theirs (Nuvio's "Continue watching"): off the
 * main thread, each row on its own so one failing row doesn't stop the others, rows found again by
 * the id they were written with, and tiles filtered in memory (some TVs refuse a query with a
 * selection on the tiles).
 */
class HomeChannels(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val packageName get() = context.packageName

    /** Writes the rows for [items]. Rows of services without favorites are removed. */
    suspend fun sync(
        items: List<Favorite>,
        groupOrder: List<String> = emptyList(),
    ) {
        if (!context.app.isTv) return
        lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    write(items, groupOrder)
                } catch (e: Exception) {
                    // A TV without the home screen provider (or a launcher that refuses) keeps the app working.
                    Log.w(TAG, "Home screen rows could not be updated", e)
                }
            }
        }
    }

    private suspend fun write(
        items: List<Favorite>,
        groupOrder: List<String>,
    ) {
        // Dates and times in the TV's own language and time zone.
        val plan =
            ChannelPlan.plan(items, context.getString(R.string.tv_row_all), aired = { item ->
                Airing.dateAndEpisode(item).takeIf { it.isNotEmpty() }?.let { context.getString(R.string.last_aired, it) }.orEmpty()
            }, groupOrder = groupOrder)
        val match = ChannelPlan.matchChannels(packageName, context.app.settings.currentDevice().channels, existingChannels())
        match.extra.forEach { id -> runCatching { resolver.delete(TvContract.buildChannelUri(id), null, null) } }
        val known = match.found.toMutableMap()
        val wanted = plan.map { it.key }.toSet()
        for ((key, id) in known.toMap()) {
            if (key !in wanted) {
                runCatching { resolver.delete(TvContract.buildChannelUri(id), null, null) }
                known.remove(key)
            }
        }
        context.app.settings.setChannels(known)

        val programs = existingPrograms()
        for (channel in plan) {
            try {
                val id =
                    known[channel.key] ?: create(channel)?.also {
                        known[channel.key] = it
                        // Saved at once: a later failure must not make the next sync add the row again.
                        context.app.settings.setChannels(known)
                    } ?: continue
                updateChannel(id, channel)
                writePrograms(id, channel, programs[id].orEmpty())
            } catch (e: Exception) {
                Log.w(TAG, "Home screen row ${channel.key} could not be updated", e)
            }
        }
    }

    /** All rows, "All" first, for Settings › Home screen rows. */
    suspend fun channels(): Map<String, Long> {
        val all = context.app.settings.currentDevice().channels
        return all.filterKeys { it == ChannelPlan.ALL } + all.filterKeys { it != ChannelPlan.ALL }
    }

    /** Android's "add this row to the home screen?" dialog for one row. */
    fun browsableRequest(channelId: Long): Intent =
        Intent(TvContract.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContract.EXTRA_CHANNEL_ID, channelId)

    /** This app's rows as Android has them (the provider only returns an app's own rows). */
    private fun existingChannels(): List<ExistingChannel> =
        resolver
            .query(
                TvContract.Channels.CONTENT_URI,
                arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, TvContract.Channels.COLUMN_PACKAGE_NAME),
                null,
                null,
                null,
            )?.use { c ->
                val id = c.getColumnIndex(TvContract.Channels._ID)
                val provider = c.getColumnIndex(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID)
                val pkg = c.getColumnIndex(TvContract.Channels.COLUMN_PACKAGE_NAME)
                if (id < 0) return@use emptyList()
                buildList {
                    while (c.moveToNext()) {
                        add(
                            ExistingChannel(
                                id = c.getLong(id),
                                providerId = if (provider >= 0) c.getString(provider) else null,
                                packageName = if (pkg >= 0) c.getString(pkg) else null,
                            ),
                        )
                    }
                }
            }.orEmpty()

    /** The tiles per row: row id → favorite id → the tiles' ids and what they show. */
    private fun existingPrograms(): Map<Long, Map<String, List<StoredProgram>>> {
        val columns = arrayOf(TvContract.PreviewPrograms._ID, TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID) + COMPARED
        val result = mutableMapOf<Long, MutableMap<String, MutableList<StoredProgram>>>()
        // No selection: some TVs (Fire OS) refuse one on tiles; the provider only returns this app's tiles.
        resolver.query(TvContract.PreviewPrograms.CONTENT_URI, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(2) ?: continue
                val shown = COMPARED.indices.associate { i -> COMPARED[i] to c.getString(3 + i) }
                result
                    .getOrPut(c.getLong(1)) { mutableMapOf() }
                    .getOrPut(key) { mutableListOf() }
                    .add(StoredProgram(c.getLong(0), shown))
            }
        }
        return result
    }

    private fun create(channel: PlannedChannel): Long? {
        val appLink = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).toUri(Intent.URI_INTENT_SCHEME)
        val values =
            ContentValues().apply {
                put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
                put(TvContract.Channels.COLUMN_DISPLAY_NAME, channel.name)
                put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, ChannelPlan.providerId(packageName, channel.key))
                put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, appLink)
            }
        val uri = resolver.insert(TvContract.Channels.CONTENT_URI, values) ?: return null
        val id = runCatching { uri.lastPathSegment?.toLong() }.getOrNull() ?: return null
        writeLogo(id)
        // Android shows an app's first row without asking; the others the user adds in Settings.
        if (channel.key == ChannelPlan.ALL) runCatching { TvContract.requestChannelBrowsable(context, id) }.onFailure { Log.w(TAG, "Row not offered", it) }
        return id
    }

    /** The row's name in the TV's language, and the id it is found by (older versions used the bare key). */
    private fun updateChannel(id: Long, channel: PlannedChannel) {
        val values =
            ContentValues().apply {
                put(TvContract.Channels.COLUMN_DISPLAY_NAME, channel.name)
                put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, ChannelPlan.providerId(packageName, channel.key))
            }
        val current =
            resolver.query(TvContract.buildChannelUri(id), arrayOf(TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID), null, null, null)?.use { c ->
                if (c.moveToFirst()) listOf(c.getString(0), c.getString(1)) else null
            }
        if (current != listOf(values.getAsString(TvContract.Channels.COLUMN_DISPLAY_NAME), values.getAsString(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID))) {
            resolver.update(TvContract.buildChannelUri(id), values, null, null)
        }
    }

    private fun writePrograms(
        channelId: Long,
        channel: PlannedChannel,
        existing: Map<String, List<StoredProgram>>,
    ) {
        val changes = ChannelPlan.programChanges(existing.mapValues { (_, rows) -> rows.map { it.id } }, channel.programs.map { it.favoriteId })
        changes.delete.forEach { resolver.delete(TvContract.buildPreviewProgramUri(it), null, null) }
        val stored = existing.values.flatten().associateBy { it.id }
        channel.programs.forEachIndexed { index, p ->
            val values = programValues(channelId, p, weight = channel.programs.size - index)
            val rowId = changes.update[p.favoriteId]
            if (rowId == null) {
                resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values)
            } else if (stored[rowId]?.shows(values) != true) {
                // Unchanged tiles are left alone, so the launcher isn't woken up every hour.
                resolver.update(TvContract.buildPreviewProgramUri(rowId), values, null, null)
            }
        }
    }

    private fun programValues(
        channelId: Long,
        p: PlannedProgram,
        weight: Int,
    ): ContentValues {
        val open =
            Intent(Intent.ACTION_VIEW, p.intentUri.toUri())
                .setClass(context, OpenFavoriteActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .toUri(Intent.URI_INTENT_SCHEME)
        return ContentValues().apply {
            put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
            put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_TV_SERIES)
            put(TvContract.PreviewPrograms.COLUMN_TITLE, p.title)
            put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, p.description.take(300))
            if (p.imageUrl.isNotBlank()) put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, p.imageUrl) else putNull(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)
            // Every tile is landscape, as the services' own pictures are.
            put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9)
            put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, open)
            put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, p.favoriteId)
            // The launcher shows heavier tiles first.
            put(TvContract.PreviewPrograms.COLUMN_WEIGHT, weight)
        }
    }

    private fun writeLogo(channelId: Long) {
        runCatching {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return
            val bitmap = createBitmap(160, 160)
            drawable.setBounds(0, 0, 160, 160)
            drawable.draw(Canvas(bitmap))
            resolver.openOutputStream(TvContract.buildChannelLogoUri(channelId))?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { Log.w(TAG, "Row logo not written", it) }
    }

    /** A tile as Android has it: its id and the columns [COMPARED]. */
    private class StoredProgram(
        val id: Long,
        val shown: Map<String, String?>,
    ) {
        fun shows(values: ContentValues): Boolean = COMPARED.all { values.getAsString(it) == shown[it] }
    }

    companion object {
        private const val TAG = "HomeChannels"
        private const val JOB_ID = 4201

        /** One sync at a time: the screen and the hourly job would otherwise both add a missing row. */
        private val lock = Mutex()

        /** What a tile shows; a tile is only rewritten when one of these changed. */
        private val COMPARED =
            arrayOf(
                TvContract.PreviewPrograms.COLUMN_TITLE,
                TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
                TvContract.PreviewPrograms.COLUMN_INTENT_URI,
                TvContract.PreviewPrograms.COLUMN_WEIGHT,
            )

        /** Refreshes the rows about every hour, so favorites added on the phone show without opening the app. */
        fun schedule(context: Context) {
            if (!context.app.isTv) return
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job =
                JobInfo
                    .Builder(JOB_ID, ComponentName(context, HomeChannelsJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(TimeUnit.HOURS.toMillis(1))
                    .build()
            scheduler.schedule(job)
        }
    }
}
