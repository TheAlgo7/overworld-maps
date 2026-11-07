package com.thealgothrim.overworld.location

import com.thealgothrim.overworld.DriveLog
import com.thealgothrim.overworld.routing.RoadLevels
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt
import uniffi.ferrostar.DeviationKind
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.RouteDeviationDetector
import uniffi.ferrostar.TripState

/**
 * Off the route only once the car has really left it: [nearMetres] away for [nearSeconds], or
 * [farMetres] for [farSeconds]. Ferrostar's static rule (one fix over 50 m) never noticed a service
 * road 40 m away, the arrow staying on the main road for 30 s, and then fired on a single fix
 * (Android navigation study, test T7). Google Maps rerouted about 10 s into 35 to 40 m.
 *
 * Measured from the step being driven and the two after it, so a route that passes near itself
 * further on can't hide a detour.
 *
 * Driving the wrong way counts too, however close: on NH-48 the other carriageway and the service
 * road are under 30 m from the route, so a car that missed its U-turn (or a route laid on the wrong
 * side) was never "off" it, and the arrow sat snapped to the route pointing the route's way
 * (29 Oct drive at Ambience). [wrongWaySeconds] moving with no route line near enough that runs
 * the car's way is a detour.
 */
class SustainedDeviation(
    private val nearMetres: Double = 30.0,
    private val nearSeconds: Long = 8,
    private val farMetres: Double = 60.0,
    private val farSeconds: Long = 2,
    /** Vaguer fixes than this can't start or settle a detour. */
    private val maxAccuracyMetres: Double = 20.0,
    private val wrongWaySeconds: Long = 5,
    /** The route's flyovers and what runs beside them (see RoadLevels), when known. */
    private val levels: (Route) -> RoadLevels? = { null },
    private val levelCheck: LevelCheck = LevelCheck(),
) : RouteDeviationDetector {
  /** Seconds driven off the route so far; null while on it. */
  @Volatile private var offFor: Double? = null
  /** Seconds driven against every nearby piece of the route; null while going its way. */
  @Volatile private var wrongFor: Double? = null
  @Volatile private var lastAt: Instant? = null
  /** After the level check calls it, the route stays "left" until a new one comes (or this time passes). */
  @Volatile private var leftRoute: Pair<RoadLevels, Instant>? = null

  override fun checkRouteDeviation(route: Route, tripState: TripState): RouteDeviation {
    val trip = tripState as? TripState.Navigating
    if (trip == null) {
      offFor = null
      wrongFor = null
      lastAt = null
      leftRoute = null
      return RouteDeviation.NoDeviation
    }
    // The raw fix: the snapped one always sits on the route, facing its way.
    val fix = trip.userLocation
    val lines = trip.remainingSteps.take(3).map { it.geometry }
    val off = metresFromSteps(fix.coordinates, lines)
    val known = levels(route)
    // Called by the level check moments ago and the new route isn't here yet: still off this one,
    // or the arrow would snap back down to the service road until it comes.
    leftRoute?.let { (lv, at) ->
      if (lv.matches(route.geometry) && Duration.between(at, fix.timestamp).seconds < HOLD_LEFT_SECONDS) return RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off ?: 0.0))
      leftRoute = null
    }
    if (known != null) {
      val along = known.length - trip.progress.distanceRemaining
      levelCheck.judge(fix, along, off, known)?.let { hint ->
        RerouteHints.offer(hint)
        DriveLog.note(
            "trip: GPS puts the car on the ${hint.level.name.lowercase().replace("surface", "road")} beside the route " +
                "(route ${off?.toInt()} m away), rerouting from it at ${DriveLog.at(hint.at)}"
        )
        offFor = null
        wrongFor = null
        leftRoute = known to fix.timestamp
        return RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off ?: 0.0))
      }
    }
    val course = fix.courseOverGround?.degrees?.toDouble()
    val against = course?.let { !runsAlong(fix.coordinates, it, lines, maxOf(WRONG_WAY_NEAR_METRES, fix.horizontalAccuracy * 1.5)) }
    return judge(off, fix.horizontalAccuracy, fix.timestamp, fix.speed?.value, against)
  }

  /**
   * The rule itself, for [off] metres from the route at [at], with a fix good to [accuracy] metres
   * and [speed] m/s. Only time spent moving counts: a car parked 40 m from where the route starts,
   * or waiting at a light beside it, isn't leaving it (counting that rerouted a parked car at once).
   */
  internal fun judge(off: Double?, accuracy: Double, at: Instant, speed: Double? = null, against: Boolean? = null): RouteDeviation {
    val previous = lastAt
    lastAt = at
    // Too vague to tell (or no route left): no verdict, and a detour's clock keeps where it was.
    if (off == null || accuracy > maxAccuracyMetres) return RouteDeviation.NoDeviation
    val step = if (previous == null) 0.0 else (Duration.between(previous, at).toMillis() / 1000.0).coerceIn(0.0, 5.0)
    // A heading means something only at speed; slower, the wrong-way clock holds where it was.
    if (against == false) wrongFor = null
    else if (against == true && speed != null && speed >= WRONG_WAY_MS) {
      val wrong = (wrongFor ?: 0.0) + step
      wrongFor = wrong
      if (wrong >= wrongWaySeconds) return RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off))
    }
    if (off < nearMetres) {
      offFor = null
      return RouteDeviation.NoDeviation
    }
    val moving = speed == null || speed >= MOVING_MS
    val seconds = (offFor ?: 0.0) + if (moving) step else 0.0
    offFor = seconds
    val gone = seconds >= nearSeconds || (off >= farMetres && seconds >= farSeconds)
    return if (gone) RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off)) else RouteDeviation.NoDeviation
  }

  private companion object {
    /** About 5 km/h. */
    const val MOVING_MS = 1.5
    /** About 15 km/h: slower than this, a phone's heading wanders too much to call a wrong way. */
    const val WRONG_WAY_MS = 4.0
    /** A route line this near that runs the car's way means it's on it (a U-turn's two legs both count). */
    const val WRONG_WAY_NEAR_METRES = 35.0
    /** How long a level-check detour holds while the new route is fetched. */
    const val HOLD_LEFT_SECONDS = 15L
  }
}

/**
 * Whether any segment of [lines] within [withinMetres] of [p] runs within 70° of [course] (degrees
 * from north): the car is going the route's way. False when nothing is that near, too.
 */
internal fun runsAlong(p: GeographicCoordinate, course: Double, lines: List<List<GeographicCoordinate>>, withinMetres: Double): Boolean {
  val kx = 111_320.0 * cos(Math.toRadians(p.lat))
  val ky = 111_320.0
  for (line in lines) {
    for (i in 0 until line.lastIndex) {
      val ax = (line[i].lng - p.lng) * kx
      val ay = (line[i].lat - p.lat) * ky
      val bx = (line[i + 1].lng - p.lng) * kx
      val by = (line[i + 1].lat - p.lat) * ky
      val dx = bx - ax
      val dy = by - ay
      val len2 = dx * dx + dy * dy
      if (len2 < 1.0) continue
      val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
      val ex = ax + dx * t
      val ey = ay + dy * t
      if (ex * ex + ey * ey > withinMetres * withinMetres) continue
      val bearing = Math.toDegrees(atan2(dx, dy))
      val diff = abs(((bearing - course) % 360.0 + 540.0) % 360.0 - 180.0)
      if (diff <= 70.0) return true
    }
  }
  return false
}

/** Metres from [p] to the nearest point of [lines]; null when they hold no segment. */
internal fun metresFromSteps(p: GeographicCoordinate, lines: List<List<GeographicCoordinate>>): Double? {
  val kx = 111_320.0 * cos(Math.toRadians(p.lat))
  val ky = 111_320.0
  var best = Double.POSITIVE_INFINITY
  for (line in lines) {
    for (i in 0 until line.lastIndex) {
      val ax = (line[i].lng - p.lng) * kx
      val ay = (line[i].lat - p.lat) * ky
      val bx = (line[i + 1].lng - p.lng) * kx
      val by = (line[i + 1].lat - p.lat) * ky
      val dx = bx - ax
      val dy = by - ay
      val len2 = dx * dx + dy * dy
      val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
      val ex = ax + dx * t
      val ey = ay + dy * t
      best = minOf(best, ex * ex + ey * ey)
    }
  }
  return if (best.isInfinite()) null else sqrt(best)
}
