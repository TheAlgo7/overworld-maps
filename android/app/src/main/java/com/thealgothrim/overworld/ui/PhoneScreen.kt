package com.thealgothrim.overworld.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.core.boundingBox
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.OverworldViewModel
import com.thealgothrim.overworld.map.rememberOverworldMapState
import com.thealgothrim.overworld.map.OverworldPhoneMap
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.gta.GtaBigMessage
import com.thealgothrim.overworld.ui.rdr.RdrBigMessage
import com.thealgothrim.overworld.ui.skin.spec
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

/**
 * The phone app, laid out like Google Maps (search bar and settings on top, map buttons on the
 * right, sheets at the bottom, turn banner while driving) and dressed in the chosen game's HUD.
 */
@Composable
fun PhoneScreen(viewModel: OverworldViewModel = AppModule.viewModel) {
  val theme by AppModule.themeStore.theme.collectAsState()
  val spec = theme.spec
  val uiState by viewModel.navigationUiState.collectAsState()
  val planner by viewModel.planner.collectAsState()
  val saved by AppModule.saved.state.collectAsState()
  val testDrive by viewModel.testDrive.collectAsState()
  val area by viewModel.area.collectAsState()
  val cameraOptions = navigationCameraOptions()
  val mapState = rememberOverworldMapState(cameraOptions)
  val scope = rememberCoroutineScope()
  val navigating = uiState.isNavigating()

  var settingsOpen by rememberSaveable { mutableStateOf(false) }
  var layersOpen by rememberSaveable { mutableStateOf(false) }
  val extras by viewModel.extras.collectAsState()
  var searchOpen by remember { mutableStateOf(false) }
  var arrivedAt by remember { mutableStateOf<String?>(null) }
  val rotated by remember { derivedStateOf { abs(mapState.cameraState.position.bearing) > 1.0 } }

  // Back steps out one level at a time, like Google Maps. The handler registered last runs first,
  // so these go from the bottom level (the place card) up to the top (the Layers sheet).
  val overlay = layersOpen || settingsOpen
  BackHandler(!overlay && !searchOpen && planner.preview == null && planner.destination != null) {
    viewModel.clearDestination()
  }
  BackHandler(!overlay && !searchOpen && planner.preview != null) { viewModel.cancelPreview() }
  BackHandler(!overlay && searchOpen) {
    searchOpen = false
    viewModel.onQueryChange("")
  }
  BackHandler(settingsOpen && !layersOpen) { settingsOpen = false }
  BackHandler(layersOpen) { layersOpen = false }

  LaunchedEffect(navigating) { if (navigating) mapState.recenter(isNavigating = true) }
  LaunchedEffect(planner.destination) {
    val d = planner.destination ?: return@LaunchedEffect
    if (planner.preview != null) return@LaunchedEffect
    mapState.cameraMode = NavigationCameraMode.FREE
    mapState.cameraState.animateTo(
        CameraPosition(target = Position(d.coordinate.lng, d.coordinate.lat), zoom = 15.0),
        duration = 900.milliseconds,
    )
  }
  LaunchedEffect(planner.preview) {
    val bounds = planner.preview?.route?.geometry?.boundingBox() ?: return@LaunchedEffect
    mapState.showRouteOverview(
        boundingBox = bounds,
        paddingValues = PaddingValues(start = 56.dp, end = 56.dp, top = 170.dp, bottom = 300.dp),
    )
  }
  LaunchedEffect(Unit) {
    viewModel.arrived.collect {
      arrivedAt = it
      delay(5_000)
      arrivedAt = null
    }
  }

  val locate: () -> Unit = { mapState.recenter(isNavigating = navigating) }
  val northUp: () -> Unit = {
    scope.launch {
      mapState.cameraMode = NavigationCameraMode.FREE
      mapState.cameraState.animateTo(mapState.cameraState.position.copy(bearing = 0.0, tilt = 0.0), duration = 400.milliseconds)
    }
  }
  val toggleOverview: () -> Unit = {
    if (mapState.isTrackingUser) {
      uiState.routeGeometry?.boundingBox()?.let {
        mapState.showRouteOverview(boundingBox = it, paddingValues = PaddingValues(start = 56.dp, end = 56.dp, top = 220.dp, bottom = 260.dp))
      }
    } else mapState.recenter(isNavigating = true)
  }

  Box(Modifier.fillMaxSize().background(theme.page)) {
    OverworldPhoneMap(
        theme = theme,
        uiState = uiState,
        mapState = mapState,
        cameraOptions = cameraOptions,
        pickedDestination = planner.destination?.coordinate,
        previewRoute = planner.preview?.route?.geometry,
        onLongPress = {
          searchOpen = false
          viewModel.dropPin(it)
        },
    )
    if (theme.paperOverlay) PaperOverlay()

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      when {
        navigating ->
            NavigationLayer(
                spec = spec,
                uiState = uiState,
                extras = extras,
                remaining = viewModel.remainingSeconds(uiState, extras),
                hazard = viewModel.hazardAhead(uiState, extras),
                onLayers = { layersOpen = true },
                area = area,
                following = mapState.isTrackingUser,
                onRecenter = locate,
                onOverview = toggleOverview,
                onMute = viewModel::toggleMute,
                onEnd = viewModel::stopNavigation,
                onSettings = { settingsOpen = true },
            )
        planner.preview != null ->
            PreviewLayer(
                spec = spec,
                preview = planner.preview!!,
                extras = extras,
                testDrive = testDrive,
                onBack = viewModel::cancelPreview,
                onStart = viewModel::startPreview,
            )
        else ->
            BrowseLayer(
                spec = spec,
                planner = planner,
                saved = saved,
                here = uiState.location?.coordinates,
                searchOpen = searchOpen,
                onSearchOpen = { searchOpen = it },
                rotated = rotated,
                onQuery = viewModel::onQueryChange,
                onChoose = {
                  searchOpen = false
                  viewModel.choose(it)
                },
                onClear = viewModel::clearDestination,
                onDirections = viewModel::directions,
                onSettings = { settingsOpen = true },
                onLayers = { layersOpen = true },
                onLocate = locate,
                onNorthUp = northUp,
            )
      }
    }

    val arrivedVisible = arrivedAt != null
    if (theme.skin == Skin.GTA) {
      GtaBigMessage(arrivedVisible, "ARRIVED", arrivedAt.orEmpty(), Modifier.align(Alignment.Center))
    } else {
      RdrBigMessage(arrivedVisible, "ARRIVED", arrivedAt.orEmpty(), Modifier.align(Alignment.Center))
    }

    if (settingsOpen) {
      SettingsScreen(
          theme = theme,
          uiState = uiState,
          testDrive = testDrive,
          saved = saved,
          onTestDrive = viewModel::setTestDrive,
          onMute = viewModel::toggleMute,
          onLayers = { layersOpen = true },
          onClose = { settingsOpen = false },
      )
    }

    if (layersOpen) MapLayersSheet(theme, onClose = { layersOpen = false })
  }
}
