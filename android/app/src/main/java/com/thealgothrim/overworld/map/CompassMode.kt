package com.thealgothrim.overworld.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.thealgothrim.overworld.location.deviceHeadings
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.launch

/**
 * The phone map's compass mode (locate tapped a second time, as in Google Maps): the map turns with
 * the phone, the arrow pointing the way the phone does. The camera follows the car with bearing,
 * fed no GPS course (the screen strips it), so Ferrostar leaves the bearing to this. Ends itself
 * when the map is dragged ([onEnded]).
 */
@Composable
fun CompassModeEffect(mapState: NavigationMapState, on: Boolean, onEnded: () -> Unit) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val ended = rememberUpdatedState(onEnded)
  LaunchedEffect(on) {
    if (!on) return@LaunchedEffect
    var target: Float? = null
    // Only while the app is on screen: a sensor left on in the background drains the battery.
    launch { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { deviceHeadings(context).collect { target = it } } }
    var shown = mapState.cameraState.position.bearing
    var last = withFrameNanos { it }
    while (true) {
      val now = withFrameNanos { it }
      val dt = (now - last) / 1e9
      last = now
      if (mapState.cameraMode != NavigationCameraMode.FOLLOW_USER_WITH_BEARING) {
        ended.value()
        break
      }
      val heading = target ?: continue
      val turn = ((heading - shown) % 360.0 + 540.0) % 360.0 - 180.0
      // The sensor shimmers a few tenths of a degree at rest: hold still under that.
      if (abs(turn) < DEADBAND_DEGREES) continue
      shown = (shown + turn * (1 - exp(-dt / EASE_SECONDS)) + 360.0) % 360.0
      mapState.cameraState.position = mapState.cameraState.position.copy(bearing = shown)
    }
  }
}

private const val DEADBAND_DEGREES = 0.6
/** How quickly the map catches up with the phone: about a tenth of a second, like Google's. */
private const val EASE_SECONDS = 0.1

/**
 * Where the phone points while its map is up, steadied; null when unknown. The phone's arrow takes
 * it standing still or walking, as Google's blue dot does: GPS has no heading worth the name there,
 * and the arrow swung about at home while the map stayed put (30 Oct). Moving, GPS's course wins.
 */
object PhoneCompass {
  val heading = mutableStateOf<Double?>(null)

  /** Keeps [heading] while composed and [on]. */
  @Composable
  fun Effect(on: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(on) {
      if (!on) {
        heading.value = null
        return@LaunchedEffect
      }
      try {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
          try {
            deviceHeadings(context).collect { raw ->
              val last = heading.value
              if (last == null) heading.value = raw.toDouble()
              else {
                val turn = ((raw - last) % 360.0 + 540.0) % 360.0 - 180.0
                if (abs(turn) >= 2.0) heading.value = (last + turn * 0.3 + 360.0) % 360.0
              }
            }
          } finally {
            heading.value = null
          }
        }
      } finally {
        heading.value = null
      }
    }
  }
}
