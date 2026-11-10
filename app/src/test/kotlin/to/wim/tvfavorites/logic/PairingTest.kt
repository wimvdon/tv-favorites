package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PairingTest {
    @Test
    fun `a household survives the QR code`() {
        val household = Pairing.newHousehold(Random(1))
        assertEquals(24, household.id.length)
        assertEquals(32, household.joinKey.length)
        assertEquals(household, Pairing.parse(Pairing.qrContent(household)))
    }

    @Test
    fun `other codes are refused`() {
        assertNull(Pairing.parse("https://example.org/pair?h=aaaaaaaaaaaaaaaaaaaaaaaa&k=bbbbbbbbbbbbbbbbbbbbbbbb"))
        assertNull(Pairing.parse("tvfavorites://pair?h=short&k=bbbbbbbbbbbbbbbbbbbbbbbb"))
        assertNull(Pairing.parse("tvfavorites://pair?h=aaaaaaaaaaaaaaaaaaaaaaaa&k=../../etc"))
        assertNull(Pairing.parse(null))
    }

    @Test
    fun `random ids use letters and digits only`() {
        val id = Pairing.randomId(Random(7), 40)
        assertEquals(40, id.length)
        assert(id.all { it.isLetterOrDigit() })
    }
}
