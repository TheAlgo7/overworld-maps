package com.thealgothrim.overworld.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.NavigationViewModel
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.NavigationMapView
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.screenSurfaceState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.surfaceStableFractionalPadding
import androidx.compose.ui.text.style.TextAlign
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.CarGameHud
import com.thealgothrim.overworld.ui.PaperOverlay
import com.thealgothrim.overworld.theme.StyleCache
import com.thealgothrim.overworld.theme.family
import com.thealgothrim.overworld.theme.style
import com.thealgothrim.overworld.theme.weight
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import uniffi.ferrostar.GeographicCoordinate

/** The phone map: themed style, route, arrow and waypoint over Ferrostar's navigation camera. */
@Composable
fun OverworldPhoneMap(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    mapState: NavigationMapState,
    cameraOptions: NavigationCameraOptions,
    pickedDestination: GeographicCoordinate?,
    attributionPadding: PaddingValues,
    onLongPress: (GeographicCoordinate) -> Unit,
    attributionAlignment: Alignment = Alignment.TopEnd,
    previewRoute: List<GeographicCoordinate>? = null,
) {
  val context = LocalContext.current
  val baseStyle = remember(theme.id) { BaseStyle.Json(StyleCache.json(context, theme, car = false)) }
  val route = remember(theme.id) { themedRouteOverlay(theme, car = false) }
  NavigationMapView(
      baseStyle = baseStyle,
      navigationMapState = mapState,
      uiState = uiState,
      mapOptions =
          MapOptions(
              ornamentOptions =
                  OrnamentOptions(
                      padding = attributionPadding,
                      isLogoEnabled = false,
                      isAttributionEnabled = true,
                      attributionAlignment = attributionAlignment,
                      isCompassEnabled = false,
                      isScaleBarEnabled = false,
                  )
          ),
      routeOverlayBuilder = route,
      navigationCameraOptions = cameraOptions,
      showDefaultPuck = false,
      onMapLongClick = { coordinate, _ ->
        if (uiState.isNavigating()) NavigationMapClickResult.Pass
        else {
          onLongPress(coordinate)
          NavigationMapClickResult.Consume
        }
      },
  ) { state ->
    // Directions preview: the route before driving, drawn the same way as the live route.
    if (!state.isNavigating() && previewRoute != null && previewRoute.size >= 2) {
      ThemedRouteLine(previewRoute, theme, car = false)
    }
    val end = state.routeGeometry?.lastOrNull() ?: previewRoute?.lastOrNull() ?: pickedDestination
    if (end != null) ThemedDestination(end, theme)
    ThemedPuck(state, theme, car = false)
  }
}

/**
 * The Android Auto map, drawn onto the car's surface. Same pieces as the phone but the calmer car
 * style, and everything kept inside the stable area Android Auto leaves free of its own cards.
 */
@Composable
fun OverworldCarMap(
    theme: OverworldTheme,
    viewModel: NavigationViewModel,
    mapState: NavigationMapState,
    cameraOptions: NavigationCameraOptions,
    surfaceAreaTracker: SurfaceAreaTracker,
) {
  val context = LocalContext.current
  val uiState by viewModel.navigationUiState.collectAsState()
  surfaceAreaTracker.rememberGestureDelegate(mapState)
  val surfaceArea by screenSurfaceState(surfaceAreaTracker)
  // The HUD sits in the area Android Auto leaves uncovered, like Google Maps' cards. The top edge
  // is pinned: Android Auto's buttons only use the right side, and following the visible area's
  // top made the cards jump every time those buttons appeared.
  val density = LocalDensity.current
  val surfaceSize = LocalWindowInfo.current.containerSize
  val stable =
      surfaceArea?.visibleArea?.let { area ->
        with(density) {
          PaddingValues(
              start = area.left.toDp(),
              top = 0.dp,
              end = (surfaceSize.width - area.right).coerceAtLeast(0).toDp(),
              bottom = (surfaceSize.height - area.bottom).coerceAtLeast(0).toDp(),
          )
        }
      } ?: surfaceStableFractionalPadding(surfaceArea?.compositeArea)
  val baseStyle = remember(theme.id) { BaseStyle.Json(StyleCache.json(context, theme, car = true)) }
  val route = remember(theme.id) { themedRouteOverlay(theme, car = true) }

  Box(Modifier.fillMaxSize()) {
    NavigationMapView(
        baseStyle = baseStyle,
        navigationMapState = mapState,
        uiState = uiState,
        mapOptions = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
        navigationCameraOptions = cameraOptions,
        routeOverlayBuilder = route,
        showDefaultPuck = false,
    ) { state ->
      state.routeGeometry?.lastOrNull()?.let { ThemedDestination(it, theme) }
      ThemedPuck(state, theme, car = true)
    }

    // Same paper as the phone, a little lighter so the roads stay crisp at a glance.
    if (theme.paperOverlay) PaperOverlay(strength = 0.75f)

    Box(Modifier.fillMaxSize().padding(stable).padding(12.dp)) {
      val gameHud by AppModule.themeStore.carGameHud.collectAsState()
      val area by AppModule.viewModel.area.collectAsState()
      if (gameHud) {
        CarGameHud(theme, uiState, area)
      } else {
        uiState.currentStepRoadName
            ?.takeIf { it.isNotBlank() && uiState.isNavigating() }
            ?.let { road ->
              Text(
                  text = theme.display(listOfNotNull(road, area).joinToString(", ")),
                  color = theme.hudFg,
                  style =
                      TextStyle(
                          fontFamily = theme.font.family,
                          fontWeight = theme.font.weight,
                          fontStyle = theme.font.style,
                          fontSize = 20.sp,
                      ),
                  modifier =
                      Modifier.align(Alignment.BottomCenter)
                          // Sits above the OpenStreetMap credit line in the corner.
                          .padding(bottom = 18.dp)
                          .background(theme.hudBg, RoundedCornerShape(12.dp))
                          .padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }
      }
      // OpenStreetMap attribution stays readable on the car screen too.
      Text(
          text = "© OpenStreetMap contributors",
          color = if (theme.dark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
          fontSize = 10.sp,
          modifier = Modifier.align(Alignment.BottomEnd),
      )
    }
  }
}
