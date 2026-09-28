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
import com.thealgothrim.overworld.theme.OverworldTheme
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
                      attributionAlignment = Alignment.TopEnd,
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
    val end = state.routeGeometry?.lastOrNull() ?: pickedDestination
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
  val stable = surfaceStableFractionalPadding(surfaceArea?.compositeArea)
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

    Box(Modifier.fillMaxSize().padding(stable).padding(8.dp)) {
      uiState.currentStepRoadName
          ?.takeIf { it.isNotBlank() && uiState.isNavigating() }
          ?.let { road ->
            Text(
                text = theme.display(road),
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
                        .background(theme.hudBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
            )
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
