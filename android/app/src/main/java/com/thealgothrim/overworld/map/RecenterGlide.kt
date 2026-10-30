package com.thealgothrim.overworld.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees

/**
 * Re-centring that lands on the car where it is now. Ferrostar's own re-centre flies for a fixed
 * second to where the car was at the tap, holding the camera off the car meanwhile, then jumps to
 * where it has got to (about 17 m at 60 km/h): the arrow slid away and snapped back. This glide
 * aims at the arrow's live position every frame, for [GLIDE_MS], and hands over to Ferrostar's
 * tracking already on the car, so nothing jumps.
 */
@Stable
class RecenterGlide {
  internal var request by mutableStateOf<NavigationCameraMode?>(null)
    private set

  /** The arrow on this map, set by the map's layers. Read each frame of a glide, never composed. */
  internal var shown: DisplayedPosition? = null

  /** Glide back to following the car in [mode]. */
  fun start(mode: NavigationCameraMode) {
    request = mode
  }

  internal fun done() {
    request = null
  }
}

private val glides = java.util.WeakHashMap<NavigationMapState, RecenterGlide>()

/** This map's re-centre glide. */
val NavigationMapState.glide: RecenterGlide
  get() = synchronized(glides) { glides.getOrPut(this) { RecenterGlide() } }

/** Back to following the car, gliding onto it (see RecenterGlide); Ferrostar's recenter otherwise. */
fun NavigationMapState.glideRecenter(isNavigating: Boolean) =
    glide.start(if (isNavigating) NavigationCameraMode.FOLLOW_USER_WITH_BEARING else NavigationCameraMode.FOLLOW_USER)

/** Runs this map's glides, aiming at its arrow each frame. */
@Composable
fun RecenterGlideEffect(mapState: NavigationMapState) {
  val glide = mapState.glide
  val mode = glide.request
  LaunchedEffect(mode) {
    if (mode == null) return@LaunchedEffect
    val from = mapState.cameraState.position
    val options = mapState.navigationCameraOptions
    // Ferrostar keeps its hands off while we fly; it notes the new mode, so it won't start its
    // own flight when we hand over.
    mapState.trackingPaused = true
    mapState.cameraMode = mode
    try {
      val start = withFrameNanos { it }
      while (true) {
        val now = withFrameNanos { it }
        // A drag or the overview took over.
        if (mapState.cameraMode != mode) break
        val location = glide.shown?.location ?: break
        val f = ((now - start) / 1_000_000f / GLIDE_MS).coerceIn(0f, 1f)
        val target = location.position.value
        val bearing = location.course?.value?.let { (it - Bearing.North).inDegrees }
        val to =
            if (mode == NavigationCameraMode.FOLLOW_USER) options.browsingUser(target)
            else options.navigatingUser(target, bearing ?: from.bearing)
        mapState.cameraState.position = between(from, to, FastOutSlowInEasing.transform(f))
        if (f >= 1f) break
      }
    } finally {
      mapState.trackingPaused = false
      glide.done()
    }
  }
}

/** The camera [t] of the way from [a] to [b]: bearing the short way round, padding included. */
internal fun between(a: CameraPosition, b: CameraPosition, t: Float): CameraPosition {
  val ld = LayoutDirection.Ltr
  fun pad(p: PaddingValues, q: PaddingValues) =
      PaddingValues(
          start = lerp(p.calculateStartPadding(ld), q.calculateStartPadding(ld), t),
          top = lerp(p.calculateTopPadding(), q.calculateTopPadding(), t),
          end = lerp(p.calculateEndPadding(ld), q.calculateEndPadding(ld), t),
          bottom = lerp(p.calculateBottomPadding(), q.calculateBottomPadding(), t),
      )
  val turn = ((b.bearing - a.bearing) % 360.0 + 540.0) % 360.0 - 180.0
  return CameraPosition(
      target = Position(a.target.longitude + (b.target.longitude - a.target.longitude) * t, a.target.latitude + (b.target.latitude - a.target.latitude) * t),
      zoom = a.zoom + (b.zoom - a.zoom) * t,
      tilt = a.tilt + (b.tilt - a.tilt) * t,
      bearing = (a.bearing + turn * t + 360.0) % 360.0,
      padding = pad(a.padding, b.padding),
  )
}

private fun lerp(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp, t: Float) = (a.value + (b.value - a.value) * t).dp

/** Long enough to read as a move, short enough to feel like an answer to the tap. */
private const val GLIDE_MS = 450f
