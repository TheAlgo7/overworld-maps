package com.thealgothrim.overworld.search

import android.util.Log
import com.thealgothrim.overworld.traffic.metres
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
)

/** Kinds of place to find nearby, with TomTom's category numbers. */
enum class Nearby(val label: String, val tomtom: String, val osm: String) {
  FUEL("Petrol & CNG", "7311", """node["amenity"="fuel"]"""),
  FOOD("Food", "7315", """node["amenity"~"^(restaurant|fast_food|cafe)$"]"""),
  PARKING("Parking", "7369,7313", """nwr["amenity"="parking"]"""),
  TOILETS("Toilets", "9932005", """node["amenity"="toilets"]"""),
  HOSPITAL("Hospitals", "7321", """nwr["amenity"~"^(hospital|clinic)$"]"""),
  HOTEL("Hotels", "7314", """nwr["tourism"~"^(hotel|motel|guest_house)$"]"""),
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
    return coroutineScope {
      val tomtom = async { runCatching { tomtom(q, near) }.onFailure { Log.w(TAG, "TomTom search failed", it) }.getOrDefault(emptyList()) }
      val osm = async { runCatching { photonSearch(q, near) }.onFailure { Log.w(TAG, "Photon search failed", it) }.getOrDefault(emptyList()) }
      merge(q, tomtom.await(), osm.await())
    }
  }

  /** The nearest places of a kind, closest first. */
  suspend fun nearby(kind: Nearby, near: GeographicCoordinate): List<Place> =
      withContext(Dispatchers.IO) {
        val places =
            if (tomtomKey.isNotBlank()) {
              runCatching { tomtomNearby(kind, near) }.onFailure { Log.w(TAG, "TomTom nearby failed", it) }.getOrNull()
            } else null
        (places ?: overpassNearby(kind, near)).sortedBy { metres(near, it.coordinate) }
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
    val ref = near ?: town.takeIf { it.isNotBlank() }?.let { t -> runCatching { photonSearch(t, null).firstOrNull()?.coordinate }.getOrNull() }
    val (lat, lng) = decodePlusCode(code, ref?.lat, ref?.lng) ?: return null
    return Place(code, town, GeographicCoordinate(lat, lng))
  }

  // ---------------------------------------------------------------- TomTom

  private suspend fun tomtom(q: String, near: GeographicCoordinate?): List<Place> =
      withContext(Dispatchers.IO) {
        if (tomtomKey.isBlank()) return@withContext emptyList()
        val url =
            "https://api.tomtom.com/search/2/search/${URLEncoder.encode(q, "UTF-8").replace("+", "%20")}.json".toHttpUrl().newBuilder().apply {
              addQueryParameter("key", tomtomKey)
              addQueryParameter("countrySet", "IN")
              addQueryParameter("limit", "8")
              addQueryParameter("typeahead", "true")
              addQueryParameter("language", "en-GB")
              near?.let {
                addQueryParameter("lat", it.lat.toString())
                addQueryParameter("lon", it.lng.toString())
              }
            }
        parseTomTom(fetch(url.build().toString()))
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
    return parseTomTom(fetch(url.toString()))
  }

  private fun parseTomTom(body: String): List<Place> {
    val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
      val r = results.getJSONObject(i)
      val pos = r.optJSONObject("position") ?: return@mapNotNull null
      val address = r.optJSONObject("address")
      val street = address?.optString("freeformAddress").orEmpty()
      val name =
          r.optJSONObject("poi")?.optString("name")?.takeIf { it.isNotBlank() }
              ?: street.substringBefore(',').takeIf { it.isNotBlank() }
              ?: return@mapNotNull null
      val detail = street.split(", ").filter { it.isNotBlank() && it != name }.take(3).joinToString(", ")
      Place(name, detail, GeographicCoordinate(pos.getDouble("lat"), pos.getDouble("lon")))
    }
  }

  // ---------------------------------------------------------------- OpenStreetMap

  private suspend fun photonSearch(q: String, near: GeographicCoordinate?): List<Place> =
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
        parsePhoton(fetch(url.build().toString()))
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
  private fun parsePhoton(body: String): List<Place> {
    val features = JSONObject(body).optJSONArray("features") ?: return emptyList()
    val places =
        (0 until features.length()).mapNotNull { i ->
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
          india to Place(name, if (india) detail else listOf(detail, p.optString("country")).filter { it.isNotBlank() }.joinToString(", "), GeographicCoordinate(lat = c.getDouble(1), lng = c.getDouble(0)))
        }
    return places.sortedByDescending { it.first }.map { it.second }
  }

  /**
   * TomTom's answers and OpenStreetMap's in one list: names that match what was typed first, then
   * TomTom's (it knows Indian businesses better), then OpenStreetMap's. The same spot twice is shown
   * once. Places abroad (Photon's, after the Indian ones) only fill a short list.
   */
  private fun merge(q: String, tomtom: List<Place>, osm: List<Place>): List<Place> {
    val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun matches(p: Place) = words.all { it in p.name.lowercase() }
    val all = (tomtom.map { it to 0 } + osm.map { it to 1 })
    val ranked = all.sortedWith(compareBy<Pair<Place, Int>>({ if (matches(it.first)) 0 else 1 }, { it.second }))
    val out = mutableListOf<Place>()
    for ((place, _) in ranked) {
      if (out.none { metres(it.coordinate, place.coordinate) < 120 && it.name.equals(place.name, ignoreCase = true) }) out += place
    }
    return out.take(10)
  }

  // ---------------------------------------------------------------- plumbing

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

  private companion object {
    const val TAG = "PlaceSearch"
    const val BROWSER = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36"
    val OVERPASS = listOf("https://overpass.kumi.systems/api/interpreter", "https://overpass-api.de/api/interpreter")
    val NEIGHBOURHOOD_KINDS = setOf("suburb", "neighbourhood", "quarter", "village", "town", "hamlet")
    val GENERIC_AREAS =
        setOf("north", "south", "east", "west", "central", "north west", "north east", "south west", "south east", "new delhi", "shahdara")
  }
}
