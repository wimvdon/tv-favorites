package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import to.wim.tvfavorites.model.ServiceOverride

class DeepLinksTest {
    private val installed =
        listOf(
            InstalledApp("com.netflix.ninja", "Netflix"),
            InstalledApp("be.example.vtmgo.tv", "VTM GO"),
            InstalledApp("com.example.player", "Some player"),
        )

    @Test
    fun `services are matched by host and path`() {
        assertEquals("netflix", ServiceCatalog.match("https://www.netflix.com/be-nl/title/80100172")?.id)
        assertEquals("vrtmax", ServiceCatalog.match("https://www.vrt.be/vrtmax/a-z/de-mol/")?.id)
        assertEquals("prime", ServiceCatalog.match("https://www.amazon.de/gp/video/detail/B0B")?.id)
        assertNull(ServiceCatalog.match("https://www.amazon.de/some-book/dp-x"))
        assertEquals("iplayer", ServiceCatalog.match("https://www.bbc.co.uk/iplayer/episode/m001")?.id)
        assertEquals("example.org", ServiceCatalog.serviceIdFor("https://www.example.org/show"))
        assertEquals("example.org", ServiceCatalog.nameOf("example.org"))
    }

    @Test
    fun `the app is found by package, by name, or as picked`() {
        assertEquals("com.netflix.ninja", DeepLinks.findApp("netflix", null, installed)?.packageName)
        // Not a known package, but the TV app carries the service's name.
        assertEquals("be.example.vtmgo.tv", DeepLinks.findApp("vtmgo", null, installed)?.packageName)
        assertEquals("com.example.player", DeepLinks.findApp("vtmgo", ServiceOverride(packageName = "com.example.player"), installed)?.packageName)
        // A picked app that isn't installed falls back to finding one.
        assertEquals("be.example.vtmgo.tv", DeepLinks.findApp("vtmgo", ServiceOverride(packageName = "gone.app"), installed)?.packageName)
        assertNull(DeepLinks.findApp("goplay", null, installed))
        assertNull(DeepLinks.findApp("example.org", null, installed))
    }

    @Test
    fun `the plan tries the pattern, the service link, the shared link, then the app`() {
        val url = "https://www.netflix.com/be-nl/title/80100172"
        val plan = DeepLinks.plan(url, "netflix", "com.netflix.ninja", ServiceOverride(linkTemplate = "nflx://www.netflix.com/title/{id}"))
        assertEquals(
            listOf(
                LaunchAttempt.View("nflx://www.netflix.com/title/80100172", "com.netflix.ninja"),
                LaunchAttempt.View("https://www.netflix.com/title/80100172", "com.netflix.ninja"),
                LaunchAttempt.View(url, "com.netflix.ninja"),
                LaunchAttempt.OpenApp("com.netflix.ninja"),
            ),
            plan,
        )
    }

    @Test
    fun `without an app only the link itself is tried`() {
        assertEquals(listOf(LaunchAttempt.View("https://x.org/a", null)), DeepLinks.plan("https://x.org/a", "x.org", null, null))
    }

    @Test
    fun `templates fill in parts of the link`() {
        val url = "https://www.goplay.be/video/de-slimste-mens/123?season=2"
        assertEquals("goplay://show/123", DeepLinks.applyTemplate("goplay://show/{id}", url))
        assertEquals("x:/video/de-slimste-mens/123?season=2", DeepLinks.applyTemplate("x:{path}?{query}", url))
        assertEquals("a?u=https%3A%2F%2Fwww.goplay.be%2Fvideo%2Fde-slimste-mens%2F123%3Fseason%3D2", DeepLinks.applyTemplate("a?u={url_encoded}", url))
    }

    @Test
    fun `youtube and netflix links are rewritten for their TV apps`() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", youtubeLink("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=abcdefgh", youtubeLink("https://www.youtube.com/shorts/abcdefgh"))
        assertEquals("https://www.netflix.com/title/80100172", netflixLink("https://www.netflix.com/be-nl/watch/80100172?trackId=1"))
    }
}
