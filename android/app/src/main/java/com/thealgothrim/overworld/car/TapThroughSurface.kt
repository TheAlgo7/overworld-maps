package com.thealgothrim.overworld.car

import android.graphics.Rect
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
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) = inner.onSurfaceAvailable(surfaceContainer)
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
