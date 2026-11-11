package to.wim.tvfavorites.logic

/**
 * The TV Favorites add-on for Nuvio (and Stremio): a Cloudflare Worker (`nuvio-addon/`) that
 * shows a household's list as catalogs and opens each favorite through this app's
 * `tvfavorites://open` link. Its address carries the household and its join key, like the QR code.
 */
object NuvioAddon {
    const val BASE_URL = "https://tv-favorites-nuvio.wim-8af.workers.dev"

    /** The address to add in Nuvio. */
    fun address(household: Household): String = "$BASE_URL/${household.id}~${household.joinKey}/manifest.json"
}
