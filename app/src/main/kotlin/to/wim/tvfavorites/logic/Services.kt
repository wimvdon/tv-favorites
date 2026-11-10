package to.wim.tvfavorites.logic

import java.net.URI
import java.util.Locale

/**
 * A streaming service the app knows. The TV app is found by [tvPackages] first, then by an
 * installed TV app whose name matches one of [appLabels] (so a renamed package still works),
 * unless the user picked one in Settings › Streaming apps.
 */
data class StreamingService(
    val id: String,
    val name: String,
    /** Hosts (and their subdomains) that belong to this service. */
    val hosts: List<String>,
    /** Only links whose path starts with one of these count (for hosts shared with other content). */
    val pathPrefixes: List<String> = emptyList(),
    val tvPackages: List<String> = emptyList(),
    val appLabels: List<String> = listOf(name),
    /** For regional services: the countries ("BE") and languages ("nl") of their own shows, to pick the right TMDB title. */
    val countries: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    /** Turns a shared link into the link the TV app understands best. */
    val tvLink: (String) -> String = { it },
)

object ServiceCatalog {
    val all: List<StreamingService> =
        listOf(
            // Belgium
            StreamingService(
                id = "vrtmax",
                countries = setOf("BE"),
                languages = setOf("nl"),
                name = "VRT MAX",
                hosts = listOf("vrt.be", "vrtmax.be", "vrtnu.be"),
                tvPackages = listOf("be.vrt.vrtmax", "be.vrt.vrtnu", "be.vrt.vrtmax.tv"),
                appLabels = listOf("VRT MAX", "VRT NU"),
            ),
            StreamingService(
                id = "vtmgo",
                countries = setOf("BE"),
                languages = setOf("nl"),
                name = "VTM GO",
                hosts = listOf("vtmgo.be", "vtm.be"),
                tvPackages = listOf("be.vmma.vtm.zenderapp", "be.dpgmedia.vtmgo", "be.vtmgo.androidtv"),
                appLabels = listOf("VTM GO"),
            ),
            StreamingService(
                id = "goplay",
                countries = setOf("BE"),
                languages = setOf("nl"),
                name = "GoPlay",
                hosts = listOf("goplay.be", "play.tv", "playtv.be"),
                tvPackages = listOf("be.goplay.app", "be.sbs.vier", "be.goplay"),
                appLabels = listOf("GoPlay", "Play"),
            ),
            StreamingService(
                id = "streamz",
                countries = setOf("BE"),
                languages = setOf("nl"),
                name = "Streamz",
                hosts = listOf("streamz.be", "streamz.nl"),
                tvPackages = listOf("be.dpgmedia.streamz", "be.streamz.androidtv"),
            ),
            StreamingService(
                id = "auvio",
                countries = setOf("BE"),
                languages = setOf("fr"),
                name = "Auvio",
                hosts = listOf("auvio.rtbf.be"),
                tvPackages = listOf("be.rtbf.auvio"),
            ),
            StreamingService(
                id = "rtlplay",
                countries = setOf("BE"),
                languages = setOf("fr"),
                name = "RTL play",
                hosts = listOf("rtlplay.be"),
                tvPackages = listOf("be.rtl.info"),
            ),
            StreamingService(
                id = "pickx",
                countries = setOf("BE"),
                languages = setOf("nl", "fr"),
                name = "Pickx",
                hosts = listOf("pickx.be"),
                tvPackages = listOf("be.proximus.pickx", "com.proximus.proximustv"),
            ),
            // Netherlands
            StreamingService(
                id = "npo",
                countries = setOf("NL"),
                languages = setOf("nl"),
                name = "NPO Start",
                hosts = listOf("npo.nl", "npostart.nl"),
                tvPackages = listOf("nl.uitzendinggemist", "nl.npo.player.tv"),
                appLabels = listOf("NPO Start", "NPO"),
            ),
            StreamingService(
                id = "videoland",
                countries = setOf("NL"),
                languages = setOf("nl"),
                name = "Videoland",
                hosts = listOf("videoland.com", "v2.videoland.com"),
                tvPackages = listOf("nl.rtl.videoland.v2", "nl.rtl.videoland"),
            ),
            StreamingService(
                id = "pathethuis",
                countries = setOf("NL"),
                languages = setOf("nl"),
                name = "Pathé Thuis",
                hosts = listOf("pathe-thuis.nl"),
                tvPackages = listOf("nl.pathe.thuis"),
                appLabels = listOf("Pathé Thuis", "Pathe Thuis"),
            ),
            // International
            StreamingService(
                id = "netflix",
                name = "Netflix",
                hosts = listOf("netflix.com"),
                tvPackages = listOf("com.netflix.ninja"),
                tvLink = ::netflixLink,
            ),
            StreamingService(
                id = "prime",
                name = "Prime Video",
                hosts = listOf("primevideo.com", "app.primevideo.com"),
                tvPackages = listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
                appLabels = listOf("Prime Video", "Amazon Prime Video"),
            ),
            StreamingService(
                id = "prime",
                name = "Prime Video",
                hosts = listOf("amazon.com", "amazon.de", "amazon.co.uk", "amazon.fr", "amazon.it", "amazon.es", "amazon.nl", "amazon.com.be"),
                pathPrefixes = listOf("/gp/video", "/dp/"),
                tvPackages = listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
                appLabels = listOf("Prime Video", "Amazon Prime Video"),
            ),
            StreamingService(
                id = "disney",
                name = "Disney+",
                hosts = listOf("disneyplus.com", "disneyplus.bn5x.net"),
                tvPackages = listOf("com.disney.disneyplus"),
                appLabels = listOf("Disney+", "Disney Plus"),
            ),
            StreamingService(
                id = "hbomax",
                name = "HBO Max",
                hosts = listOf("hbomax.com", "max.com", "play.max.com", "play.hbomax.com"),
                tvPackages = listOf("com.wbd.stream", "com.hbo.hbonow"),
                appLabels = listOf("HBO Max", "Max"),
            ),
            StreamingService(
                id = "appletv",
                name = "Apple TV",
                hosts = listOf("tv.apple.com"),
                tvPackages = listOf("com.apple.atve.androidtv.appletv"),
                appLabels = listOf("Apple TV", "Apple TV+"),
            ),
            StreamingService(
                id = "youtube",
                name = "YouTube",
                hosts = listOf("youtube.com", "youtu.be", "m.youtube.com"),
                tvPackages = listOf("com.google.android.youtube.tv", "com.google.android.youtube.googletv"),
                tvLink = ::youtubeLink,
            ),
            StreamingService(
                id = "skyshowtime",
                name = "SkyShowtime",
                hosts = listOf("skyshowtime.com"),
                tvPackages = listOf("com.skyshowtime.skyshowtime.google", "com.skyshowtime.skyshowtime"),
            ),
            StreamingService(
                id = "paramount",
                name = "Paramount+",
                hosts = listOf("paramountplus.com"),
                tvPackages = listOf("com.cbs.ott", "com.cbs.ca"),
                appLabels = listOf("Paramount+", "Paramount Plus"),
            ),
            StreamingService(
                id = "peacock",
                name = "Peacock",
                hosts = listOf("peacocktv.com"),
                tvPackages = listOf("com.peacocktv.peacockandroid"),
            ),
            StreamingService(
                id = "hulu",
                name = "Hulu",
                hosts = listOf("hulu.com"),
                tvPackages = listOf("com.hulu.livingroomplus", "com.hulu.plus"),
            ),
            StreamingService(
                id = "viaplay",
                name = "Viaplay",
                hosts = listOf("viaplay.com", "viaplay.nl", "viaplay.se", "viaplay.dk", "viaplay.no", "viaplay.fi"),
                tvPackages = listOf("com.viaplay.android"),
            ),
            StreamingService(
                id = "canalplus",
                countries = setOf("FR"),
                languages = setOf("fr"),
                name = "Canal+",
                hosts = listOf("canalplus.com", "canalplus.fr"),
                tvPackages = listOf("com.canal.android.canal", "com.canalplus.canalplus"),
                appLabels = listOf("Canal+", "myCanal"),
            ),
            StreamingService(
                id = "rakuten",
                name = "Rakuten TV",
                hosts = listOf("rakuten.tv"),
                tvPackages = listOf("tv.wuaki.apptv", "tv.wuaki"),
            ),
            StreamingService(
                id = "plutotv",
                name = "Pluto TV",
                hosts = listOf("pluto.tv"),
                tvPackages = listOf("tv.pluto.android"),
            ),
            StreamingService(
                id = "crunchyroll",
                name = "Crunchyroll",
                hosts = listOf("crunchyroll.com"),
                tvPackages = listOf("com.crunchyroll.crunchyroid"),
            ),
            StreamingService(
                id = "mubi",
                name = "MUBI",
                hosts = listOf("mubi.com"),
                tvPackages = listOf("com.mubi"),
            ),
            StreamingService(
                id = "dazn",
                name = "DAZN",
                hosts = listOf("dazn.com"),
                tvPackages = listOf("com.dazn"),
            ),
            StreamingService(
                id = "discoveryplus",
                name = "discovery+",
                hosts = listOf("discoveryplus.com"),
                tvPackages = listOf("com.discovery.discoveryplus.androidtv", "com.discovery.discoveryplus.mobile"),
                appLabels = listOf("discovery+", "Discovery Plus"),
            ),
            StreamingService(
                id = "tubi",
                name = "Tubi",
                hosts = listOf("tubitv.com", "tubi.tv"),
                tvPackages = listOf("com.tubitv"),
            ),
            StreamingService(
                id = "plex",
                name = "Plex",
                hosts = listOf("plex.tv", "watch.plex.tv"),
                tvPackages = listOf("com.plexapp.android"),
            ),
            // Germany, France, United Kingdom, Italy, Spain
            StreamingService(
                id = "ard",
                countries = setOf("DE"),
                languages = setOf("de"),
                name = "ARD Mediathek",
                hosts = listOf("ardmediathek.de"),
                tvPackages = listOf("de.swr.ard.avp.mobile.android.amazon", "de.ard.mediathek.tv.android"),
            ),
            StreamingService(
                id = "zdf",
                countries = setOf("DE"),
                languages = setOf("de"),
                name = "ZDFmediathek",
                hosts = listOf("zdf.de"),
                tvPackages = listOf("com.zdf.android.mediathek"),
                appLabels = listOf("ZDFmediathek", "ZDF"),
            ),
            StreamingService(
                id = "joyn",
                countries = setOf("DE"),
                languages = setOf("de"),
                name = "Joyn",
                hosts = listOf("joyn.de", "joyn.at"),
                tvPackages = listOf("de.prosiebensat1digital.seventv"),
            ),
            StreamingService(
                id = "rtlplus",
                countries = setOf("DE"),
                languages = setOf("de"),
                name = "RTL+",
                hosts = listOf("plus.rtl.de"),
                tvPackages = listOf("de.cbc.tvnow.firetv", "de.cbc.tvnowplus"),
            ),
            StreamingService(
                id = "arte",
                countries = setOf("FR", "DE"),
                languages = setOf("fr", "de"),
                name = "Arte",
                hosts = listOf("arte.tv"),
                tvPackages = listOf("tv.arte.plus7"),
            ),
            StreamingService(
                id = "francetv",
                countries = setOf("FR"),
                languages = setOf("fr"),
                name = "france.tv",
                hosts = listOf("france.tv"),
                tvPackages = listOf("fr.francetv.pluzz"),
            ),
            StreamingService(
                id = "iplayer",
                countries = setOf("GB"),
                languages = setOf("en"),
                name = "BBC iPlayer",
                hosts = listOf("bbc.co.uk"),
                pathPrefixes = listOf("/iplayer"),
                tvPackages = listOf("uk.co.bbc.iplayer"),
            ),
            StreamingService(
                id = "itvx",
                countries = setOf("GB"),
                languages = setOf("en"),
                name = "ITVX",
                hosts = listOf("itv.com"),
                tvPackages = listOf("air.ITVMobilePlayer"),
            ),
            StreamingService(
                id = "channel4",
                countries = setOf("GB"),
                languages = setOf("en"),
                name = "Channel 4",
                hosts = listOf("channel4.com"),
                tvPackages = listOf("com.channel4.ondemand"),
            ),
            StreamingService(
                id = "raiplay",
                countries = setOf("IT"),
                languages = setOf("it"),
                name = "RaiPlay",
                hosts = listOf("raiplay.it"),
                tvPackages = listOf("it.rainet.androidtv", "it.rainet.raiplay"),
            ),
            StreamingService(
                id = "mediaset",
                countries = setOf("IT"),
                languages = setOf("it"),
                name = "Mediaset Infinity",
                hosts = listOf("mediasetinfinity.mediaset.it", "mediasetplay.mediaset.it"),
                tvPackages = listOf("it.mediaset.infinitytv", "it.mediaset.mediasetplay"),
            ),
            StreamingService(
                id = "movistar",
                countries = setOf("ES"),
                languages = setOf("es"),
                name = "Movistar Plus+",
                hosts = listOf("movistarplus.es"),
                tvPackages = listOf("es.plus.yomvi"),
            ),
            StreamingService(
                id = "atresplayer",
                countries = setOf("ES"),
                languages = setOf("es"),
                name = "atresplayer",
                hosts = listOf("atresplayer.com"),
                tvPackages = listOf("com.a3.sgt"),
            ),
        )

    /** One entry per service id, for settings lists. */
    val distinct: List<StreamingService> = all.distinctBy { it.id }

    fun byId(id: String): StreamingService? = all.firstOrNull { it.id == id }

    /**
     * The service a link belongs to, or null for a site the app doesn't know. [hostOf] then
     * gives the id the app uses for it.
     */
    fun match(url: String): StreamingService? {
        val uri = parse(url) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
        val path = uri.rawPath.orEmpty()
        return all.firstOrNull { service ->
            service.hosts.any { host == it || host.endsWith(".$it") } &&
                (service.pathPrefixes.isEmpty() || service.pathPrefixes.any { path.startsWith(it) })
        }
    }

    /** The id to store for [url]: a known service, or the link's host ("example.com"). */
    fun serviceIdFor(url: String): String = match(url)?.id ?: hostOf(url) ?: "other"

    /** The name to show for [serviceId]: the service's name, or the host itself. */
    fun nameOf(serviceId: String): String = byId(serviceId)?.name ?: serviceId

    fun hostOf(url: String): String? =
        parse(url)?.host?.lowercase(Locale.ROOT)?.removePrefix("www.")?.takeIf { it.isNotBlank() }

    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()?.takeIf { it.host != null }
}

/** Netflix's TV app opens a title page from https://www.netflix.com/title/<id> (without a country part). */
internal fun netflixLink(url: String): String {
    val id = Regex("""/(?:title|watch)/(\d+)""").find(url)?.groupValues?.get(1) ?: return url
    return "https://www.netflix.com/title/$id"
}

/** YouTube's TV app understands watch?v= links, not youtu.be or shorts links. */
internal fun youtubeLink(url: String): String {
    val id =
        Regex("""youtu\.be/([\w-]{6,})""").find(url)?.groupValues?.get(1)
            ?: Regex("""/shorts/([\w-]{6,})""").find(url)?.groupValues?.get(1)
            ?: return url
    return "https://www.youtube.com/watch?v=$id"
}
