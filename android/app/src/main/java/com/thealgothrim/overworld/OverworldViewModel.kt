package com.thealgothrim.overworld

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.location.toUserLocation
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.traffic.metres
import com.thealgothrim.overworld.traffic.RoadFeature
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import com.thealgothrim.overworld.traffic.RouteLine
import com.thealgothrim.overworld.traffic.TrafficEta
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.TripState
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

/** Road data for the current route: lights and cameras, live incidents, live travel time. */
data class RouteExtras(
    val signals: List<RoadFeature> = emptyList(),
    val incidents: List<RoadFeature> = emptyList(),
    val eta: TrafficEta? = null,
    /** Distance left when [eta] was fetched, to scale it down as the trip goes on. */
    val etaAtDistance: Double = 0.0,
) {
  val lightsOnRoute: Int
    get() = signals.count { it.kind == RoadFeatureKind.TRAFFIC_LIGHT && it.along != null }

  val incidentsOnRoute: List<RoadFeature>
    get() = incidents.filter { it.along != null }
}

/** The next incident or camera ahead on the route, for the "Accident ahead" alert. */
data class HazardAhead(val feature: RoadFeature, val distance: Double)

/** A route shown before driving, like Google Maps' directions preview. */
data class RoutePreview(
    val place: Place,
    val route: Route,
    val durationSeconds: Double,
    val distanceMeters: Double,
    /** The road the route spends longest on, for "via ...". */
    val via: String?,
)

/** What the phone shows around the map when not navigating. */
data class PlannerState(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val destination: Place? = null,
    val routing: Boolean = false,
    val error: String? = null,
    val preview: RoutePreview? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class OverworldViewModel :
    DefaultNavigationViewModel(
        AppModule.ferrostarCore,
        valhallaExtendedOSRMAnnotationPublisher(),
    ) {
  private val core = AppModule.ferrostarCore
  private val locationProvider = AppModule.locationProvider

  private val hasLocationPermission = MutableStateFlow(false)
  private val lastLocation = MutableStateFlow<UserLocation?>(null)

  private val _testDrive = MutableStateFlow(false)
  /** When on, the next trip is driven by the simulator instead of GPS. */
  val testDrive: StateFlow<Boolean> = _testDrive.asStateFlow()

  private val _planner = MutableStateFlow(PlannerState())
  val planner: StateFlow<PlannerState> = _planner.asStateFlow()

  private var searchJob: Job? = null

  private val _area = MutableStateFlow<String?>(null)
  /** Neighbourhood around the driver, refreshed about every 45 s while navigating. */
  val area: StateFlow<String?> = _area.asStateFlow()

  private val _arrived = MutableSharedFlow<String>(extraBufferCapacity = 1)
  /** Emits the destination name once when a trip reaches its end (not when the driver ends it). */
  val arrived: SharedFlow<String> = _arrived.asSharedFlow()

  private var destinationName: String? = null

  private val traffic = AppModule.traffic
  private val _extras = MutableStateFlow(RouteExtras())
  /** Signals, incidents and live travel time for the previewed or active route. */
  val extras: StateFlow<RouteExtras> = _extras.asStateFlow()
  private var extrasJob: Job? = null
  private var extrasRoute: List<GeographicCoordinate>? = null
  private var extrasLive = false
  private val extrasLoaded = mutableSetOf<RoadFeatureKind>()

  /** Shows the user's position on the map even before a trip starts. */
  override val navigationUiState: StateFlow<NavigationUiState> =
      combine(super.navigationUiState, lastLocation) { state, location ->
            if (state.isNavigating()) state else state.copy(location = location)
          }
          .stateIn(viewModelScope, SharingStarted.Eagerly, NavigationUiState.empty())

  init {
    // The car can start before the phone screen ever asks, so read the current grant directly.
    refreshLocationPermission()
    viewModelScope.launch {
      hasLocationPermission
          .flatMapLatest { granted ->
            if (granted) locationProvider.locationUpdates(3000L).map { it.toUserLocation() }
            else flowOf(null)
          }
          .collect {
            if (it != null) {
              lastLocation.value = it
              rememberFix(it.coordinates)
            }
          }
    }
    viewModelScope.launch {
      var lastLookup = 0L
      navigationUiState.collect { state ->
        val at = state.location?.coordinates ?: return@collect
        val now = System.currentTimeMillis()
        if (state.isNavigating() && now - lastLookup > 45_000) {
          lastLookup = now
          launch { runCatching { AppModule.search.areaAt(at) }.getOrNull()?.let { _area.value = it } }
        }
      }
    }
    viewModelScope.launch {
      navigationUiState.map { it.routeGeometry }.collect { geometry ->
        if (geometry != null && geometry.size >= 2 && (geometry != extrasRoute || !extrasLive)) loadExtras(geometry, live = true)
        if (geometry == null && _planner.value.preview == null) {
          extrasJob?.cancel()
          extrasRoute = null
          extrasLive = false
          _extras.value = RouteExtras()
        }
      }
    }
    viewModelScope.launch {
      var announced = false
      navigationUiState.map { it.tripState }.collect { trip ->
        when (trip) {
          is TripState.Complete ->
              if (!announced) {
                announced = true
                _arrived.tryEmit(destinationName ?: "your destination")
                stopNavigation()
              }
          is TripState.Navigating -> announced = false
          else -> Unit
        }
      }
    }
  }

  fun setLocationPermission(granted: Boolean) {
    hasLocationPermission.value = granted
  }

  fun refreshLocationPermission() {
    val context = AppModule.context
    hasLocationPermission.value =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
  }

  fun setTestDrive(on: Boolean) {
    _testDrive.value = on
  }

  fun enableAutoDrive() {
    _testDrive.value = true
  }

  val currentCoordinate: GeographicCoordinate?
    get() = navigationUiState.value.location?.coordinates

  fun onQueryChange(query: String) {
    _planner.value = _planner.value.copy(query = query, error = null)
    searchJob?.cancel()
    if (query.isBlank()) {
      _planner.value = _planner.value.copy(results = emptyList(), searching = false)
      return
    }
    searchJob =
        viewModelScope.launch {
          delay(350)
          _planner.value = _planner.value.copy(searching = true)
          val results =
              runCatching { AppModule.search.search(query, currentCoordinate) }
                  .onFailure { Log.w(TAG, "search failed", it) }
                  .getOrDefault(emptyList())
          _planner.value = _planner.value.copy(results = results, searching = false)
        }
  }

  fun choose(place: Place) {
    searchJob?.cancel()
    _planner.value = PlannerState(destination = place)
  }

  /** Long-press on the map: drop a pin, then name it from Photon if possible. */
  fun dropPin(at: GeographicCoordinate) {
    val pin = Place("Dropped pin", "%.5f, %.5f".format(at.lat, at.lng), at)
    choose(pin)
    viewModelScope.launch {
      val named = runCatching { AppModule.search.reverse(at) }.getOrNull() ?: return@launch
      if (_planner.value.destination?.coordinate == at) {
        _planner.value = _planner.value.copy(destination = named)
      }
    }
  }

  fun clearDestination() {
    _planner.value = PlannerState()
  }

  private val origin: UserLocation
    get() = lastLocation.value ?: UserLocation(AppModule.defaultStart, 6.0, null, Instant.now(), null)

  private val prefs by lazy { AppModule.context.getSharedPreferences("overworld", Context.MODE_PRIVATE) }
  private var savedFix: GeographicCoordinate? = null

  /** Where the map opens: the live fix, else where the phone was last seen, else Chhatarpur. */
  val startPoint: GeographicCoordinate
    get() =
        lastLocation.value?.coordinates
            ?: prefs.getString("last_fix", null)?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 }?.let {
              GeographicCoordinate(it[0], it[1])
            }
            ?: AppModule.defaultStart

  private fun rememberFix(at: GeographicCoordinate) {
    val last = savedFix
    if (last != null && metres(last, at) < 250.0) return
    savedFix = at
    prefs.edit().putString("last_fix", "${at.lat},${at.lng}").apply()
  }

  /** Directions: fetch the route and show it with time, distance and the main road. */
  fun directions() {
    val place = _planner.value.destination ?: return
    _planner.value = _planner.value.copy(routing = true, error = null)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val route =
            core.getRoutes(origin, listOf(Waypoint(coordinate = place.coordinate, kind = WaypointKind.BREAK))).first()
        val via =
            route.steps
                .filter { !it.roadName.isNullOrBlank() }
                .groupBy { it.roadName!! }
                .maxByOrNull { (_, steps) -> steps.sumOf { it.distance } }
                ?.key
        val preview = RoutePreview(place, route, route.steps.sumOf { it.duration }, route.distance, via)
        _planner.value = _planner.value.copy(routing = false, preview = preview)
        launch(Dispatchers.Main) { loadExtras(route.geometry, live = false) }
      } catch (e: Exception) {
        Log.w(TAG, "routing failed", e)
        _planner.value = _planner.value.copy(routing = false, error = "Couldn't find a route. Check the connection and try again.")
      }
    }
  }

  /** Voice guidance on or off, remembered for next time (it starts off). */
  override fun toggleMute() {
    super.toggleMute()
    prefs.edit().putBoolean("voice", !AppModule.ttsObserver.isMuted).apply()
  }

  fun cancelPreview() {
    _planner.value = _planner.value.copy(preview = null)
    if (!navigationUiState.value.isNavigating()) {
      extrasJob?.cancel()
      extrasRoute = null
      extrasLive = false
      _extras.value = RouteExtras()
    }
  }

  /**
   * Loads lights and cameras once per route, and incidents plus live travel time now and, while
   * driving, every 150 seconds from the current position.
   */
  private fun loadExtras(route: List<GeographicCoordinate>, live: Boolean) {
    extrasJob?.cancel()
    val sameRoute = route == extrasRoute
    extrasRoute = route
    extrasLive = live
    if (!sameRoute) {
      _extras.value = RouteExtras()
      extrasLoaded.clear()
    }
    val destination = route.last()
    extrasJob =
        viewModelScope.launch {
          // Lights come from the routing server in about a second; cameras from Overpass, which can
          // be slow or busy, so each arrives on its own.
          for ((kind, fetch) in listOf(RoadFeatureKind.TRAFFIC_LIGHT to traffic::trafficLights, RoadFeatureKind.SPEED_CAMERA to traffic::speedCameras)) {
            if (kind in extrasLoaded) continue
            launch {
              val found = runCatching { fetch(route) }.getOrNull() ?: return@launch
              extrasLoaded += kind
              _extras.update { e -> e.copy(signals = (e.signals.filter { it.kind != kind } + found).sortedBy { it.along ?: Double.MAX_VALUE }) }
            }
          }
          while (true) {
            val from = navigationUiState.value.location?.coordinates ?: route.first()
            val incidents = runCatching { traffic.incidents(route) }.getOrDefault(emptyList())
            val eta = runCatching { traffic.eta(from, destination) }.getOrNull()
            val left = navigationUiState.value.progress?.distanceRemaining ?: routeLength(route)
            _extras.value = _extras.value.copy(incidents = incidents, eta = eta ?: _extras.value.eta, etaAtDistance = left)
            if (!live || !traffic.hasLiveTraffic) break
            delay(150_000)
          }
        }
  }

  private fun routeLength(route: List<GeographicCoordinate>) = RouteLine(route).length

  /**
   * Time to go: TomTom's live-traffic time when there is one (scaled down as distance is covered),
   * otherwise the routing engine's estimate.
   */
  fun remainingSeconds(state: NavigationUiState, extras: RouteExtras): Double? {
    val progress = state.progress ?: return null
    val eta = extras.eta ?: return progress.durationRemaining
    if (extras.etaAtDistance <= 0) return progress.durationRemaining
    return eta.travelSeconds * (progress.distanceRemaining / extras.etaAtDistance).coerceIn(0.0, 1.5)
  }

  /** The nearest incident or speed camera within 2 km ahead on the route. */
  fun hazardAhead(state: NavigationUiState, extras: RouteExtras): HazardAhead? {
    val progress = state.progress ?: return null
    val length = state.routeGeometry?.let { routeLength(it) } ?: return null
    val done = length - progress.distanceRemaining
    return (extras.incidentsOnRoute + extras.signals.filter { it.kind == RoadFeatureKind.SPEED_CAMERA })
        .mapNotNull { f -> f.along?.let { a -> HazardAhead(f, a - done) } }
        .filter { it.distance in 0.0..2_000.0 }
        .minByOrNull { it.distance }
  }

  /** Start: drive the previewed route. */
  fun startPreview() {
    val preview = _planner.value.preview ?: return
    begin(preview.route, preview.place)
    _planner.value = PlannerState()
  }

  private fun begin(route: Route, place: Place) {
    if (_testDrive.value) locationProvider.enableSimulationOn(route)
    setDestination(place.name)
    destinationName = place.name
    _area.value = null
    AppModule.saved.addRecent(place)
    if (navigationUiState.value.isNavigating()) core.replaceRoute(route = route) else core.startNavigation(route = route)
  }

  /** Straight to driving, without a preview (used by the car and by saved-place shortcuts there). */
  fun startNavigation(destination: GeographicCoordinate, name: String?) {
    _planner.value = _planner.value.copy(routing = true, error = null)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val route =
            core.getRoutes(origin, listOf(Waypoint(coordinate = destination, kind = WaypointKind.BREAK))).first()
        begin(route, Place(name ?: "Destination", "", destination))
        _planner.value = PlannerState()
      } catch (e: Exception) {
        Log.w(TAG, "routing failed", e)
        _planner.value =
            _planner.value.copy(routing = false, error = "Couldn't find a route. Check the connection and try again.")
      }
    }
  }

  override fun stopNavigation() {
    locationProvider.disableSimulation()
    core.stopNavigation()
    setDestination(null)
  }

  companion object {
    private const val TAG = "OverworldViewModel"
  }
}
