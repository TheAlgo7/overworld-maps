package com.thealgothrim.overworld.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameMillis
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.thealgothrim.overworld.theme.Skin
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.delay

/**
 * How each game's map camera sits while driving, matched to the games: GTA VI's minimap is a steep
 * 3D view close in (Rockstar's Extended Look, 60° is MapLibre's limit), GTA V's radar a gentler
 * tilt, and Red Dead's minimap is flat, looked at from straight above.
 */
data class DriveCamera(val tilt: Double, val zoomOffset: Double)

val Skin.driveCamera: DriveCamera
  get() =
      when (this) {
        Skin.GTA -> DriveCamera(tilt = 45.0, zoomOffset = 0.0)
        Skin.RDR -> DriveCamera(tilt = 0.0, zoomOffset = 0.0)
        Skin.GTA6 -> DriveCamera(tilt = 60.0, zoomOffset = 0.4)
      }

/**
 * The zoom Google Maps would pick: closer in slow city traffic and before a turn, pulled back on a
 * fast straight road so more of it ahead shows. [base] is the zoom at about 40 km/h.
 */
fun autoZoom(base: Double, speedMs: Double?, toTurnMetres: Double?): Double {
  val kmh = (speedMs ?: 0.0) * 3.6
  // 20 km/h and under: base + 0.4. 110 km/h and over: base - 1.2.
  var zoom = base + 0.4 - ((kmh - 20.0).coerceIn(0.0, 90.0) / 90.0) * 1.6
  if (toTurnMetres != null) {
    // A long way to the next turn on a fast road: a little further out still.
    if (toTurnMetres > 2_000 && kmh > 50) zoom -= 0.3
    // From 400 m out, come in to base + 0.8 by the last 80 m.
    val near = 1.0 - ((toTurnMetres - 80.0) / 320.0).coerceIn(0.0, 1.0)
    zoom += (base + 0.8 - zoom).coerceAtLeast(0.0) * near
  }
  return zoom
}

/**
 * Eases the following camera's zoom towards [autoZoom] while it follows heading-up. Ferrostar keeps
 * whatever zoom the camera has, so without this it never changed on its own. A zoom the driver set
 * (pinch or the + / - buttons) is left alone for [MANUAL_HOLD_MS].
 */
@Composable
fun AutoZoomEffect(state: NavigationMapState, uiState: State<NavigationUiState>, base: Double) {
  LaunchedEffect(state, base) {
    var written: Double? = null
    var heldUntil = 0L
    var lastFrame = 0L
    while (true) {
      val following = state.cameraMode == NavigationCameraMode.FOLLOW_USER_WITH_BEARING
      if (!following) {
        written = null
        lastFrame = 0L
        delay(300)
        continue
      }
      val now = withFrameMillis { it }
      val position = state.cameraState.position
      val w = written
      // Moved by someone else: the driver zoomed, so hold off for a while.
      if (w != null && abs(position.zoom - w) > 0.15) heldUntil = now + MANUAL_HOLD_MS
      if (now < heldUntil) {
        written = position.zoom
        lastFrame = now
        delay(250)
        continue
      }
      val ui = uiState.value
      val target = autoZoom(base, ui.location?.speed?.value, ui.progress?.distanceToNextManeuver?.takeIf { ui.isNavigating() })
      val dt = if (lastFrame == 0L) 16L else (now - lastFrame).coerceIn(1L, 250L)
      lastFrame = now
      val gap = target - position.zoom
      if (abs(gap) < 0.01) {
        written = position.zoom
        delay(250)
        lastFrame = 0L
        continue
      }
      // Ease over about 1.6 s, never faster than 0.6 zoom levels a second.
      val step = (gap * (1 - exp(-dt / 1600.0))).coerceIn(-0.6 * dt / 1000.0, 0.6 * dt / 1000.0)
      val next = position.zoom + step
      state.cameraState.position = position.copy(zoom = next)
      written = next
    }
  }
}

private const val MANUAL_HOLD_MS = 15_000L
