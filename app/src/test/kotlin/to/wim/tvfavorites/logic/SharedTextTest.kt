package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTextTest {
    @Test
    fun `finds the link in a share sentence and the quoted title`() {
        val link = SharedText.parse("Bekijk 'De Mol' op VRT MAX: https://www.vrt.be/vrtmax/a-z/de-mol/?utm_source=share&utm_medium=app")!!
        assertEquals("https://www.vrt.be/vrtmax/a-z/de-mol/", link.url)
        assertEquals("De Mol", link.titleHint)
    }

    @Test
    fun `keeps meaningful query parameters and drops trackers`() {
        assertEquals("https://www.youtube.com/watch?v=abc123", SharedText.cleanUrl("https://www.youtube.com/watch?v=abc123&si=XYZ&feature=share"))
        assertEquals("https://app.primevideo.com/detail?gti=amzn1.dv.gti.1", SharedText.cleanUrl("https://app.primevideo.com/detail?gti=amzn1.dv.gti.1&ref_=share_ios_movie"))
    }

    @Test
    fun `uses the subject as title hint when the text is only a link`() {
        val link = SharedText.parse("https://www.netflix.com/title/80100172", subject = "Dark")!!
        assertEquals("Dark", link.titleHint)
    }

    @Test
    fun `trailing punctuation is not part of the link`() {
        assertEquals("https://www.goplay.be/de-slimste-mens", SharedText.parse("Kijk nu (https://www.goplay.be/de-slimste-mens).")!!.url)
    }

    @Test
    fun `no link gives null`() {
        assertNull(SharedText.parse("just some text"))
        assertNull(SharedText.parse(null))
    }

    @Test
    fun `title from the path skips ids and generic parts`() {
        assertEquals("De Mol", SharedText.titleFromPath("https://www.vrt.be/vrtmax/a-z/de-mol/"))
        assertEquals("", SharedText.titleFromPath("https://www.netflix.com/title/80100172"))
        assertEquals("Thuis", SharedText.titleFromPath("https://www.vtmgo.be/vtmgo/thuis/b0f3c1a2-1234-4abc-9def-123456789abc"))
    }
}
