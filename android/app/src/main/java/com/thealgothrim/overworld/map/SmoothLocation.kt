package com.thealgothrim.overworld.map

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.traffic.metres
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
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
  val smooth = rememberSmoothLocation(uiState.location)
  val onRoute =
      uiState.isNavigating() &&
          uiState.routeDeviation is RouteDeviation.NoDeviation &&
          (uiState.routeGeometry?.size ?: 0) >= 2
  return if (onRoute || smooth == null) uiState else uiState.copy(location = smooth)
}

@Composable
private fun rememberSmoothLocation(raw: UserLocation?): UserLocation? {
  val glide = remember { Glide() }
  val progress = remember { Animatable(1f) }
  // Unwrapped degrees, so turning from 350 to 10 goes through north instead of all the way round.
  val heading = remember { Animatable(0f) }
  var hasHeading by remember { mutableStateOf(false) }

  LaunchedEffect(raw) {
    raw ?: return@LaunchedEffect
    val now = SystemClock.elapsedRealtime()
    val shown = glide.at(progress.value)
    val previousFix = glide.to
    // A first fix, or one far from the arrow (a GPS jump, the app coming back): go straight there.
    val jump = shown == null || metres(shown, raw.coordinates) > MAX_GLIDE_METRES
    glide.from = if (jump) raw.coordinates else shown
    glide.to = raw.coordinates
    val gap = glide.lastFixAt?.let { now - it }?.coerceIn(MIN_GLIDE_MS, MAX_GLIDE_MS) ?: 1000L
    glide.lastFixAt = now

    headingOf(raw, previousFix)?.let { target ->
      if (!hasHeading) {
        heading.snapTo(target.toFloat())
        hasHeading = true
      } else {
        val current = heading.value
        val delta = ((target - current) % 360 + 540) % 360 - 180
        launch { heading.animateTo((current + delta).toFloat(), tween(TURN_MS)) }
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

/** The stretch being glided along: from where the arrow was to the newest fix. */
private class Glide {
  var from: GeographicCoordinate? = null
  var to: GeographicCoordinate? = null
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
private const val TURN_MS = 600
/** 7 km/h: slower than this, GPS course is noise. */
private const val MIN_HEADING_SPEED = 2.0
private const val MIN_HEADING_MOVE = 8.0
