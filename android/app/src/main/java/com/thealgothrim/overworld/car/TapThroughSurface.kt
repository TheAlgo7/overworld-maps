package com.thealgothrim.overworld.car

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rallista.car.app.compose.ComposableScreen

/**
 * car-app-compose draws the map but drops taps on it. This passes everything to its surface
 * callback and hands taps (surface pixels) to [onTap], so buttons drawn on the map can be pressed.
 * It re-installs itself each time the screen starts, right after car-app-compose installs its own.
 */
fun ComposableScreen.passTapsTo(onTap: (x: Float, y: Float) -> Unit) {
  val inner =
      ComposableScreen::class.java.getDeclaredField("surfaceCallback").let {
        it.isAccessible = true
        it.get(this) as SurfaceCallback
      }
  val wrapper =
      object : SurfaceCallback {
        // car-app-compose builds a virtual display on the surface. Android throws for one with no
        // size or density, and a throw here closes the app in the car: skip such a surface (the map
        // stays blank until the head unit sends a usable one) rather than crash.
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
          val c = surfaceContainer
          if (c.surface == null || c.width <= 0 || c.height <= 0 || c.dpi <= 0) {
            Log.w(TAG, "Skipping unusable car surface ${c.width}x${c.height} @${c.dpi}dpi")
            return
          }
          try {
            inner.onSurfaceAvailable(c)
          } catch (e: RuntimeException) {
            Log.w(TAG, "Could not draw on the car surface", e)
          }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = inner.onSurfaceDestroyed(surfaceContainer)
        override fun onVisibleAreaChanged(visibleArea: Rect) = inner.onVisibleAreaChanged(visibleArea)
        override fun onStableAreaChanged(stableArea: Rect) = inner.onStableAreaChanged(stableArea)
        override fun onScroll(distanceX: Float, distanceY: Float) = inner.onScroll(distanceX, distanceY)
        override fun onFling(velocityX: Float, velocityY: Float) = inner.onFling(velocityX, velocityY)
        override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) = inner.onScale(focusX, focusY, scaleFactor)
        override fun onClick(x: Float, y: Float) = onTap(x, y)
      }
  lifecycle.addObserver(
      object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
          carContext.getCarService(AppManager::class.java).setSurfaceCallback(wrapper)
        }
      }
  )
}

private const val TAG = "CarSurface"
