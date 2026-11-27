package com.thealgothrim.overworld.car

import androidx.activity.OnBackPressedCallback
import androidx.car.app.CarContext
import androidx.car.app.versioning.CarAppApiLevels
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.thealgothrim.overworld.ui.arrivalClock
import com.thealgothrim.overworld.ui.formatDistance
import com.thealgothrim.overworld.ui.formatDuration
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.rallista.car.app.compose.ComposableScreen
import com.stadiamaps.ferrostar.car.app.navigation.NavigationManagerBridge
import com.stadiamaps.ferrostar.car.app.navigation.TurnByTurnNotificationManager
import com.stadiamaps.ferrostar.car.app.template.icons.InterfaceCarIcons
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.boundingBox
import com.stadiamaps.ferrostar.core.extensions.progress
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.screenSurfaceState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.search.Nearby
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.map.rememberOverworldMapState
import com.thealgothrim.overworld.map.OverworldCarMap
import com.thealgothrim.overworld.map.AutoZoomEffect
import com.thealgothrim.overworld.map.driveCamera
import com.thealgothrim.overworld.map.animatedPadding
import com.thealgothrim.overworld.map.carHudInsets
import com.thealgothrim.overworld.map.carHudScale
import com.thealgothrim.overworld.map.rememberDriveTilt
import com.thealgothrim.overworld.ui.CarTapTargets
import com.thealgothrim.overworld.ui.CarPanel
import com.thealgothrim.overworld.ui.CarPanelActions
import com.thealgothrim.overworld.ui.CarPanels
import com.thealgothrim.overworld.ui.skin.GameIcon
import androidx.car.app.Screen
import com.thealgothrim.overworld.map.glide
import com.thealgothrim.overworld.map.glideRecenter
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uniffi.ferrostar.DrivingSide

/**
 * The Android Auto screen. Android Auto draws the turn card, ETA and buttons from the template;
 * this screen draws the themed map under them and tints the turn card with the theme colour.
 * Destinations are chosen on the phone; the car shows the trip.
 */
class CarNavigationScreen(carContext: CarContext) : ComposableScreen(carContext) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val viewModel = AppModule.viewModel
  private val themeStore = AppModule.themeStore
  private val icons = InterfaceCarIcons(carContext)
  private var observeJob: Job? = null

  private val notificationManager =
      TurnByTurnNotificationManager(context = carContext, smallIconRes = R.drawable.ic_navigation)

  private val navigationManagerBridge =
      NavigationManagerBridge(
          navigationManager = carContext.getCarService(NavigationManager::class.java),
          viewModel = viewModel,
          context = carContext,
          notificationManager = notificationManager,
          // India drives on the left; Ferrostar assumes the right when a step doesn't say, which
          // would spin roundabouts the wrong way on the car's own cluster display.
          backupDrivingSide = DrivingSide.LEFT,
          onStopNavigation = { viewModel.stopNavigation() },
          // The Desktop Head Unit's "autodrive" command turns on the simulator.
          onAutoDriveEnabled = { viewModel.enableAutoDrive() },
          isCarForeground = { lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
      )

  private var uiState: NavigationUiState? by mutableStateOf(null)
  /** What the game HUD's template was last built for (on a trip or not); null with the HUD off. */
  private var templateShape: Boolean? = null
  /** The place the last trip reached and when (see the "Arrived" card). */
  private var arrivedAt: Pair<String, Long>? = null
  /** The car screen's density (px per dp), unscaled; set while the screen draws. */
  @Volatile private var carDensity = 1f
  private var mapState: NavigationMapState? = null
  private var overviewPadding = PaddingValues()
  private val surfaceAreaTracker = SurfaceAreaTracker { surfaceGestureCallback = it }

  /** Back on the route preview cancels it, like Google Maps. */
  private val cancelPreviewOnBack =
      object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.cancelPreview()
      }

  /**
   * Settings, Saved and Nearby open over the live map, as Google Maps opens its lists, never as a
   * screen of their own: car-app-compose pauses the map under any screen pushed over it. Drawn by
   * Overworld in the theme by default (CarPanelView, its taps checked in passTapsTo); with that
   * switched off, Android Auto's own list beside the map (MapWithContentTemplate, Car App API 7).
   * Either way coming back to the NavigationTemplate restarts Android Auto's five-template count.
   */
  private val panel: CarPanel?
    get() = CarPanels.open

  private val themedLists: Boolean
    get() = themeStore.carThemedLists.value

  /**
   * Android Auto's lists beside the map need Car App API 7 on the head unit; older ones get the
   * separate screens. Read when a list opens: the level isn't known yet while the screen is built
   * (nor ever in tests).
   */
  private val hostPanels: Boolean
    get() = runCatching { carContext.carAppApiLevel >= CarAppApiLevels.LEVEL_7 }.getOrDefault(false)

  /** Back in a panel: from a nearby list to the kinds, otherwise back to the map. */
  private val panelBack =
      object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = open(if (panel is CarPanel.NearbyList) CarPanel.NearbyKinds else null)
      }

  private fun open(next: CarPanel?) {
    CarPanels.open = next
    CarTapTargets.panelOpen.value = next != null && !themedLists
    panelBack.isEnabled = next != null
    invalidate()
  }

  /**
   * Android Auto's strip stays up beside the themed panel (a map screen must have one) and works as
   * its tabs: Nearby switches the panel to Nearby, and the open one's button closes it.
   */
  private fun show(next: CarPanel, screen: () -> Screen) =
      when {
        !themedLists && !hostPanels -> screenManager.push(screen())
        panel == next || (next == CarPanel.NearbyKinds && panel is CarPanel.NearbyList) -> open(null)
        else -> open(next)
      }

  private fun showSettings() = show(CarPanel.Settings) { CarSettingsScreen(carContext) }

  private fun showSaved() = show(CarPanel.Saved) { SavedPlacesScreen(carContext) }

  private fun showNearby() = show(CarPanel.NearbyKinds) { CarNearbyScreen(carContext) }

  private fun loadNearby(kind: Nearby) {
    open(CarPanel.NearbyList(kind, null))
    scope.launch {
      val found = viewModel.findNearby(kind)
      // Only if that list is still the one open.
      if ((panel as? CarPanel.NearbyList)?.kind == kind) open(CarPanel.NearbyList(kind, found))
    }
  }

  /**
   * Android Auto's keyboard page (the only place an app may have a keyboard), then, with the themed
   * lists on, the results in the panel over the map rather than in that page's own list.
   */
  private fun search() {
    open(null)
    screenManager.pushForResult(CarSearchScreen(carContext, themedLists)) { result ->
      val query = result as? String ?: return@pushForResult
      open(CarPanel.Results(query, null))
      scope.launch {
        val found = runCatching { AppModule.search.search(query, viewModel.currentCoordinate) }.getOrDefault(emptyList())
        if ((panel as? CarPanel.Results)?.query == query) open(CarPanel.Results(query, found))
      }
    }
  }

  private fun pickSaved(place: Place) {
    open(null)
    viewModel.startNavigation(place.coordinate, place.name)
  }

  private fun pickNearby(place: Place) {
    open(null)
    previewOrStop(carContext, place)
  }

  private val panelActions =
      object : CarPanelActions {
        override fun showNearby() = this@CarNavigationScreen.showNearby()

        override fun showSaved() = this@CarNavigationScreen.showSaved()

        override fun showSettings() = this@CarNavigationScreen.showSettings()

        override fun search() = this@CarNavigationScreen.search()

        override fun endTrip() {
          open(null)
          viewModel.stopNavigation()
        }

        override fun close() = open(null)

        override fun back() = panelBack.handleOnBackPressed()

        override fun nearby(kind: Nearby) = loadNearby(kind)

        override fun pickNearby(place: Place) = this@CarNavigationScreen.pickNearby(place)

        override fun pickSaved(place: Place) = this@CarNavigationScreen.pickSaved(place)
      }

  /** Android Auto's own list beside the map, for when the themed panel is switched off. */
  private fun hostPanelTemplate(p: CarPanel): Template {
    val content =
        when (p) {
          CarPanel.Settings -> carSettingsTemplate(withBack = true)
          CarPanel.Saved -> savedPlacesTemplate(withBack = true, onPick = ::pickSaved)
          // On a trip a pick becomes a stop on the way, and Search finds any place for one.
          CarPanel.NearbyKinds ->
              nearbyGridTemplate(
                  withBack = true,
                  onSearch = if (uiState?.isNavigating() == true) ::search else null,
                  onSkip =
                      viewModel.stop.value?.takeIf { uiState?.isNavigating() == true }?.let {
                        {
                          open(null)
                          viewModel.skipStop { ok -> androidx.car.app.CarToast.makeText(carContext, if (ok) "Stop skipped" else "Couldn't skip the stop", androidx.car.app.CarToast.LENGTH_SHORT).show() }
                        }
                      },
                  onKind = ::loadNearby,
              )
          is CarPanel.NearbyList -> nearbyListTemplate(p.kind, p.places, withBack = true, onPick = ::pickNearby)
          // Not reached: results only open as the themed panel (see search()).
          is CarPanel.Results -> nearbyGridTemplate(withBack = true, onKind = ::loadNearby)
        }
    return MapWithContentTemplate.Builder()
        .setContentTemplate(content)
        // PAN keeps drags and pinches on the map beside the list.
        .setMapController(MapController.Builder().setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build()).build())
        .build()
  }

  /**
   * Android Auto's strip with the game HUD: only search, with the theme's own search icon. A map
   * screen must keep a strip of Android Auto's; the other buttons are drawn in the theme beside it
   * (CarThemedStrip), and typing needs Android Auto's keyboard page anyway.
   */
  private fun searchStrip(): ActionStrip =
      ActionStrip.Builder()
          .addAction(
              Action.Builder()
                  .setIcon(carIcon(GameIcon.SEARCH))
                  .setOnClickListener { search() }
                  .build()
          )
          .build()

  init {
    navigationManagerBridge.start(scope)
    observeJob =
        viewModel.navigationUiState
            .onEach {
              uiState = it
              // Android Auto's list over the map doesn't change with each fix; the map under it does on its own.
              if (panel != null && !themedLists) return@onEach
              // With the game HUD the template is only Android Auto's strip, the same for a whole
              // trip: sending it again at every fix was a round trip to Android Auto a second for
              // nothing. Its own cards (HUD off) do change with each fix.
              val shape = if (themeStore.carGameHud.value) it.isNavigating() else null
              if (shape == null || shape != templateShape) invalidate()
              templateShape = shape
            }
            .launchIn(scope)
    // The Settings list shows changes from the phone's remote, and its own toggles, at once.
    merge(themeStore.carDetails, themeStore.carMapTime, themeStore.carCamButton, viewModel.cameraBeep)
        .onEach { if (panel == CarPanel.Settings && !themedLists) invalidate() }
        .launchIn(scope)
    themeStore.carTheme.onEach { invalidate() }.launchIn(scope)
    themeStore.carGameHud.onEach { invalidate() }.launchIn(scope)
    // The phone's route preview shows on the car too, with Start and Cancel.
    viewModel.planner.onEach { invalidate() }.launchIn(scope)
    // The live-traffic time and the road's details fill in the preview card a moment after the route.
    viewModel.extras.onEach { if (viewModel.planner.value.preview != null) invalidate() }.launchIn(scope)
    // + Cam comes and goes with its switch.
    themeStore.carCamButton.onEach { invalidate() }.launchIn(scope)
    // A trip that reached its end: "Arrived" with the place for a while, then "Set a waypoint" again.
    scope.launch {
      viewModel.arrived.collect { place ->
        arrivedAt = place to System.currentTimeMillis()
        invalidate()
        delay(ARRIVED_SHOWN_MS)
        invalidate()
      }
    }
    carContext.onBackPressedDispatcher.addCallback(this, cancelPreviewOnBack)
    // Added last, so it runs first: a panel closes before Back reaches the preview.
    carContext.onBackPressedDispatcher.addCallback(this, panelBack)

    // Buttons drawn on the map (+ Cam, Home and Work, the preview card's Start and Cancel), pressed
    // through map taps. Each takes at least Android Auto's minimum touch target, 76 x 76 dp, around
    // it (the buttons are 50 to 56 dp, smaller still when the HUD shrinks); where two overlap, the
    // nearer button wins.
    passTapsTo { x, y, shift ->
      val now = System.currentTimeMillis()
      if (panel != null && themedLists) {
        // A row scrolled out of the panel's view keeps a sliver of an area at its edge (its bounds
        // are clipped to the view): only rows still really showing count.
        val parts =
            CarTapTargets.panelTargets.entries
                .filter { it.key != "panel" && it.value.first.height >= MIN_SHOWN_PX && it.value.first.width >= MIN_SHOWN_PX }
                .map { (key, target) -> Triple(key, target.first, target.second) }
        val inside = CarTapTargets.panelTargets["panel"]?.first
        val hit =
            listOf(Offset(x + shift, y), Offset(x, y)).firstNotNullOfOrNull { tap ->
              parts.filter { (_, rect, _) -> rect.forCarTouch(carDensity).contains(tap) }.minByOrNull { (_, rect, _) -> (rect.center - tap).getDistanceSquared() }
            }
        when {
          hit != null -> {
            CarTapTargets.pressed.value = hit.first to now
            hit.third()
          }
          inside == null || listOf(Offset(x + shift, y), Offset(x, y)).none { inside.contains(it) } -> open(null)
        }
        return@passTapsTo
      }
      CarTapTargets.touchedAt.longValue = now
      val previewing = viewModel.planner.value.preview != null
      // Map buttons that came up with this very touch (see MapButtons) aren't pressed by it.
      val buttonsSettled = now - CarTapTargets.buttonsUpAt >= REVEAL_MS
      val overviewSettled = now - CarTapTargets.overviewUpAt >= REVEAL_MS
      fun press(key: String, action: () -> Unit): () -> Unit = {
        CarTapTargets.pressed.value = key to now
        action()
      }
      val targets =
          listOfNotNull(
              CarTapTargets.camera?.let { it to { viewModel.markCamera() } },
              // The menu row that comes up with a touch, once it's up: that touch presses nothing.
              *CarTapTargets.stripTargets.entries
                  .filter { !previewing && buttonsSettled }
                  .map { (key, target) -> target.first to press(key, target.second) }
                  .toTypedArray(),
              // The "Set a waypoint" card, always there while it shows.
              *CarTapTargets.cardTargets.entries
                  .filter { !previewing }
                  .map { (key, target) -> target.first to press(key, target.second) }
                  .toTypedArray(),
              CarTapTargets.mute?.takeIf { buttonsSettled }?.let { it to press("mute") { viewModel.toggleMute() } },
              CarTapTargets.zoomIn?.takeIf { buttonsSettled }?.let { it to press("zoomIn") { zoomBy(1.0) } },
              CarTapTargets.zoomOut?.takeIf { buttonsSettled }?.let { it to press("zoomOut") { zoomBy(-1.0) } },
              CarTapTargets.overview?.takeIf { overviewSettled }?.let { it to press("overview") { toggleOverview() } },
              CarTapTargets.home?.let { it to { AppModule.saved.state.value.home?.let { h -> viewModel.startNavigation(h.coordinate, "Home") }; Unit } },
              CarTapTargets.work?.let { it to { AppModule.saved.state.value.work?.let { w -> viewModel.startNavigation(w.coordinate, "Work") }; Unit } },
              CarTapTargets.start?.takeIf { previewing }?.let { it to { viewModel.startPreview() } },
              CarTapTargets.cancel?.takeIf { previewing }?.let { it to { viewModel.cancelPreview() } },
          )
      // In split screen the tap comes measured from the map's window (see windowShift); the raw
      // point is the fallback, so a wrong guess never costs a button that full screen would hit.
      listOf(Offset(x + shift, y), Offset(x, y))
          .firstNotNullOfOrNull { tap ->
            targets
                .filter { (rect, _) -> rect.forCarTouch(carDensity).contains(tap) }
                .minByOrNull { (rect, _) -> (rect.center - tap).getDistanceSquared() }
          }
          ?.second
          ?.invoke()
    }

    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          // Ferrostar posts a turn notification only while this screen is hidden (Android Auto shows
          // it as a pop-up over Spotify and the like), but never takes it back: coming back to the
          // map left a stale "Continue for 1.5 kilometers" pop-up over our own turn card.
          override fun onStart(owner: LifecycleOwner) {
            notificationManager.clear()
            viewModel.mapInView(true)
          }

          override fun onStop(owner: LifecycleOwner) = viewModel.mapInView(false)

          override fun onDestroy(owner: LifecycleOwner) {
            navigationManagerBridge.stop()
            observeJob?.cancel()
            scope.cancel()
          }
        }
    )
  }

  @Composable
  override fun content() {
    val theme by themeStore.carTheme.collectAsState()
    val surfaceArea by screenSurfaceState(surfaceAreaTracker)
    // The strip's room: from the cards to the map's right edge. Four named buttons need about 760 dp.
    val stripRoom = surfaceArea?.let { a -> with(LocalDensity.current) { (a.visibleArea.right - a.stableArea.left).toDp() } }
    LaunchedEffect(stripRoom) {
      val narrow = stripRoom != null && stripRoom < STRIP_NAMED_MIN
      if (narrow != narrowStrip) {
        narrowStrip = narrow
        invalidate()
      }
    }
    // From the stable area: the part Android Auto keeps clear even while its buttons show. The
    // visible area changes as they come and go, and the arrow moved with it.
    val normalPadding = animatedPadding(safeStablePadding(surfaceArea?.stableArea), "browse")
    // Keep the arrow low and a little right of centre while driving, like Google's (its car sits
    // about 85% of the way down; ours now about 80%): the road ahead gets the screen and the cards
    // keep the left.
    val trackingPadding = animatedPadding(safeStablePadding(surfaceArea?.stableArea, top = 0.6f, start = 0.25f), "follow")
    val driveCamera = theme.skin.driveCamera
    val zoom = CAR_ZOOM + driveCamera.zoomOffset
    val navState = viewModel.navigationUiState.collectAsState()
    // Only whether a trip is on is read here, not each fix: this screen's whole content was
    // composed again at every fix, in the same frame as the map's own work.
    val navigating by remember { derivedStateOf { uiState?.isNavigating() == true } }
    val tilt = rememberDriveTilt(driveCamera, navState)
    val cameraOptions =
        navigationCameraOptions()
            .copy(browsingPadding = normalPadding, navigationPadding = trackingPadding, navigationZoom = zoom, navigationTilt = tilt)
    // With no trip the car still drives: follow it heading-up like a trip does (Google Maps' free
    // drive). North-up made the map look stuck while the car turned.
    val state = rememberOverworldMapState(cameraOptions, initialCameraMode = NavigationCameraMode.FOLLOW_USER_WITH_BEARING)
    mapState = state
    AutoZoomEffect(state, navState, base = zoom, held = CarTapTargets.zoomHeld, release = CarTapTargets.zoomReleasedAt, zoomedAt = CarTapTargets.zoomedAt)
    // The car screen's own density, before the HUD's scaling: the size of a finger on this screen.
    carDensity = LocalDensity.current.density

    // Route overviews fit the part of the map nothing covers: inside Android Auto's visible area
    // (which leaves out its own cards) and, while driving with the game HUD, right of our cards.
    val density = LocalDensity.current
    val surfaceSize = LocalWindowInfo.current.containerSize
    val gameHud by themeStore.carGameHud.collectAsState()
    val planner by viewModel.planner.collectAsState()
    // The HUD's cards are 440 dp wide at full size and shrink with the HUD on smaller screens.
    val hudScale = carHudScale(carHudInsets(surfaceArea))
    val hudStart = if (gameHud && (navigating || planner.preview != null)) 440.dp * hudScale else 0.dp
    val padding =
        surfaceArea?.visibleArea?.let { area ->
          with(density) {
            PaddingValues(
                start = area.left.coerceAtLeast(0).toDp() + 48.dp + hudStart,
                top = area.top.coerceAtLeast(0).toDp() + 48.dp,
                end = (surfaceSize.width - area.right).coerceAtLeast(0).toDp() + 48.dp,
                bottom = (surfaceSize.height - area.bottom).coerceAtLeast(0).toDp() + 48.dp,
            )
          }
        } ?: normalPadding
    overviewPadding = padding

    // The route preview picked on the phone: show the whole route once the card is in place.
    val previewRoute = planner.preview?.route
    LaunchedEffect(previewRoute, padding) {
      val bounds = previewRoute?.geometry?.boundingBox() ?: return@LaunchedEffect
      delay(150)
      // Start may have been pressed meanwhile (the tap also brings up Android Auto's strip, which
      // changes the padding); the drive's follow camera must win then.
      if (viewModel.planner.value.preview?.route !== previewRoute || uiState?.isNavigating() == true) return@LaunchedEffect
      state.showRouteOverview(boundingBox = bounds, paddingValues = padding)
    }
    // Preview cancelled: back to following the car.
    LaunchedEffect(previewRoute == null) {
      if (previewRoute == null && uiState?.isNavigating() != true && state.cameraMode == NavigationCameraMode.OVERVIEW) followCar(state)
    }

    LaunchedEffect(navigating, state) {
      if (navigating) {
        // Following keeps the current zoom, which after the route preview is the whole city.
        state.cameraState.position = state.cameraState.position.copy(zoom = cameraOptions.navigationZoom)
        state.recenter(isNavigating = true)
      } else if (state.cameraMode == NavigationCameraMode.FOLLOW_USER) {
        // Ferrostar drops to north-up when a trip ends; the car keeps following heading-up.
        followCar(state)
      }
    }
    LaunchedEffect(state) {
      snapshotFlow { state.cameraMode }
          .collectLatest {
            CarTapTargets.following.value = state.isTrackingUser
            invalidate()
          }
    }

    OverworldCarMap(
        theme = theme,
        viewModel = viewModel,
        mapState = state,
        cameraOptions = cameraOptions,
        surfaceAreaTracker = surfaceAreaTracker,
        panelActions = panelActions,
    )
  }

  /** What the car screen last showed, for the drive log. */
  private var shownKind: String? = null

  /**
   * What the car shows, with each change written to the drive log. On the 26 Nov drive a trip
   * started from the phone left the car showing the preview's Start; three ways of starting one on
   * the desktop head unit all switched over within 1.5 s, so the log says what the car was on next time.
   */
  override fun onGetTemplate(): Template {
    val template = buildTemplate()
    val kind =
        when {
          panel != null && !themedLists -> "a $panel list"
          uiState?.isNavigating() == true -> "the trip"
          viewModel.planner.value.preview != null -> "the route preview"
          else -> "the map"
        } + " (Ferrostar ${if (viewModel.navigationUiState.value.isNavigating()) "on a trip" else "idle"})"
    if (kind != shownKind) {
      shownKind = kind
      com.thealgothrim.overworld.DriveLog.note("car: showing $kind")
    }
    return template
  }

  private fun buildTemplate(): Template {
    panel?.takeIf { !themedLists }?.let { return hostPanelTemplate(it) }
    val theme = themeStore.carTheme.value
    val state = uiState
    val tripState = state?.tripState
    if (state != null && state.isNavigating() && tripState != null) {
      val card = theme.carCard.toArgb()
      return NavigationTemplate.Builder()
          .setBackgroundColor(CarColor.createCustom(card, card))
          .apply {
            // The old game HUD (test builds only) draws the turn and ETA on the map instead of
            // these cards. Turn data reaches the car's own cluster display through NavigationManager
            // either way.
            if (!themeStore.carGameHud.value) {
              val extras = viewModel.extras.value
              metricRoutingInfo(
                      carContext, tripState, theme.skin,
                      rerouting = state.isCalculatingNewRoute == true,
                      cue = viewModel.levelCueAhead(state, extras),
                  )
                  ?.let { setNavigationInfo(it) }
              // The same time to go as the old HUD's: live traffic's, eased (see remainingSeconds).
              val seconds = viewModel.remainingSeconds(state, extras)
              tripState.progress()?.let { setDestinationTravelEstimate(it.toMetricTravelEstimate(etaColor(theme.skin, extras.eta), seconds)) }
            }
          }
          .setActionStrip(if (themeStore.carGameHud.value) searchStrip() else
              ActionStrip.Builder()
                  .addAction(worldAction(GameIcon.CLOSE, R.string.end_trip) { viewModel.stopNavigation() })
                  // Any place by name, or petrol, food, toilets along the rest of the route: a pick
                  // becomes a stop. Named for what it does on a trip (26 Nov: dropping a friend
                  // without a stop rerouted 15 times in 7 minutes).
                  .addAction(worldAction(GameIcon.ADD_STOP, "Add stop", titled = !narrowStrip) { showNearby() })
                  .addAction(worldAction(GameIcon.SETTINGS, R.string.settings, titled = !narrowStrip) { showSettings() })
                  .apply {
                    // + Cam, when it's switched on: Android Auto's own button, where the old HUD
                    // drew one on the map.
                    if (themeStore.carCamButton.value) addAction(worldAction(GameIcon.CAMERA, "+ Cam", titled = !narrowStrip) { markCamera() })
                  }
                  .build())
          .apply {
            // With the game HUD, its own themed map buttons stand in for these (see MapButtons).
            // PAN alone still has to be there: without it Android Auto keeps drags and pinches on
            // the map to itself, so the map couldn't be moved on a trip. Touch screens don't show it.
            if (themeStore.carGameHud.value) setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            else setMapActionStrip(
              ActionStrip.Builder()
                  .addAction(mapAction(if (state.isMuted == true) GameIcon.SOUND_OFF else GameIcon.SOUND_ON) { viewModel.toggleMute() })
                  .addAction(mapAction(GameIcon.ZOOM_IN) { zoomBy(1.0) })
                  .addAction(mapAction(GameIcon.ZOOM_OUT) { zoomBy(-1.0) })
                  // The route overview, or back to the car once the map has been moved.
                  .addAction(mapAction(if (mapState?.isTrackingUser != false) GameIcon.OVERVIEW else GameIcon.NAVIGATE) { toggleOverview() })
                  .build()
          )
          }
          .build()
    }

    // Not navigating: the themed map with Search, Nearby, Saved and Settings. In game-HUD mode the "Set a waypoint" card is
    // drawn on the map in the theme; otherwise Android Auto shows it as its own message card.
    val preview = viewModel.planner.value.preview
    cancelPreviewOnBack.isEnabled = preview != null
    if (preview != null) return previewTemplate()
    val idleActions =
        ActionStrip.Builder()
            // Find a place on the car screen itself; the phone can stay in its mount.
            .addAction(worldAction(GameIcon.SEARCH, R.string.search) { screenManager.push(CarSearchScreen(carContext)) })
            .addAction(worldAction(GameIcon.NEARBY, R.string.nearby, titled = !narrowStrip) { showNearby() })
            .addAction(worldAction(GameIcon.STAR, R.string.saved, titled = !narrowStrip) { showSaved() })
            .addAction(worldAction(GameIcon.SETTINGS, R.string.settings, titled = !narrowStrip) { showSettings() })
            .build()
    return NavigationTemplate.Builder()
        .apply {
          if (!themeStore.carGameHud.value) {
            val card = theme.carCard.toArgb()
            setBackgroundColor(CarColor.createCustom(card, card))
            // Just arrived: the place, like the phone's ARRIVED card. Otherwise "Set a waypoint".
            val arrived = arrivedAt?.takeIf { System.currentTimeMillis() - it.second < ARRIVED_SHOWN_MS }?.first
            setNavigationInfo(
                if (arrived != null) MessageInfo.Builder("Arrived").setText(arrived).build()
                // The games' own words for picking a destination. The world's name is already on the map.
                else MessageInfo.Builder(carContext.getString(R.string.car_idle_title)).setText(carContext.getString(R.string.car_idle_hint)).build()
            )
          }
        }
        .setActionStrip(if (themeStore.carGameHud.value) searchStrip() else idleActions)
        .setMapActionStrip(
            // The game HUD draws its own zoom and re-centre buttons (see MapButtons); PAN stays so
            // the map can still be dragged and pinched.
            if (themeStore.carGameHud.value) ActionStrip.Builder().addAction(Action.PAN).build()
            else
                ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .addAction(mapAction(GameIcon.ZOOM_IN) { zoomBy(1.0) })
                    .addAction(mapAction(GameIcon.ZOOM_OUT) { zoomBy(-1.0) })
                    // Back to following the car after looking around (there was no way back before).
                    .addAction(mapAction(GameIcon.NAVIGATE) { mapState?.let { followCar(it) }; invalidate() })
                    .build()
        )
        .build()
  }

  /** One of Android Auto's top buttons with the world's own icon beside its name. */
  private fun worldAction(icon: GameIcon, title: Int, titled: Boolean = true, onClick: () -> Unit): Action =
      worldAction(icon, carContext.getString(title), titled, onClick)

  /** A button in Android Auto's strip; [titled] false: the icon alone (a narrow map, see [narrowStrip]). */
  private fun worldAction(icon: GameIcon, title: String, titled: Boolean = true, onClick: () -> Unit): Action =
      Action.Builder().setIcon(carIcon(icon)).apply { if (titled) setTitle(title) }.setOnClickListener { onClick() }.build()

  /**
   * The map is too narrow for the strip's buttons with their names (split screen beside a media
   * card, small head units): Android Auto doesn't shrink them, it pushed the last one (Settings)
   * off the edge. Then only the first keeps its name, the rest are icons, like Google Maps'.
   */
  private var narrowStrip = false

  /** One of Android Auto's round map buttons with the world's own icon. */
  private fun mapAction(icon: GameIcon, onClick: () -> Unit): Action =
      Action.Builder().setIcon(carIcon(icon)).setOnClickListener { onClick() }.build()

  /** + Cam: marks a speed camera here, and says so for a moment. */
  private fun markCamera() {
    viewModel.markCamera()
    androidx.car.app.CarToast.makeText(carContext, "Speed camera marked", androidx.car.app.CarToast.LENGTH_SHORT).show()
  }

  /**
   * The route preview. With Android Auto's cards (API 7 heads): a card beside the map with the place,
   * the time and the road's details, Start in the world's colour, and the route on the map beside it.
   * Older heads, and the old HUD (whose own card is drawn on the map), keep Start and Cancel in
   * Android Auto's strip.
   */
  private fun previewTemplate(): Template {
    val preview = viewModel.planner.value.preview
    if (preview != null && !themeStore.carGameHud.value && hostPanels) {
      return previewCardTemplate(carContext, preview, viewModel.extras.value, themeStore.carTheme.value, viewModel::startPreview, viewModel::cancelPreview)
    }
    return NavigationTemplate.Builder()
          .setActionStrip(
              ActionStrip.Builder()
                  .addAction(
                      Action.Builder()
                          .setTitle(carContext.getString(R.string.start))
                          .setFlags(Action.FLAG_PRIMARY)
                          .setOnClickListener { viewModel.startPreview() }
                          .build()
                  )
                  .addAction(Action.Builder().setTitle(carContext.getString(R.string.cancel)).setOnClickListener { viewModel.cancelPreview() }.build())
                  .build()
          )
          .build()
  }

  /** The driver's own zoom, held for a while against the automatic one (see AutoZoomEffect). */
  private fun zoomBy(delta: Double) {
    CarTapTargets.zoomedAt.longValue = System.currentTimeMillis()
    if (delta > 0) mapState?.zoomIn(delta, ZOOM_MS) else mapState?.zoomOut(-delta, ZOOM_MS)
  }

  /** Follow the car heading-up, the way the map runs with no trip. */
  private fun followCar(state: NavigationMapState) {
    state.glide.start(NavigationCameraMode.FOLLOW_USER_WITH_BEARING)
  }

  /**
   * The bottom round button: re-centre when the map was moved (dragged, the route overview, or a
   * zoom of the driver's own), otherwise the route overview.
   */
  private fun toggleOverview() {
    val state = mapState ?: return
    when {
      !state.isTrackingUser -> {
        if (uiState?.isNavigating() == true) state.glideRecenter(isNavigating = true) else followCar(state)
        CarTapTargets.zoomReleasedAt.longValue = System.currentTimeMillis()
      }
      CarTapTargets.zoomHeld.value -> CarTapTargets.zoomReleasedAt.longValue = System.currentTimeMillis()
      else ->
          viewModel.navigationUiState.value.routeGeometry?.boundingBox()?.let {
            state.showRouteOverview(boundingBox = it, paddingValues = overviewPadding)
          }
    }
    invalidate()
  }
}

/** A tap's zoom step: quicker than Ferrostar's 300 ms, since Android Auto already held the tap half a second. */
private val ZOOM_MS = 200.milliseconds

/**
 * The route preview as Android Auto's card beside the map (Car App API 7): the place, the time and
 * distance with the arrival, the road's details (the way, traffic lights, incidents, traffic), Start
 * in the world's colour and Cancel. The route itself shows on the map beside it.
 */
internal fun previewCardTemplate(
    carContext: CarContext,
    preview: com.thealgothrim.overworld.RoutePreview,
    extras: com.thealgothrim.overworld.RouteExtras,
    theme: com.thealgothrim.overworld.theme.OverworldTheme,
    onStart: () -> Unit,
    onCancel: () -> Unit,
): Template {
  val seconds = extras.eta?.travelSeconds ?: preview.durationSeconds
  val facts =
      listOfNotNull(
          preview.via?.let { "via $it" },
          extras.lightsOnRoute.takeIf { it > 0 }?.let { "$it traffic lights" },
          extras.markedIncidents.size.takeIf { it > 0 }?.let { "$it incident${if (it == 1) "" else "s"}" },
          com.thealgothrim.overworld.ui.trafficNote(extras.eta),
      )
  val accent = theme.hudAccent.toArgb()
  val pane =
      Pane.Builder()
          .addRow(
              Row.Builder()
                  .setTitle("${formatDuration(seconds)}  ·  ${formatDistance(preview.distanceMeters)}")
                  .addText("Arrive at ${arrivalClock(seconds)}")
                  .build()
          )
          .apply { if (facts.isNotEmpty()) addRow(Row.Builder().setTitle(facts.joinToString("  ·  ")).build()) }
          .addAction(
              Action.Builder()
                  .setTitle(carContext.getString(R.string.start))
                  .setFlags(Action.FLAG_PRIMARY)
                  .setBackgroundColor(CarColor.createCustom(accent, accent))
                  .setOnClickListener { onStart() }
                  .build()
          )
          .addAction(Action.Builder().setTitle(carContext.getString(R.string.cancel)).setOnClickListener { onCancel() }.build())
          .build()
  return MapWithContentTemplate.Builder()
      .setContentTemplate(PaneTemplate.Builder(pane).setTitle(preview.place.name).setHeaderAction(Action.BACK).build())
      .setMapController(MapController.Builder().setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build()).build())
      .build()
}

/** How long the "Arrived" card stays after a trip reaches its end. */
private const val ARRIVED_SHOWN_MS = 20_000L

/** A tap this soon after the map buttons came up brought them up; it presses none. */
private const val REVEAL_MS = 650L

/** A panel row at least this many pixels of which still show can be tapped. */
private const val MIN_SHOWN_PX = 12f

/** The car map's following zoom at about 40 km/h, before the theme's own offset (see DriveCamera). */
private const val CAR_ZOOM = 16.4

/** At least 76 x 76 dp (Android Auto's minimum touch target) on a screen of [density], centred on the button. */
internal fun androidx.compose.ui.geometry.Rect.forCarTouch(density: Float): androidx.compose.ui.geometry.Rect {
  val min = CAR_TOUCH_DP * density
  val w = maxOf(width, min)
  val h = maxOf(height, min)
  return androidx.compose.ui.geometry.Rect(center.x - w / 2f, center.y - h / 2f, center.x + w / 2f, center.y + h / 2f)
}

private const val CAR_TOUCH_DP = 76f

/** The room four named buttons need in Android Auto's strip (Curvv: 996 dp full screen fits, 572 dp split doesn't). */
private val STRIP_NAMED_MIN = 760.dp
