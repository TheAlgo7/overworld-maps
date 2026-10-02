package com.thealgothrim.overworld.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.thealgothrim.overworld.AppModule
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

/**
 * Ferrostar's map state opens on the whole world and flies in once GPS answers. This one opens
 * where the phone was last seen, like Google Maps does. [initialCameraMode]: how it follows the
 * user before any trip (the car follows heading-up, the phone north-up).
 */
@Composable
fun rememberOverworldMapState(
    cameraOptions: NavigationCameraOptions,
    initialCameraMode: NavigationCameraMode = NavigationCameraMode.FOLLOW_USER,
): NavigationMapState {
  val state = rememberNavigationMapState(initialCameraMode = initialCameraMode, navigationCameraOptions = cameraOptions)
  remember(state.cameraState) {
    val start = AppModule.viewModel.startPoint
    state.cameraState.position = CameraPosition(target = Position(start.lng, start.lat), zoom = cameraOptions.browsingZoom)
  }
  return state
}
