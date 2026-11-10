package to.wim.tvfavorites.logic

import to.wim.tvfavorites.model.ServiceOverride
import java.net.URI
import java.net.URLEncoder

/** One way to open a favorite, tried in order until one works. */
sealed interface LaunchAttempt {
    /** Open [uri] in [packageName] (any app when null). */
    data class View(val uri: String, val packageName: String?) : LaunchAttempt

    /** Open the app's start screen: the show itself couldn't be opened. */
    data class OpenApp(val packageName: String) : LaunchAttempt
}

/** An installed app, as Android describes it. */
data class InstalledApp(val packageName: String, val label: String)

object DeepLinks {
    /**
     * The TV app for [serviceId]: the one picked in settings when it is installed, then a known
     * package, then an installed app with the service's name. Null when none is installed.
     */
    fun findApp(serviceId: String, override: ServiceOverride?, installed: List<InstalledApp>): InstalledApp? {
        val byPackage = installed.associateBy { it.packageName }
        override?.packageName?.trim()?.takeIf { it.isNotEmpty() }?.let { picked -> byPackage[picked]?.let { return it } }
        val service = ServiceCatalog.byId(serviceId)
        val candidates = ServiceCatalog.all.filter { it.id == serviceId }
        candidates.flatMap { it.tvPackages }.forEach { pkg -> byPackage[pkg]?.let { return it } }
        val labels = (candidates.flatMap { it.appLabels } + listOfNotNull(service?.name)).map(Titles::normalize).filter { it.isNotEmpty() }.distinct()
        if (labels.isEmpty()) return null
        // An exact name first ("VTM GO"), then a name that starts with it ("VTM GO TV").
        return installed.firstOrNull { Titles.normalize(it.label) in labels }
            ?: installed.firstOrNull { app -> labels.any { Titles.normalize(app.label).startsWith("$it ") } }
    }

    /**
     * The ways to open [url] on the TV, best first: the hand-made link pattern, the link in the
     * form the service's app understands, the shared link itself, and finally the app's start
     * screen. Without an app ([packageName] null) only the link itself, for any app that takes it.
     */
    fun plan(url: String, serviceId: String, packageName: String?, override: ServiceOverride?): List<LaunchAttempt> {
        if (packageName == null) return listOf(LaunchAttempt.View(url, null))
        val service = ServiceCatalog.byId(serviceId)
        val links = buildList {
            override?.linkTemplate?.takeIf { it.isNotBlank() }?.let { add(applyTemplate(it, url)) }
            service?.let { add(it.tvLink(url)) }
            add(url)
        }.distinct()
        return links.map { LaunchAttempt.View(it, packageName) } + LaunchAttempt.OpenApp(packageName)
    }

    /**
     * Fills a link pattern: {url} the shared link (encoded with {url_encoded}), {id} its last
     * path part, {path} its path and {query} its query.
     */
    fun applyTemplate(template: String, url: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        val path = uri?.rawPath.orEmpty()
        val id = path.trimEnd('/').substringAfterLast('/')
        return template
            .replace("{url_encoded}", URLEncoder.encode(url, "UTF-8"))
            .replace("{url}", url)
            .replace("{id}", id)
            .replace("{path}", path)
            .replace("{query}", uri?.rawQuery.orEmpty())
            .trim()
    }
}
