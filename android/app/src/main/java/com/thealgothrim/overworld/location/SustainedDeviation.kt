package com.thealgothrim.overworld.location

import java.time.Duration
import java.time.Instant
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
 */
class SustainedDeviation(
    private val nearMetres: Double = 30.0,
    private val nearSeconds: Long = 8,
    private val farMetres: Double = 60.0,
    private val farSeconds: Long = 2,
    /** Vaguer fixes than this can't start or settle a detour. */
    private val maxAccuracyMetres: Double = 20.0,
) : RouteDeviationDetector {
  /** Seconds driven off the route so far; null while on it. */
  @Volatile private var offFor: Double? = null
  @Volatile private var lastAt: Instant? = null

  override fun checkRouteDeviation(route: Route, tripState: TripState): RouteDeviation {
    val trip = tripState as? TripState.Navigating
    if (trip == null) {
      offFor = null
      lastAt = null
      return RouteDeviation.NoDeviation
    }
    val fix = trip.userLocation
    val off = metresFromSteps(fix.coordinates, trip.remainingSteps.take(3).map { it.geometry })
    return judge(off, fix.horizontalAccuracy, fix.timestamp, fix.speed?.value)
  }

  /**
   * The rule itself, for [off] metres from the route at [at], with a fix good to [accuracy] metres
   * and [speed] m/s. Only time spent moving counts: a car parked 40 m from where the route starts,
   * or waiting at a light beside it, isn't leaving it (counting that rerouted a parked car at once).
   */
  internal fun judge(off: Double?, accuracy: Double, at: Instant, speed: Double? = null): RouteDeviation {
    val previous = lastAt
    lastAt = at
    // Too vague to tell (or no route left): no verdict, and a detour's clock keeps where it was.
    if (off == null || accuracy > maxAccuracyMetres) return RouteDeviation.NoDeviation
    if (off < nearMetres) {
      offFor = null
      return RouteDeviation.NoDeviation
    }
    val moving = speed == null || speed >= MOVING_MS
    val step = if (previous == null) 0.0 else (Duration.between(previous, at).toMillis() / 1000.0).coerceIn(0.0, 5.0)
    val seconds = (offFor ?: 0.0) + if (moving) step else 0.0
    offFor = seconds
    val gone = seconds >= nearSeconds || (off >= farMetres && seconds >= farSeconds)
    return if (gone) RouteDeviation.Deviation(DeviationKind.CompletelyOffRoute(off)) else RouteDeviation.NoDeviation
  }

  private companion object {
    /** About 5 km/h. */
    const val MOVING_MS = 1.5
  }
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
