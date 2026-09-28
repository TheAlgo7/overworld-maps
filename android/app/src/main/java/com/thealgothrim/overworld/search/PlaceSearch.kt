package com.thealgothrim.overworld.search

import kotlinx.coroutines.Dispatchers
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

/** Free place search and reverse lookup through Photon (komoot's public OSM geocoder). */
class PlaceSearch(private val http: OkHttpClient) {
  private val base = "https://photon.komoot.io"

  suspend fun search(query: String, near: GeographicCoordinate?): List<Place> =
      withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url =
            "$base/api/".toHttpUrl().newBuilder().apply {
              addQueryParameter("q", query.trim())
              addQueryParameter("limit", "8")
              addQueryParameter("lang", "en")
              near?.let {
                addQueryParameter("lat", it.lat.toString())
                addQueryParameter("lon", it.lng.toString())
                // Mostly local results, still able to find other cities.
                addQueryParameter("location_bias_scale", "0.3")
              }
            }
        parse(fetch(url.build().toString()))
      }

  suspend fun reverse(at: GeographicCoordinate): Place? =
      withContext(Dispatchers.IO) {
        val url = "$base/reverse?lat=${at.lat}&lon=${at.lng}&lang=en&limit=1"
        parse(fetch(url)).firstOrNull()?.copy(coordinate = at)
      }

  /**
   * The neighbourhood around a point, for the HUD's "Street | Area" line. Asks for the nearest
   * place node; Delhi's OSM districts are often compass words ("South"), so those are skipped.
   */
  suspend fun areaAt(at: GeographicCoordinate): String? =
      withContext(Dispatchers.IO) {
        val body = fetch("$base/reverse?lat=${at.lat}&lon=${at.lng}&lang=en&limit=1&osm_tag=place")
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

  private companion object {
    val NEIGHBOURHOOD_KINDS = setOf("suburb", "neighbourhood", "quarter", "village", "town", "hamlet")
    val GENERIC_AREAS =
        setOf("north", "south", "east", "west", "central", "north west", "north east", "south west", "south east", "new delhi", "shahdara")
  }

  private fun fetch(url: String): String =
      http.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("Search failed (${response.code})")
        response.body.string()
      }

  private fun parse(body: String): List<Place> {
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
      Place(name, detail, GeographicCoordinate(lat = c.getDouble(1), lng = c.getDouble(0)))
    }
  }
}
