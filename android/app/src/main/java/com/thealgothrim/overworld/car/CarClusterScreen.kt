package com.thealgothrim.overworld.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.rallista.car.app.compose.ComposableScreen
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.screenSurfaceState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.map.AutoZoomEffect
import com.thealgothrim.overworld.map.OverworldClusterMap
import com.thealgothrim.overworld.map.animatedPadding
import com.thealgothrim.overworld.map.driveCamera
import com.thealgothrim.overworld.map.rememberDriveTilt
import com.thealgothrim.overworld.map.rememberOverworldMapState

/**
 * Android Auto's session for the instrument cluster, the screen between the dials, in cars that
 * have one (the Curvv's "map view"). The host opens it beside the main session, usually once a trip
 * has started, and may open it first, alone, or never.
 */
class OverworldClusterSession : Session() {
  override fun onCreateScreen(intent: Intent): Screen {
    AppModule.init(carContext)
    AppModule.viewModel.refreshLocationPermission()
    return CarClusterScreen(carContext)
  }
}

/**
 * The cluster's map (see OverworldClusterMap). Nothing on a cluster can be touched, so it only
 * follows the car heading-up, on a trip or not, with the arrow centred across and three quarters
 * down, where GTA VI's minimap keeps it.
 */
class CarClusterScreen(carContext: CarContext) : ComposableScreen(carContext) {
  private val surfaceAreaTracker = SurfaceAreaTracker { surfaceGestureCallback = it }

  init {
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          // GPS runs while any map is up: the cluster can stay on with the main screen on Spotify.
          override fun onStart(owner: LifecycleOwner) = AppModule.viewModel.mapInView(true)

          override fun onStop(owner: LifecycleOwner) = AppModule.viewModel.mapInView(false)
        }
    )
  }

  @Composable
  override fun content() {
    val theme by AppModule.themeStore.theme.collectAsState()
    val surfaceArea by screenSurfaceState(surfaceAreaTracker)
    val driveCamera = theme.skin.driveCamera
    val zoom = CLUSTER_ZOOM + driveCamera.zoomOffset
    val navState = AppModule.viewModel.navigationUiState.collectAsState()
    val tilt = rememberDriveTilt(driveCamera, navState)
    // Inside the room the car leaves free of its own turn and time cards.
    val room = safeStablePadding(surfaceArea?.stableArea)
    val follow = animatedPadding(safeStablePadding(surfaceArea?.stableArea, top = 0.45f), "cluster-follow")
    val cameraOptions =
        navigationCameraOptions().copy(browsingPadding = follow, navigationPadding = follow, navigationZoom = zoom, navigationTilt = tilt)
    val state = rememberOverworldMapState(cameraOptions, initialCameraMode = NavigationCameraMode.FOLLOW_USER_WITH_BEARING)
    AutoZoomEffect(state, navState, base = zoom)
    val navigating = navState.value.isNavigating()
    LaunchedEffect(navigating, state) {
      if (navigating) {
        state.cameraState.position = state.cameraState.position.copy(zoom = zoom)
        state.recenter(isNavigating = true)
      } else {
        // Ferrostar drops to north-up when a trip ends; the cluster keeps following heading-up.
        state.cameraMode = NavigationCameraMode.FOLLOW_USER_WITH_BEARING
      }
    }
    OverworldClusterMap(theme, AppModule.viewModel, state, cameraOptions, room)
  }

  override fun onGetTemplate(): Template =
      NavigationTemplate.Builder()
          // The template needs a strip; a cluster shows only the map, so it is never drawn there.
          .setActionStrip(
              ActionStrip.Builder().addAction(Action.Builder().setTitle(carContext.getString(R.string.app_name)).setOnClickListener {}.build()).build()
          )
          .build()
}

/** The cluster's following zoom at about 40 km/h, before the theme's own offset: a little wider than the car screen's, for its smaller map. */
private const val CLUSTER_ZOOM = 16.1
