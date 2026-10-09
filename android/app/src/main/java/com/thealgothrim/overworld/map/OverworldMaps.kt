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
import androidx.compose.runtime.derivedStateOf
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
import com.thealgothrim.overworld.car.safeStablePadding
import androidx.compose.ui.text.style.TextAlign
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.RouteExtras
import com.thealgothrim.overworld.theme.MapDetails
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.traffic.RoadFeature
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import com.thealgothrim.overworld.traffic.TrafficSpan
import com.thealgothrim.overworld.ui.CarGameHud
import com.thealgothrim.overworld.ui.PaperOverlay
import com.thealgothrim.overworld.theme.StyleCache
import com.thealgothrim.overworld.theme.family
import com.thealgothrim.overworld.theme.style
import com.thealgothrim.overworld.theme.weight
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.TripState

/** The phone map: themed style, route, arrow and waypoint over Ferrostar's navigation camera. */
@Composable
fun OverworldPhoneMap(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    mapState: NavigationMapState,
    cameraOptions: NavigationCameraOptions,
    pickedDestination: GeographicCoordinate?,
    onLongPress: (GeographicCoordinate) -> Unit,
    previewRoute: List<GeographicCoordinate>? = null,
) {
  val context = LocalContext.current
  val details by AppModule.themeStore.details.collectAsState()
  val extras by AppModule.viewModel.extras.collectAsState()
  val trafficTiles = AppModule.traffic.flowTilesUrl?.takeIf { details.traffic }
  // GTA VI changes palette with the time of day (day, golden hour, night); the others keep a single palette.
  val variant by AppModule.viewModel.mapVariant.collectAsState()
  val flat = !details.buildings3d
  val baseStyle =
      remember(theme.id, trafficTiles, variant, flat) { BaseStyle.Json(StyleCache.json(context, theme, car = false, trafficTiles, variant, flat)) }
  NavigationMapView(
      baseStyle = baseStyle,
      navigationMapState = mapState,
      uiState = rememberSmoothedUiState(uiState),
      // No MapLibre ornaments: its (i) attribution button moved around with the camera padding.
      // The OpenStreetMap credit is drawn by the screen instead (MapCredit).
      mapOptions = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
      // The route is drawn by OverworldLayers, which trims it at the arrow.
      routeOverlayBuilder = null,
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
    OverworldLayers(state, theme, car = false, extras, details, previewRoute, pickedDestination, style = baseStyle)
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
              start = area.left.coerceAtLeast(0).toDp(),
              top = 0.dp,
              end = (surfaceSize.width - area.right).coerceAtLeast(0).toDp(),
              bottom = (surfaceSize.height - area.bottom).coerceAtLeast(0).toDp(),
          )
        }
      } ?: safeStablePadding(surfaceArea?.compositeArea)
  val details by AppModule.themeStore.details.collectAsState()
  val extras by AppModule.viewModel.extras.collectAsState()
  val trafficTiles = AppModule.traffic.flowTilesUrl?.takeIf { details.traffic }
  val planner by AppModule.viewModel.planner.collectAsState()

  Box(Modifier.fillMaxSize()) {
    CarMapView(theme, uiState, mapState, cameraOptions, trafficTiles, extras, details, planner.preview?.route?.geometry)

    // Same paper as the phone, a little lighter so the roads stay crisp at a glance.
    if (theme.paperOverlay) PaperOverlay(strength = 0.75f)

    Box(Modifier.fillMaxSize().padding(stable).padding(12.dp)) {
      val gameHud by AppModule.themeStore.carGameHud.collectAsState()
      val area by AppModule.viewModel.area.collectAsState()
      if (gameHud) {
        val vm = AppModule.viewModel
        CarGameHud(theme, uiState, area, extras, vm.remainingSeconds(uiState, extras), vm.hazardAhead(uiState, extras), planner.preview)
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
      val variant by AppModule.viewModel.mapVariant.collectAsState()
      Text(
          text = "© OpenStreetMap contributors",
          color = if (theme.isDark(variant)) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
          fontSize = 10.sp,
          modifier = Modifier.align(Alignment.BottomEnd),
      )
    }
  }
}

/**
 * The map itself, in its own function: the arrow glides every frame, and only this part should run
 * again for that, not the HUD and the paper drawn over it.
 */
@Composable
private fun CarMapView(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    mapState: NavigationMapState,
    cameraOptions: NavigationCameraOptions,
    trafficTiles: String?,
    extras: RouteExtras,
    details: MapDetails,
    previewRoute: List<GeographicCoordinate>?,
) {
  val context = LocalContext.current
  val variant by AppModule.viewModel.mapVariant.collectAsState()
  val flat = !details.buildings3d
  val baseStyle =
      remember(theme.id, trafficTiles, variant, flat) { BaseStyle.Json(StyleCache.json(context, theme, car = true, trafficTiles, variant, flat)) }
  NavigationMapView(
      baseStyle = baseStyle,
      navigationMapState = mapState,
      uiState = rememberSmoothedUiState(uiState),
      mapOptions = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
      navigationCameraOptions = cameraOptions,
      routeOverlayBuilder = null,
      showDefaultPuck = false,
  ) { state ->
    OverworldLayers(state, theme, car = true, extras, details, previewRoute, null, style = baseStyle)
  }
}

/**
 * Everything Overworld draws on the map, phone and car alike: the route (the preview, or on a
 * trip the road still ahead from the arrow), traffic on it, lights, cameras and incidents, the
 * waypoint and the arrow. On a trip this runs every frame.
 */
@Composable
@MaplibreComposable
private fun OverworldLayers(
    state: NavigationUiState,
    theme: OverworldTheme,
    car: Boolean,
    extras: RouteExtras,
    details: MapDetails,
    previewRoute: List<GeographicCoordinate>?,
    pin: GeographicCoordinate?,
    /** The map style in use; the arrow and the near route resend their data when it changes. */
    style: Any,
) {
  val shown = rememberDisplayedPosition(state)
  val trip = state.routeGeometry?.takeIf { state.isNavigating() && it.size >= 2 }
  val route = shown?.route
  if (trip != null && route != null) {
    ThemedRouteAhead(route, shown, theme, car, style)
    TripTrafficLine(trip, extras.trafficSpans, shown, theme, car)
  } else if (previewRoute != null && previewRoute.size >= 2) {
    ThemedRouteLine(previewRoute, theme, car)
    RouteTrafficLine(previewRoute, extras.trafficSpans, theme, car)
  }
  RoadFeatureLayers(visibleFeatures(extras, details), theme, car)
  val end = trip?.lastOrNull() ?: previewRoute?.lastOrNull() ?: pin
  end?.let { ThemedDestination(it, theme, car) }
  // Stops added on the way (Nearby during a trip) get the waypoint marker too.
  (state.tripState as? TripState.Navigating)?.remainingWaypoints?.dropLast(1)?.forEachIndexed { i, stop ->
    ThemedDestination(stop.coordinate, theme, car, id = "ow-stop-$i")
  }
  shown?.let { ThemedPuck(it, theme, car, style) }
}

/**
 * Traffic on the trip's route, trimmed behind the arrow every 25 m (it composes only then). Where
 * the route fades in ahead of the arrow (GTA VI), the traffic colours start after the fade.
 */
@Composable
@MaplibreComposable
private fun TripTrafficLine(trip: List<GeographicCoordinate>, spans: List<TrafficSpan>, shown: DisplayedPosition, theme: OverworldTheme, car: Boolean) {
  val from by remember(shown, theme.routeFade) { derivedStateOf { (((shown.along ?: 0.0) + theme.routeFade) / 25).toInt() * 25.0 } }
  RouteTrafficLine(trip, spans, theme, car, from = from, id = "ow-trip-traffic")
}

/** Road features the map should show, per the Layers switches, with the cameras marked by hand. */
@Composable
fun visibleFeatures(extras: RouteExtras, details: MapDetails): List<RoadFeature> {
  val marked by AppModule.cameras.all.collectAsState()
  return remember(extras, details, marked) {
    buildList {
      if (details.signals) {
        addAll(extras.signals)
        marked.forEach { add(RoadFeature(RoadFeatureKind.SPEED_CAMERA, it.at)) }
      }
      if (details.incidents) addAll(extras.markedIncidents)
    }
  }
}
