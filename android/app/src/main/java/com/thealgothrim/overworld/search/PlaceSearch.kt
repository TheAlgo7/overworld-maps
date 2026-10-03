package com.thealgothrim.overworld.search

import android.util.Log
import com.thealgothrim.overworld.DriveLog
import com.thealgothrim.overworld.traffic.RouteLine
import com.thealgothrim.overworld.traffic.angleBetween
import com.thealgothrim.overworld.traffic.bearing
import com.thealgothrim.overworld.traffic.metres
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

data class Place(
    val name: String,
    val detail: String,
    val coordinate: GeographicCoordinate,
    /** Shown instead of the straight-line distance, e.g. "On the way" or "+4 min" for a place along the trip. */
    val note: String? = null,
)

/** Kinds of place to find nearby, with TomTom's category numbers and the words for its route search. */
enum class Nearby(val label: String, val tomtom: String, val words: String, val osm: String) {
  FUEL("Petrol & CNG", "7311", "petrol station", """node["amenity"="fuel"]"""),
  FOOD("Food", "7315", "restaurant", """node["amenity"~"^(restaurant|fast_food|cafe)$"]"""),
  PARKING("Parking", "7369,7313", "parking", """nwr["amenity"="parking"]"""),
  TOILETS("Toilets", "9932005", "toilet", """node["amenity"="toilets"]"""),
  HOSPITAL("Hospitals", "7321", "hospital", """nwr["amenity"~"^(hospital|clinic)$"]"""),
  HOTEL("Hotels", "7314", "hotel", """nwr["tourism"~"^(hotel|motel|guest_house)$"]"""),
}

/**
 * Finding places. Two sources, both free:
 * - TomTom search (with the traffic key): businesses and addresses across India, petrol pumps,
 *   toilets and parking by distance. Searches stay in India.
 * - Photon (komoot, OpenStreetMap): roads, areas and landmarks, India first and the world after.
 * Typed coordinates and plus codes ("F5QR+3F New Delhi", from any Google Maps place page) are read
 * directly. Places only Google knows (a small church, a friend's house) come in by sharing them
 * from Google Maps; see [fromShared].
 */
class PlaceSearch(private val http: OkHttpClient, private val tomtomKey: String) {
  private val photon = "https://photon.komoot.io"

  suspend fun search(query: String, near: GeographicCoordinate?): List<Place> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    written(q, near)?.let { return listOf(it) }
    // Typing back over a word asks the same things again; answer those at once.
    val key = "${q.lowercase()}|${near?.let { "%.2f,%.2f".format(it.lat, it.lng) }}"
    cache.get(key)?.let { return it }
    return coroutineScope {
      // Each source gets a few seconds; a slow one doesn't hold up the other's answers.
      val tomtom = async { withTimeoutOrNull(SOURCE_WAIT) { runCatching { tomtom(q, near) }.onFailure { Log.w(TAG, "TomTom search failed", it) }.getOrNull() } }
      val osm = async { withTimeoutOrNull(SOURCE_WAIT) { runCatching { photonSearch(q, near) }.onFailure { Log.w(TAG, "Photon search failed", it) }.getOrNull() } }
      val t = tomtom.await()
      val o = osm.await()
      val found = rank(q, t.orEmpty() + o.orEmpty())
      DriveLog.note(
          "search \"$q\" near ${DriveLog.at(near)}: TomTom ${t?.size ?: "failed"}, OpenStreetMap ${o?.size ?: "failed"}, " +
              "first ${found.take(3).joinToString(" / ") { it.name }}"
      )
      // Only a full answer is kept; a source that failed is asked again next time.
      if (t != null && o != null) cache.put(key, found)
      found
    }
  }

  /**
   * The nearest places of a kind. On a trip ([ahead] is the road still to drive) the ones along the
   * route come first, soonest reached first with their detour, like Google's search along route;
   * the rest by distance. Moving with no trip, places behind the car ([heading]) count as farther.
   */
  suspend fun nearby(kind: Nearby, near: GeographicCoordinate, heading: Double? = null, ahead: List<GeographicCoordinate>? = null): List<Place> =
      withContext(Dispatchers.IO) {
        val onRoute =
            if (tomtomKey.isNotBlank() && ahead != null && ahead.size >= 2) {
              runCatching { tomtomAlongRoute(kind, ahead) }.onFailure { Log.w(TAG, "TomTom along-route failed", it) }.getOrNull().orEmpty()
            } else emptyList()
        if (onRoute.size >= MAX_ON_ROUTE) return@withContext onRoute.also { DriveLog.note("nearby ${kind.name} at ${DriveLog.at(near)}: ${it.size} along the route") }
        val around =
            (if (tomtomKey.isNotBlank()) runCatching { tomtomNearby(kind, near) }.onFailure { Log.w(TAG, "TomTom nearby failed", it) }.getOrNull() else null)
                ?: overpassNearby(kind, near)
        fun cost(p: Place): Double {
          val d = metres(near, p.coordinate)
          if (heading == null || d < 150) return d
          val off = angleBetween(bearing(near, p.coordinate), heading)
          return d * if (off > 100) 2.5 else if (off > 60) 1.4 else 1.0
        }
        val found = onRoute + around.filter { p -> onRoute.none { metres(it.coordinate, p.coordinate) < 60 } }.sortedBy(::cost)
        DriveLog.note("nearby ${kind.name} at ${DriveLog.at(near)}: ${onRoute.size} along the route, ${found.size} in all, first ${found.firstOrNull()?.name}")
        found
      }

  /**
   * A place shared from another app, usually Google Maps ("Prarthana Bhavan\nhttps://maps.app.goo.gl/...").
   * The short link is opened to find the full one, whose pin gives the exact spot. Text without a
   * link is read as coordinates, a plus code or a search.
   */
  suspend fun fromShared(text: String, near: GeographicCoordinate?): Place? =
      withContext(Dispatchers.IO) {
        val link = linkIn(text)
        val words = text.lines().map { it.trim() }.filter { it.isNotEmpty() && linkIn(it) == null }
        if (link == null) return@withContext search(words.joinToString(" "), near).firstOrNull()
        val full = runCatching { expand(link) }.onFailure { Log.w(TAG, "could not open $link", it) }.getOrNull()
        val at = full?.let { coordinatesInMapsLink(it.first) ?: coordinatesInMapsLink(it.second) }
        val name = words.firstOrNull() ?: full?.first?.let(::placeNameInMapsLink) ?: "Shared place"
        if (at != null) {
          Place(name, words.drop(1).joinToString(", "), GeographicCoordinate(at.first, at.second))
        } else {
          search(name, near).firstOrNull()
        }
      }

  suspend fun reverse(at: GeographicCoordinate): Place? =
      withContext(Dispatchers.IO) {
        val url = "$photon/reverse?lat=${at.lat}&lon=${at.lng}&lang=en&limit=1"
        parsePhoton(fetch(url)).firstOrNull()?.copy(coordinate = at)
      }

  /**
   * The neighbourhood around a point, for the HUD's "Street | Area" line. Asks for the nearest
   * place node; Delhi's OSM districts are often compass words ("South"), so those are skipped.
   */
  suspend fun areaAt(at: GeographicCoordinate): String? =
      withContext(Dispatchers.IO) {
        val body = fetch("$photon/reverse?lat=${at.lat}&lon=${at.lng}&lang=en&limit=1&osm_tag=place")
        val p =
            JSONObject(body).optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties")
                ?: return@withContext null
        val kind = p.optString("osm_value")
        val name = p.optString("name")
        val district = p.optString("district").takeUnless { it.lowercase() in GENERIC_AREAS }
        when {
          kind in NEIGHBOURHOOD_KINDS && name.isNotBlank() -> name
          !district.isNullOrBlank() -> district
          else -> listOf("locality", "city").map { p.optString(it) }.firstOrNull { it.isNotBlank() }
        }
      }

  // ---------------------------------------------------------------- written places

  /** Coordinates or a plus code typed (or pasted) into the search box. */
  private suspend fun written(q: String, near: GeographicCoordinate?): Place? {
    coordinatesIn(q)?.let { (lat, lng) -> return Place("Pinned point", "%.5f, %.5f".format(lat, lng), GeographicCoordinate(lat, lng)) }
    val (code, town) = plusCodeIn(q) ?: return null
    // A short code is relative to a town; use here, or find the named town.
    val ref = near ?: town.takeIf { it.isNotBlank() }?.let { t -> runCatching { photonSearch(t, null).firstOrNull()?.place?.coordinate }.getOrNull() }
    val (lat, lng) = decodePlusCode(code, ref?.lat, ref?.lng) ?: return null
    return Place(code, town, GeographicCoordinate(lat, lng))
  }

  // ---------------------------------------------------------------- TomTom

  private suspend fun tomtom(q: String, near: GeographicCoordinate?): List<Candidate> =
      withContext(Dispatchers.IO) {
        if (tomtomKey.isBlank()) return@withContext emptyList()
        val url =
            "https://api.tomtom.com/search/2/search/${URLEncoder.encode(q, "UTF-8").replace("+", "%20")}.json".toHttpUrl().newBuilder().apply {
              addQueryParameter("key", tomtomKey)
              addQueryParameter("countrySet", "IN")
              addQueryParameter("limit", "10")
              addQueryParameter("typeahead", "true")
              addQueryParameter("language", "en-GB")
              near?.let {
                addQueryParameter("lat", it.lat.toString())
                addQueryParameter("lon", it.lng.toString())
              }
            }
        val results = JSONObject(fetch(url.build().toString())).optJSONArray("results") ?: return@withContext emptyList()
        (0 until results.length()).mapNotNull { i ->
          val r = results.getJSONObject(i)
          val place = tomtomPlace(r) ?: return@mapNotNull null
          val kind =
              when (r.optString("type")) {
                "Geography" -> PlaceKind.AREA
                "Street", "Cross Street" -> PlaceKind.STREET
                "POI" -> PlaceKind.PLACE
                else -> PlaceKind.ADDRESS
              }
          Candidate(place, kind, tomtom = true, rank = i, metres = near?.let { metres(it, place.coordinate) })
        }
      }

  private fun tomtomNearby(kind: Nearby, near: GeographicCoordinate): List<Place> {
    val url =
        "https://api.tomtom.com/search/2/nearbySearch/.json".toHttpUrl().newBuilder()
            .addQueryParameter("key", tomtomKey)
            .addQueryParameter("lat", near.lat.toString())
            .addQueryParameter("lon", near.lng.toString())
            .addQueryParameter("radius", "10000")
            .addQueryParameter("limit", "20")
            .addQueryParameter("categorySet", kind.tomtom)
            .addQueryParameter("language", "en-GB")
            .build()
    val results = JSONObject(fetch(url.toString())).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { tomtomPlace(results.getJSONObject(it)) }
  }

  /**
   * Places of a kind along the road still to drive, within ten minutes' detour. Each says its
   * detour and how far ahead it is; they come soonest reached first, a detour counting twice
   * (there and back to the route).
   */
  private fun tomtomAlongRoute(kind: Nearby, ahead: List<GeographicCoordinate>): List<Place> {
    val points = JSONArray(RouteLine(ahead).spaced(1000).map { JSONObject().put("lat", it.lat).put("lon", it.lng) })
    val url =
        "https://api.tomtom.com/search/2/searchAlongRoute/${URLEncoder.encode(kind.words, "UTF-8").replace("+", "%20")}.json".toHttpUrl().newBuilder()
            .addQueryParameter("key", tomtomKey)
            .addQueryParameter("maxDetourTime", "600")
            .addQueryParameter("limit", "20")
            .addQueryParameter("categorySet", kind.tomtom)
            .addQueryParameter("sortBy", "detourTime")
            .addQueryParameter("language", "en-GB")
            .build()
    val body = JSONObject().put("route", JSONObject().put("points", points)).toString()
    val response =
        http.newCall(Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
          if (!r.isSuccessful) error("Route search failed (${r.code})")
          r.body.string()
        }
    val results = JSONObject(response).optJSONArray("results") ?: return emptyList()
    return (0 until results.length())
        .mapNotNull { i ->
          val r = results.getJSONObject(i)
          val place = tomtomPlace(r) ?: return@mapNotNull null
          val detour = r.optDouble("detourTime", 0.0).coerceAtLeast(0.0)
          val along = r.optDouble("dist", Double.NaN).takeUnless { it.isNaN() }
          val note =
              listOfNotNull(
                      if (detour < 60) "On the way" else "+${(detour / 60).roundToInt()} min",
                      along?.let { "${formatKm(it)} ahead" },
                  )
                  .joinToString(" · ")
          ((along ?: 0.0) / CITY_SPEED + 2 * detour) to place.copy(note = note)
        }
        .sortedBy { it.first }
        .map { it.second }
  }

  private fun tomtomPlace(r: JSONObject): Place? {
    val pos = r.optJSONObject("position") ?: return null
    val address = r.optJSONObject("address")
    val street = address?.optString("freeformAddress").orEmpty()
    val name =
        r.optJSONObject("poi")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: street.substringBefore(',').takeIf { it.isNotBlank() }
            ?: return null
    val detail = street.split(", ").filter { it.isNotBlank() && it != name }.take(3).joinToString(", ")
    return Place(name, detail, GeographicCoordinate(pos.getDouble("lat"), pos.getDouble("lon")))
  }

  // ---------------------------------------------------------------- OpenStreetMap

  private suspend fun photonSearch(q: String, near: GeographicCoordinate?): List<Candidate> =
      withContext(Dispatchers.IO) {
        val url =
            "$photon/api/".toHttpUrl().newBuilder().apply {
              addQueryParameter("q", q)
              addQueryParameter("limit", "10")
              addQueryParameter("lang", "en")
              near?.let {
                addQueryParameter("lat", it.lat.toString())
                addQueryParameter("lon", it.lng.toString())
                // Mostly local results, still able to find other cities.
                addQueryParameter("location_bias_scale", "0.3")
              }
            }
        parsePhotonCandidates(fetch(url.build().toString()), near)
      }

  private fun overpassNearby(kind: Nearby, near: GeographicCoordinate): List<Place> {
    val query = "[out:json][timeout:20];${kind.osm}(around:5000,${near.lat},${near.lng});out center 40;"
    val body =
        OVERPASS.firstNotNullOfOrNull { server ->
          runCatching {
                http.newCall(Request.Builder().url(server).post(okhttp3.FormBody.Builder().add("data", query).build()).build()).execute().use { r ->
                  if (r.isSuccessful) r.body.string() else null
                }
              }
              .getOrNull()
        } ?: return emptyList()
    val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
    return (0 until elements.length()).mapNotNull { i ->
      val e = elements.getJSONObject(i)
      val tags = e.optJSONObject("tags") ?: JSONObject()
      val lat = e.optDouble("lat").takeUnless { it.isNaN() } ?: e.optJSONObject("center")?.optDouble("lat") ?: return@mapNotNull null
      val lng = e.optDouble("lon").takeUnless { it.isNaN() } ?: e.optJSONObject("center")?.optDouble("lon") ?: return@mapNotNull null
      val name = tags.optString("name").ifBlank { tags.optString("brand").ifBlank { kind.label.removeSuffix("s") } }
      Place(name, tags.optString("addr:street"), GeographicCoordinate(lat, lng))
    }
  }

  /** [Place]s from a Photon answer, India first. */
  private fun parsePhoton(body: String): List<Place> =
      parsePhotonCandidates(body, null).sortedBy { it.abroad }.map { it.place }

  private fun parsePhotonCandidates(body: String, near: GeographicCoordinate?): List<Candidate> {
    val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
    return (0 until features.length()).mapNotNull { i ->
      val f = features.getJSONObject(i)
      val p = f.optJSONObject("properties") ?: return@mapNotNull null
      val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
      val street = listOfNotNull(p.opt("housenumber"), p.opt("street")).joinToString(" ")
      val name =
          p.optString("name").ifBlank { street }.ifBlank { p.optString("district") }
              .ifBlank { return@mapNotNull null }
      val detail =
          listOf(
                  street.takeIf { it != name },
                  p.optString("district"),
                  p.optString("city"),
                  p.optString("state"),
              )
              .filter { !it.isNullOrBlank() && it != name }
              .distinct()
              .take(3)
              .joinToString(", ")
      val india = p.optString("countrycode").equals("IN", ignoreCase = true)
      val at = GeographicCoordinate(lat = c.getDouble(1), lng = c.getDouble(0))
      val kind =
          when (p.optString("osm_key")) {
            "place", "boundary" -> PlaceKind.AREA
            "highway" -> if (p.optString("osm_value") in ROAD_KINDS) PlaceKind.STREET else PlaceKind.PLACE
            else -> if (p.has("housenumber")) PlaceKind.ADDRESS else PlaceKind.PLACE
          }
      Candidate(
          Place(name, if (india) detail else listOf(detail, p.optString("country")).filter { it.isNotBlank() }.joinToString(", "), at),
          kind,
          tomtom = false,
          rank = i,
          metres = near?.let { metres(it, at) },
          abroad = !india,
      )
    }
  }

  // ---------------------------------------------------------------- plumbing

  private fun formatKm(metres: Double) = if (metres < 950) "${(metres / 50).roundToInt() * 50} m" else "%.1f km".format(metres / 1000)

  /** Follows a short link to the full one; returns the full URL and the start of the page. */
  private fun expand(link: String): Pair<String, String> =
      http.newBuilder().followRedirects(true).followSslRedirects(true).build()
          .newCall(Request.Builder().url(link).header("User-Agent", BROWSER).build())
          .execute()
          .use { r -> r.request.url.toString() to r.peekBody(400_000).string() }

  private fun fetch(url: String): String =
      http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("Search failed (${response.code})")
        response.body.string()
      }

  /** Recent answers by query and area, for ten minutes. */
  private val cache = AnswerCache()

  private class AnswerCache {
    private val entries =
        object : LinkedHashMap<String, Pair<Long, List<Place>>>(32, 0.75f, true) {
          override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Place>>>) = size > 40
        }

    @Synchronized
    fun get(key: String): List<Place>? = entries[key]?.takeIf { System.currentTimeMillis() - it.first < 600_000 }?.second

    @Synchronized
    fun put(key: String, places: List<Place>) {
      entries[key] = System.currentTimeMillis() to places
    }
  }

  private companion object {
    const val TAG = "PlaceSearch"
    const val SOURCE_WAIT = 3_500L
    /** Enough places along the route that the ones around the car aren't needed. */
    const val MAX_ON_ROUTE = 6
    /** Metres a second, to weigh a place's distance ahead against its detour time. */
    const val CITY_SPEED = 9.0
    val ROAD_KINDS =
        setOf(
            "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service", "living_street", "road",
            "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
        )
    const val BROWSER = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    val OVERPASS = listOf("https://overpass.kumi.systems/api/interpreter", "https://overpass-api.de/api/interpreter")
    val NEIGHBOURHOOD_KINDS = setOf("suburb", "neighbourhood", "quarter", "village", "town", "hamlet")
    val GENERIC_AREAS =
        setOf("north", "south", "east", "west", "central", "north west", "north east", "south west", "south east", "new delhi", "shahdara")
  }
}
