package to.wim.tvfavorites.logic

/** What a press of the remote's OK button on a card means. */
enum class Press { None, Click, LongPress }

/**
 * Tells a short press of OK from a long one, from the key's down and up events. The long press
 * fires once while OK is still held (Android repeats the key-down while it is held) or, on remotes
 * that don't repeat, when OK is let go after [timeoutMillis]. The release that follows a long press
 * is swallowed, so it never also opens the card. A release without a press on this card (OK
 * pressed elsewhere, then the focus moved here) does nothing.
 */
class LongPressDetector(private val timeoutMillis: Long = 500) {
    private var downAt: Long? = null
    private var fired = false

    /** A key-down; [repeatCount] is 0 for the first one. Returns [Press.LongPress] when it fires now. */
    fun down(repeatCount: Int, timeMillis: Long): Press {
        if (repeatCount == 0) {
            downAt = timeMillis
            fired = false
            return Press.None
        }
        // Held since before this card had the focus: not this card's press.
        val start = downAt ?: return Press.None
        if (!fired && timeMillis - start >= timeoutMillis) {
            fired = true
            return Press.LongPress
        }
        return Press.None
    }

    /** The key-up. */
    fun up(timeMillis: Long): Press {
        val start = downAt ?: return Press.None
        downAt = null
        if (fired) return Press.None
        return if (timeMillis - start >= timeoutMillis) Press.LongPress else Press.Click
    }

    /** Forgets a press, e.g. when the card loses the focus. */
    fun reset() {
        downAt = null
        fired = false
    }
}
