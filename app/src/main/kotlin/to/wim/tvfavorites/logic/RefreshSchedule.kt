package to.wim.tvfavorites.logic

/**
 * The TV's background refresh of the home screen rows and air dates: about every hour, kept
 * across restarts of the TV.
 */
object RefreshSchedule {
    const val INTERVAL_MILLIS: Long = 60 * 60 * 1000L

    /** The job as Android has it now, or `null` when there is none. */
    data class Pending(
        val persisted: Boolean,
        val intervalMillis: Long,
    )

    /**
     * Whether the job has to be (re)scheduled. A job from an older version that is lost on a
     * restart (not persisted) is replaced; an up-to-date one is left alone, so its timer keeps running.
     */
    fun needsSchedule(pending: Pending?): Boolean = pending == null || !pending.persisted || pending.intervalMillis != INTERVAL_MILLIS
}
