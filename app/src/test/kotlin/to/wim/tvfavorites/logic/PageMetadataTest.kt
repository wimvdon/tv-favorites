package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMetadataTest {
    private val html =
        """
        <html><head>
        <title>Ignored when og:title exists</title>
        <meta property="og:title" content="Watch Dark | Netflix Official Site">
        <meta content="A family saga with a supernatural twist &amp; more." property="og:description" />
        <meta property='og:image' content='/images/dark.jpg'>
        <meta property="og:site_name" content="Netflix">
        </head><body><meta property="og:title" content="not in head"></body></html>
        """.trimIndent()

    @Test
    fun `reads open graph tags in any attribute order and quote style`() {
        val page = PageMetadataParser.parse(html, "https://www.netflix.com/be/title/80100172")
        assertEquals("Watch Dark | Netflix Official Site", page.title)
        assertEquals("A family saga with a supernatural twist & more.", page.description)
        assertEquals("https://www.netflix.com/images/dark.jpg", page.imageUrl)
        assertEquals("Netflix", page.siteName)
    }

    @Test
    fun `falls back to the title tag and decodes entities`() {
        val page = PageMetadataParser.parse("<head><title>Caf&eacute; &#8211; VRT MAX</title></head>", "https://www.vrt.be/")
        assertEquals("Café – VRT MAX", page.title)
    }

    @Test
    fun `cleans site names and filler from titles`() {
        assertEquals("Dark", Titles.clean("Watch Dark | Netflix Official Site", "Netflix"))
        assertEquals("De Mol", Titles.clean("De Mol - VRT MAX", "VRT MAX"))
        assertEquals("Thuis", Titles.clean("Kijk Thuis op VTM GO", "VTM GO"))
        assertEquals("Mission: Impossible - Dead Reckoning", Titles.clean("Mission: Impossible - Dead Reckoning", "Prime Video"))
    }

    @Test
    fun `a page titled only with the site name is generic`() {
        assertTrue(Titles.isGeneric("VRT MAX", "VRT MAX"))
        assertTrue(Titles.isGeneric("", "GoPlay"))
        assertFalse(Titles.isGeneric("De Slimste Mens", "GoPlay"))
    }

    @Test
    fun `normalize ignores case, accents and punctuation`() {
        assertEquals("pokemon the series", Titles.normalize("Pokémon: The Series!"))
    }
}
