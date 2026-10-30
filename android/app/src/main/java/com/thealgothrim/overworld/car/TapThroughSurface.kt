package com.thealgothrim.overworld.car

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rallista.car.app.compose.ComposableScreen
import com.thealgothrim.overworld.ui.CarTapTargets

/**
 * car-app-compose draws the map but drops taps on it. This passes everything to its surface
 * callback and hands taps (surface pixels) to [onTap], so buttons drawn on the map can be pressed.
 * It re-installs itself each time the screen starts, right after car-app-compose installs its own.
 */
fun ComposableScreen.passTapsTo(onTap: (x: Float, y: Float, shift: Float) -> Unit) {
  val inner =
      ComposableScreen::class.java.getDeclaredField("surfaceCallback").let {
        it.isAccessible = true
        it.get(this) as SurfaceCallback
      }
  val wrapper =
      object : SurfaceCallback {
        var width = 0
        var visible: Rect? = null
        var stable: Rect? = null

        // car-app-compose builds a virtual display on the surface. Android throws for one with no
        // size or density, and a throw here closes the app in the car: skip such a surface (the map
        // stays blank until the head unit sends a usable one) rather than crash.
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
          val c = surfaceContainer
          if (c.surface == null || c.width <= 0 || c.height <= 0 || c.dpi <= 0) {
            Log.w(TAG, "Skipping unusable car surface ${c.width}x${c.height} @${c.dpi}dpi")
            return
          }
          width = c.width
          try {
            inner.onSurfaceAvailable(c)
          } catch (e: RuntimeException) {
            Log.w(TAG, "Could not draw on the car surface", e)
          }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = inner.onSurfaceDestroyed(surfaceContainer)

        override fun onVisibleAreaChanged(visibleArea: Rect) {
          visible = visibleArea
          noteHostButtons()
          inner.onVisibleAreaChanged(visibleArea)
        }

        override fun onStableAreaChanged(stableArea: Rect) {
          stable = stableArea
          noteHostButtons()
          inner.onStableAreaChanged(stableArea)
        }

        // Android Auto shrinks the visible area to the stable one while its buttons are up (at the
        // touch, before the tap itself arrives) and gives the room back when they go, 10 s later.
        // That shrink is the touch itself, about half a second before Android Auto hands over the
        // tap (it waits out a double-tap): our buttons come up with it, not after.
        fun noteHostButtons() {
          val v = visible ?: return
          val s = stable ?: return
          val up = !(v.contains(s) && v != s)
          if (up && CarTapTargets.hostButtonsUp.value == false) CarTapTargets.touchedAt.longValue = System.currentTimeMillis()
          CarTapTargets.hostButtonsUp.value = up
        }
        override fun onScroll(distanceX: Float, distanceY: Float) {
          // A drag counts as a touch: the map buttons come up for it too.
          CarTapTargets.touchedAt.longValue = System.currentTimeMillis()
          inner.onScroll(distanceX, distanceY)
        }
        override fun onFling(velocityX: Float, velocityY: Float) = inner.onFling(velocityX, velocityY)
        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
          // A pinch: the driver's own zoom, held like the + / - buttons' (see AutoZoomEffect).
          CarTapTargets.zoomedAt.longValue = System.currentTimeMillis()
          CarTapTargets.touchedAt.longValue = CarTapTargets.zoomedAt.longValue
          inner.onScale(focusX + windowShift(width, visible), focusY, scaleFactor)
        }

        override fun onClick(x: Float, y: Float) = onTap(x, y, windowShift(width, visible))
      }
  lifecycle.addObserver(
      object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
          carContext.getCarService(AppManager::class.java).setSurfaceCallback(wrapper)
        }
      }
  )
}

/**
 * How far right of the surface's edge Android Auto's map window starts. In split screen the app
 * still draws on a full-width surface, with the media card laid over one side, but taps arrive
 * measured from the map's own window: with the map on the right, as in right-hand-drive cars,
 * every tap landed about 630 px left of the button under the finger. The window is the visible
 * area with an even margin on both sides, so a wider gap on the left is the window's offset. Full
 * screen, and a map on the left, give no shift.
 */
internal fun windowShift(width: Int, visible: Rect?): Float {
  if (visible == null || width <= 0) return 0f
  val gap = visible.left - (width - visible.right)
  return if (gap > 0) gap.toFloat() else 0f
}

private const val TAG = "CarSurface"
