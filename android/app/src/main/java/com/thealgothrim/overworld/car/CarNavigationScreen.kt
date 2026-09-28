package com.thealgothrim.overworld.car

import androidx.activity.OnBackPressedCallback
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.stadiamaps.ferrostar.car.app.intent.NavigationDestination
import com.stadiamaps.ferrostar.car.app.navigation.NavigationManagerBridge
import com.stadiamaps.ferrostar.car.app.navigation.TurnByTurnNotificationManager
import com.stadiamaps.ferrostar.car.app.template.icons.InterfaceCarIcons
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.boundingBox
import com.stadiamaps.ferrostar.core.extensions.progress
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.SurfaceAreaTracker
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.screenSurfaceState
import com.stadiamaps.ferrostar.ui.maplibre.car.app.runtime.surfaceStableFractionalPadding
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.map.rememberOverworldMapState
import com.thealgothrim.overworld.map.OverworldCarMap
import com.thealgothrim.overworld.ui.CarTapTargets
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The Android Auto screen. Android Auto draws the turn card, ETA and buttons from the template;
 * this screen draws the themed map under them and tints the turn card with the theme colour.
 * Destinations are chosen on the phone; the car shows the trip.
 */
class CarNavigationScreen(
    carContext: CarContext,
    initialDestination: NavigationDestination? = null,
) : ComposableScreen(carContext) {

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
          onStopNavigation = { viewModel.stopNavigation() },
          // The Desktop Head Unit's "autodrive" command turns on the simulator.
          onAutoDriveEnabled = { viewModel.enableAutoDrive() },
          isCarForeground = { lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
      )

  private var uiState: NavigationUiState? by mutableStateOf(null)
  private var mapState: NavigationMapState? = null
  private var overviewPadding = PaddingValues()
  private val surfaceAreaTracker = SurfaceAreaTracker { surfaceGestureCallback = it }

  /** Back on the route preview cancels it, like Google Maps. */
  private val cancelPreviewOnBack =
      object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = viewModel.cancelPreview()
      }

  init {
    navigationManagerBridge.start(scope)
    observeJob =
        viewModel.navigationUiState
            .onEach {
              uiState = it
              invalidate()
            }
            .launchIn(scope)
    themeStore.theme.onEach { invalidate() }.launchIn(scope)
    themeStore.carGameHud.onEach { invalidate() }.launchIn(scope)
    // The phone's route preview shows on the car too, with Start and Cancel.
    viewModel.planner.onEach { invalidate() }.launchIn(scope)
    carContext.onBackPressedDispatcher.addCallback(this, cancelPreviewOnBack)

    initialDestination?.location?.let {
      viewModel.startNavigation(
          uniffi.ferrostar.GeographicCoordinate(it.latitude, it.longitude),
          initialDestination.displayName,
      )
    }

    // The themed preview card's Start and Cancel, pressed through map taps (a little slack around them).
    passTapsTo { x, y ->
      val hit = { r: androidx.compose.ui.geometry.Rect? -> r?.inflate(12f)?.contains(Offset(x, y)) == true }
      when {
        viewModel.planner.value.preview == null -> {}
        hit(CarTapTargets.start) -> viewModel.startPreview()
        hit(CarTapTargets.cancel) -> viewModel.cancelPreview()
      }
    }

    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
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
    val theme by themeStore.theme.collectAsState()
    val surfaceArea by screenSurfaceState(surfaceAreaTracker)
    val normalPadding = surfaceStableFractionalPadding(surfaceArea?.compositeArea)
    // Keep the arrow in the lower part of the visible map while driving.
    val trackingPadding = surfaceStableFractionalPadding(surfaceArea?.compositeArea, top = 0.45f)
    val cameraOptions =
        navigationCameraOptions()
            .copy(browsingPadding = normalPadding, navigationPadding = trackingPadding, navigationZoom = 16.4)
    val state = rememberOverworldMapState(cameraOptions)
    mapState = state

    // Route overviews fit the part of the map nothing covers: inside Android Auto's visible area
    // (which leaves out its own cards) and, while driving with the game HUD, right of our cards.
    val density = LocalDensity.current
    val surfaceSize = LocalWindowInfo.current.containerSize
    val gameHud by themeStore.carGameHud.collectAsState()
    val planner by viewModel.planner.collectAsState()
    val hudStart = if (gameHud && (uiState?.isNavigating() == true || planner.preview != null)) 440.dp else 0.dp
    val padding =
        surfaceArea?.visibleArea?.let { area ->
          with(density) {
            PaddingValues(
                start = area.left.toDp() + 48.dp + hudStart,
                top = area.top.toDp() + 48.dp,
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

    LaunchedEffect(uiState?.isNavigating(), state) {
      if (uiState?.isNavigating() == true) {
        // Following keeps the current zoom, which after the route preview is the whole city.
        state.cameraState.position = state.cameraState.position.copy(zoom = cameraOptions.navigationZoom)
        state.recenter(isNavigating = true)
      }
    }
    LaunchedEffect(state) { snapshotFlow { state.cameraMode }.collectLatest { invalidate() } }

    OverworldCarMap(
        theme = theme,
        viewModel = viewModel,
        mapState = state,
        cameraOptions = cameraOptions,
        surfaceAreaTracker = surfaceAreaTracker,
    )
  }

  override fun onGetTemplate(): Template {
    val theme = themeStore.theme.value
    val state = uiState
    val tripState = state?.tripState
    if (state != null && state.isNavigating() && tripState != null) {
      val card = theme.carCard.toArgb()
      return NavigationTemplate.Builder()
          .setBackgroundColor(CarColor.createCustom(card, card))
          .apply {
            // Game HUD mode draws the turn and ETA on the map surface instead of these cards.
            // Turn data still reaches the car's own cluster display through NavigationManager.
            if (!themeStore.carGameHud.value) {
              metricRoutingInfo(carContext, tripState)?.let { setNavigationInfo(it) }
              val timeColor = if (theme.skin == Skin.RDR) CarColor.YELLOW else CarColor.GREEN
              tripState.progress()?.let { setDestinationTravelEstimate(it.toMetricTravelEstimate(timeColor)) }
            }
          }
          .setActionStrip(
              ActionStrip.Builder()
                  .addAction(
                      Action.Builder()
                          .setTitle(carContext.getString(R.string.end_trip))
                          .setOnClickListener { viewModel.stopNavigation() }
                          .build()
                  )
                  .build()
          )
          .setMapActionStrip(
              ActionStrip.Builder()
                  .addAction(
                      Action.Builder()
                          .setIcon(icons.mute(state.isMuted == true))
                          .setOnClickListener { viewModel.toggleMute() }
                          .build()
                  )
                  .addAction(Action.Builder().setIcon(icons.add).setOnClickListener { mapState?.zoomIn() }.build())
                  .addAction(Action.Builder().setIcon(icons.remove).setOnClickListener { mapState?.zoomOut() }.build())
                  .addAction(
                      Action.Builder()
                          .setIcon(icons.camera(mapState?.isTrackingUser != false))
                          .setOnClickListener { toggleOverview() }
                          .build()
                  )
                  .build()
          )
          .build()
    }

    // Not navigating: the themed map with Saved and Theme. In game-HUD mode the "Where to?" card is
    // drawn on the map in the theme; otherwise Android Auto shows it as its own message card.
    val preview = viewModel.planner.value.preview
    cancelPreviewOnBack.isEnabled = preview != null
    if (preview != null) return previewTemplate()
    val idleActions =
        ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.saved))
                    .setOnClickListener { screenManager.push(SavedPlacesScreen(carContext)) }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.next_theme))
                    .setOnClickListener { themeStore.cycle() }
                    .build()
            )
            .build()
    return NavigationTemplate.Builder()
        .apply {
          if (!themeStore.carGameHud.value) {
            setNavigationInfo(MessageInfo.Builder(theme.name).setText(carContext.getString(R.string.car_idle_hint)).build())
          }
        }
        .setActionStrip(idleActions)
        .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
        .build()
  }

  /**
   * The route preview: our themed card is drawn on the map (its Start and Cancel are pressed through
   * map taps); Start and Cancel are in Android Auto's strip too.
   */
  private fun previewTemplate(): Template =
      NavigationTemplate.Builder()
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

  private fun toggleOverview() {
    val state = mapState ?: return
    if (state.isTrackingUser) {
      viewModel.navigationUiState.value.routeGeometry?.boundingBox()?.let {
        state.showRouteOverview(boundingBox = it, paddingValues = overviewPadding)
      }
    } else {
      state.recenter(isNavigating = uiState?.isNavigating() == true)
    }
    invalidate()
  }
}
