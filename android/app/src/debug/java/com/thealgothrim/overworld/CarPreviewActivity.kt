package com.thealgothrim.overworld

import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.thealgothrim.overworld.car.safeStablePadding
import com.thealgothrim.overworld.map.OverworldCarMap
import com.thealgothrim.overworld.map.rememberOverworldMapState

/**
 * Debug builds only: the Android Auto screen's map and game HUD in an ordinary activity, to check
 * a theme on the emulator without the phone and a head unit. Android Auto's own buttons and strips
 * aren't drawn. Set the emulator to the Curvv's screen first (1920x720 at 200 dpi):
 *
 *   adb shell wm size 1920x720 && adb shell wm density 200
 *   adb shell am start -n com.thealgothrim.overworld.debug/com.thealgothrim.overworld.CarPreviewActivity
 *
 * and back with `adb shell wm size reset && adb shell wm density reset`.
 */
class CarPreviewActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppModule.init(this)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    setContent {
      val theme by AppModule.themeStore.theme.collectAsState()
      val viewModel = AppModule.viewModel
      val uiState by viewModel.navigationUiState.collectAsState()
      // The whole window stands in for the area Android Auto leaves free, as on the car screen.
      val surface = LocalWindowInfo.current.containerSize
      val area = Rect(0, 0, surface.width, surface.height)
      val cameraOptions =
          navigationCameraOptions()
              .copy(browsingPadding = safeStablePadding(area), navigationPadding = safeStablePadding(area, top = 0.45f), navigationZoom = 16.4)
      val state = rememberOverworldMapState(cameraOptions, initialCameraMode = NavigationCameraMode.FOLLOW_USER_WITH_BEARING)
      val tracker = remember { SurfaceAreaTracker {} }
      val navigating = uiState.isNavigating()
      LaunchedEffect(navigating) {
        if (navigating) {
          state.cameraState.position = state.cameraState.position.copy(zoom = cameraOptions.navigationZoom)
          state.recenter(isNavigating = true)
        } else {
          state.cameraMode = NavigationCameraMode.FOLLOW_USER_WITH_BEARING
        }
      }
      OverworldCarMap(theme, viewModel, state, cameraOptions, tracker)
    }
  }

  override fun onStart() {
    super.onStart()
    AppModule.viewModel.mapInView(true)
  }

  override fun onStop() {
    AppModule.viewModel.mapInView(false)
    super.onStop()
  }
}
