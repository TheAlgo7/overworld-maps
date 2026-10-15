// The request and the positions read from the answer follow Vela (github.com/PimpinPumpkin/Vela,
// GPL-3.0): core/data/google/DirectionsPb.kt and parse/DirectionsParser.kt.
package com.thealgothrim.overworld.routing

import android.util.Log
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import uniffi.ferrostar.GeographicCoordinate

/**
 * Google Maps' own driving routes, asked the way its website asks, with no key and no account
 * (`/maps/preview/directions`). Google knows Delhi's traffic, closures and one-ways better than any
 * open data, and its time is the one people compare against. Nothing here is a documented API: it
 * can change or refuse without warning, so every caller has the open routers to fall back on.
 */
class GoogleMaps(http: OkHttpClient) {
  /** One of Google's routes: its line, length, times, the road it names it by, and its jams. */
  data class GoogleRoute(
      val line: List<GeographicCoordinate>,
      val metres: Double,
      /** Google's usual time for the trip at this hour. */
      val typicalSeconds: Double,
      /** With today's traffic; null when Google gives none (late at night). */
      val trafficSeconds: Double?,
      val via: String?,
      val jams: List<Jam>,
  )

  /** A stretch in traffic: [level] 1 slow, 2 heavy; metres along the route. */
  data class Jam(val level: Int, val from: Double, val length: Double)

  /** A place from Google's search: name, address, where, and what it is ("Apostolic church"). */
  data class GooglePlace(val name: String, val address: String?, val at: GeographicCoordinate, val category: String?)

  private val client = http.newBuilder().cookieJar(MemoryCookies()).build()
  @Volatile private var warmedAt = 0L

  suspend fun driving(from: GeographicCoordinate, to: GeographicCoordinate): List<GoogleRoute> =
      withContext(Dispatchers.IO) {
        warm()
        val body = get(ENDPOINT + URLEncoder.encode(pb(from, to), "UTF-8")) ?: return@withContext emptyList()
        runCatching { parse(body) }.onFailure { Log.w(TAG, "couldn't read Google's routes", it) }.getOrDefault(emptyList())
      }

  /**
   * Google Maps' place search for [query] around [near], as its website asks it (`/search?tbm=map`).
   * Google knows the small places OpenStreetMap and TomTom don't (Prarthana Bhavan in Rajpur
   * Khurd Extension was in neither).
   */
  suspend fun search(query: String, near: GeographicCoordinate): List<GooglePlace> =
      withContext(Dispatchers.IO) {
        warm()
        val pb = SEARCH_TEMPLATE.replace("{QUERY}", query.replace('!', ' ').trim()).replace("{LNG}", near.lng.toString()).replace("{LAT}", near.lat.toString())
        val url = SEARCH_ENDPOINT + "&q=" + URLEncoder.encode(query, "UTF-8") + "&pb=" + URLEncoder.encode(pb, "UTF-8")
        val body = get(url) ?: return@withContext emptyList()
        runCatching { parseSearch(body) }.onFailure { Log.w(TAG, "couldn't read Google's search", it) }.getOrDefault(emptyList())
      }

  /** Google answers better once the session has a cookie, as a browser's would. Once an hour. */
  private fun warm() {
    val now = System.currentTimeMillis()
    if (now - warmedAt < 3_600_000) return
    warmedAt = now
    get("https://www.google.com/maps?hl=en")
  }

  private fun get(url: String): String? =
      try {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Accept-Language", "en-IN,en;q=0.9").build()
        client.newCall(request).execute().use { r -> if (r.isSuccessful) r.body.string() else null.also { Log.w(TAG, "Google -> ${r.code}") } }
      } catch (e: Exception) {
        Log.w(TAG, "Google unreachable", e)
        null
      }

  private fun pb(from: GeographicCoordinate, to: GeographicCoordinate): String {
    fun n(v: Double) = String.format(Locale.US, "%.7f", v)
    return TEMPLATE.replace("{OLAT}", n(from.lat))
        .replace("{OLNG}", n(from.lng))
        .replace("{DLAT}", n(to.lat))
        .replace("{DLNG}", n(to.lng))
        .replace("{CLAT}", n((from.lat + to.lat) / 2))
        .replace("{CLNG}", n((from.lng + to.lng) / 2))
  }

  companion object {
    private const val TAG = "GoogleMaps"
    private const val ENDPOINT = "https://www.google.com/maps/preview/directions?authuser=0&hl=en&gl=in&pb="
    private const val SEARCH_ENDPOINT = "https://www.google.com/search?tbm=map&authuser=0&hl=en&gl=in"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    /** Google's web client's place search (Vela's SearchPb template); a ~25 km window around {LAT},{LNG}. */
    private const val SEARCH_TEMPLATE =
        "!1s{QUERY}!4m8!1m3!1d25229.167291701906!2d{LNG}!3d{LAT}!3m2!1i1024!2i768!4f13.1!7i20!10b1!" +
        "12m52!1m5!18b1!30b1!31m1!1b1!34e1!2m4!5m1!6e2!20e3!39b1!6m25!32i1!49b1!63m0!66b1!85b1!114b" +
        "1!149b1!206b1!209b1!212b1!216b1!222b1!223b1!232b1!234b1!235b1!244b1!246b1!250b1!253b1!260b" +
        "1!266b1!273b1!281b1!291m0!10b1!12b1!13b1!14b1!16b1!17m1!3e1!20m3!5e2!6b1!14b1!46m1!1b0!96b" +
        "1!99b1!19m4!2m3!1i360!2i120!4i8!20m57!2m2!1i203!2i100!3m2!2i4!5b1!6m6!1m2!1i86!2i86!1m2!1i" +
        "408!2i240!7m33!1m3!1e1!2b0!3e3!1m3!1e2!2b1!3e2!1m3!1e2!2b0!3e3!1m3!1e8!2b0!3e3!1m3!1e10!2b" +
        "0!3e3!1m3!1e10!2b1!3e2!1m3!1e10!2b0!3e4!1m3!1e9!2b1!3e2!2b1!9b0!15m8!1m7!1m2!1m1!1e2!2m2!1" +
        "i195!2i195!3i20!15i9937!24m107!1m25!13m9!2b1!3b1!4b1!6i1!8b1!9b1!14b1!20b1!25b1!18m14!3b1!" +
        "4b1!5b1!6b1!13b1!14b1!17b1!21b1!22b1!32b1!33m1!1b1!34b1!36e2!10m1!8e3!11m1!3e1!17b1!20m2!1" +
        "e3!1e6!24b1!25b1!26b1!27b1!29b1!30m1!2b1!36b1!37b1!39m3!2m2!2i1!3i1!43b1!52b1!54m1!1b1!55b" +
        "1!56m1!1b1!61m2!1m1!1e1!65m5!3m4!1m3!1m2!1i224!2i298!72m22!1m8!2b1!5b1!7b1!12m4!1b1!2b1!4m" +
        "1!1e1!4b1!8m10!1m6!4m1!1e1!4m1!1e3!4m1!1e4!3sother_user_google_review_posts__and__hotel_an" +
        "d_vr_partner_review_posts!6m1!1e1!9b1!89b1!90m2!1m1!1e2!98m3!1b1!2b1!3b1!103b1!113b1!114m3" +
        "!1b1!2m1!1b1!117b1!122m1!1b1!126b1!127b1!128m1!1b0!26m4!2m3!1i80!2i92!4i8!30m28!1m6!1m2!1i" +
        "0!2i0!2m2!1i530!2i768!1m6!1m2!1i974!2i0!2m2!1i1024!2i768!1m6!1m2!1i0!2i0!2m2!1i1024!2i20!1" +
        "m6!1m2!1i0!2i748!2m2!1i1024!2i768!34m19!2b1!3b1!4b1!6b1!8m6!1b1!3b1!4b1!5b1!6b1!7b1!9b1!12" +
        "b1!14b1!20b1!23b1!25b1!26b1!31b1!37m1!1e81!42b1!49m10!3b1!6m2!1b1!2b1!7m2!1e3!2b1!8b1!9b1!" +
        "10e2!50m3!2e2!3m1!3b1!61b1!67m5!7b1!10b1!14b1!15m1!1b0!69i782!77b1"

    /**
     * The search answer: `)]}'` and JSON (sometimes with a `/*""*/` tail). Results at [64], each
     * a place node at [1]: name [11], lat [9][2], lng [9][3], address [39], kind [13][0]. A search
     * that lands on one place answers it at [0][1][0][14] instead.
     */
    internal fun parseSearch(raw: String): List<GooglePlace> {
      val text = raw.replace("/*\"\"*/", "").let { if (it.startsWith(")]}'")) it.substringAfter('\n') else it }
      val root = JSONArray(text)
      fun place(node: Any?): GooglePlace? {
        val name = node.at(11) as? String ?: return null
        val lat = node.at(9).at(2).num() ?: return null
        val lng = node.at(9).at(3).num() ?: return null
        return GooglePlace(name, node.at(39) as? String, GeographicCoordinate(lat, lng), node.at(13).at(0) as? String)
      }
      val results = root.at(64) as? JSONArray
      if (results != null) return (0 until results.length()).mapNotNull { place(results.opt(it).at(1)) }
      return listOfNotNull(place(root.at(0).at(1).at(0).at(14)))
    }

    /** Google's web client's request, driving (`!20m5!1e0`), with the trip's two ends and a viewport between them. */
    private const val TEMPLATE =
        "!1m4!3m2!3d{OLAT}!4d{OLNG}!6e2!1m4!3m2!3d{DLAT}!4d{DLNG}!6e2" +
            "!3m12!1m3!1d24960.741896132306!2d{CLNG}!3d{CLAT}!2m3!1f0.0!2f0.0!3f0.0" +
            "!3m2!1i1024!2i768!4f13.1!6m56!1m5!18b1!30b1!31m1!1b1!34e1!2m4!5m1!6e2!20e3!39b1!6m27!32i1" +
            "!49b1!63m0!66b1!85b1!114b1!149b1!206b1!209b1!212b1!216b1!222b1!223b1!232b1!234b1!235b1" +
            "!244b1!246b1!250b1!253b1!260b1!266b1!270b1!273b1!279b1!281b1!291m0!10b1!12b1!13b1!14b1" +
            "!16b1!17m1!3e1!20m5!1e0!2e3!5e2!6b1!14b1!46m1!1b0!96b1!99b1!15i10142!20m28!1m6!1m2" +
            "!1i0!2i0!2m2!1i530!2i768!1m6!1m2!1i974!2i0!2m2!1i1024!2i768!1m6!1m2!1i0!2i0!2m2!1i1024" +
            "!2i20!1m6!1m2!1i0!2i748!2m2!1i1024!2i768!27b1!40i783!47m2!8b1!10e2"

    /**
     * The answer: `)]}'` then JSON. Routes at [0][1] (each with its summary at [0]: name [1],
     * metres [2][0], usual seconds [3][0], seconds in traffic [10][0][0]; jams at [3][5][0] as
     * [level, start metres, length]), and each route's line at [0][7][i] as E7 lat and lng deltas.
     */
    internal fun parse(raw: String): List<GoogleRoute> {
      val root = JSONArray(raw.substringAfter('\n'))
      val routes = root.at(0).at(1) as? JSONArray ?: return emptyList()
      val lines = root.at(0).at(7) as? JSONArray
      return (0 until routes.length()).mapNotNull { i ->
        val route = routes.opt(i)
        val summary = route.at(0)
        val metres = summary.at(2).at(0).num() ?: return@mapNotNull null
        val typical = summary.at(3).at(0).num() ?: return@mapNotNull null
        val line = decode(lines?.opt(i)) ?: return@mapNotNull null
        val jams =
            (route.at(3).at(5).at(0) as? JSONArray)?.let { a ->
              (0 until a.length()).mapNotNull { j ->
                val s = a.opt(j)
                val level = s.at(0).num()?.toInt() ?: return@mapNotNull null
                val start = s.at(1).num() ?: return@mapNotNull null
                val length = s.at(2).num() ?: return@mapNotNull null
                if (level < 1 || length <= 0.0) null else Jam(level, start, length)
              }
            }.orEmpty()
        GoogleRoute(line, metres, typical, summary.at(10).at(0).at(0).num(), summary.at(1) as? String, jams.sortedBy { it.from })
      }
    }

    private fun decode(node: Any?): List<GeographicCoordinate>? {
      val lat = node.at(0) as? JSONArray ?: return null
      val lng = node.at(1) as? JSONArray ?: return null
      if (lat.length() != lng.length() || lat.length() < 2) return null
      var a = 0L
      var b = 0L
      return (0 until lat.length()).map { i ->
        a += lat.optLong(i)
        b += lng.optLong(i)
        GeographicCoordinate(a / 1e7, b / 1e7)
      }
    }

    private fun Any?.at(i: Int): Any? = (this as? JSONArray)?.takeIf { i < it.length() }?.opt(i)?.takeIf { it != org.json.JSONObject.NULL }

    private fun Any?.num(): Double? = (this as? Number)?.toDouble()
  }
}

/** Cookies kept for this session only, like a private browser window. */
private class MemoryCookies : CookieJar {
  private val store = ConcurrentHashMap<String, List<Cookie>>()

  override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    store[url.host] = (store[url.host].orEmpty().filter { old -> cookies.none { it.name == old.name } } + cookies)
  }

  override fun loadForRequest(url: HttpUrl): List<Cookie> {
    val now = System.currentTimeMillis()
    return store.entries.filter { url.host.endsWith(it.key.removePrefix("www.")) }.flatMap { it.value }.filter { it.expiresAt > now }
  }
}
