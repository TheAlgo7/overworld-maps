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
import kotlin.math.atan2
import kotlin.math.cos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 *
 * Google is only ever given a point, never the way the car faces, so on a divided road it can
 * start the line on the other carriageway; then it's asked again from a little way along the road
 * the car is really on. And where its line takes a U-turn through a median gap OpenStreetMap maps
 * as a ramp further on (NH-48 at Ambience, every reroute of the 29 Oct drive), the match comes
 * back in pieces: each gap is bridged with Valhalla's own way across and matched again.
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
    // Valhalla is asked at once, beside Google, not after it: Google may need its route for a
    // start, and when Google can't help the fallback is already in hand (a preview took 3 s when
    // they ran one after the other, 30 Oct). Left to finish on its own when Google wins.
    val own = background.async { valhallaRoute(userLocation, waypoints) }
    if (waypoints.size == 1 && googleOn()) {
      val viaGoogle =
          try {
            googleRoute(userLocation, waypoints.single()) { own.await() }
          } catch (e: CancellationException) {
            own.cancel()
            throw e
          } catch (e: Exception) {
            Log.w(TAG, "Google route failed", e)
            null
          }
      if (viaGoogle != null) {
        own.cancel()
        lastSource = "Google"
        return viaGoogle
      }
    }
    lastSource = "Valhalla"
    DriveLog.note("route: Valhalla (${when { !googleOn() -> "Google off"; waypoints.size > 1 -> "stops on the way"; else -> "Google unavailable" }})")
    return own.await()
  }

  private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** Ferrostar's own request to Valhalla, as it would have sent it, plus the way the car faces. */
  private suspend fun valhallaRoute(userLocation: UserLocation, waypoints: List<Waypoint>): List<Route> {
    val request = valhalla.generateRequest(userLocation, waypoints) as RouteRequest.HttpPost
    val body = JSONObject(String(request.body))
    movingCourse(userLocation)?.let { course ->
      body.optJSONArray("locations")?.optJSONObject(0)?.put("heading", course.toInt())?.put("heading_tolerance", HEADING_TOLERANCE)
    }
    val (code, bytes) = post(request.url, request.headers, body.toString().toByteArray())
    if (code !in 200..299) throw InvalidStatusCodeException(code)
    return valhalla.parseResponse(bytes ?: throw NoResponseBodyException())
  }

  private suspend fun googleRoute(userLocation: UserLocation, destination: Waypoint, ownRoutes: suspend () -> List<Route>): List<Route>? {
    var best = google.driving(userLocation.coordinates, destination.coordinate).firstOrNull() ?: return null
    // The stretch before Google's line when it had to be asked from further along.
    var lead = emptyList<GeographicCoordinate>()
    val course = movingCourse(userLocation)
    if (course != null && startsAgainst(best.line, course)) {
      // Google put the car on the other side of the road. Valhalla, told the heading, knows which
      // side it's on: ask Google again from a point that far along Valhalla's way.
      val own = ownRoutes().firstOrNull()?.geometry ?: return null
      lead = prefix(own, ((userLocation.speed?.value ?: 0.0) * LEAD_SECONDS).coerceIn(LEAD_MIN_METRES, LEAD_MAX_METRES))
      if (lead.size < 2) return null
      val again = google.driving(lead.last(), destination.coordinate).firstOrNull() ?: return null
      if (startsAgainst(again.line, bearing(lead[lead.lastIndex - 1], lead.last()))) {
        DriveLog.note("route: Google started against the car twice, used Valhalla")
        return null
      }
      DriveLog.note("route: Google started against the car; asked again ${RouteLine(lead).length.toInt()} m ahead")
      best = again
    }
    val metres = (if (lead.size < 2) 0.0 else RouteLine(lead).length) + best.metres
    if (metres > MAX_MATCH_METRES) return null
    // The same options Ferrostar sends Valhalla (instructions, lanes, speed limits), with Google's
    // line to follow instead of two ends to route between.
    val request = valhalla.generateRequest(userLocation, listOf(destination)) as? RouteRequest.HttpPost ?: return null
    val body = JSONObject(String(request.body))
    body.remove("locations")
    body.put("shape_match", "map_snap")
    val traceUrl = request.url.substringBeforeLast("/route") + "/trace_route"
    suspend fun trace(shape: List<GeographicCoordinate>): JSONObject? {
      body.put(
          "shape",
          JSONArray(
              shape.mapIndexed { i, p ->
                JSONObject().put("lat", p.lat).put("lon", p.lng).put("type", if (i == 0 || i == shape.lastIndex) "break" else "via")
              }
          ),
      )
      val (code, bytes) = post(traceUrl, request.headers, body.toString().toByteArray())
      if (code !in 200..299 || bytes == null) {
        Log.w(TAG, "trace_route -> $code")
        return null
      }
      return JSONObject(String(bytes))
    }
    var shape = thinned(if (lead.size < 2) best.line else lead.dropLast(1) + best.line)
    var answer = trace(shape) ?: return null
    val pieces = answer.optJSONArray("matchings")?.length() ?: 0
    if (pieces > 1) {
      // Matched in pieces: OpenStreetMap's way across a gap differs from Google's (a U-turn cut
      // mapped as a ramp further on). Bridge each gap with Valhalla's way and match once more.
      val bridged = bridged(shape, answer, request.url, request.headers)
      if (bridged == null) {
        DriveLog.note("route: Google's ${metres.toInt()} m matched in $pieces pieces with no way across, used Valhalla")
        return null
      }
      shape = bridged
      answer = trace(shape) ?: return null
      DriveLog.note("route: Google's line matched in $pieces pieces, bridged to ${answer.optJSONArray("matchings")?.length() ?: 0}")
    }
    val matchings = answer.optJSONArray("matchings") ?: return null
    val match = matchings.optJSONObject(0) ?: return null
    val matched = match.optDouble("distance", Double.NaN)
    // Laid on a different road somewhere (OpenStreetMap missing Google's road): the turns would be wrong.
    if (matchings.length() > 1 || matched.isNaN() || abs(matched - metres) > metres * MATCH_SLACK + 150.0) {
      Log.w(TAG, "Google's ${metres.toInt()} m matched as ${matched.toInt()} m; not used")
      DriveLog.note("route: Google's ${metres.toInt()} m matched as ${matched.toInt()} m, used Valhalla")
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

  /**
   * [shape] with each gap between [answer]'s matchings replaced by Valhalla's own route across it,
   * driven the way Google's line enters and leaves it. Null when a way across is missing or a long
   * way round (then OpenStreetMap really lacks Google's road, and its turns would be wrong).
   */
  private suspend fun bridged(shape: List<GeographicCoordinate>, answer: JSONObject, routeUrl: String, headers: Map<String, String>): List<GeographicCoordinate>? {
    val gaps = gaps(answer.optJSONArray("tracepoints") ?: return null)
    if (gaps.isEmpty() || gaps.size > MAX_BRIDGES) return null
    // Every gap asked at once.
    val crossings =
        coroutineScope {
          gaps.map { gap ->
                async {
                  val inHeading = bearing(shape[maxOf(gap.fromIndex - 1, 0)], shape[gap.fromIndex])
                  val outHeading = bearing(shape[gap.toIndex], shape[minOf(gap.toIndex + 1, shape.lastIndex)])
                  fun location(p: GeographicCoordinate, heading: Double) =
                      JSONObject().put("lat", p.lat).put("lon", p.lng).put("heading", heading.toInt()).put("heading_tolerance", HEADING_TOLERANCE)
                  val request = JSONObject().put("costing", "auto").put("locations", JSONArray().put(location(gap.from, inHeading)).put(location(gap.to, outHeading)))
                  val (code, bytes) = post(routeUrl, headers, request.toString().toByteArray())
                  if (code !in 200..299 || bytes == null) return@async null
                  val leg = JSONObject(String(bytes)).optJSONObject("trip")?.optJSONArray("legs")?.optJSONObject(0) ?: return@async null
                  decodePolyline6(leg.optString("shape")).takeIf { it.size >= 2 }
                }
              }
              .awaitAll()
        }
    val out = shape.toMutableList()
    // From the end, so earlier indices stay put.
    for ((gap, across) in gaps.zip(crossings).reversed()) {
      if (across == null) return null
      val straight = com.thealgothrim.overworld.traffic.metres(gap.from, gap.to)
      if (RouteLine(across).length > maxOf(MAX_BRIDGE_METRES, straight * 8)) return null
      out.subList(gap.fromIndex, gap.toIndex + 1).clear()
      out.addAll(gap.fromIndex, listOf(gap.from) + across.drop(1).dropLast(1) + gap.to)
    }
    return out
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

    /** Gaps bridged at most per route; more pieces than that and Google's road isn't OpenStreetMap's. */
    private const val MAX_BRIDGES = 4
    /**
     * A way across a gap longer than this (or 8 times the gap) is a detour, not the same U-turn: the
     * Ambience ramp was 219 m; a 1.1 km loop near Chhatarpur only cost time before being refused.
     */
    private const val MAX_BRIDGE_METRES = 600.0
    /** Valhalla keeps to roads within this many degrees of a heading. */
    private const val HEADING_TOLERANCE = 45
    /** A course means something from about 11 km/h; slower, a phone's heading wanders. */
    private const val COURSE_MIN_MS = 3.0
    /** Google is asked again this far ahead of a car it started the wrong way: 12 s of driving, 150 to 400 m. */
    private const val LEAD_SECONDS = 12.0
    private const val LEAD_MIN_METRES = 150.0
    private const val LEAD_MAX_METRES = 400.0

    /** The car's course in degrees when it's moving fast enough for one to mean something. */
    internal fun movingCourse(at: UserLocation): Double? =
        at.courseOverGround?.degrees?.toDouble()?.takeIf { (at.speed?.value ?: 0.0) >= COURSE_MIN_MS }

    /** Degrees from north, [a] to [b]. */
    internal fun bearing(a: GeographicCoordinate, b: GeographicCoordinate): Double {
      val dx = (b.lng - a.lng) * cos(Math.toRadians(a.lat))
      val dy = b.lat - a.lat
      return (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
    }

    /** Whether [line]'s first 40 m or so heads more than 110° away from [course]: it starts behind the car. */
    internal fun startsAgainst(line: List<GeographicCoordinate>, course: Double): Boolean {
      if (line.size < 2) return false
      val first = line.first()
      val probe = line.drop(1).firstOrNull { com.thealgothrim.overworld.traffic.metres(first, it) >= 40.0 } ?: line.last()
      if (com.thealgothrim.overworld.traffic.metres(first, probe) < 10.0) return false
      val diff = abs(((bearing(first, probe) - course) % 360.0 + 540.0) % 360.0 - 180.0)
      return diff > 110.0
    }

    /** [line] up to [metres] along it, the last point cut where it ends. */
    internal fun prefix(line: List<GeographicCoordinate>, metres: Double): List<GeographicCoordinate> {
      if (line.size < 2) return line
      val out = mutableListOf(line.first())
      var left = metres
      for (i in 1 until line.size) {
        val step = com.thealgothrim.overworld.traffic.metres(line[i - 1], line[i])
        if (step >= left) {
          val t = if (step == 0.0) 0.0 else left / step
          out += GeographicCoordinate(line[i - 1].lat + (line[i].lat - line[i - 1].lat) * t, line[i - 1].lng + (line[i].lng - line[i - 1].lng) * t)
          return out
        }
        left -= step
        out += line[i]
      }
      return out
    }

    /** Where a match broke: shape points [fromIndex] and [toIndex], matched at [from] and [to]. */
    internal data class Gap(val fromIndex: Int, val toIndex: Int, val from: GeographicCoordinate, val to: GeographicCoordinate)

    /** The gaps in a trace_route answer's tracepoints: consecutive matched points in different matchings. */
    internal fun gaps(tracepoints: JSONArray): List<Gap> {
      fun at(o: JSONObject) = o.optJSONArray("location")?.let { GeographicCoordinate(it.optDouble(1), it.optDouble(0)) }
      val out = mutableListOf<Gap>()
      var prevIndex = -1
      var prev: JSONObject? = null
      for (i in 0 until tracepoints.length()) {
        val tp = tracepoints.optJSONObject(i) ?: continue
        val p = prev
        if (p != null && tp.optInt("matchings_index") != p.optInt("matchings_index")) {
          val from = at(p)
          val to = at(tp)
          if (from != null && to != null) out += Gap(prevIndex, i, from, to)
        }
        prev = tp
        prevIndex = i
      }
      return out
    }

    /** Valhalla's shapes: the polyline encoding at six decimal places. */
    internal fun decodePolyline6(encoded: String): List<GeographicCoordinate> {
      val out = mutableListOf<GeographicCoordinate>()
      var i = 0
      var lat = 0L
      var lng = 0L
      fun next(): Long {
        var shift = 0
        var result = 0L
        while (i < encoded.length) {
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
        out += GeographicCoordinate(lat / 1e6, lng / 1e6)
      }
      return out
    }
  }
}
