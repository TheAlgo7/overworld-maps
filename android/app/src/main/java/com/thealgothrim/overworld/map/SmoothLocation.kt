package com.thealgothrim.overworld.map

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.traffic.metres
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.UserLocation

/**
 * The location the map shows when it isn't following a route (no trip, or off the route).
 *
 * GPS gives one fix a second. Shown as they come, the arrow and the map jumped once a fix and the
 * map pointed north, which is how free driving looked in the Curvv. This glides from where the arrow
 * is to each new fix over the time the fix took to arrive, and turns the heading the short way
 * round. Ferrostar's camera and our arrow both follow it, so they stay together. While on a route
 * Ferrostar animates its own route-snapped position, so that case is passed through untouched.
 */
@Composable
fun rememberSmoothedUiState(uiState: NavigationUiState): NavigationUiState {
  val onRoute =
      uiState.isNavigating() &&
          uiState.routeDeviation is RouteDeviation.NoDeviation &&
          (uiState.routeGeometry?.size ?: 0) >= 2
  val smooth = rememberSmoothLocation(uiState.location, active = !onRoute)
  if (onRoute) return rememberLeadOnRoute(uiState)
  return if (smooth == null) uiState else uiState.copy(location = smooth)
}

/**
 * What the map is given on a route. The fix moves ahead along it by [leadMetres]: Ferrostar's
 * camera and our arrow both glide a second to each fix, so aiming at the fix itself left both a
 * second behind the car. And the line is [steadied]: the camera turns to the route's direction a
 * few metres either side of the arrow, and OpenStreetMap's little kinks (a node a metre off, a lane
 * merge drawn as a zigzag) swung the map left and right on a straight road.
 */
@Composable
private fun rememberLeadOnRoute(raw: NavigationUiState): NavigationUiState {
  val geometry = raw.routeGeometry ?: return raw
  val steady = remember(geometry) { steadied(geometry) }
  val uiState = raw.copy(routeGeometry = steady)
  val fix = uiState.location ?: return uiState
  val route = remember(steady) { RoutePolyline(steady) }
  val led =
      remember(fix, route) {
        val lead = leadMetres(fix)
        if (lead <= 0.0) return@remember fix
        val at = route.project(Position(fix.coordinates.lng, fix.coordinates.lat)).progressMeters
        // Lead only along the line the car is following: projected on the route, then ahead on it.
        val p = route.positionAt((at + lead).coerceAtMost(route.length))
        fix.copy(coordinates = GeographicCoordinate(p.latitude, p.longitude))
      }
  return if (led === fix) uiState else uiState.copy(location = led)
}

/**
 * [line] without the points that sit within [STEADY_METRES] of the straight line around them
 * (Douglas-Peucker). Corners stay: a real turn is far more than a couple of metres off.
 */
internal fun steadied(line: List<GeographicCoordinate>): List<GeographicCoordinate> {
  if (line.size < 3) return line
  val keep = BooleanArray(line.size)
  keep[0] = true
  keep[line.lastIndex] = true
  val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to line.lastIndex) }
  val lat0 = Math.toRadians(line[0].lat)
  fun x(p: GeographicCoordinate) = p.lng * 111_320.0 * cos(lat0)
  fun y(p: GeographicCoordinate) = p.lat * 111_320.0
  while (stack.isNotEmpty()) {
    val (a, b) = stack.removeLast()
    if (b - a < 2) continue
    val ax = x(line[a]); val ay = y(line[a]); val bx = x(line[b]); val by = y(line[b])
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    var far = -1
    var farDist = STEADY_METRES
    for (i in a + 1 until b) {
      val px = x(line[i]) - ax; val py = y(line[i]) - ay
      val t = if (len2 == 0.0) 0.0 else ((px * dx + py * dy) / len2).coerceIn(0.0, 1.0)
      val ex = px - dx * t; val ey = py - dy * t
      val d = Math.sqrt(ex * ex + ey * ey)
      if (d > farDist) {
        farDist = d
        far = i
      }
    }
    if (far >= 0) {
      keep[far] = true
      stack.add(a to far)
      stack.add(far to b)
    }
  }
  return line.filterIndexed { i, _ -> keep[i] }
}

private const val STEADY_METRES = 2.0

/**
 * The gliding location. Only while [active]: reading the animation here recomposes the whole map
 * every frame, which on a route is already Ferrostar's job, so then it stays idle.
 */
@Composable
private fun rememberSmoothLocation(raw: UserLocation?, active: Boolean): UserLocation? {
  val glide = remember { Glide() }
  val progress = remember { Animatable(1f) }
  // Unwrapped degrees, so turning from 350 to 10 goes through north instead of all the way round.
  val heading = remember { Animatable(0f) }
  var hasHeading by remember { mutableStateOf(false) }

  LaunchedEffect(raw, active) {
    raw ?: return@LaunchedEffect
    if (!active) {
      // Ferrostar moves the arrow; forget the glide so it starts afresh off the route.
      glide.from = null
      glide.to = null
      glide.fix = null
      glide.lastFixAt = null
      hasHeading = false
      progress.snapTo(1f)
      return@LaunchedEffect
    }
    val now = SystemClock.elapsedRealtime()
    val shown = glide.at(progress.value)
    val previousFix = glide.fix
    val gap = glide.lastFixAt?.let { now - it }?.coerceIn(MIN_GLIDE_MS, MAX_GLIDE_MS) ?: 1000L
    glide.lastFixAt = now
    glide.fix = raw.coordinates

    val course = headingOf(raw, previousFix)
    // Aim where the car will be when the glide ends, not where the fix was: gliding to the fix
    // itself kept the arrow a second or more behind the car.
    val target = course?.let { ahead(raw.coordinates, it, predictMetres(raw, gap)) } ?: raw.coordinates
    // A first fix, or one far from the arrow (a GPS jump, the app coming back): go straight there.
    val jump = shown == null || metres(shown, target) > MAX_GLIDE_METRES
    glide.from = if (jump) target else shown
    glide.to = target

    course?.let { turnTo ->
      if (!hasHeading) {
        heading.snapTo(turnTo.toFloat())
        hasHeading = true
      } else {
        val current = heading.value
        val delta = ((turnTo - current) % 360 + 540) % 360 - 180
        // GPS course wobbles a few degrees fix to fix; turning the map for that is the jitter.
        // A spring also carries its speed into the next fix's turn instead of stopping and starting.
        if (abs(delta) >= HEADING_DEADBAND) {
          launch { heading.animateTo((current + delta).toFloat(), spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow)) }
        }
      }
    }

    if (jump) {
      progress.snapTo(1f)
    } else {
      progress.snapTo(0f)
      progress.animateTo(1f, tween(gap.toInt(), easing = LinearEasing))
    }
  }

  raw ?: return null
  if (!active) return raw
  val at = glide.at(progress.value) ?: raw.coordinates
  val course =
      if (hasHeading) CourseOverGround(((heading.value.roundToInt() % 360) + 360).rem(360).toUShort(), null)
      else raw.courseOverGround
  return raw.copy(coordinates = at, courseOverGround = course)
}

/**
 * Which way the car points, from a fix: GPS's own course once moving faster than a walk (below
 * that it is noise, and stopped at a light it spins), or the line from the last fix when GPS gives
 * none. Null keeps the heading the map already has.
 */
private fun headingOf(fix: UserLocation, previous: GeographicCoordinate?): Double? {
  val speed = fix.speed?.value
  if (speed != null && speed < MIN_HEADING_SPEED) return null
  fix.courseOverGround?.let { return it.degrees.toDouble() }
  if (previous == null || metres(previous, fix.coordinates) < MIN_HEADING_MOVE) return null
  val dx = (fix.coordinates.lng - previous.lng) * cos(Math.toRadians(fix.coordinates.lat))
  val dy = fix.coordinates.lat - previous.lat
  return (Math.toDegrees(atan2(dx, dy)) + 360) % 360
}

/** Metres the car covers between [fix] being taken and the end of a [gapMs] glide. */
private fun predictMetres(fix: UserLocation, gapMs: Long): Double {
  val speed = fix.speed?.value ?: return 0.0
  if (speed < MIN_HEADING_SPEED) return 0.0
  val age = (Duration.between(fix.timestamp, Instant.now()).toMillis()).coerceIn(0L, 1500L)
  return speed * (gapMs + age) / 1000.0
}

/** The point [metres] from [from] towards compass [bearing]. */
private fun ahead(from: GeographicCoordinate, bearing: Double, metres: Double): GeographicCoordinate {
  if (metres <= 0.0) return from
  val rad = Math.toRadians(bearing)
  val dLat = metres * cos(rad) / 111_320.0
  val dLng = metres * sin(rad) / (111_320.0 * cos(Math.toRadians(from.lat)))
  return GeographicCoordinate(from.lat + dLat, from.lng + dLng)
}

/** The stretch being glided along: from where the arrow was to where the car should be next. */
private class Glide {
  var from: GeographicCoordinate? = null
  var to: GeographicCoordinate? = null
  /** The newest raw fix, for the heading from movement when GPS gives none. */
  var fix: GeographicCoordinate? = null
  var lastFixAt: Long? = null

  fun at(t: Float): GeographicCoordinate? {
    val a = from ?: return to
    val b = to ?: return a
    return GeographicCoordinate(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
  }
}

/** Farther than this from the arrow, a fix is a jump, not movement. About 7 s at 70 km/h. */
private const val MAX_GLIDE_METRES = 150.0
private const val MIN_GLIDE_MS = 250L
private const val MAX_GLIDE_MS = 1500L
/** Course changes smaller than this, in degrees, are GPS wobble and don't turn the map. */
private const val HEADING_DEADBAND = 2.5
/** 7 km/h: slower than this, GPS course is noise. */
private const val MIN_HEADING_SPEED = 2.0
private const val MIN_HEADING_MOVE = 8.0
