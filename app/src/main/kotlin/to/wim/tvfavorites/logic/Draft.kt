package to.wim.tvfavorites.logic

/** The details of a new favorite, before the user saves it. */
data class Draft(
    val url: String,
    val serviceId: String,
    val title: String,
    val description: String,
    val imageUrl: String,
)

object DraftBuilder {
    /**
     * Combines what is known about a shared link: the page's own title, description and
     * picture first, then the title in the shared text, then the link's path.
     */
    fun build(link: SharedLink, finalUrl: String, page: PageMetadata?): Draft {
        // Never a consent wall's address: the page's own link or the shared one.
        val url = ConsentGate.chooseLink(link.url, finalUrl.ifBlank { link.url }, page?.canonicalUrl.orEmpty())
        val service = ServiceCatalog.match(url) ?: ServiceCatalog.match(link.url)
        val serviceId = service?.id ?: ServiceCatalog.serviceIdFor(url)
        val serviceName = service?.name ?: ServiceCatalog.nameOf(serviceId)
        // A page whose title is only the site's name (a sign-in wall or a landing page) has the
        // site's general description and picture too: those say nothing about the show.
        val usefulPage = page?.takeUnless { Titles.isGeneric(Titles.clean(it.title, serviceName, it.siteName), serviceName, it.siteName) }
        return Draft(
            url = url,
            serviceId = serviceId,
            title = titleFor(link, url, page, serviceName),
            description = usefulPage?.description.orEmpty(),
            imageUrl = usefulPage?.imageUrl.orEmpty(),
        )
    }

    /** The show's title: the page's, else the one in the shared text, else the link's path. */
    fun titleFor(link: SharedLink, url: String, page: PageMetadata?, serviceName: String): String {
        val fromPage = page?.let { Titles.clean(it.title, serviceName, it.siteName) }.orEmpty()
        if (!Titles.isGeneric(fromPage, serviceName, page?.siteName.orEmpty())) return fromPage
        val fromShare = Titles.clean(link.titleHint, serviceName)
        if (!Titles.isGeneric(fromShare, serviceName)) return fromShare
        return SharedText.titleFromPath(url)
    }
}
