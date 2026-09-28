package com.thealgothrim.overworld

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
import kotlinx.coroutines.launch
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.TripState
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

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
          .collect { if (it != null) lastLocation.value = it }
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
      } catch (e: Exception) {
        Log.w(TAG, "routing failed", e)
        _planner.value = _planner.value.copy(routing = false, error = "Couldn't find a route. Check the connection and try again.")
      }
    }
  }

  fun cancelPreview() {
    _planner.value = _planner.value.copy(preview = null)
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
