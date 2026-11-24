package com.thealgothrim.overworld.routing

import com.thealgothrim.overworld.traffic.encodePolyline6
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

/**
 * A walk along Google's own line when OpenStreetMap can't follow it: an OSRM answer (the form
 * Ferrostar reads Valhalla's in) whose turns are the line's own bends.
 *
 * Google knows the lanes of Delhi's urban villages that OpenStreetMap lacks. On 24 Nov a walk to
 * The Outfit House went down such a gali: Google's 362 m line matched onto OpenStreetMap as 17 m,
 * so it was dropped for Valhalla's way round the block, and the walk was "off the route" the whole
 * way. A drive still needs OpenStreetMap's roads (lanes, limits, flyovers); a walk only needs the
 * line and where it bends.
 */
internal object LineRoute {
  /** A bend at least this sharp is a turn (degrees). Lanes wiggle less than this. */
  const val TURN_DEGREES = 30.0
  /** Turns closer than this along the line are one turn (a dog-leg), metres. */
  const val MIN_STEP_METRES = 12.0
  /** Bearings into and out of a bend are taken over this much line, so GPS-drawn kinks don't count. */
  const val LOOK_METRES = 10.0

  data class Turn(val index: Int, val modifier: String)

  /**
   * The OSRM response for walking [line] to its end in [seconds] (Google's time), or null when it
   * is too short to walk.
   */
  fun osrm(line: List<GeographicCoordinate>, seconds: Double): String? {
    val points = line.zipWithNext().filter { (a, b) -> metres(a, b) > 0.3 }.map { it.first } + listOfNotNull(line.lastOrNull())
    if (points.size < 2) return null
    val total = length(points)
    if (total < 5.0) return null
    val perMetre = seconds.takeIf { it > 0 }?.div(total) ?: (1 / WALK_MS)
    val turns = turns(points)
    // Step boundaries: the start, each turn, the end.
    val cuts = listOf(0) + turns.map { it.index } + listOf(points.lastIndex)
    val steps = JSONArray()
    for (i in 0 until cuts.size - 1) {
      val part = points.subList(cuts[i], cuts[i + 1] + 1)
      val d = length(part)
      val before = if (i == 0) null else bearing(back(points, cuts[i]), points[cuts[i]])
      val after = bearing(points[cuts[i]], ahead(points, cuts[i]))
      val maneuver =
          if (i == 0) JSONObject().put("type", "depart").put("instruction", "Head ${compass(after)}.")
          else {
            val m = turns[i - 1].modifier
            JSONObject().put("type", "turn").put("modifier", m).put("instruction", sentence("turn", m))
          }
      maneuver.put("location", lngLat(points[cuts[i]])).put("bearing_after", after.toInt()).put("bearing_before", (before ?: after).toInt())
      // What this step's card says: the move at its end (the next turn, or arriving).
      val (type, modifier) = if (i < turns.size) "turn" to turns[i].modifier else "arrive" to null
      steps.put(step(part, d, d * perMetre, maneuver, cuts[i], type, modifier))
    }
    // OSRM ends a leg on a zero-length arrive step.
    val end = points.last()
    steps.put(
        step(
            listOf(end, end), 0.0, 0.0,
            JSONObject().put("type", "arrive").put("instruction", ARRIVE).put("location", lngLat(end))
                .put("bearing_before", bearing(back(points, points.lastIndex), end).toInt()).put("bearing_after", 0),
            points.lastIndex, null, null,
        )
    )
    val leg = JSONObject().put("steps", steps).put("distance", total).put("duration", total * perMetre).put("weight", total * perMetre).put("summary", "").put("via_waypoints", JSONArray())
    val route =
        JSONObject()
            .put("geometry", encodePolyline6(points))
            .put("distance", total)
            .put("duration", total * perMetre)
            .put("weight", total * perMetre)
            .put("weight_name", "pedestrian")
            .put("legs", JSONArray().put(leg))
    fun waypoint(p: GeographicCoordinate) = JSONObject().put("location", lngLat(p)).put("name", "").put("distance", 0.0)
    return JSONObject().put("code", "Ok").put("routes", JSONArray().put(route)).put("waypoints", JSONArray().put(waypoint(points.first())).put(waypoint(end))).toString()
  }

  /**
   * The bends of [points] sharp enough to call. Bends within [MIN_STEP_METRES] of each other are
   * one place, judged by the way in and the way out of the whole group: a lane's jog (right, then
   * left a few metres on) nets out to straight on and isn't called; a corner is one turn.
   */
  fun turns(points: List<GeographicCoordinate>): List<Turn> {
    val along = DoubleArray(points.size)
    for (i in 1 until points.size) along[i] = along[i - 1] + metres(points[i - 1], points[i])
    val total = along.last()
    fun turnAt(i: Int) = signed(bearing(back(points, i), points[i]), bearing(points[i], ahead(points, i)))
    // Vertices that bend, grouped while each is near the last.
    val groups = mutableListOf<MutableList<Int>>()
    for (i in 1 until points.lastIndex) {
      if (along[i] < MIN_STEP_METRES / 2 || total - along[i] < MIN_STEP_METRES / 2) continue
      if (abs(turnAt(i)) < TURN_DEGREES) continue
      val g = groups.lastOrNull()
      if (g != null && along[i] - along[g.last()] < MIN_STEP_METRES) g += i else groups += mutableListOf(i)
    }
    val out = mutableListOf<Turn>()
    for (g in groups) {
      val net = signed(bearing(back(points, g.first()), points[g.first()]), bearing(points[g.last()], ahead(points, g.last())))
      if (abs(net) < TURN_DEGREES) continue
      // Called where the bend is sharpest.
      val at = g.maxBy { abs(turnAt(it)) }
      if (out.isNotEmpty() && along[at] - along[out.last().index] < MIN_STEP_METRES) continue
      out += Turn(at, modifier(net))
    }
    return out
  }

  private fun step(part: List<GeographicCoordinate>, d: Double, s: Double, maneuver: JSONObject, startIndex: Int, nextType: String?, nextModifier: String?): JSONObject {
    val step =
        JSONObject()
            .put("geometry", encodePolyline6(part))
            .put("distance", d)
            .put("duration", s)
            .put("weight", s)
            .put("name", "")
            .put("mode", "walking")
            .put("driving_side", "left")
            .put("maneuver", maneuver)
            .put(
                "intersections",
                JSONArray().put(
                    JSONObject().put("location", maneuver.getJSONArray("location")).put("bearings", JSONArray().put(maneuver.optInt("bearing_after")))
                        .put("entry", JSONArray().put(true)).put("out", 0).put("geometry_index", startIndex)
                ),
            )
    if (nextType != null) {
      val text = if (nextType == "arrive") ARRIVE else sentence(nextType, nextModifier)
      val primary = JSONObject().put("type", nextType).put("text", text).put("components", JSONArray().put(JSONObject().put("text", text).put("type", "text")))
      nextModifier?.let { primary.put("modifier", it) }
      step.put("bannerInstructions", JSONArray().put(JSONObject().put("distanceAlongGeometry", d).put("primary", primary)))
      val say = minOf(d, 20.0)
      step.put("voiceInstructions", JSONArray().put(JSONObject().put("distanceAlongGeometry", say).put("announcement", text).put("ssmlAnnouncement", "<speak>$text</speak>")))
    } else {
      step.put("bannerInstructions", JSONArray()).put("voiceInstructions", JSONArray())
    }
    return step
  }

  private fun sentence(type: String, modifier: String?): String =
      when {
        type == "arrive" -> ARRIVE
        modifier == "uturn" -> "Make a U-turn."
        modifier?.startsWith("slight") == true -> "Bear ${modifier.removePrefix("slight ")}."
        modifier?.startsWith("sharp") == true -> "Turn sharp ${modifier.removePrefix("sharp ")}."
        else -> "Turn ${modifier ?: "ahead"}."
      }

  /** OSRM's word for a turn of [degrees] (positive = right). */
  fun modifier(degrees: Double): String {
    val side = if (degrees > 0) "right" else "left"
    val a = abs(degrees)
    return when {
      a >= 165 -> "uturn"
      a >= 120 -> "sharp $side"
      a >= 55 -> side
      else -> "slight $side"
    }
  }

  private fun compass(b: Double): String = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")[(((b + 22.5) % 360) / 45).toInt() % 8]

  /** The point [LOOK_METRES] back along the line from [i] (or the start). */
  private fun back(p: List<GeographicCoordinate>, i: Int): GeographicCoordinate {
    var d = 0.0
    var j = i
    while (j > 0 && d < LOOK_METRES) {
      d += metres(p[j - 1], p[j])
      j--
    }
    return p[j].takeIf { j != i } ?: p[maxOf(i - 1, 0)]
  }

  /** The point [LOOK_METRES] on along the line from [i] (or the end). */
  private fun ahead(p: List<GeographicCoordinate>, i: Int): GeographicCoordinate {
    var d = 0.0
    var j = i
    while (j < p.lastIndex && d < LOOK_METRES) {
      d += metres(p[j], p[j + 1])
      j++
    }
    return p[j].takeIf { j != i } ?: p[minOf(i + 1, p.lastIndex)]
  }

  private fun signed(from: Double, to: Double) = ((to - from) % 360 + 540) % 360 - 180

  private fun lngLat(p: GeographicCoordinate) = JSONArray().put(p.lng).put(p.lat)

  private fun length(p: List<GeographicCoordinate>) = p.zipWithNext().sumOf { (a, b) -> metres(a, b) }

  private fun metres(a: GeographicCoordinate, b: GeographicCoordinate): Double {
    val kx = 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
    val dx = (b.lng - a.lng) * kx
    val dy = (b.lat - a.lat) * 111_320.0
    return sqrt(dx * dx + dy * dy)
  }

  private fun bearing(a: GeographicCoordinate, b: GeographicCoordinate): Double {
    val kx = cos(Math.toRadians((a.lat + b.lat) / 2))
    return (Math.toDegrees(atan2((b.lng - a.lng) * kx, b.lat - a.lat)) + 360) % 360
  }

  private const val ARRIVE = "You have arrived at your destination."
  /** 5.1 km/h, the walk setting's default, when Google gives no time. */
  private const val WALK_MS = 1.42
}
