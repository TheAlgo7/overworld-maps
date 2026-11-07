package com.thealgothrim.overworld.location

import android.os.SystemClock
import com.thealgothrim.overworld.routing.LevelAnalysis
import com.thealgothrim.overworld.routing.Level
import com.thealgothrim.overworld.routing.ParallelRoad
import com.thealgothrim.overworld.routing.RoadLevels
import kotlin.math.cos
import kotlin.math.hypot
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.UserLocation

/** Height changes over the last few seconds (the barometer; a stand-in in tests). */
interface HeightChange {
  /** Metres risen over the last [seconds], or null without readings. */
  fun rise(seconds: Int): Float?

  /** Metres fallen over the last [seconds], or null without readings. */
  fun drop(seconds: Int): Float?
}

/** The phone's barometer as a [HeightChange]. */
object PhoneHeight : HeightChange {
  override fun rise(seconds: Int) = Barometer.rise(seconds)

  override fun drop(seconds: Int) = Barometer.drop(seconds)
}

/**
 * Where a reroute should start when [LevelCheck] found the car on a road beside the route: on that
 * road ([at], heading [heading]), not wherever a router would snap the raw fix. Between a flyover
 * and the service road under it, a router given the fix picks either, and handing it the road the
 * route already used would only send the car back there.
 */
data class RerouteHint(val at: GeographicCoordinate, val heading: Double, val level: Level, val madeAtMs: Long)

object RerouteHints {
  @Volatile private var pending: RerouteHint? = null

  fun offer(hint: RerouteHint) {
    pending = hint
  }

  /** The hint for a route from [near], if one was made in the last 20 s close to it; each is used once. */
  fun take(near: GeographicCoordinate): RerouteHint? {
    val h = pending ?: return null
    pending = null
    if (SystemClock.elapsedRealtime() - h.madeAtMs > FRESH_MS) return null
    val kx = 111_320.0 * cos(Math.toRadians(near.lat))
    if (hypot((h.at.lng - near.lng) * kx, (h.at.lat - near.lat) * 111_320.0) > NEAR_M) return null
    return h
  }

  private const val FRESH_MS = 20_000L
  private const val NEAR_M = 80.0
}

/**
 * Whether the car is on a road beside the route at another level: the flyover the route goes
 * under, or the service road under the flyover the route takes. They are 10 to 30 m apart, under
 * the general off-route rule's 30 m (SustainedDeviation), so the arrow stayed snapped to the route
 * while the car was on the other road (7 Nov, Shankar Chowk to Ambience, until a late reroute).
 *
 * A fix counts when it's within [ALT_NEAR_M] of the other road and clearly nearer it than the
 * route. Four such fixes in a row (about 4 s) make the call; two when the barometer agrees (a climb
 * onto a flyover the route doesn't take, no climb where the route goes up). It never calls it
 * against the barometer unless GPS insists for [AGAINST_HEIGHT] fixes.
 */
class LevelCheck(
    private val height: HeightChange = PhoneHeight,
    /** Elapsed time, for the hint's freshness (a plain counter in tests). */
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
  private var streak = 0
  /** Fixes in a row, right after a split, heading the other road's way. */
  private var forkStreak = 0
  private var quietUntilMs = Long.MIN_VALUE

  private class Candidate(val road: ParallelRoad, val distance: Double, val point: GeographicCoordinate, val heading: Double)

  private enum class HeightSays { AGREES, DISAGREES, UNKNOWN }

  /**
   * The hint to reroute from, when [fix] (with the route [offRoute] metres away, [along] metres into
   * it) settles that the car is on one of [levels]' parallel roads; null otherwise.
   */
  fun judge(fix: UserLocation, along: Double, offRoute: Double?, levels: RoadLevels): RerouteHint? {
    val nowMs = fix.timestamp.toEpochMilli()
    if (nowMs < quietUntilMs) return null
    val speed = fix.speed?.value
    val course = fix.courseOverGround?.degrees?.toDouble()
    // Too slow or too vague to say: the count holds where it was.
    if (offRoute == null || speed == null || course == null || speed < MIN_SPEED_MS || fix.horizontalAccuracy > MAX_ACCURACY_M) return null
    var best: Candidate? = null
    for (road in levels.parallels) {
      if (along < road.from - REACH_M || along > road.to + REACH_M) continue
      val c = nearestOn(fix.coordinates, course, road) ?: continue
      if (best == null || c.distance < best.distance) best = c
    }
    val b = best
    if (b == null || b.distance > ALT_NEAR_M) {
      streak = 0
      forkStreak = 0
      return null
    }
    // Just past the split the two roads are only metres apart, but already run at different
    // angles: a car that carried straight on is told by its heading. Google Maps switched route a
    // second after the Shankar Chowk split in a replay of the 7 Nov drive (on GPS alone, no
    // barometer); waiting for the roads to part took this check 10 s and more.
    if (along >= b.road.from - FORK_BEFORE_M && along <= b.road.from + FORK_REACH_M && offRoute >= b.distance) {
      val offRouteWay = LevelAnalysis.angle(course, levels.headingAt(along))
      val offOtherWay = LevelAnalysis.angle(course, b.heading)
      if (offRouteWay >= FORK_TURN_DEG && offOtherWay <= offRouteWay - FORK_MARGIN_DEG) {
        if (++forkStreak >= FORK_FIXES) return called(nowMs, b)
      } else forkStreak = 0
    } else forkStreak = 0
    if (offRoute - b.distance < maxOf(MIN_GAP_M, GAP_SHARE * (offRoute + b.distance))) {
      streak = 0
      return null
    }
    streak = (streak + 1).coerceAtMost(AGAINST_HEIGHT)
    val needed =
        when (heightSays(b.road, levels, along)) {
          HeightSays.AGREES -> WITH_HEIGHT
          HeightSays.UNKNOWN -> ON_GPS_ALONE
          HeightSays.DISAGREES -> AGAINST_HEIGHT
        }
    if (streak < needed) return null
    return called(nowMs, b)
  }

  private fun called(nowMs: Long, b: Candidate): RerouteHint {
    streak = 0
    forkStreak = 0
    quietUntilMs = nowMs + QUIET_MS
    return RerouteHint(b.point, b.heading, b.road.level, clock())
  }

  /** What the barometer says about the car being on [road] rather than the route. */
  private fun heightSays(road: ParallelRoad, levels: RoadLevels, along: Double): HeightSays {
    val rise = height.rise(WINDOW_S) ?: return HeightSays.UNKNOWN
    val drop = height.drop(WINDOW_S) ?: return HeightSays.UNKNOWN
    val routeLevel = levels.levelAt(along)
    val routeStart = levels.structureStartAt(along)
    val intoOther = if (road.structureAt.isNaN()) Double.NEGATIVE_INFINITY else along - road.structureAt
    return when {
      // A flyover beside the route: the car climbed onto it, or is plainly still on the ground.
      road.level == Level.BRIDGE && routeLevel == Level.SURFACE ->
          when {
            rise >= CLIMB_M -> HeightSays.AGREES
            intoOther >= ON_STRUCTURE_M && rise < FLAT_M -> HeightSays.DISAGREES
            else -> HeightSays.UNKNOWN
          }
      road.level == Level.TUNNEL && routeLevel == Level.SURFACE ->
          when {
            drop >= CLIMB_M -> HeightSays.AGREES
            intoOther >= ON_STRUCTURE_M && drop < FLAT_M -> HeightSays.DISAGREES
            else -> HeightSays.UNKNOWN
          }
      // The route's own flyover: the car stayed down on the road beside it, or climbed with it.
      road.level == Level.SURFACE && routeLevel == Level.BRIDGE && routeStart != null ->
          when {
            along - routeStart >= ON_STRUCTURE_M && rise < FLAT_M -> HeightSays.AGREES
            rise >= CLIMB_M -> HeightSays.DISAGREES
            else -> HeightSays.UNKNOWN
          }
      road.level == Level.SURFACE && routeLevel == Level.TUNNEL && routeStart != null ->
          when {
            along - routeStart >= ON_STRUCTURE_M && drop < FLAT_M -> HeightSays.AGREES
            drop >= CLIMB_M -> HeightSays.DISAGREES
            else -> HeightSays.UNKNOWN
          }
      else -> HeightSays.UNKNOWN
    }
  }

  /** The nearest point of [road] to [p] on a stretch running within [ALIGN_DEG] of [course] (either way). */
  private fun nearestOn(p: GeographicCoordinate, course: Double, road: ParallelRoad): Candidate? {
    val kx = 111_320.0 * cos(Math.toRadians(p.lat))
    val ky = 111_320.0
    var best: Candidate? = null
    for (line in road.lines) {
      for (i in 0 until line.lastIndex) {
        val ax = (line[i].lng - p.lng) * kx
        val ay = (line[i].lat - p.lat) * ky
        val bx = (line[i + 1].lng - p.lng) * kx
        val by = (line[i + 1].lat - p.lat) * ky
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 < 0.25) continue
        val t = (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val ex = ax + dx * t
        val ey = ay + dy * t
        val d = hypot(ex, ey)
        if (d > REACH_M || (best != null && d >= best.distance)) continue
        val segHeading = LevelAnalysis.bearing(dx, dy)
        val turn = LevelAnalysis.angle(segHeading, course)
        val heading =
            when {
              turn <= ALIGN_DEG -> segHeading
              turn >= 180 - ALIGN_DEG -> (segHeading + 180) % 360
              else -> continue
            }
        best = Candidate(road, d, GeographicCoordinate(p.lat + ey / ky, p.lng + ex / kx), heading)
      }
    }
    return best
  }

  private companion object {
    /** About 15 km/h: slower, GPS course wanders too much to tell two roads side by side apart. */
    const val MIN_SPEED_MS = 4.0
    const val MAX_ACCURACY_M = 15.0
    /** The car is on the other road only when GPS puts it this close to it. */
    const val ALT_NEAR_M = 10.0
    /** And this much nearer it than the route, or this share of the gap between them. */
    const val MIN_GAP_M = 5.0
    const val GAP_SHARE = 0.3
    const val REACH_M = 30.0
    const val ALIGN_DEG = 35.0
    const val ON_GPS_ALONE = 4
    const val WITH_HEIGHT = 2
    const val AGAINST_HEIGHT = 10
    /** No second call for a while after one: the reroute takes a moment to arrive. */
    const val QUIET_MS = 25_000L
    const val WINDOW_S = 25
    /** A flyover deck is 6 to 8 m up; half of that is a climb. */
    const val CLIMB_M = 2.5f
    const val FLAT_M = 1.0f
    /** This far along a flyover or underpass, its deck or floor is well away from the ground. */
    const val ON_STRUCTURE_M = 100.0
    /** The heading rule runs from just before a split to this far past it... */
    const val FORK_BEFORE_M = 10.0
    const val FORK_REACH_M = 150.0
    /** ...where the car's heading is at least this far off the route's way... */
    const val FORK_TURN_DEG = 10.0
    /** ...and this much closer to the other road's... */
    const val FORK_MARGIN_DEG = 7.0
    /** ...for this many fixes in a row. */
    const val FORK_FIXES = 2
  }
}
