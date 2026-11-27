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
    /**
     * A clean turn off the route: [divergeMetres] away and getting further every fix, heading off at
     * an angle, for [divergeSeconds]. The 26 Nov drive took a right the route didn't want (GPS ±4 m)
     * and the 30 m / 8 s rule called it 12 s and 77 m later, the arrow sliding on along the old line
     * the whole time. Google Maps reroutes a second or two after a turn like that.
     */
    private val divergeMetres: Double = 20.0,
    private val divergeSeconds: Double = 2.0,
    /** The route's flyovers and what runs beside them (see RoadLevels), when known. */
    private val levels: (Route) -> RoadLevels? = { null },
    private val levelCheck: LevelCheck = LevelCheck(),
) : RouteDeviationDetector {
  /** Seconds driven off the route so far; null while on it. */
  @Volatile private var offFor: Double? = null
  /** Seconds driven against every nearby piece of the route; null while going its way. */
  @Volatile private var wrongFor: Double? = null
  @Volatile private var lastAt: Instant? = null
  /** Metres off the route at the last fix, to see whether the car is getting further from it. */
  @Volatile private var lastOff: Double? = null
  /** Seconds the car has been turning away from the route (see divergeMetres); null while not. */
  @Volatile private var divergeFor: Double? = null
  /** The last clear verdict was "off the route" (see [judge]); for which route, by its line. */
  @Volatile private var gone = false
  @Volatile private var goneOn: List<GeographicCoordinate>? = null
  /** After the level check calls it, the route stays "left" until a new one comes (or this time passes). */
  @Volatile private var leftRoute: Pair<RoadLevels, Instant>? = null

  override fun checkRouteDeviation(route: Route, tripState: TripState): RouteDeviation {
    val trip = tripState as? TripState.Navigating
    if (trip == null) {
      offFor = null
      wrongFor = null
      lastAt = null
      lastOff = null
      divergeFor = null
      leftRoute = null
      gone = false
      goneOn = null
      return RouteDeviation.NoDeviation
    }
    // A new route (the reroute came): whatever was said about the old one doesn't hold for it.
    if (route.geometry !== goneOn && route.geometry != goneOn) {
      gone = false
      lastOff = null
      divergeFor = null
    }
    goneOn = route.geometry
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
    val angle = course?.let { angleToNearest(fix.coordinates, it, lines) }
    return judge(off, fix.horizontalAccuracy, fix.timestamp, fix.speed?.value, against, angle)
  }

  /**
   * The rule itself, for [off] metres from the route at [at], with a fix good to [accuracy] metres
   * and [speed] m/s. Only time spent moving counts: a car parked 40 m from where the route starts,
   * or waiting at a light beside it, isn't leaving it (counting that rerouted a parked car at once).
   */
  internal fun judge(off: Double?, accuracy: Double, at: Instant, speed: Double? = null, against: Boolean? = null, angle: Double? = null): RouteDeviation {
    val previous = lastAt
    lastAt = at
    // No route left: nothing to be off.
    if (off == null) {
      lastOff = null
      divergeFor = null
      return RouteDeviation.NoDeviation
    }
    // Too vague to tell: the last clear verdict stands, and a detour's clock keeps where it was. A
    // vague fix used to read as "on the route", so a car already off it (the new route still on
    // its way, between tall buildings) had its arrow pulled back onto the old line for that fix.
    if (accuracy > maxAccuracyMetres) {
      lastOff = null
      divergeFor = null
      return if (gone) RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off)) else RouteDeviation.NoDeviation
    }
    val step = if (previous == null) 0.0 else (Duration.between(previous, at).toMillis() / 1000.0).coerceIn(0.0, 5.0)
    // Ferrostar asks twice about one fix when it advances a step: only a new fix moves this rule.
    if (step > 0.0) {
      val before = lastOff
      lastOff = off
      val turningAway =
          before != null && off - before >= DIVERGE_GROWTH_METRES && off >= divergeMetres && accuracy <= DIVERGE_ACCURACY_METRES &&
              speed != null && speed >= DIVERGE_MS && (angle ?: 0.0) >= DIVERGE_DEGREES
      divergeFor = if (turningAway) (divergeFor ?: 0.0) + step else null
    } else if (previous == null) {
      lastOff = off
    }
    if ((divergeFor ?: 0.0) >= divergeSeconds) {
      gone = true
      return RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off))
    }
    // A heading means something only at speed; slower, the wrong-way clock holds where it was.
    if (against == false) wrongFor = null
    else if (against == true && speed != null && speed >= WRONG_WAY_MS) {
      val wrong = (wrongFor ?: 0.0) + step
      wrongFor = wrong
      if (wrong >= wrongWaySeconds) {
        gone = true
        return RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off))
      }
    }
    if (off < nearMetres) {
      offFor = null
      gone = false
      return RouteDeviation.NoDeviation
    }
    val moving = speed == null || speed >= MOVING_MS
    val seconds = (offFor ?: 0.0) + if (moving) step else 0.0
    offFor = seconds
    gone = seconds >= nearSeconds || (off >= farMetres && seconds >= farSeconds)
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
    /** A turn away needs the gap to the route to grow at least this much each fix (about 4 km/h sideways). */
    const val DIVERGE_GROWTH_METRES = 1.0
    /** Only sharp fixes count for a quick call: ±15 m covers a clear sky and most of Delhi's streets. */
    const val DIVERGE_ACCURACY_METRES = 15.0
    /** About 11 km/h: slower, the course wobbles too much to tell a turn. */
    const val DIVERGE_MS = 3.0
    /** Heading off the route's own line by at least this much (a slip road's fork is about 20 to 30°). */
    const val DIVERGE_DEGREES = 20.0
  }
}

/**
 * Degrees between [course] and the direction of the piece of [lines] nearest [p] (0 to 180); null
 * when they hold no segment.
 */
internal fun angleToNearest(p: GeographicCoordinate, course: Double, lines: List<List<GeographicCoordinate>>): Double? {
  val kx = 111_320.0 * cos(Math.toRadians(p.lat))
  val ky = 111_320.0
  var best = Double.POSITIVE_INFINITY
  var bearing: Double? = null
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
      val d = ex * ex + ey * ey
      if (d < best) {
        best = d
        bearing = Math.toDegrees(atan2(dx, dy))
      }
    }
  }
  return bearing?.let { abs(((it - course) % 360.0 + 540.0) % 360.0 - 180.0) }
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
