package com.thealgothrim.overworld.car

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drags, flicks and pinches on the car map. Android Auto sends them as events, and Ferrostar moved
 * the camera once per event, zoomed every pinch round the middle of the screen whatever the
 * fingers were on, and turned a flick into a tenth of its speed: next to Google Maps the map
 * trailed the finger and stopped dead when it lifted (his 1 Nov video). Here the events are added
 * up and applied once a frame, a pinch keeps the ground under the fingers in place, and a flick
 * glides on and slows down like a phone's map. A pinch while the map follows the car only zooms,
 * so the car stays followed.
 */
class CarMapGestures(private val mapState: NavigationMapState, private val scope: CoroutineScope, private val density: () -> Float) {
  private var panX = 0f
  private var panY = 0f
  private var scale = 1.0
  private var focusX = -1f
  private var focusY = -1f
  private var frame: Job? = null
  private var glide: Job? = null

  /** Android Auto's onScroll: how far the camera should go, surface pixels (finger travel reversed). */
  fun scroll(distanceX: Float, distanceY: Float) {
    glide?.cancel()
    panX += distanceX
    panY += distanceY
    nextFrame()
  }

  /** Android Auto's onScale; a negative focus means the head unit has none (a rotary knob). */
  fun scale(focusX: Float, focusY: Float, factor: Float) {
    if (!(factor > 0f) || !factor.isFinite()) return
    glide?.cancel()
    scale *= factor
    this.focusX = focusX
    this.focusY = focusY
    nextFrame()
  }

  /** Android Auto's onFling: the finger's speed as it lifted, pixels a second. */
  fun fling(velocityX: Float, velocityY: Float) {
    glide?.cancel()
    if (hypot(velocityX, velocityY) < MIN_FLING) return
    glide =
        scope.launch {
          // Speed falls off exponentially, the way a phone map slows: in all it travels v x tau.
          val start = withFrameNanos { it }
          var done = 0.0
          while (true) {
            val t = (withFrameNanos { it } - start) / 1e9
            val reached = 1 - exp(-t / FLING_TAU)
            val step = reached - done
            done = reached
            panX -= (velocityX * FLING_TAU * step).toFloat()
            panY -= (velocityY * FLING_TAU * step).toFloat()
            apply()
            if (hypot(velocityX, velocityY) * exp(-t / FLING_TAU) < FLING_STOP) break
          }
        }
  }

  private fun nextFrame() {
    if (frame?.isActive == true) return
    frame =
        scope.launch {
          withFrameNanos {}
          apply()
        }
  }

  private fun apply() {
    val px = panX
    val py = panY
    val s = scale
    panX = 0f
    panY = 0f
    scale = 1.0
    val camera = mapState.cameraState
    val projection = camera.projection ?: return
    val position = camera.position
    val zoom = (position.zoom + ln(s) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val panned = px != 0f || py != 0f
    if (!panned && mapState.isTrackingUser) {
      // A pinch while following: zoom, and keep following (Google Maps does the same).
      if (abs(zoom - position.zoom) > 1e-6) camera.position = position.copy(zoom = zoom)
      return
    }
    // The zoom actually made, after the limits.
    val made = 2.0.pow(zoom - position.zoom)
    val d = density()
    val centre = projection.screenLocationFromPosition(position.target)
    var x = centre.x.value.toDouble()
    var y = centre.y.value.toDouble()
    if (made != 1.0 && focusX >= 0f && focusY >= 0f) {
      // The new centre is the screen point that keeps the ground under the fingers where it is.
      val k = 1 - 1 / made
      x += (focusX / d - x) * k
      y += (focusY / d - y) * k
    }
    // The drag was made at the new zoom.
    x += px / d / made
    y += py / d / made
    val target = projection.positionFromScreenLocation(DpOffset(x.toFloat().dp, y.toFloat().dp))
    if (mapState.cameraMode != NavigationCameraMode.FREE) mapState.cameraMode = NavigationCameraMode.FREE
    camera.position = position.copy(target = target, zoom = zoom)
  }

  companion object {
    /** The car map's gestures while its screen is up; Ferrostar's handle them otherwise. */
    @Volatile var active: CarMapGestures? = null

    private const val MIN_ZOOM = 3.0
    private const val MAX_ZOOM = 20.0
    /** Seconds; Android's own scrolling slows over about this long. */
    private const val FLING_TAU = 0.325
    private const val MIN_FLING = 250f
    private const val FLING_STOP = 40.0
  }
}
