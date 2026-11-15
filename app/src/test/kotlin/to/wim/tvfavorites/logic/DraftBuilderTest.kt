package to.wim.tvfavorites.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftBuilderTest {
    @Test
    fun `draft prefers the page, then the share text, then the path`() {
        val link = SharedLink("https://www.vrt.be/vrtmax/a-z/de-mol/", "De Mol")
        val generic = PageMetadata(title = "VRT MAX", description = "Kijk gratis naar alles van VRT.", imageUrl = "https://vrt.be/logo.png")
        val draft = DraftBuilder.build(link, link.url, generic)
        assertEquals("De Mol", draft.title)
        assertEquals("vrtmax", draft.serviceId)
        // The landing page's general description and logo say nothing about the show.
        assertEquals("", draft.description)
        assertEquals("", draft.imageUrl)

        val page = PageMetadata(title = "De Mol | VRT MAX", description = "Wie is de mol?", imageUrl = "https://vrt.be/mol.jpg")
        val full = DraftBuilder.build(link, link.url, page)
        assertEquals("Wie is de mol?", full.description)
        assertEquals("https://vrt.be/mol.jpg", full.imageUrl)
    }

    @Test
    fun `the landscape picture is preferred over a portrait one`() {
        val html =
            """
            <head>
            <meta property="og:title" content="De Mol">
            <meta property="og:image" content="https://img.vrt.be/tall.jpg">
            <meta property="og:image:width" content="600"><meta property="og:image:height" content="900">
            <meta property="og:image" content="https://img.vrt.be/wide.jpg">
            <meta property="og:image:width" content="1920"><meta property="og:image:height" content="1080">
            </head>
            """.trimIndent()
        assertEquals("https://img.vrt.be/wide.jpg", PageMetadataParser.parse(html, "https://www.vrt.be/").imageUrl)
        // A picture without a size is used as it is; schema.org pictures count too.
        val ld = """<head><meta property="og:title" content="Thuis"></head><body><script type="application/ld+json">{"image":"https:\/\/cdn.x.be\/thuis.jpg"}</script></body>"""
        assertEquals("https://cdn.x.be/thuis.jpg", PageMetadataParser.parse(ld, "https://www.x.be/").imageUrl)
        // Only a portrait picture: still better than none.
        val tall = """<head><meta property="og:image" content="https://a/t.jpg"><meta property="og:image:width" content="2"><meta property="og:image:height" content="3"></head>"""
        assertEquals("https://a/t.jpg", PageMetadataParser.parse(tall, "https://a/").imageUrl)
    }
}
