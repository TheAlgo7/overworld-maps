package com.thealgothrim.overworld.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.thealgothrim.overworld.AppModule
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

/**
 * Ferrostar's map state opens on the whole world and flies in once GPS answers. This one opens
 * where the phone was last seen, like Google Maps does.
 */
@Composable
fun rememberOverworldMapState(cameraOptions: NavigationCameraOptions): NavigationMapState {
  val state = rememberNavigationMapState(navigationCameraOptions = cameraOptions)
  remember(state.cameraState) {
    val start = AppModule.viewModel.startPoint
    state.cameraState.position = CameraPosition(target = Position(start.lng, start.lat), zoom = cameraOptions.browsingZoom)
  }
  return state
}
