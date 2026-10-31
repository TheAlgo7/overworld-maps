package com.thealgothrim.overworld.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.NavigationViewModel
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.NavigationMapView
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraOptions
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.screenSurfaceState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceArea
import androidx.compose.ui.text.style.TextAlign
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.RouteExtras
import com.thealgothrim.overworld.theme.MapDetails
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.traffic.RoadFeature
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import com.thealgothrim.overworld.ui.CarGameHud
import com.thealgothrim.overworld.ui.PaperOverlay
import com.thealgothrim.overworld.ui.skin.LocalIconSkin
import com.thealgothrim.overworld.ui.vi.ViEdgeMarkers
import com.thealgothrim.overworld.ui.vi.ViMapLight
import com.thealgothrim.overworld.theme.Skin
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import com.thealgothrim.overworld.search.Place
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.compose.camera.CameraProjection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.compose.map.GestureOptions

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
    /** A shop, temple, petrol pump... tapped on the map: picked like a search result. */
    onPlaceTap: (Place) -> Unit = {},
) {
  val context = LocalContext.current
  val details by AppModule.themeStore.details.collectAsState()
  val extras by AppModule.viewModel.extras.collectAsState()
  val trafficTiles = AppModule.traffic.flowTilesUrl?.takeIf { details.traffic }
  // GTA VI changes palette with the time of day (day, golden hour, night); the others keep a single palette.
  val variant by AppModule.viewModel.mapVariant.collectAsState()
  val flat = !details.buildings3d
  val places = details.placeIcons
  val baseStyle =
      remember(theme.id, trafficTiles, variant, flat, places) { BaseStyle.Json(StyleCache.json(context, theme, car = false, trafficTiles, variant, flat, places)) }
  Box(Modifier.fillMaxSize().tiltGesture(mapState)) {
    NavigationMapView(
        baseStyle = baseStyle,
        navigationMapState = mapState,
        uiState = rememberSmoothedUiState(uiState),
        // No MapLibre ornaments: its (i) attribution button moved around with the camera padding.
        // The OpenStreetMap credit is drawn by the screen instead (MapCredit).
        // Tilt is ours (tiltGesture): MapLibre's started late and moved half as far.
        mapOptions = MapOptions(gestureOptions = PHONE_GESTURES, ornamentOptions = OrnamentOptions.AllDisabled),
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
        onMapClick = { _, at ->
          val place = if (uiState.isNavigating()) null else mapState.cameraState.projection?.let { placeAt(it, at) }
          if (place == null) NavigationMapClickResult.Pass
          else {
            onPlaceTap(place)
            NavigationMapClickResult.Consume
          }
        },
    ) { state ->
      OverworldLayers(state, theme, car = false, extras, details, previewRoute, pickedDestination, style = baseStyle, headingUp = mapState.headingUp, glide = mapState.glide)
    }
  }
  RecenterGlideEffect(mapState)
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
  val density = LocalDensity.current
  val roomTarget = carHudInsets(surfaceArea)
  val stable = animatedPadding(roomTarget, "hud-room")
  val details by AppModule.themeStore.details.collectAsState()
  val extras by AppModule.viewModel.extras.collectAsState()
  val trafficTiles = AppModule.traffic.flowTilesUrl?.takeIf { details.traffic }
  val planner by AppModule.viewModel.planner.collectAsState()

  Box(Modifier.fillMaxSize()) {
    CarMapView(theme, uiState, mapState, cameraOptions, trafficTiles, extras, details, planner.preview?.route?.geometry)

    // Same paper as the phone, a little lighter so the roads stay crisp at a glance.
    if (theme.paperOverlay) PaperOverlay(strength = 0.75f)
    if (theme.skin == Skin.GTA6) {
      val variant by AppModule.viewModel.mapVariant.collectAsState()
      ViMapLight(variant)
      // The minimap's "N" and, when the destination is off the map, its pink dot on the edge.
      val destination = (uiState.routeGeometry?.takeIf { uiState.isNavigating() } ?: planner.preview?.route?.geometry)?.lastOrNull()
      ViEdgeMarkers(mapState.cameraState, destination, stable)
    }

    // The HUD was drawn for the Curvv's wide screen. Smaller head units, and the map's half of a
    // split screen (Spotify beside it), get the same HUD scaled down to fit instead of overlapping.
    val hudScale = animatedScale(carHudScale(roomTarget), "hud-scale")
    // The room Android Auto leaves (stable) at the screen's real density, the HUD scaled inside it.
    // Applied inside the scaling, the room shrank with the HUD: in the split screen, where the map
    // starts a media card's width in, the cards slid under the media card and were cut off.
    Box(Modifier.fillMaxSize().padding(stable)) {
    CompositionLocalProvider(LocalDensity provides Density(density.density * hudScale, density.fontScale), LocalIconSkin provides theme.skin) {
    Box(Modifier.fillMaxSize().padding(12.dp)) {
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
  }
}

/**
 * The map in the car's instrument cluster, between the dials (Android Auto's cluster display, in
 * cars that have one, like the Curvv): the car screen's themed map, route and arrow, and no cards,
 * since the cluster prints the next turn and the time itself from the trip Android Auto is sent.
 * Android's rule for cluster displays is the map and the route only; GTA VI keeps its minimap's
 * "N" and waypoint dot on the edge, which are part of its map.
 */
@Composable
fun OverworldClusterMap(
    theme: OverworldTheme,
    viewModel: NavigationViewModel,
    mapState: NavigationMapState,
    cameraOptions: NavigationCameraOptions,
    /** The part of the cluster's map the car leaves clear of its own turn and time cards. */
    room: PaddingValues,
) {
  val uiState by viewModel.navigationUiState.collectAsState()
  val details by AppModule.themeStore.details.collectAsState()
  val extras by AppModule.viewModel.extras.collectAsState()
  val trafficTiles = AppModule.traffic.flowTilesUrl?.takeIf { details.traffic }
  val planner by AppModule.viewModel.planner.collectAsState()
  Box(Modifier.fillMaxSize()) {
    CarMapView(theme, uiState, mapState, cameraOptions, trafficTiles, extras, details, planner.preview?.route?.geometry)
    if (theme.paperOverlay) PaperOverlay(strength = 0.75f)
    if (theme.skin == Skin.GTA6) {
      val variant by AppModule.viewModel.mapVariant.collectAsState()
      ViMapLight(variant)
      val destination = (uiState.routeGeometry?.takeIf { uiState.isNavigating() } ?: planner.preview?.route?.geometry)?.lastOrNull()
      ViEdgeMarkers(mapState.cameraState, destination, room)
    }
  }
}

/**
 * The car HUD's room: inside Android Auto's stable area, the part it keeps clear even while its
 * buttons show (Android's guidance for persistent content). It used the visible area, which changes
 * as those buttons come and go, so the HUD could resize at a touch. The top edge is pinned:
 * Android Auto's buttons only use the right side, and following the top made the cards jump.
 */
@Composable
fun carHudInsets(surfaceArea: SurfaceArea?): PaddingValues {
  val area = surfaceArea?.stableArea ?: return PaddingValues(0.dp)
  val surfaceSize = LocalWindowInfo.current.containerSize
  // In split screen the map is a smaller window with round corners, and the HUD's own margin
  // shrinks with the HUD: "Where to?" sat right in the corner and had its edge cut off in the
  // Curvv. A margin that doesn't shrink keeps the cards clear of the corner.
  val split = surfaceSize.width > 0 && area.width() < surfaceSize.width * 0.75f
  val corner = if (split) 16.dp else 0.dp
  return with(LocalDensity.current) {
    PaddingValues(
        start = area.left.coerceAtLeast(0).toDp() + corner,
        top = corner,
        end = (surfaceSize.width - area.right).coerceAtLeast(0).toDp(),
        bottom = (surfaceSize.height - area.bottom).coerceAtLeast(0).toDp(),
    )
  }
}

/** [hudScale] for the room [insets] leave on this car screen. */
@Composable
fun carHudScale(insets: PaddingValues): Float {
  val surfaceSize = LocalWindowInfo.current.containerSize
  return with(LocalDensity.current) {
    val width = (surfaceSize.width.toDp() - insets.calculateLeftPadding(LayoutDirection.Ltr) - insets.calculateRightPadding(LayoutDirection.Ltr)).value
    val height = (surfaceSize.height.toDp() - insets.calculateTopPadding() - insets.calculateBottomPadding()).value
    hudScale(width, height)
  }
}

/**
 * How much to shrink the car HUD for a map area of [widthDp] x [heightDp]: 1 on the Curvv (about
 * 1500 x 540 dp), less on smaller screens and split screens, never under 0.6 so it stays readable.
 */
fun hudScale(widthDp: Float, heightDp: Float): Float =
    minOf(1f, heightDp / HUD_DESIGN_HEIGHT_DP, widthDp / HUD_DESIGN_WIDTH_DP).coerceAtLeast(0.6f)

/** The smallest map area the HUD fits at full size: the turn card and time card stacked, plus the street. */
private const val HUD_DESIGN_HEIGHT_DP = 500f
private const val HUD_DESIGN_WIDTH_DP = 1000f

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
  val places = details.carPlaceIcons
  val baseStyle =
      remember(theme.id, trafficTiles, variant, flat, places) { BaseStyle.Json(StyleCache.json(context, theme, car = true, trafficTiles, variant, flat, places)) }
  NavigationMapView(
      baseStyle = baseStyle,
      navigationMapState = mapState,
      uiState = rememberSmoothedUiState(uiState),
      mapOptions = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
      navigationCameraOptions = cameraOptions,
      routeOverlayBuilder = null,
      showDefaultPuck = false,
  ) { state ->
    OverworldLayers(state, theme, car = true, extras, details, previewRoute, null, style = baseStyle, headingUp = mapState.headingUp, glide = mapState.glide)
  }
  RecenterGlideEffect(mapState)
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
    /** Whether the camera follows heading-up (see ThemedPuck). */
    headingUp: () -> Boolean,
    /** The map's re-centre, which aims at this arrow. */
    glide: RecenterGlide,
) {
  val shown = rememberDisplayedPosition(state)
  SideEffect { glide.shown = shown }
  val trip = state.routeGeometry?.takeIf { state.isNavigating() && it.size >= 2 }
  val route = shown?.route
  if (trip != null && route != null) {
    ThemedRouteAhead(route, shown, theme, car, style, extras.bridges, extras.trafficSpans)
  } else if (previewRoute != null && previewRoute.size >= 2) {
    ThemedRouteLine(previewRoute, theme, car, extras.trafficSpans)
  }
  RoadFeatureLayers(visibleFeatures(extras, details), theme, car)
  val end = trip?.lastOrNull() ?: previewRoute?.lastOrNull() ?: pin
  SavedPlacesLayer(theme, car, end)
  end?.let { ThemedDestination(it, theme, car) }
  // Stops added on the way (Nearby during a trip) get the waypoint marker too.
  (state.tripState as? TripState.Navigating)?.remainingWaypoints?.dropLast(1)?.forEachIndexed { i, stop ->
    ThemedDestination(stop.coordinate, theme, car, id = "ow-stop-$i")
  }
  shown?.let { ThemedPuck(it, theme, car, style, headingUp) }
}

/**
 * The map's place icon under a finger at [at] (a fingertip's width around it), as a place to pick:
 * its OpenStreetMap name and kind ("Restaurant"). Null where there's none, or it has no name.
 */
private fun placeAt(projection: CameraProjection, at: DpOffset): Place? {
  val r = 22.dp
  val box = DpRect(at.x - r, at.y - r, at.x + r, at.y + r)
  // Your own places first: Home over a shop that happens to share the spot.
  val hits =
      listOf(SAVED_LAYERS, setOf("poi-blips")).flatMap { runCatching { projection.queryRenderedFeatures(box, it) }.getOrNull().orEmpty() }
  for (f in hits) {
    val props = f.properties ?: continue
    fun text(key: String) = (props[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    if (props.containsKey("detail")) {
      val point = f.geometry as? Point ?: continue
      return Place(text("name") ?: continue, text("detail").orEmpty(), GeographicCoordinate(point.latitude, point.longitude))
    }
    val name = text("name:en") ?: text("name_en") ?: text("name") ?: continue
    val point = f.geometry as? Point ?: continue
    val kind = (text("subclass") ?: text("class"))?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Place"
    return Place(name, kind, GeographicCoordinate(point.latitude, point.longitude))
  }
  return null
}

/** Whether the camera follows the car heading-up. */
private val NavigationMapState.headingUp: () -> Boolean
  get() = { cameraMode == NavigationCameraMode.FOLLOW_USER_WITH_BEARING }

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

/** The map's own gestures on the phone, all but tilt. */
private val PHONE_GESTURES = GestureOptions(isTiltEnabled = false)
