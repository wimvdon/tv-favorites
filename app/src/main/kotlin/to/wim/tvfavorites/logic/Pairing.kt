package to.wim.tvfavorites.logic

import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * The shared list of a TV and the phones paired with it. [id] names it in the database and
 * [joinKey] lets a device join it; both travel only in the QR code the TV shows.
 */
data class Household(val id: String, val joinKey: String)

object Pairing {
    private const val SCHEME = "tvfavorites"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    /** 24 characters from 62: about 143 bits, far too many to guess. */
    fun randomId(random: Random = SecureRandom().asKotlinRandom(), length: Int = 24): String =
        buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun newHousehold(random: Random = SecureRandom().asKotlinRandom()) = Household(randomId(random), randomId(random, 32))

    /** What the TV's QR code says. */
    fun qrContent(household: Household): String = "$SCHEME://pair?h=${household.id}&k=${household.joinKey}"

    /** The household in a scanned QR code, or null when it isn't one of ours. */
    fun parse(content: String?): Household? {
        val uri = runCatching { URI(content?.trim().orEmpty()) }.getOrNull() ?: return null
        if (uri.scheme != SCHEME || uri.host != "pair") return null
        val params =
            uri.rawQuery.orEmpty().split('&').associate { part ->
                part.substringBefore('=') to URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            }
        val id = params["h"].orEmpty()
        val key = params["k"].orEmpty()
        val valid = Regex("""[A-Za-z0-9]{20,64}""")
        if (!valid.matches(id) || !valid.matches(key)) return null
        return Household(id, key)
    }
}
