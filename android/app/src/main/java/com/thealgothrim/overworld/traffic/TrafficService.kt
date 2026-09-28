package com.thealgothrim.overworld.traffic

import android.util.Log
import com.thealgothrim.overworld.BuildConfig
import com.thealgothrim.overworld.AppModule
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

/** Things on or near the road worth showing: signals, cameras and live incidents. */
enum class RoadFeatureKind(val incident: Boolean, val label: String) {
  TRAFFIC_LIGHT(false, "Traffic light"),
  SPEED_CAMERA(false, "Speed camera"),
  ACCIDENT(true, "Accident"),
  ROADWORKS(true, "Road works"),
  CLOSURE(true, "Road closed"),
  LANE_CLOSED(true, "Lane closed"),
  JAM(true, "Traffic jam"),
  BROKEN_DOWN(true, "Broken-down vehicle"),
  FLOODING(true, "Flooding"),
  HAZARD(true, "Hazard"),
}

data class RoadFeature(
    val kind: RoadFeatureKind,
    val at: GeographicCoordinate,
    val description: String? = null,
    /** Delay in seconds the incident adds, when known. */
    val delaySeconds: Double = 0.0,
    /** 0 unknown, 1 minor, 2 moderate, 3 major, 4 closure. */
    val magnitude: Int = 0,
    /** Metres along the route where it sits; null when it is not on the route. */
    val along: Double? = null,
)

/** Travel time with live traffic for the rest of a trip. */
data class TrafficEta(val travelSeconds: Double, val delaySeconds: Double, val noTrafficSeconds: Double) {
  /** 0 = clear, 1 = some traffic, 2 = heavy, the way Google colours the time. */
  val level: Int
    get() {
      val ratio = if (noTrafficSeconds > 0) delaySeconds / noTrafficSeconds else 0.0
      return when {
        ratio < 0.12 -> 0
        ratio < 0.35 -> 1
        else -> 2
      }
    }
}

/**
 * Road data around a route.
 * - Traffic lights and speed cameras come from OpenStreetMap through the Overpass API (no key).
 * - Live traffic flow, incidents (accidents, road works, closures, jams) and traffic-aware travel
 *   time come from TomTom's free tier, and only when a key is set (tomtomKey in local.properties).
 */
class TrafficService(private val http: OkHttpClient) {
  val tomtomKey: String = BuildConfig.TOMTOM_KEY
  val hasLiveTraffic: Boolean
    get() = tomtomKey.isNotBlank()

  /** Vector tile URL for TomTom's traffic flow layer, or null without a key. */
  val flowTilesUrl: String?
    get() =
        if (hasLiveTraffic) "https://api.tomtom.com/traffic/map/4/tile/flow/relative/{z}/{x}/{y}.pbf?key=$tomtomKey"
        else null

  /** Overpass can take its full 25 s server timeout on a long route; the shared client stops at 20 s. */
  private val slowHttp: OkHttpClient by lazy {
    http.newBuilder().callTimeout(45, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
  }

  // ------------------------------------------------------------------ OpenStreetMap

  /**
   * Traffic lights along the route, from the routing engine itself: Valhalla walks the route's
   * edges and flags every junction with signals (OpenStreetMap highway=traffic_signals). One quick
   * call to the server that made the route, instead of a slow Overpass search.
   */
  suspend fun trafficLights(route: List<GeographicCoordinate>): List<RoadFeature> =
      withContext(Dispatchers.IO) {
        if (route.size < 2) return@withContext emptyList()
        val shape = encodePolyline6(route)
        val body =
            listOf("edge_walk", "map_snap").firstNotNullOfOrNull { match -> traceAttributes(shape, match) }
                ?: return@withContext emptyList()
        val root = JSONObject(body)
        val matched = root.optString("shape").takeIf { it.isNotEmpty() }?.let(::decodePolyline6) ?: route
        val edges = root.optJSONArray("edges") ?: return@withContext emptyList()
        val line = RouteLine(matched)
        val lights = mutableListOf<RoadFeature>()
        for (i in 0 until edges.length()) {
          val edge = edges.getJSONObject(i)
          val signal = edge.optBoolean("traffic_signal") || edge.optJSONObject("end_node")?.optBoolean("traffic_signal") == true
          val at = matched.getOrNull(edge.optInt("end_shape_index", -1)) ?: continue
          // A big junction can have a signal on each carriageway; show one light per junction.
          if (signal && lights.none { metres(it.at, at) < 55.0 }) {
            lights += RoadFeature(RoadFeatureKind.TRAFFIC_LIGHT, at, along = line.project(at).first)
          }
        }
        lights
      }

  private fun traceAttributes(shape: String, match: String): String? {
    val payload =
        JSONObject()
            .put("encoded_polyline", shape)
            .put("shape_match", match)
            .put("costing", "auto")
            .put(
                "filters",
                JSONObject()
                    .put("attributes", JSONArray(listOf("shape", "edge.end_shape_index", "edge.traffic_signal", "node.traffic_signal")))
                    .put("action", "include"),
            )
    return try {
      val request = Request.Builder().url(TRACE_URL).post(payload.toString().toRequestBody(JSON)).build()
      http.newCall(request).execute().use { r ->
        if (r.isSuccessful) r.body.string() else null.also { Log.w(TAG, "trace_attributes $match -> ${r.code}") }
      }
    } catch (e: Exception) {
      Log.w(TAG, "trace_attributes $match failed", e)
      null
    }
  }

  /**
   * Speed cameras along the route, from OpenStreetMap through Overpass. Overpass is quick at
   * "cameras in this box" and slow at "cameras near a long line", so this asks for small boxes
   * along the route and measures the distance here.
   */
  suspend fun speedCameras(route: List<GeographicCoordinate>): List<RoadFeature> =
      withContext(Dispatchers.IO) {
        if (route.size < 2) return@withContext emptyList()
        val line = RouteLine(route)
        val boxes = line.chunkBoxes(chunkMetres = 4000.0, padMetres = 60.0).joinToString("") { (s, w, n, e) ->
          "node[highway=speed_camera](${String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", s, w, n, e)});"
        }
        val body = overpass("[out:json][timeout:25];($boxes);out body;") ?: return@withContext emptyList()
        val elements = JSONObject(body).optJSONArray("elements") ?: return@withContext emptyList()
        (0 until elements.length())
            .mapNotNull { i ->
              val e = elements.getJSONObject(i)
              val at = GeographicCoordinate(e.getDouble("lat"), e.getDouble("lon"))
              val (along, off) = line.project(at)
              if (off <= 30.0) RoadFeature(RoadFeatureKind.SPEED_CAMERA, at, along = along) else null
            }
            .sortedBy { it.along }
      }

  private suspend fun overpass(query: String): String? {
    val servers = listOf("https://overpass-api.de/api/interpreter", "https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
    for ((i, url) in servers.withIndex()) {
      try {
        val request = Request.Builder().url(url).post(FormBody.Builder().add("data", query).build()).build()
        slowHttp.newCall(request).execute().use { r ->
          if (r.isSuccessful) return r.body.string()
          Log.w(TAG, "overpass $url -> ${r.code}")
        }
      } catch (e: Exception) {
        Log.w(TAG, "overpass $url failed", e)
      }
      if (i < servers.lastIndex) delay(1500)
    }
    return null
  }

  // ------------------------------------------------------------------ TomTom

  suspend fun incidents(route: List<GeographicCoordinate>): List<RoadFeature> =
      withContext(Dispatchers.IO) {
        if (!hasLiveTraffic || route.size < 2) return@withContext emptyList()
        val line = RouteLine(route)
        // TomTom limits a query to 10,000 km²; long trips ask about the next stretch only.
        val box = line.boundingBox(maxAreaKm2 = 9000.0, pad = 0.01)
        val fields = "{incidents{type,geometry{type,coordinates},properties{iconCategory,magnitudeOfDelay,events{description},delay,from,to}}}"
        val url =
            "https://api.tomtom.com/traffic/services/5/incidentDetails?key=$tomtomKey" +
                "&bbox=${box.joinToString(",") { String.format(Locale.US, "%.5f", it) }}" +
                "&fields=${URLEncoder.encode(fields, "UTF-8")}&language=en-GB&timeValidityFilter=present"
        val body = get(url) ?: return@withContext emptyList()
        val list = JSONObject(body).optJSONArray("incidents") ?: return@withContext emptyList()
        (0 until list.length()).mapNotNull { i ->
          val inc = list.getJSONObject(i)
          val props = inc.optJSONObject("properties") ?: return@mapNotNull null
          val kind = incidentKind(props.optInt("iconCategory")) ?: return@mapNotNull null
          val at = firstCoordinate(inc.optJSONObject("geometry")) ?: return@mapNotNull null
          val events = props.optJSONArray("events")
          val description = events?.optJSONObject(0)?.optString("description")?.takeIf { it.isNotBlank() }
          val (along, off) = line.project(at)
          RoadFeature(
              kind = kind,
              at = at,
              description = description,
              delaySeconds = props.optDouble("delay", 0.0).takeUnless { it.isNaN() } ?: 0.0,
              magnitude = props.optInt("magnitudeOfDelay"),
              along = along.takeIf { off < 80.0 },
          )
        }
      }

  suspend fun eta(from: GeographicCoordinate, to: GeographicCoordinate): TrafficEta? =
      withContext(Dispatchers.IO) {
        if (!hasLiveTraffic) return@withContext null
        val url =
            "https://api.tomtom.com/routing/1/calculateRoute/${from.lat},${from.lng}:${to.lat},${to.lng}/json" +
                "?key=$tomtomKey&traffic=true&travelMode=car&routeType=fastest&computeTravelTimeFor=all"
        val body = get(url) ?: return@withContext null
        val summary = JSONObject(body).optJSONArray("routes")?.optJSONObject(0)?.optJSONObject("summary") ?: return@withContext null
        TrafficEta(
            travelSeconds = summary.optDouble("travelTimeInSeconds"),
            delaySeconds = summary.optDouble("trafficDelayInSeconds", 0.0),
            noTrafficSeconds = summary.optDouble("noTrafficTravelTimeInSeconds", summary.optDouble("travelTimeInSeconds")),
        ).takeUnless { it.travelSeconds.isNaN() }
      }

  private fun get(url: String): String? =
      try {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
          if (r.isSuccessful) r.body.string() else null.also { Log.w(TAG, "GET ${url.substringBefore('?')} -> ${r.code}") }
        }
      } catch (e: Exception) {
        Log.w(TAG, "GET ${url.substringBefore('?')} failed", e)
        null
      }

  private fun incidentKind(iconCategory: Int): RoadFeatureKind? =
      when (iconCategory) {
        1 -> RoadFeatureKind.ACCIDENT
        6 -> RoadFeatureKind.JAM
        7 -> RoadFeatureKind.LANE_CLOSED
        8 -> RoadFeatureKind.CLOSURE
        9 -> RoadFeatureKind.ROADWORKS
        11 -> RoadFeatureKind.FLOODING
        14 -> RoadFeatureKind.BROKEN_DOWN
        2, 3, 4, 5, 10 -> RoadFeatureKind.HAZARD
        else -> null
      }

  private fun firstCoordinate(geometry: JSONObject?): GeographicCoordinate? {
    geometry ?: return null
    val coords = geometry.optJSONArray("coordinates") ?: return null
    val first = if (geometry.optString("type") == "Point") coords else coords.optJSONArray(0) ?: return null
    return GeographicCoordinate(first.getDouble(1), first.getDouble(0))
  }

  private companion object {
    const val TAG = "TrafficService"
    val TRACE_URL = AppModule.VALHALLA_URL.replace("/route", "/trace_attributes")
    val JSON = "application/json".toMediaType()
  }
}

// -------------------------------------------------------------------- geometry

internal fun metres(a: GeographicCoordinate, b: GeographicCoordinate): Double {
  val kx = 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
  val dx = (b.lng - a.lng) * kx
  val dy = (b.lat - a.lat) * 111_320.0
  return kotlin.math.sqrt(dx * dx + dy * dy)
}

/** Google's polyline format at 6 decimals, as Valhalla uses it. */
internal fun encodePolyline6(points: List<GeographicCoordinate>): String {
  val out = StringBuilder()
  var lastLat = 0L
  var lastLng = 0L
  fun put(delta: Long) {
    var v = if (delta < 0) (delta shl 1).inv() else delta shl 1
    while (v >= 0x20) {
      out.append(((0x20L or (v and 0x1f)) + 63).toInt().toChar())
      v = v shr 5
    }
    out.append((v + 63).toInt().toChar())
  }
  for (p in points) {
    val lat = Math.round(p.lat * 1e6)
    val lng = Math.round(p.lng * 1e6)
    put(lat - lastLat)
    put(lng - lastLng)
    lastLat = lat
    lastLng = lng
  }
  return out.toString()
}

internal fun decodePolyline6(encoded: String): List<GeographicCoordinate> {
  val points = mutableListOf<GeographicCoordinate>()
  var i = 0
  var lat = 0L
  var lng = 0L
  fun next(): Long {
    var result = 0L
    var shift = 0
    while (true) {
      val b = encoded[i++].code - 63
      result = result or ((b and 0x1f).toLong() shl shift)
      shift += 5
      if (b < 0x20) break
    }
    return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
  }
  while (i < encoded.length) {
    lat += next()
    lng += next()
    points += GeographicCoordinate(lat / 1e6, lng / 1e6)
  }
  return points
}

/** A route polyline with distances along it. */
class RouteLine(private val points: List<GeographicCoordinate>) {
  private val cumulative: DoubleArray =
      DoubleArray(points.size).also { c -> for (i in 1 until points.size) c[i] = c[i - 1] + metres(points[i - 1], points[i]) }

  val length: Double
    get() = cumulative.lastOrNull() ?: 0.0

  /** Metres along the route of the closest point, and how far off the route [p] is. */
  fun project(p: GeographicCoordinate): Pair<Double, Double> {
    var best = Double.MAX_VALUE
    var bestAlong = 0.0
    val kx = 111_320.0 * cos(Math.toRadians(p.lat))
    for (i in 0 until points.size - 1) {
      val a = points[i]
      val b = points[i + 1]
      val ax = (a.lng - p.lng) * kx
      val ay = (a.lat - p.lat) * 111_320.0
      val bx = (b.lng - p.lng) * kx
      val by = (b.lat - p.lat) * 111_320.0
      val dx = bx - ax
      val dy = by - ay
      val len2 = dx * dx + dy * dy
      val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
      val cx = ax + dx * t
      val cy = ay + dy * t
      val d = kotlin.math.sqrt(cx * cx + cy * cy)
      if (d < best) {
        best = d
        bestAlong = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t
      }
    }
    return bestAlong to best
  }

  /** [south, west, north, east] boxes, each around a stretch of at most [chunkMetres] of the route. */
  fun chunkBoxes(chunkMetres: Double, padMetres: Double): List<List<Double>> {
    if (points.isEmpty()) return emptyList()
    val boxes = mutableListOf<List<Double>>()
    var start = 0
    while (start < points.size) {
      var end = start
      while (end + 1 < points.size && cumulative[end + 1] - cumulative[start] <= chunkMetres) end++
      val stretch = points.subList(start, end + 1)
      val padLat = padMetres / 111_320.0
      val padLon = padMetres / (111_320.0 * cos(Math.toRadians(stretch[0].lat)))
      boxes +=
          listOf(
              stretch.minOf { it.lat } - padLat,
              stretch.minOf { it.lng } - padLon,
              stretch.maxOf { it.lat } + padLat,
              stretch.maxOf { it.lng } + padLon,
          )
      // Overlap by one point so no stretch of road falls between two boxes.
      start = if (end == start) end + 1 else end
      if (end == points.lastIndex) break
    }
    return boxes
  }

  /** [minLon, minLat, maxLon, maxLat], trimmed from the start of the route to stay under [maxAreaKm2]. */
  fun boundingBox(maxAreaKm2: Double, pad: Double): List<Double> {
    var minLon = Double.MAX_VALUE
    var minLat = Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    for (p in points) {
      val nMinLon = min(minLon, p.lng - pad)
      val nMinLat = min(minLat, p.lat - pad)
      val nMaxLon = max(maxLon, p.lng + pad)
      val nMaxLat = max(maxLat, p.lat + pad)
      val area = abs(nMaxLon - nMinLon) * 111.32 * cos(Math.toRadians(p.lat)) * abs(nMaxLat - nMinLat) * 111.32
      if (area > maxAreaKm2 && minLon != Double.MAX_VALUE) break
      minLon = nMinLon; minLat = nMinLat; maxLon = nMaxLon; maxLat = nMaxLat
    }
    return listOf(minLon, minLat, maxLon, maxLat)
  }
}
