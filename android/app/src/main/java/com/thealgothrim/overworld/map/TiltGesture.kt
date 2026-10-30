package com.thealgothrim.overworld.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import kotlin.math.abs
import kotlin.math.sign

/**
 * The phone map's two-finger tilt, done here instead of by MapLibre. MapLibre's waits out a long
 * stretch of the drag before it starts and then gives about half the tilt per finger movement of
 * Mapbox's (measured 30 Oct with the same injected 500 px drag on the phone: about 25° here, 45°
 * in a Mapbox app), which felt slow and laggy. This one starts after [START_DP] of two fingers
 * moving up or down together and gives [DEGREES_PER_DP]. Once it has started, the map's own
 * gesture is cancelled (the events are consumed before the map sees them), so pan and pinch don't
 * fight it; anything else (a pinch, a rotate) is left to the map untouched.
 */
fun Modifier.tiltGesture(mapState: NavigationMapState): Modifier =
    pointerInput(mapState) {
      val start = START_DP.dp.toPx()
      val sideBySide = SIDE_BY_SIDE_DP.dp.toPx()
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var origin: Map<PointerId, Offset>? = null
        var tilting = false
        var given = false
        var startY = 0f
        var startTilt = 0.0
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          val down = event.changes.filter { it.pressed }
          if (down.isEmpty()) break
          if (down.size != 2) {
            // A third finger, or one lifted: a tilt in progress keeps the gesture; nothing new starts.
            if (tilting) event.changes.forEach { it.consume() }
            origin = null
            continue
          }
          val (a, b) = down
          val from = origin?.takeIf { it.keys == setOf(a.id, b.id) } ?: mapOf(a.id to a.position, b.id to b.position).also { origin = it }
          if (!tilting && !given) {
            val da = a.position - from.getValue(a.id)
            val db = b.position - from.getValue(b.id)
            val vertical = abs(da.x) < abs(da.y) * 0.7f && abs(db.x) < abs(db.y) * 0.7f
            val together = sign(da.y) == sign(db.y)
            val apart = abs(a.position.y - b.position.y) < sideBySide
            if (abs(da.y) > start && abs(db.y) > start && vertical && together && apart) {
              tilting = true
              startY = (a.position.y + b.position.y) / 2
              startTilt = mapState.cameraState.position.tilt
              // Following forces its own tilt every fix: let go of the car, as a drag does.
              mapState.cameraMode = NavigationCameraMode.FREE
            } else if ((da - db).getDistance() > start * 2 || maxOf(da.getDistance(), db.getDistance()) > start * 4) {
              // Pinch, rotate or a two-finger pan: the map's.
              given = true
            }
          }
          if (tilting) {
            val dy = (a.position.y + b.position.y) / 2 - startY
            val tilt = (startTilt - dy / density * DEGREES_PER_DP).coerceIn(0.0, MAX_TILT)
            mapState.cameraState.position = mapState.cameraState.position.copy(tilt = tilt)
            event.changes.forEach { it.consume() }
          }
        }
      }
    }

/** Fingers moving up together this far start a tilt (MapLibre waited about three times as long). */
private const val START_DP = 8f
/** Fingers further apart vertically than this aren't side by side: a rotate or pinch, not a tilt. */
private const val SIDE_BY_SIDE_DP = 140f
/** About Mapbox's response: a 180 dp drag (500 px on Gaurav's S24 Ultra) tilts about 45°. */
private const val DEGREES_PER_DP = 0.25
/** MapLibre's own limit. */
private const val MAX_TILT = 60.0
