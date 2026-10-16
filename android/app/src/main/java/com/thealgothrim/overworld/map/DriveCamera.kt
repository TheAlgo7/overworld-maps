package com.thealgothrim.overworld.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.thealgothrim.overworld.theme.Skin
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log2
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
  // 20 km/h and under: base + 0.4. 110 km/h and over: base - 0.8. The games' minimaps stay close
  // in; pulled back further, the tilted 3D map turned into a sea of rooftops.
  var zoom = base + 0.4 - ((kmh - 20.0).coerceIn(0.0, 90.0) / 90.0) * 1.2
  if (toTurnMetres != null) {
    // A long way to the next turn on a fast road: a little further out still.
    if (toTurnMetres > 2_000 && kmh > 50) zoom -= 0.3
    // The turn coming up from 1.5 km: out far enough to show it, as Google Maps frames the next
    // turn. Overworld used to notice it only 400 m out (Android navigation study, section 1).
    if (toTurnMetres in 400.0..1_500.0) {
      val framed = (base + log2(FRAME_METRES / toTurnMetres)).coerceAtLeast(base - MAX_FRAME_OUT)
      zoom = minOf(zoom, framed)
    }
    // From 400 m out, come in to base + 0.8 by the last 80 m.
    val near = 1.0 - ((toTurnMetres - 80.0) / 320.0).coerceIn(0.0, 1.0)
    zoom += (base + 0.8 - zoom).coerceAtLeast(0.0) * near
  }
  return zoom
}

/**
 * Eases the following camera's zoom towards [autoZoom] while it follows heading-up. Ferrostar keeps
 * whatever zoom the camera has, so without this it never changed on its own. A zoom the driver set
 * (pinch or the + / - buttons) is left alone for [MANUAL_HOLD_MS], reported through [held]; a new
 * value in [release] (re-centre) hands the zoom straight back.
 */
@Composable
fun AutoZoomEffect(
    state: NavigationMapState,
    uiState: State<NavigationUiState>,
    base: Double,
    held: MutableState<Boolean>? = null,
    release: State<Long>? = null,
    zoomedAt: State<Long>? = null,
) {
  LaunchedEffect(state, base) {
    var written: Double? = null
    var heldUntil = 0L
    var lastFrame = 0L
    var released = release?.value
    var zoomed = zoomedAt?.value
    fun report(on: Boolean) {
      if (held != null && held.value != on) held.value = on
    }
    while (true) {
      if (release != null && release.value != released) {
        released = release.value
        heldUntil = 0L
        written = null
      }
      // The + / - buttons say so outright: their zoom is animated, a little each frame, and could
      // pass for this loop's own easing.
      if (zoomedAt != null && zoomedAt.value != zoomed) {
        zoomed = zoomedAt.value
        heldUntil = System.currentTimeMillis() + MANUAL_HOLD_MS
      }
      val following = state.cameraMode == NavigationCameraMode.FOLLOW_USER_WITH_BEARING
      if (!following) {
        written = null
        lastFrame = 0L
        report(false)
        delay(300)
        continue
      }
      withFrameMillis {}
      val now = System.currentTimeMillis()
      val position = state.cameraState.position
      val w = written
      // Moved by someone else: the driver pinched, so hold off for a while.
      if (w != null && abs(position.zoom - w) > 0.15) heldUntil = now + MANUAL_HOLD_MS
      report(now < heldUntil)
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

/** A turn this far ahead fits at the base zoom; further away, the camera pulls back to keep it in view. */
private const val FRAME_METRES = 600.0
/** No further out than this to frame a turn: GTA VI pulled further back turned into a sea of rooftops. */
private const val MAX_FRAME_OUT = 0.8

/**
 * The game's tilt, looking more steeply down over the last [FLATTEN_METRES] before a turn so the
 * junction reads clearly, like Google Maps (about 60° cruising, 20° there). Back to the game's own
 * tilt once the turn is behind. Red Dead's map is flat already.
 */
fun turnTilt(camera: DriveCamera, toTurnMetres: Double?): Double =
    if (toTurnMetres != null && toTurnMetres < FLATTEN_METRES) camera.tilt * NEAR_TURN_TILT else camera.tilt

/** [turnTilt] for the following camera, eased over 2 s; changes once per turn, so the screen only redraws then. */
@Composable
fun rememberDriveTilt(camera: DriveCamera, uiState: State<NavigationUiState>): Double {
  val toTurn by remember(uiState) {
    derivedStateOf {
      val ui = uiState.value
      ui.progress?.distanceToNextManeuver?.takeIf { ui.isNavigating() && it < FLATTEN_METRES }
    }
  }
  val near = toTurn != null
  val tilt by animateFloatAsState(turnTilt(camera, if (near) 0.0 else null).toFloat(), tween(durationMillis = 2_000), label = "tilt")
  return tilt.toDouble()
}

private const val FLATTEN_METRES = 550.0
private const val NEAR_TURN_TILT = 0.5
