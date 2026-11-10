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
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import android.util.Log
import androidx.core.content.ContextCompat
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.launch.OpenFavoriteActivity
import to.wim.tvfavorites.logic.Airing
import to.wim.tvfavorites.logic.ChannelPlan
import to.wim.tvfavorites.logic.PlannedChannel
import to.wim.tvfavorites.model.Favorite
import to.wim.tvfavorites.ui.MainActivity
import java.util.concurrent.TimeUnit

/**
 * The rows on the Android TV home screen: "All" and one per service ([ChannelPlan]). Only the
 * "All" row is shown by itself; the others are offered in Settings › Home screen rows, as
 * Android asks the user to approve each extra row.
 */
class HomeChannels(private val context: Context) {
    private val resolver get() = context.contentResolver

    /** Writes the rows for [items]. Rows of services without favorites are removed. */
    suspend fun sync(items: List<Favorite>) {
        if (!context.app.isTv) return
        try {
            // Dates and times in the TV's own language and time zone.
            val plan =
                ChannelPlan.plan(items, context.getString(R.string.tv_row_all), aired = { item ->
                    Airing.dateAndEpisode(item).takeIf { it.isNotEmpty() }?.let { context.getString(R.string.last_aired, it) }.orEmpty()
                })
            val known = context.app.settings.currentDevice().channels.toMutableMap()
            val wanted = plan.map { it.key }.toSet()
            for ((key, id) in known.toMap()) {
                if (key !in wanted) {
                    resolver.delete(TvContract.buildChannelUri(id), null, null)
                    known.remove(key)
                }
            }
            for (channel in plan) {
                val id = known[channel.key]?.takeIf(::exists) ?: create(channel).also { known[channel.key] = it }
                if (id < 0) continue
                rename(id, channel.name)
                writePrograms(id, channel)
                if (channel.key == ChannelPlan.ALL) TvContract.requestChannelBrowsable(context, id)
            }
            context.app.settings.setChannels(known.filterValues { it >= 0 })
        } catch (e: Exception) {
            // A TV without the home screen provider (or a launcher that refuses) keeps the app working.
            Log.w(TAG, "Home screen rows could not be updated", e)
        }
    }

    /** The service rows (not "All") that exist, for Settings › Home screen rows. */
    suspend fun serviceChannels(): Map<String, Long> = context.app.settings.currentDevice().channels.filterKeys { it != ChannelPlan.ALL }

    /** Android's "add this row to the home screen?" dialog for one row. */
    fun browsableRequest(channelId: Long): Intent =
        Intent(TvContract.ACTION_REQUEST_CHANNEL_BROWSABLE).putExtra(TvContract.EXTRA_CHANNEL_ID, channelId)

    private fun exists(id: Long): Boolean =
        runCatching {
            resolver.query(TvContract.buildChannelUri(id), arrayOf(TvContract.Channels._ID), null, null, null)?.use { it.moveToFirst() } == true
        }.getOrDefault(false)

    private fun create(channel: PlannedChannel): Long {
        val values =
            ContentValues().apply {
                put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
                put(TvContract.Channels.COLUMN_DISPLAY_NAME, channel.name)
                put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, channel.key)
                put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, Intent(context, MainActivity::class.java).toUri(Intent.URI_INTENT_SCHEME))
            }
        val uri = resolver.insert(TvContract.Channels.CONTENT_URI, values) ?: return -1
        val id = runCatching { uri.lastPathSegment?.toLong() }.getOrNull() ?: return -1
        writeLogo(id)
        return id
    }

    private fun rename(id: Long, name: String) {
        val values = ContentValues().apply { put(TvContract.Channels.COLUMN_DISPLAY_NAME, name) }
        resolver.update(TvContract.buildChannelUri(id), values, null, null)
    }

    private fun writePrograms(channelId: Long, channel: PlannedChannel) {
        // Simple and safe: replace the row's tiles. Rows hold at most 50 tiles.
        resolver.query(TvContract.buildPreviewProgramsUriForChannel(channelId), arrayOf(TvContract.PreviewPrograms._ID), null, null, null)?.use { c ->
            while (c.moveToNext()) resolver.delete(TvContract.buildPreviewProgramUri(c.getLong(0)), null, null)
        }
        channel.programs.forEachIndexed { index, p ->
            val open =
                Intent(Intent.ACTION_VIEW, p.intentUri.toUri())
                    .setClass(context, OpenFavoriteActivity::class.java)
                    .toUri(Intent.URI_INTENT_SCHEME)
            val values =
                ContentValues().apply {
                    put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
                    put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_TV_SERIES)
                    put(TvContract.PreviewPrograms.COLUMN_TITLE, p.title)
                    put(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION, p.description.take(300))
                    if (p.imageUrl.isNotBlank()) {
                        put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, p.imageUrl)
                        put(
                            TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                            if (p.portrait) TvContract.PreviewPrograms.ASPECT_RATIO_2_3 else TvContract.PreviewPrograms.ASPECT_RATIO_16_9,
                        )
                    }
                    put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, open)
                    put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, p.favoriteId)
                    // The launcher shows heavier tiles first.
                    put(TvContract.PreviewPrograms.COLUMN_WEIGHT, channel.programs.size - index)
                }
            resolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values)
        }
    }

    private fun writeLogo(channelId: Long) {
        runCatching {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return
            val bitmap = createBitmap(160, 160)
            drawable.setBounds(0, 0, 160, 160)
            drawable.draw(Canvas(bitmap))
            resolver.openOutputStream(TvContract.buildChannelLogoUri(channelId))?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    companion object {
        private const val TAG = "HomeChannels"
        private const val JOB_ID = 4201

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
