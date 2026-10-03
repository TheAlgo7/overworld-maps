// Adapted from Ferrostar (github.com/stadiamaps/ferrostar, BSD 3-Clause, Stadia Maps):
// ui-maplibre runtime/DisplayedNavigationLocation.kt and FerrostarLocation.kt, which are
// internal there. Overworld draws its own puck, so it needs the same snapped, animated
// position the Ferrostar tracking camera follows, or the puck would drift off-centre.
package com.thealgothrim.overworld.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import com.stadiamaps.ferrostar.core.NavigationUiState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import org.maplibre.compose.location.BearingWithAccuracy
import org.maplibre.compose.location.Location
import org.maplibre.compose.location.PositionWithAccuracy
import org.maplibre.compose.location.SpeedWithAccuracy
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.degrees
import org.maplibre.spatialk.units.extensions.inDegrees
import org.maplibre.spatialk.units.extensions.meters
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.UserLocation

private val DISPLAY_LOCATION_ANIMATION_DURATION = 1000.milliseconds
private const val DISPLAY_BEARING_LOOKBACK_METERS = 5.0
private const val DISPLAY_BEARING_LOOKAHEAD_METERS = 15.0

/**
 * Where the arrow is drawn. On a trip also the route and how far along it the arrow is ([along],
 * metres), measured on the same line, so the route ahead can start exactly under the arrow.
 *
 * [location] and [along] change every frame while the arrow glides between fixes. They are read
 * only where they are drawn, so the rest of the map isn't composed again each frame.
 */
@Stable
class DisplayedPosition(
    val route: RoutePolyline?,
    private val locationState: State<Location>,
    private val alongState: State<Double?>,
) {
  val location: Location
    get() = locationState.value

  val along: Double?
    get() = alongState.value
}

@Composable
fun rememberDisplayedPosition(
    uiState: NavigationUiState,
): DisplayedPosition? {
  val userLocation = uiState.location?.toMapLibreLocation() ?: return null
  return rememberRouteSnappedLocation(uiState, userLocation)
}

@Composable
private fun rememberRouteSnappedLocation(
    uiState: NavigationUiState,
    userLocation: Location,
): DisplayedPosition {
  val route =
      remember(uiState.routeGeometry) {
        uiState.routeGeometry?.takeIf { it.size >= 2 }?.let(::RoutePolyline)
      }
  val raw = rememberUpdatedState(userLocation)

  if (!uiState.isNavigating() || route == null) return remember(raw) { DisplayedPosition(null, raw, NotOnRoute) }
  if (uiState.routeDeviation !is RouteDeviation.NoDeviation) {
    // Off the route: the arrow goes where GPS says, and the route shows from Ferrostar's last
    // point on it until the new route comes.
    val along = rememberUpdatedState(uiState.progress?.let { route.length - it.distanceRemaining })
    return remember(route, raw) { DisplayedPosition(route, raw, along) }
  }

  val targetProjection =
      remember(route, userLocation.position.value) { route.project(userLocation.position.value) }
  val animatedProgress =
      remember(route) { Animatable(targetProjection.progressMeters, DoubleToVector) }

  LaunchedEffect(route, targetProjection.progressMeters) {
    val targetProgress = max(animatedProgress.value, targetProjection.progressMeters)
    if (targetProgress == animatedProgress.value) {
      return@LaunchedEffect
    }

    animatedProgress.animateTo(
        targetValue = targetProgress,
        animationSpec =
            tween(
                durationMillis = DISPLAY_LOCATION_ANIMATION_DURATION.inWholeMilliseconds.toInt(),
                easing = LinearEasing,
            ),
    )
  }

  return remember(route, raw) {
    val location = derivedStateOf {
      val progress = animatedProgress.value
      val fix = raw.value
      Location(
          position = PositionWithAccuracy(value = route.positionAt(progress), accuracy = fix.position.accuracy),
          // While on route, use the route tangent as the display bearing so puck and camera
          // rotation stay stable even when course-over-ground is noisy.
          course = BearingWithAccuracy(value = Bearing.North + route.bearingAt(progress).degrees, accuracy = fix.course?.accuracy),
          speed = fix.speed,
          timestamp = TimeSource.Monotonic.markNow(),
      )
    }
    DisplayedPosition(route, location, derivedStateOf<Double?> { animatedProgress.value })
  }
}

private object NotOnRoute : State<Double?> {
  override val value: Double? = null
}

class RoutePolyline(
    routeGeometry: List<GeographicCoordinate>,
) {
  private val points = routeGeometry.map { Position(it.lng, it.lat) }
  private val cumulativeDistancesMeters = buildCumulativeDistances(points)
  private val totalLengthMeters = cumulativeDistancesMeters.last()

  val length: Double
    get() = totalLengthMeters

  /** The line between [from] and [to] metres along it. */
  fun slice(from: Double, to: Double): List<GeographicCoordinate> {
    val start = positionAt(from)
    val end = positionAt(to)
    val out = mutableListOf(GeographicCoordinate(start.latitude, start.longitude))
    var i = segmentIndexAt(from.coerceIn(0.0, totalLengthMeters)) + 1
    while (i < points.size && cumulativeDistancesMeters[i] < to) {
      if (cumulativeDistancesMeters[i] > from) out += GeographicCoordinate(points[i].latitude, points[i].longitude)
      i++
    }
    out += GeographicCoordinate(end.latitude, end.longitude)
    return out
  }

  internal fun project(position: Position): RouteProjection {
    var bestDistanceSquared = Double.POSITIVE_INFINITY
    var bestProjection = RouteProjection(progressMeters = 0.0)

    for (index in 0 until points.lastIndex) {
      val segmentProjection = projectOntoSegment(index, position)
      if (segmentProjection.distanceSquaredMeters < bestDistanceSquared) {
        bestDistanceSquared = segmentProjection.distanceSquaredMeters
        bestProjection = RouteProjection(progressMeters = segmentProjection.progressMeters)
      }
    }

    return bestProjection
  }

  fun positionAt(progressMeters: Double): Position {
    val clampedProgress = progressMeters.coerceIn(0.0, totalLengthMeters)
    val segmentIndex = segmentIndexAt(clampedProgress)
    val segmentStart = points[segmentIndex]
    val segmentEnd = points[segmentIndex + 1]
    val segmentLength =
        cumulativeDistancesMeters[segmentIndex + 1] - cumulativeDistancesMeters[segmentIndex]

    if (segmentLength == 0.0) {
      return segmentStart
    }

    val t = (clampedProgress - cumulativeDistancesMeters[segmentIndex]) / segmentLength
    return Position(
        interpolateCoordinate(segmentStart.longitude, segmentEnd.longitude, t),
        interpolateCoordinate(segmentStart.latitude, segmentEnd.latitude, t),
    )
  }

  fun bearingAt(progressMeters: Double): Double {
    val clampedProgress = progressMeters.coerceIn(0.0, totalLengthMeters)
    val startProgress = (clampedProgress - DISPLAY_BEARING_LOOKBACK_METERS).coerceAtLeast(0.0)
    val endProgress =
        (clampedProgress + DISPLAY_BEARING_LOOKAHEAD_METERS).coerceAtMost(totalLengthMeters)

    if (endProgress <= startProgress) {
      val segmentIndex = segmentIndexAt(clampedProgress)
      return bearingDegrees(points[segmentIndex], points[segmentIndex + 1])
    }

    return bearingDegrees(positionAt(startProgress), positionAt(endProgress))
  }

  /** The segment holding [progressMeters]; a binary search, as this runs several times a frame. */
  private fun segmentIndexAt(progressMeters: Double): Int {
    var low = 0
    var high = cumulativeDistancesMeters.lastIndex - 1
    if (high < 0) return 0
    while (low < high) {
      val mid = (low + high) / 2
      if (progressMeters <= cumulativeDistancesMeters[mid + 1]) high = mid else low = mid + 1
    }
    return max(0, low)
  }

  private fun projectOntoSegment(index: Int, position: Position): SegmentProjection {
    val start = points[index]
    val end = points[index + 1]
    val meanLatitudeRadians =
        Math.toRadians((start.latitude + end.latitude + position.latitude) / 3.0)
    val scaleX = 111_320.0 * cos(meanLatitudeRadians)
    val scaleY = 111_320.0

    val startX = start.longitude * scaleX
    val startY = start.latitude * scaleY
    val endX = end.longitude * scaleX
    val endY = end.latitude * scaleY
    val pointX = position.longitude * scaleX
    val pointY = position.latitude * scaleY

    val segmentX = endX - startX
    val segmentY = endY - startY
    val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
    val rawT =
        if (segmentLengthSquared == 0.0) {
          0.0
        } else {
          ((pointX - startX) * segmentX + (pointY - startY) * segmentY) / segmentLengthSquared
        }
    val t = rawT.coerceIn(0.0, 1.0)
    val projectedX = startX + segmentX * t
    val projectedY = startY + segmentY * t
    val distanceSquaredMeters =
        (pointX - projectedX) * (pointX - projectedX) +
            (pointY - projectedY) * (pointY - projectedY)
    val segmentLengthMeters = sqrt(segmentLengthSquared)

    return SegmentProjection(
        progressMeters = cumulativeDistancesMeters[index] + segmentLengthMeters * t,
        distanceSquaredMeters = distanceSquaredMeters,
    )
  }
}

internal data class RouteProjection(
    val progressMeters: Double,
)

private data class SegmentProjection(
    val progressMeters: Double,
    val distanceSquaredMeters: Double,
)

private val DoubleToVector =
    TwoWayConverter<Double, AnimationVector1D>(
        convertToVector = { AnimationVector1D(it.toFloat()) },
        convertFromVector = { it.value.toDouble() },
    )

private fun interpolateCoordinate(start: Double, end: Double, t: Double): Double =
    start + (end - start) * t

private fun buildCumulativeDistances(points: List<Position>): List<Double> {
  val cumulativeDistances = ArrayList<Double>(points.size)
  cumulativeDistances += 0.0

  for (index in 1 until points.size) {
    cumulativeDistances +=
        cumulativeDistances.last() + distanceMeters(points[index - 1], points[index])
  }

  return cumulativeDistances
}

private fun distanceMeters(a: Position, b: Position): Double {
  val latitudeDeltaMeters = (b.latitude - a.latitude) * 111_320.0
  val averageLatitudeRadians = Math.toRadians((a.latitude + b.latitude) / 2.0)
  val longitudeDeltaMeters = (b.longitude - a.longitude) * 111_320.0 * cos(averageLatitudeRadians)

  return sqrt(
      latitudeDeltaMeters * latitudeDeltaMeters + longitudeDeltaMeters * longitudeDeltaMeters,
  )
}

private fun bearingDegrees(start: Position, end: Position): Double {
  val meanLatitudeRadians = Math.toRadians((start.latitude + end.latitude) / 2.0)
  val deltaX = (end.longitude - start.longitude) * cos(meanLatitudeRadians)
  val deltaY = end.latitude - start.latitude
  return (Math.toDegrees(atan2(deltaX, deltaY)) + 360.0) % 360.0
}

private fun UserLocation.toMapLibreLocation(): Location =
    Location(
        position =
            PositionWithAccuracy(
                value = Position(coordinates.lng, coordinates.lat),
                accuracy = horizontalAccuracy.meters,
            ),
        course =
            courseOverGround?.let { course ->
              BearingWithAccuracy(
                  value = Bearing.North + course.degrees.toDouble().degrees,
                  accuracy = course.accuracy?.toDouble()?.degrees,
              )
            },
        speed =
            speed?.let { speed ->
              SpeedWithAccuracy(
                  distancePerSecond = speed.value.meters,
                  accuracy = speed.accuracy?.meters,
              )
            },
        timestamp = TimeSource.Monotonic.markNow(),
    )

val Location.courseDegrees: Double?
  get() = course?.value?.let { (it - Bearing.North).inDegrees }
