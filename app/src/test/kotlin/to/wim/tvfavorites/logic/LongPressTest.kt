package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressTest {
    @Test
    fun `a short press is a click`() {
        val d = LongPressDetector(500)
        assertEquals(Press.None, d.down(0, 1_000))
        assertEquals(Press.Click, d.up(1_120))
    }

    @Test
    fun `holding OK fires the long press once, and letting go does not also open`() {
        val d = LongPressDetector(500)
        d.down(0, 1_000)
        assertEquals(Press.None, d.down(1, 1_400))
        assertEquals(Press.LongPress, d.down(2, 1_550))
        assertEquals(Press.None, d.down(3, 1_600))
        assertEquals(Press.None, d.up(1_700))
    }

    @Test
    fun `a remote without key repeats gets the long press on release`() {
        val d = LongPressDetector(500)
        d.down(0, 1_000)
        assertEquals(Press.LongPress, d.up(1_800))
    }

    @Test
    fun `a release without a press on this card does nothing`() {
        val d = LongPressDetector(500)
        assertEquals(Press.None, d.up(1_000))
        // OK held before the focus came here.
        assertEquals(Press.None, d.down(5, 2_000))
        assertEquals(Press.None, d.down(6, 3_000))
        assertEquals(Press.None, d.up(3_100))
    }

    @Test
    fun `reset forgets the press`() {
        val d = LongPressDetector(500)
        d.down(0, 1_000)
        d.reset()
        assertEquals(Press.None, d.up(1_100))
    }
}
