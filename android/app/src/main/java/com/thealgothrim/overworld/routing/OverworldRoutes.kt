package com.thealgothrim.overworld.routing

import android.util.Log
import com.stadiamaps.ferrostar.core.CustomRouteProvider
import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NoResponseBodyException
import com.thealgothrim.overworld.DriveLog
import com.thealgothrim.overworld.traffic.RouteLine
import com.thealgothrim.overworld.traffic.RouteTraffic
import com.thealgothrim.overworld.traffic.TrafficEta
import com.thealgothrim.overworld.traffic.TrafficSpan
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteAdapter
import uniffi.ferrostar.RouteRequest
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint

/**
 * Where trips get their route: Google's own drive (it knows Delhi's traffic, closures and one-ways),
 * laid onto OpenStreetMap's roads by Valhalla's map matching so the turn-by-turn, lanes and speed
 * limits come out exactly as before. Valhalla's own route when Google can't help: a trip with
 * stops on the way, a very long one, Google refusing, or its road not matching OpenStreetMap's.
 * Ferrostar asks here for previews and reroutes alike. The same idea as Vela's (see GoogleMaps).
 */
class OverworldRoutes(
    private val valhalla: RouteAdapter,
    private val http: OkHttpClient,
    val google: GoogleMaps,
    /** The Settings switch: Google may be asked. */
    private val googleOn: () -> Boolean,
) : CustomRouteProvider {
  /** Which engine made the last route, for the drive log and Settings. */
  @Volatile var lastSource: String = "none"
    private set

  override suspend fun getRoutes(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> {
    if (waypoints.size == 1 && googleOn()) {
      val viaGoogle = runCatching { googleRoute(userLocation, waypoints.single()) }.onFailure { Log.w(TAG, "Google route failed", it) }.getOrNull()
      if (viaGoogle != null) {
        lastSource = "Google"
        return viaGoogle
      }
    }
    lastSource = "Valhalla"
    DriveLog.note("route: Valhalla (${when { !googleOn() -> "Google off"; waypoints.size > 1 -> "stops on the way"; else -> "Google unavailable" }})")
    return valhallaRoute(userLocation, waypoints)
  }

  /** Ferrostar's own request to Valhalla, as it would have sent it. */
  private suspend fun valhallaRoute(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> {
    val request = valhalla.generateRequest(userLocation, waypoints) as RouteRequest.HttpPost
    val (code, bytes) = post(request.url, request.headers, request.body)
    if (code !in 200..299) throw InvalidStatusCodeException(code)
    return valhalla.parseResponse(bytes ?: throw NoResponseBodyException())
  }

  private suspend fun googleRoute(userLocation: UserLocation, destination: Waypoint): List<Route>? {
    val best = google.driving(userLocation.coordinates, destination.coordinate).firstOrNull() ?: return null
    if (best.metres > MAX_MATCH_METRES) return null
    // The same options Ferrostar sends Valhalla (instructions, lanes, speed limits), with Google's
    // line to follow instead of two ends to route between.
    val request = valhalla.generateRequest(userLocation, listOf(destination)) as? RouteRequest.HttpPost ?: return null
    val body = JSONObject(String(request.body))
    body.remove("locations")
    val shape = thinned(best.line)
    body.put(
        "shape",
        JSONArray(
            shape.mapIndexed { i, p ->
              JSONObject().put("lat", p.lat).put("lon", p.lng).put("type", if (i == 0 || i == shape.lastIndex) "break" else "via")
            }
        ),
    )
    body.put("shape_match", "map_snap")
    val url = request.url.substringBeforeLast("/route") + "/trace_route"
    val (code, bytes) = post(url, request.headers, body.toString().toByteArray())
    if (code !in 200..299 || bytes == null) {
      Log.w(TAG, "trace_route -> $code")
      return null
    }
    val match = JSONObject(String(bytes)).optJSONArray("matchings")?.optJSONObject(0) ?: return null
    val matched = match.optDouble("distance", Double.NaN)
    // Laid on a different road somewhere (OpenStreetMap missing Google's road): the turns would be wrong.
    if (matched.isNaN() || abs(matched - best.metres) > best.metres * MATCH_SLACK + 150.0) {
      Log.w(TAG, "Google's ${best.metres.toInt()} m matched as ${matched.toInt()} m; not used")
      DriveLog.note("route: Google's ${best.metres.toInt()} m matched as ${matched.toInt()} m, used Valhalla")
      return null
    }
    // Valhalla answers a match in OSRM's map-matching form; Ferrostar reads OSRM's route form.
    fun waypoint(p: GeographicCoordinate) = JSONObject().put("location", JSONArray().put(p.lng).put(p.lat)).put("name", "")
    val asRoute =
        JSONObject()
            .put("code", "Ok")
            .put("routes", JSONArray().put(match))
            .put("waypoints", JSONArray().put(waypoint(shape.first())).put(waypoint(destination.coordinate)))
    val routes = valhalla.parseResponse(asRoute.toString().toByteArray())
    DriveLog.note(
        "route: Google via ${best.via ?: "?"}, ${"%.1f".format(best.metres / 1000)} km, " +
            "${DriveLog.minutes(best.trafficSeconds ?: best.typicalSeconds)} with traffic (usual ${DriveLog.minutes(best.typicalSeconds)})"
    )
    return routes.takeIf { it.isNotEmpty() }
  }

  /**
   * Live traffic for [route] (the road still ahead) from Google: its time and jams for the same
   * trip, when one of its routes is the same length as ours (so the same road). Null otherwise.
   */
  suspend fun routeTraffic(route: List<GeographicCoordinate>): RouteTraffic? {
    if (route.size < 2 || !googleOn()) return null
    val line = RouteLine(route)
    val same =
        google.driving(route.first(), route.last()).minByOrNull { abs(it.metres - line.length) }?.takeIf {
          abs(it.metres - line.length) <= line.length * SAME_ROAD_SLACK + 100.0
        } ?: return null
    val travel = same.trafficSeconds ?: same.typicalSeconds
    // Google's metres along its line; ours is the same road give or take a few metres per km.
    val scale = line.length / same.metres
    val spans = same.jams.map { TrafficSpan(it.from * scale, (it.from + it.length) * scale, it.level.coerceIn(1, 3)) }
    return RouteTraffic(TrafficEta(travelSeconds = travel, delaySeconds = maxOf(travel - same.typicalSeconds, 0.0), noTrafficSeconds = same.typicalSeconds), spans)
  }

  private suspend fun post(url: String, headers: Map<String, String>, body: ByteArray): Pair<Int, ByteArray?> =
      withContext(Dispatchers.IO) {
        val request =
            Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toRequestBody((headers["Content-Type"] ?: "application/json").toMediaType()))
                .build()
        http.newCall(request).execute().use { r -> r.code to r.body.bytes() }
      }

  companion object {
    private const val TAG = "OverworldRoutes"
    /** Longer than this, Valhalla's map matching refuses (200 km on the public server); its own route then. */
    private const val MAX_MATCH_METRES = 150_000.0
    /** The matched route may differ from Google's length by this share (plus 150 m at the ends). */
    private const val MATCH_SLACK = 0.05
    /** Google's route counts as ours for traffic within this share of the length. */
    private const val SAME_ROAD_SLACK = 0.08

    /** [line] with a point every [SPACING_METRES] or so (Valhalla's limit on shape points), ends kept. */
    internal fun thinned(line: List<GeographicCoordinate>): List<GeographicCoordinate> {
      if (line.size <= 2) return line
      val spacing = maxOf(SPACING_METRES, RouteLine(line).length / MAX_SHAPE_POINTS)
      val out = mutableListOf(line.first())
      for (p in line.drop(1).dropLast(1)) if (com.thealgothrim.overworld.traffic.metres(out.last(), p) >= spacing) out += p
      out += line.last()
      return out
    }

    private const val SPACING_METRES = 25.0
    private const val MAX_SHAPE_POINTS = 2_000
  }
}
