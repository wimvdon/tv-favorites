package com.example.template.ui.home

/**
 * Placeholder logic for the home screen: counts taps.
 *
 * Plain Kotlin with no Android types, so it is tested on the JVM (see TapCounterTest).
 * Keep real app logic in classes like this one, separate from the Compose UI.
 */
data class TapCounter(val count: Int = 0) {
    init {
        require(count >= 0) { "count must not be negative" }
    }

    /** Adds one tap. Stops at [MAX] instead of overflowing. */
    fun tap(): TapCounter = if (count >= MAX) this else copy(count = count + 1)

    fun reset(): TapCounter = TapCounter()

    companion object {
        const val MAX = 9_999
    }
}
