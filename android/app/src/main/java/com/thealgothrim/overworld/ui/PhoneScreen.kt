package com.thealgothrim.overworld.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import com.stadiamaps.ferrostar.core.boundingBox
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.thealgothrim.overworld.map.AutoZoomEffect
import com.thealgothrim.overworld.map.driveCamera
import com.thealgothrim.overworld.map.rememberDriveTilt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.asPaddingValues
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.OverworldViewModel
import com.thealgothrim.overworld.map.rememberOverworldMapState
import com.thealgothrim.overworld.map.OverworldPhoneMap
import com.thealgothrim.overworld.map.glideRecenter
import com.thealgothrim.overworld.map.CompassModeEffect
import com.thealgothrim.overworld.map.PhoneCompass
import com.thealgothrim.overworld.map.glide
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.gta.GtaBigMessage
import com.thealgothrim.overworld.ui.rdr.RdrBigMessage
import com.thealgothrim.overworld.ui.vi.ViBigMessage
import com.thealgothrim.overworld.ui.vi.ViMapLight
import com.thealgothrim.overworld.ui.skin.spec
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.thealgothrim.overworld.traffic.metres
import uniffi.ferrostar.GeographicCoordinate
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

/**
 * The phone app, laid out like Google Maps (search bar and settings on top, map buttons on the
 * right, sheets at the bottom, turn banner while driving) and dressed in the chosen game's HUD.
 */
@Composable
fun PhoneScreen(viewModel: OverworldViewModel = AppModule.viewModel) {
  // With Android Auto on, the phone is the car's remote (RemoteScreen); "Phone map" brings the map
  // back until the car disconnects, and Back returns to the remote.
  val projecting = rememberCarProjecting()
  var phoneMap by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(projecting) { if (!projecting) phoneMap = false }
  if (projecting && !phoneMap) {
    RemoteScreen(viewModel, onShowMap = { phoneMap = true })
    return
  }
  BackHandler(projecting) { phoneMap = false }
  PhoneMapScreen(viewModel)
}

@Composable
private fun PhoneMapScreen(viewModel: OverworldViewModel) {
  val theme by AppModule.themeStore.theme.collectAsState()
  val spec = theme.spec
  val uiState by viewModel.navigationUiState.collectAsState()
  val planner by viewModel.planner.collectAsState()
  val saved by AppModule.saved.state.collectAsState()
  val testDrive by viewModel.testDrive.collectAsState()
  val area by viewModel.area.collectAsState()
  val variant by viewModel.mapVariant.collectAsState()
  val driveCamera = theme.skin.driveCamera
  val phoneZoom = PHONE_ZOOM + driveCamera.zoomOffset
  val navState = viewModel.navigationUiState.collectAsState()
  val tilt = rememberDriveTilt(driveCamera, navState)
  // How much of the screen's bottom the trip sheet and street line cover while driving.
  var bottomBlocked by remember { mutableStateOf(0.dp) }
  /** The speed readout's and street name's places in the window, for the edge markers to step round. */
  var speedBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
  var streetBox by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
  // What the driving cards leave clear of the window (see NavigationLayer).
  var mapRoom by remember { mutableStateOf(PaddingValues(0.dp)) }
  val defaults = navigationCameraOptions()
  val cameraOptions =
      defaults.copy(navigationZoom = phoneZoom, navigationTilt = tilt, navigationPadding = clearOfBottom(defaults.navigationPadding, bottomBlocked))
  val mapState = rememberOverworldMapState(cameraOptions)
  AutoZoomEffect(mapState, navState, base = phoneZoom)
  val scope = rememberCoroutineScope()
  val navigating = uiState.isNavigating()

  var settingsOpen by rememberSaveable { mutableStateOf(false) }
  var layersOpen by rememberSaveable { mutableStateOf(false) }
  var indexOpen by rememberSaveable { mutableStateOf(false) }
  // You (saved lists, Timeline, history), a Timeline day on the map, the offline download frame.
  var youOpen by rememberSaveable { mutableStateOf(false) }
  var youTab by rememberSaveable { mutableStateOf(YouTab.SAVED) }
  var dayOnMap by remember { mutableStateOf<java.time.LocalDate?>(null) }
  var pickingArea by remember { mutableStateOf(false) }
  var notice by remember { mutableStateOf<String?>(null) }
  val keepNorthUp by AppModule.prefs.northUp.collectAsState()
  val extras by viewModel.extras.collectAsState()
  var searchOpen by remember { mutableStateOf(false) }
  var arrivedAt by remember { mutableStateOf<String?>(null) }
  val rotated by remember { derivedStateOf { abs(mapState.cameraState.position.bearing) > 1.0 } }

  // Back steps out one level at a time, like Google Maps. The handler registered last runs first,
  // so these go from the bottom level (the place card) up to the top (the Layers sheet).
  val overlay = layersOpen || settingsOpen || youOpen || indexOpen || dayOnMap != null || pickingArea
  BackHandler(!overlay && !searchOpen && planner.preview == null && planner.destination != null) {
    viewModel.clearDestination()
  }
  BackHandler(!overlay && !searchOpen && planner.preview != null) { viewModel.cancelPreview() }
  BackHandler(!overlay && searchOpen) {
    searchOpen = false
    viewModel.onQueryChange("")
  }
  BackHandler(pickingArea) { pickingArea = false }
  BackHandler(dayOnMap != null && !youOpen) {
    dayOnMap = null
    youTab = YouTab.TIMELINE
    youOpen = true
  }
  BackHandler(youOpen && !settingsOpen) { youOpen = false }
  BackHandler(settingsOpen && !layersOpen) { settingsOpen = false }
  BackHandler(layersOpen) { layersOpen = false }
  BackHandler(indexOpen) { indexOpen = false }

  // A Timeline day on the map: its pieces, and the camera framing them.
  val daySegments by androidx.compose.runtime.produceState<List<com.thealgothrim.overworld.timeline.Segment>?>(null, dayOnMap) {
    val d = dayOnMap
    value = if (d == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppModule.timeline.store.day(d) }
  }
  LaunchedEffect(daySegments) {
    val segs = daySegments?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
    val points =
        segs.flatMap { s -> listOfNotNull(s.at, s.to) + (s.path?.let { com.thealgothrim.overworld.routing.OverworldRoutes.decodePolyline6(it) } ?: emptyList()) }
    if (points.isEmpty()) return@LaunchedEffect
    mapState.cameraMode = NavigationCameraMode.FREE
    val bounds = points.boundingBox() ?: return@LaunchedEffect
    mapState.showRouteOverview(boundingBox = bounds, paddingValues = PaddingValues(start = 48.dp, end = 48.dp, top = 120.dp, bottom = 240.dp))
  }
  LaunchedEffect(notice) {
    if (notice != null) {
      delay(6_000)
      notice = null
    }
  }
  // Debug builds open these from adb (DebugDriveReceiver), for checks and screenshots.
  LaunchedEffect(Unit) {
    DebugUi.you.collect { t ->
      if (t == null) return@collect
      if (t == "none") youOpen = false
      else {
        settingsOpen = false
        youTab = runCatching { YouTab.valueOf(t.uppercase()) }.getOrDefault(YouTab.SAVED)
        youOpen = true
      }
      DebugUi.you.value = null
    }
  }
  LaunchedEffect(Unit) {
    DebugUi.settingsTab.collect { t ->
      if (t == null) return@collect
      youOpen = false
      settingsOpen = t >= 0
      // The tab itself is picked by SettingsScreen, which reads the same value.
      if (t < 0) DebugUi.settingsTab.value = null
    }
  }
  LaunchedEffect(Unit) {
    DebugUi.day.collect { d ->
      if (d == null) return@collect
      youOpen = false
      settingsOpen = false
      dayOnMap =
          when (d) {
            "today" -> java.time.LocalDate.now()
            "yesterday" -> java.time.LocalDate.now().minusDays(1)
            "none" -> null
            else -> runCatching { java.time.LocalDate.parse(d) }.getOrNull()
          }
      DebugUi.day.value = null
    }
  }
  LaunchedEffect(Unit) {
    DebugUi.pickArea.collect { on ->
      if (!on) return@collect
      youOpen = false
      settingsOpen = false
      pickingArea = true
      DebugUi.pickArea.value = false
    }
  }

  LaunchedEffect(navigating) { if (navigating) mapState.recenter(isNavigating = true) }
  // A trip that ends at its destination offers to save the parking there, like Google's arrival card.
  var offerParking by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    viewModel.arrived.collect {
      if (viewModel.tripMode.value != com.thealgothrim.overworld.routing.TravelMode.WALK) {
        offerParking = true
        delay(30_000)
        offerParking = false
      }
    }
  }
  LaunchedEffect(planner.destination) {
    val d = planner.destination ?: return@LaunchedEffect
    if (planner.preview != null) return@LaunchedEffect
    mapState.cameraMode = NavigationCameraMode.FREE
    // A quick flight down onto the place, tilted the way the theme drives (RDR2 stays flat, like its
    // map), set above the place card: 500 ms to zoom 16, as the Mapbox game-map apps do. It was a
    // flat 900 ms drift to zoom 15 that read as slow.
    mapState.cameraState.animateTo(
        CameraPosition(
            target = Position(d.coordinate.lng, d.coordinate.lat),
            zoom = 16.0,
            tilt = driveCamera.tilt,
            bearing = mapState.cameraState.position.bearing,
            padding = PaddingValues(bottom = 240.dp),
        ),
        duration = 500.milliseconds,
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

  // Locate asks Android to turn location on first when it is off, like Google Maps. A second tap,
  // already following, is compass mode: the map turns with the phone (a third goes back north-up).
  var compass by remember { mutableStateOf(false) }
  LaunchedEffect(navigating) { if (navigating) compass = false }
  val locate = rememberWithLocationOn {
    when {
      navigating -> mapState.glideRecenter(isNavigating = true)
      !compass && mapState.cameraMode == NavigationCameraMode.FOLLOW_USER -> {
        compass = true
        mapState.glide.start(NavigationCameraMode.FOLLOW_USER_WITH_BEARING)
      }
      else -> {
        compass = false
        mapState.glideRecenter(isNavigating = false)
      }
    }
  }
  CompassModeEffect(mapState, compass) { compass = false }
  // The arrow points where the phone does while slow, off a trip (on one it follows the road).
  PhoneCompass.Effect(on = !navigating)

  // GTA V's pause map names the area under the map's centre (bottom-left, with the scale bar).
  var mapArea by remember { mutableStateOf<String?>(null) }
  // The camera moves every frame while driving. Read here directly, it redrew the whole screen
  // each frame; this only changes the screen when the scale bar's value does (in 2% steps).
  val scale by remember {
    derivedStateOf {
      val p = mapState.cameraState.position
      val m = metresPerDp(p.target.latitude, p.zoom)
      val step = 10.0.pow(kotlin.math.floor(kotlin.math.log10(m)) - 2) * 2
      (m / step).roundToInt() * step
    }
  }
  // The GTA pause maps name the area at the map's centre (GtaMapCorner); Red Dead's doesn't.
  // Not on a trip: the name isn't shown then, and the camera moves every frame, so this only
  // started and dropped a lookup that never ran, sixty times a second, for the whole drive.
  LaunchedEffect(spec.rdr, navigating) {
    if (spec.rdr || navigating) return@LaunchedEffect
    var looked: Pair<GeographicCoordinate, Double>? = null
    snapshotFlow { mapState.cameraState.position }
        .collectLatest { p ->
          delay(800) // wait for the map to settle
          val centre = GeographicCoordinate(p.target.latitude, p.target.longitude)
          val last = looked
          if (last != null && metres(last.first, centre) < 250.0 && abs(last.second - p.zoom) < 1.5) return@collectLatest
          looked = centre to p.zoom
          runCatching { AppModule.search.areaAt(centre) }.getOrNull()?.let { mapArea = it }
        }
  }
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
    } else mapState.glideRecenter(isNavigating = true)
  }

  Box(Modifier.fillMaxSize().background(theme.pageFor(variant))) {
    OverworldPhoneMap(
        theme = theme,
        // Compass mode turns the map itself; GPS's course would fight it.
        uiState = if (compass) uiState.copy(location = uiState.location?.copy(courseOverGround = null)) else uiState,
        mapState = mapState,
        cameraOptions = cameraOptions,
        pickedDestination = planner.destination?.coordinate,
        previewRoute = planner.preview?.route?.geometry,
        onLongPress = {
          searchOpen = false
          viewModel.dropPin(it)
        },
        onPlaceTap = {
          searchOpen = false
          viewModel.choose(it)
        },
        timelineDay = daySegments,
        northUp = keepNorthUp && navigating,
    )
    if (theme.paperOverlay) PaperOverlay()
    if (theme.skin == Skin.GTA6) ViMapLight(variant)
    WantedFlash(theme, car = false)
    // Red Dead's cursor on a picked place: red ticks round it and its name beside it.
    planner.destination?.takeIf { theme.skin == Skin.RDR && !navigating }?.let { picked ->
      com.thealgothrim.overworld.ui.rdr.RdrCursor(mapState.cameraState, picked.coordinate, picked.name)
    }
    // Driving, the map is the minimap: north round the edge and the waypoint on it when the
    // destination is off screen, in every world (GTA V and Red Dead lost theirs in the 28 Sep
    // layout change), inside what the cards leave clear.
    if (navigating) {
      val safe = WindowInsets.safeDrawing.asPaddingValues()
      val room =
          PaddingValues(
              start = maxOf(mapRoom.calculateStartPadding(LayoutDirection.Ltr), safe.calculateStartPadding(LayoutDirection.Ltr)),
              top = maxOf(mapRoom.calculateTopPadding(), safe.calculateTopPadding()),
              end = maxOf(mapRoom.calculateEndPadding(LayoutDirection.Ltr), safe.calculateEndPadding(LayoutDirection.Ltr)),
              // The sheet's edge, not above the speed and street line: the arrow sits just over that
              // line, so with it N and W huddled round the arrow whenever north was behind (13 Nov).
              bottom = maxOf(mapRoom.calculateBottomPadding(), safe.calculateBottomPadding()),
          )
      MinimapEdge(theme, mapState.cameraState, uiState.routeGeometry?.lastOrNull(), room, avoid = listOfNotNull(speedBox, streetBox))
    }

    // Under You or Settings the map still shows faintly, as behind a game's pause menu, but not the
    // map's own cards: an open Save panel read through as ghost text (8 Nov).
    val menuOpen = youOpen || settingsOpen || indexOpen
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).graphicsLayer { alpha = if (menuOpen) 0f else 1f }) {
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
                onBottomBlocked = { bottomBlocked = it },
                onMapRoom = { mapRoom = it },
                onSpeedBox = { speedBox = it },
                onStreetBox = { streetBox = it },
            )
        pickingArea ->
            OfflineAreaPicker(spec, mapState, mapArea ?: area) { message ->
              pickingArea = false
              notice = message
            }
        dayOnMap != null ->
            TimelineDayCard(
                spec,
                dayOnMap!!,
                daySegments.orEmpty(),
                onDate = { dayOnMap = it },
                onClose = {
                  dayOnMap = null
                  youTab = YouTab.TIMELINE
                  youOpen = true
                },
            )
        planner.preview != null ->
            PreviewLayer(
                spec = spec,
                preview = planner.preview!!,
                extras = extras,
                testDrive = testDrive,
                onBack = viewModel::cancelPreview,
                onStart = viewModel::startPreview,
                mode = planner.mode,
                modeTimes = planner.modeTimes,
                routing = planner.routing,
                onMode = viewModel::setPreviewMode,
                saved = saved.trips.any { it.to.coordinate == planner.preview!!.place.coordinate && it.mode == planner.preview!!.mode },
                onSaveTrip = viewModel::saveTrip,
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
                onNearby = {
                  searchOpen = true
                  viewModel.searchNearby(it)
                },
                onChoose = {
                  searchOpen = false
                  viewModel.choose(it)
                },
                onClear = viewModel::clearDestination,
                onDirections = viewModel::directions,
                onSettings = { settingsOpen = true },
                onLayers = { layersOpen = true },
                onLocate = locate,
                onIndex = {
                  searchOpen = false
                  indexOpen = true
                },
                onYou = {
                  searchOpen = false
                  youTab = YouTab.SAVED
                  youOpen = true
                },
                compass = compass,
                following = mapState.isTrackingUser,
                onNorthUp = northUp,
                mapArea = mapArea,
                metresPerDp = scale,
            )
      }
    }

    // No location: a small pill under the top bar (his ask, 8 Nov: subtle on the phone), not a card
    // over the middle of the map. With the phone's location switched off it offers to turn it on.
    val lost by viewModel.gpsLost.collectAsState()
    val off by viewModel.locationOff.collectAsState()
    val turnOn = rememberWithLocationOn {}
    if (lost && !searchOpen && !settingsOpen && !layersOpen && !youOpen && dayOnMap == null && !pickingArea) {
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val top = if (navigating) mapRoom.calculateTopPadding() + 8.dp else 124.dp
        Row(
            Modifier.align(Alignment.TopCenter)
                .padding(top = top)
                .skinPanel(spec, elevated = false)
                .then(if (off) Modifier.clickable { turnOn() } else Modifier)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          GameIconView(GameIcon.LOCATE, if (off) spec.accent else spec.sub, Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          SkinText(if (off) "Location is off  ·  Turn on" else "No GPS signal", spec.body, 14.sp, spec.fg)
        }
      }
    }

    val arrivedVisible = arrivedAt != null
    when (theme.skin) {
      Skin.GTA -> GtaBigMessage(arrivedVisible, "ARRIVED", arrivedAt.orEmpty(), Modifier.align(Alignment.Center))
      Skin.GTA6 -> ViBigMessage(arrivedVisible, "Arrived", arrivedAt.orEmpty(), Modifier.align(Alignment.Center))
      Skin.RDR -> RdrBigMessage(arrivedVisible, "ARRIVED", arrivedAt.orEmpty(), Modifier.align(Alignment.Center))
    }

    // A word that something started (an offline download), over the map for a few seconds.
    notice?.let { text ->
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.align(Alignment.TopCenter).padding(top = 76.dp, start = 16.dp, end = 16.dp).skinPanel(spec).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          GameIconView(GameIcon.DOWNLOAD, spec.accent, Modifier.size(22.dp))
          Spacer(Modifier.width(10.dp))
          SkinText(text, spec.body, 15.sp, spec.fg, maxLines = 3)
        }
      }
    }

    // Support Overworld, asked once (see SupportAsk): decided when the screen first opens, so it
    // never comes up in the middle of the session a trip ended in, and only over the plain map.
    var askSupport by remember {
      mutableStateOf(
          com.thealgothrim.overworld.prefs.SupportAsk.due(AppModule.prefs.finishedTrips, AppModule.prefs.supportAnswered, viewModel.carConnected, navigating)
      )
    }
    if (askSupport && !navigating && !overlay && !searchOpen && !offerParking && planner.destination == null && planner.preview == null) {
      val context = androidx.compose.ui.platform.LocalContext.current
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 96.dp).fillMaxWidth().skinPanel(spec).padding(horizontal = 16.dp, vertical = 14.dp)) {
          SkinText("Overworld is free, with no ads.", spec.body, 17.sp, spec.fg)
          SkinText("If it's earned its place in your car, you can support it. Nothing in the app changes either way.", spec.body, 14.sp, spec.sub, Modifier.padding(top = 4.dp), maxLines = 3)
          Row(Modifier.padding(top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            com.thealgothrim.overworld.ui.skin.SkinPrimaryButton(spec, "Support", {
              AppModule.prefs.answerSupport()
              askSupport = false
              runCatching {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.thealgothrim.overworld.prefs.SupportAsk.URL))
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
              }
            }, Modifier.weight(1f))
            com.thealgothrim.overworld.ui.skin.SkinSecondaryButton(spec, "Not now", {
              AppModule.prefs.answerSupport()
              askSupport = false
            })
          }
        }
      }
    }

    // Arrived by car: save the parking here, one tap (it goes after 30 s).
    if (offerParking && !navigating && saved.parking?.let { p -> uiState.location?.coordinates?.let { metres(it, p.at) < 60.0 } } != true) {
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 96.dp).fillMaxWidth().skinPanel(spec).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          GameIconView(GameIcon.PARKING, spec.accent, Modifier.size(26.dp))
          Spacer(Modifier.width(12.dp))
          SkinText("Save your parking?", spec.body, 16.sp, spec.fg, Modifier.weight(1f))
          com.thealgothrim.overworld.ui.skin.SkinPrimaryButton(spec, "Save", {
            viewModel.saveParking()
            offerParking = false
          })
        }
      }
    }

    if (settingsOpen) {
      SettingsScreen(
          theme = theme,
          uiState = uiState,
          testDrive = testDrive,
          saved = saved,
          onTestDrive = viewModel::setTestDrive,
          onMute = viewModel::toggleMute,
          onClose = { settingsOpen = false },
          onDownloadArea = {
            settingsOpen = false
            viewModel.clearDestination()
            pickingArea = true
          },
      )
    }

    if (youOpen) {
      YouScreen(
          theme = theme,
          saved = saved,
          here = uiState.location?.coordinates,
          start = youTab,
          onChoose = {
            youOpen = false
            viewModel.choose(it)
          },
          onSearch = {
            youOpen = false
            searchOpen = true
            viewModel.onQueryChange(it)
          },
          onShowDay = {
            youOpen = false
            viewModel.clearDestination()
            dayOnMap = it
          },
          onClose = { youOpen = false },
      )
    }

    if (layersOpen) MapLayersSheet(theme, onClose = { layersOpen = false })

    if (indexOpen) {
      MapIndexPanel(
          theme,
          mapState.cameraState,
          uiState.location?.coordinates,
          onChoose = {
            indexOpen = false
            viewModel.choose(it)
          },
          onClose = { indexOpen = false },
      )
    }
  }
}

/**
 * [base] with enough extra bottom padding that the followed arrow sits above what covers the
 * bottom of the screen ([blocked], plus the system's own bar). On a full-height phone nothing
 * changes; in a short window (split screen) the arrow used to sit behind the trip sheet.
 */
@Composable
private fun clearOfBottom(base: PaddingValues, blocked: Dp): PaddingValues {
  val density = LocalDensity.current
  val height = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
  val inset = with(density) { WindowInsets.safeDrawing.getBottom(density).toDp() }
  val top = base.calculateTopPadding()
  val bottom = base.calculateBottomPadding()
  // MapLibre centres the arrow in the padded view, so raising the bottom edge by x lifts it by x / 2.
  val arrow = (height + top - bottom) / 2
  val lowest = height - inset - blocked - ARROW_CLEARANCE
  val extra = ((arrow - lowest) * 2).coerceAtLeast(0.dp)
  if (extra == 0.dp) return base
  return PaddingValues(
      start = base.calculateStartPadding(LayoutDirection.Ltr),
      top = top,
      end = base.calculateEndPadding(LayoutDirection.Ltr),
      bottom = bottom + extra,
  )
}

private val ARROW_CLEARANCE = 24.dp

/** The phone map's following zoom at about 40 km/h (Ferrostar's driving default). */
private const val PHONE_ZOOM = 16.0
