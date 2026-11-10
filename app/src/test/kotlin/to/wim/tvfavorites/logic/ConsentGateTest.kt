package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentGateTest {
    @Test
    fun `consent wall addresses are recognised whatever the site`() {
        assertTrue(ConsentGate.isGateUrl("https://myprivacy.dpgmedia.be/consent?siteKey=abc"))
        assertTrue(ConsentGate.isGateUrl("https://consent.youtube.com/m?continue=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dx"))
        assertTrue(ConsentGate.isGateUrl("https://www.example.nl/cookiewall/?target=x"))
        assertTrue(ConsentGate.isGateUrl("https://www.example.de/privacy-gate/accept"))
        assertTrue(ConsentGate.isGateUrl("https://cmp.example.it/notice"))
        assertFalse(ConsentGate.isGateUrl("https://www.vtmgo.be/vtmgo/mijn-restaurant~f62bbb08"))
        assertFalse(ConsentGate.isGateUrl("https://www.netflix.com/title/80100172"))
    }

    @Test
    fun `a page about cookies without a picture is a wall, a show page is not`() {
        val wall = PageMetadata(title = "DPG Media Privacy Gate")
        assertTrue(ConsentGate.isGate("https://www.example.be/x", wall))
        assertTrue(ConsentGate.isGate("https://www.example.de/x", PageMetadata(title = "Datenschutz-Einstellungen")))
        assertTrue(ConsentGate.isGate("https://www.example.fr/x", PageMetadata(title = "Gestion des cookies")))
        assertFalse(ConsentGate.isGate("https://www.example.be/x", PageMetadata(title = "Mijn Restaurant", imageUrl = "https://img/x.jpg")))
        // A show about privacy, with its picture, is still a show.
        assertFalse(ConsentGate.isGate("https://www.example.com/x", PageMetadata(title = "The Privacy Files", imageUrl = "https://img/p.jpg")))
        assertFalse(ConsentGate.isGate("https://www.example.com/x", null))
    }

    @Test
    fun `the page a wall returns to is found in any parameter, also nested`() {
        val gate =
            "https://myprivacy.dpgmedia.be/consent?siteKey=k&callbackUrl=" +
                "https%3A%2F%2Fwww.vtmgo.be%2Fprivacy-gate%2Faccept%3FredirectUri%3D%252Fvtmgo%252Fshow%26originalUrl%3Dhttps%253A%252F%252Fwww.vtmgo.be%252Fvtmgo%252Fmijn-restaurant"
        assertEquals("https://www.vtmgo.be/vtmgo/mijn-restaurant", ConsentGate.returnUrl(gate, "https://link.vtmgo.be/vtmgo/mijn-restaurant"))
        assertEquals(
            "https://www.youtube.com/watch?v=x",
            ConsentGate.returnUrl("https://consent.youtube.com/m?continue=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dx&gl=BE", "https://youtu.be/x"),
        )
        assertNull(ConsentGate.returnUrl("https://consent.example.com/?a=1", "https://www.example.com/show"))
    }

    @Test
    fun `the kept link is never a wall and stays on the shared site`() {
        val shared = "https://link.vtmgo.be/vtmgo/mijn-restaurant~f62"
        assertEquals("https://www.vtmgo.be/vtmgo/mijn-restaurant~f62", ConsentGate.chooseLink(shared, "https://myprivacy.dpgmedia.be/consent?x=1", "https://www.vtmgo.be/vtmgo/mijn-restaurant~f62"))
        assertEquals(shared, ConsentGate.chooseLink(shared, "https://myprivacy.dpgmedia.be/consent?x=1", ""))
        assertEquals("https://www.netflix.com/title/1", ConsentGate.chooseLink("https://netflix.com/title/1?s=a", "https://www.netflix.com/title/1?trackId=2&utm_source=x", "")
            .substringBefore("?trackId"))
        assertEquals("bbc.co.uk", ConsentGate.siteOf("https://www.bbc.co.uk/iplayer"))
    }

    @Test
    fun `a wall's details never end up in the favorite`() {
        val link = SharedText.parse("https://link.vtmgo.be/vtmgo/mijn-restaurant~f62bbb08-66af-4496-8c48-23e3d2997d90?utm_source=VTM_GO&utm_medium=share_button")!!
        val draft = DraftBuilder.build(link, "https://myprivacy.dpgmedia.be/consent?siteKey=k", null)
        assertEquals("https://link.vtmgo.be/vtmgo/mijn-restaurant~f62bbb08-66af-4496-8c48-23e3d2997d90", draft.url)
        assertEquals("vtmgo", draft.serviceId)
        assertEquals("Mijn Restaurant", draft.title)
    }

    @Test
    fun `the real page gives title, description, picture and its own link`() {
        val html =
            """
            <head>
            <meta property="og:description" content="Vijf duo's jagen hun droom na." data-next-head="">
            <meta property="og:image" content="https://images4.persgroep.net/rcs/a/264318921/_fitwidth/426?appId=1&amp;quality=0.8" data-next-head="">
            <meta property="og:url" content="https://www.vtmgo.be/vtmgo/mijn-restaurant~f62" data-next-head="">
            <meta property="al:android:url" content="https://www.vtmgo.be/vtmgo/mijn-restaurant~f62" data-next-head="">
            <meta name="og:title" content="Mijn Restaurant - Seizoen 2 - Kijk volledige afleveringen op VTM GO " data-next-head="">
            </head>
            """.trimIndent()
        val page = PageMetadataParser.parse(html, "https://www.vtmgo.be/vtmgo/mijn-restaurant~f62?referrer=x")
        assertEquals("https://www.vtmgo.be/vtmgo/mijn-restaurant~f62", page.canonicalUrl)
        assertEquals("https://images4.persgroep.net/rcs/a/264318921/_fitwidth/426?appId=1&quality=0.8", page.imageUrl)
        val draft = DraftBuilder.build(SharedLink("https://link.vtmgo.be/vtmgo/mijn-restaurant~f62", ""), "https://www.vtmgo.be/vtmgo/mijn-restaurant~f62?referrer=x", page)
        assertEquals("Mijn Restaurant", draft.title)
        assertEquals("Vijf duo's jagen hun droom na.", draft.description)
        assertEquals("https://www.vtmgo.be/vtmgo/mijn-restaurant~f62", draft.url)
    }
}
