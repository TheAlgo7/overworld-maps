package com.thealgothrim.overworld

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.car.app.intent.NavigationDestination
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.core.isNavigating
import com.stadiamaps.ferrostar.core.location.toUserLocation
import com.thealgothrim.overworld.search.Nearby
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.traffic.angleBetween
import com.thealgothrim.overworld.traffic.bearing
import com.thealgothrim.overworld.traffic.metres
import com.thealgothrim.overworld.traffic.RoadFeature
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import com.thealgothrim.overworld.traffic.RouteLine
import com.thealgothrim.overworld.traffic.TrafficEta
import com.thealgothrim.overworld.traffic.TrafficSpan
import java.time.Instant
import kotlin.math.roundToInt
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** Stretches of the route in traffic (metres along the whole route), drawn on the route line. */
    val trafficSpans: List<TrafficSpan> = emptyList(),
) {
  val lightsOnRoute: Int
    get() = signals.count { it.kind == RoadFeatureKind.TRAFFIC_LIGHT && it.along != null }

  /**
   * Incidents on the route itself. Ordinary jams are left to the traffic colours on the roads;
   * only stationary traffic (TomTom's "major" delay) gets an icon and an alert.
   */
  val incidentsOnRoute: List<RoadFeature>
    get() = incidents.filter { it.along != null && (it.kind != RoadFeatureKind.JAM || it.magnitude >= 3) }

  /** What gets an icon on the map and a count in the preview: jams are shown on the route line instead. */
  val markedIncidents: List<RoadFeature>
    get() = incidentsOnRoute.filter { it.kind != RoadFeatureKind.JAM }
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
    /** Set when [results] are the nearest places of a kind (closest first) rather than a search. */
    val nearby: Nearby? = null,
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
  /** Map screens in view right now (the phone app, the car screen). */
  private val watchers = MutableStateFlow(0)

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

  private val _estimatedKmh = MutableStateFlow<Int?>(null)
  /**
   * Speed worked out from movement when the location has none (a test drive's simulated trip),
   * so the speed and limit warning still show. Real GPS fixes carry their own speed.
   */
  val estimatedKmh: StateFlow<Int?> = _estimatedKmh.asStateFlow()

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
      // A fix a second while a map is on screen (the arrow glides between them; see
      // map/SmoothLocation.kt). Every 3 s made free driving jump, and running with no screen open
      // only spent battery. During a trip Ferrostar asks for its own fixes.
      combine(hasLocationPermission, watchers) { granted, screens -> granted && screens > 0 }
          .distinctUntilChanged()
          .flatMapLatest { on ->
            if (on) locationProvider.locationUpdates(1000L).map { it.toUserLocation() }
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
      var last: Pair<GeographicCoordinate, Long>? = null
      var smooth: Double? = null
      navigationUiState.collect { state ->
        val location = state.location
        if (location == null || location.speed != null || !state.isNavigating()) {
          _estimatedKmh.value = null
          last = null
          smooth = null
          return@collect
        }
        val now = System.currentTimeMillis()
        val prev = last
        if (prev == null) {
          last = location.coordinates to now
          return@collect
        }
        val seconds = (now - prev.second) / 1000.0
        if (seconds < 0.8) return@collect
        val kmh = metres(prev.first, location.coordinates) / seconds * 3.6
        last = location.coordinates to now
        if (kmh > 250) return@collect
        smooth = smooth?.let { it * 0.6 + kmh * 0.4 } ?: kmh
        _estimatedKmh.value = smooth?.roundToInt()
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
        // Ferrostar reports no route as an empty list, not null. Without this the last trip's
        // lights and incidents stayed on the map, and its live traffic was re-fetched every
        // 150 s for as long as the app lived.
        if (geometry.isNullOrEmpty() && _planner.value.preview == null) {
          extrasJob?.cancel()
          extrasRoute = null
          extrasLive = false
          _extras.value = RouteExtras()
        }
      }
    }
    viewModelScope.launch {
      // Ferrostar applies GPS fixes on a background thread. A fix being applied at the moment the
      // trip is ended writes "navigating" back over the stop, with no route left: the trip came back
      // after End. A trip always has a route, so a navigating state without one is that leftover.
      core.state.collect { state ->
        if (state.tripState is TripState.Navigating && state.routeGeometry.isEmpty()) core.stopNavigation()
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

  /** A map screen (phone or car) came into view, or left it. GPS runs while at least one is up. */
  fun mapInView(visible: Boolean) {
    watchers.update { (it + if (visible) 1 else -1).coerceAtLeast(0) }
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
    _planner.value = _planner.value.copy(query = query, error = null, nearby = null)
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

  /** The nearest petrol pumps, toilets, parking and so on, closest first, in the search list. */
  fun searchNearby(kind: Nearby) {
    searchJob?.cancel()
    _planner.value = _planner.value.copy(query = kind.label, nearby = kind, results = emptyList(), searching = true, error = null)
    val here = currentCoordinate ?: startPoint
    searchJob =
        viewModelScope.launch {
          val found =
              runCatching { AppModule.search.nearby(kind, here) }
                  .onFailure { Log.w(TAG, "nearby ${kind.name} failed", it) }
                  .getOrDefault(emptyList())
          _planner.value = _planner.value.copy(results = found, searching = false)
        }
  }

  /**
   * A place shared into the app (Google Maps' Share button, a geo: link, an address in a message):
   * shown on the map with Directions, like a search result. Covers places only Google knows.
   */
  fun openShared(text: String) {
    _planner.value = PlannerState(searching = true)
    viewModelScope.launch {
      val place =
          runCatching { AppModule.search.fromShared(text, currentCoordinate) }
              .onFailure { Log.w(TAG, "shared place failed", it) }
              .getOrNull()
      if (place != null) choose(place)
      else _planner.value = PlannerState(error = "Couldn't read a place from that. Try sharing it again, or search for it.")
    }
  }

  /** A geo: or google.navigation: link opened on the phone: the place, ready for Directions. */
  fun openDestination(destination: NavigationDestination) {
    val lat = destination.latitude
    val lng = destination.longitude
    if (lat != null && lng != null) choose(Place(destination.query ?: "Pinned point", "", GeographicCoordinate(lat, lng)))
    else destination.query?.let { openShared(it) }
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
        _planner.value = _planner.value.copy(routing = false, error = routeError(e))
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
          val line = RouteLine(route)
          while (true) {
            // While driving, ask about the road still ahead so the time counts from here.
            val left = navigationUiState.value.progress?.distanceRemaining?.takeIf { live } ?: line.length
            val done = (line.length - left).coerceAtLeast(0.0)
            val ahead = if (done > 50.0) line.slice(done, line.length) else route
            val incidents = runCatching { traffic.incidents(route) }.getOrDefault(emptyList())
            val fresh = runCatching { traffic.routeTraffic(ahead) }.getOrNull()
            _extras.update { e ->
              e.copy(
                  incidents = incidents,
                  eta = fresh?.eta ?: e.eta,
                  etaAtDistance = left,
                  trafficSpans = fresh?.spans?.map { it.copy(from = it.from + done, to = it.to + done) } ?: e.trafficSpans,
              )
            }
            if (!live || !traffic.hasLiveTraffic) break
            delay(150_000)
          }
        }
  }

  private fun routeLength(route: List<GeographicCoordinate>) = RouteLine(route).length

  /**
   * Time to go: TomTom's live-traffic time when there is one (scaled down as distance is covered),
   * otherwise the routing engine's estimate.
   *
   * The arrival time eases toward each new estimate over about 20 s. It used to jump whenever live
   * traffic refreshed (every 150 s) or a source took over from the other. A new route (a trip, a
   * reroute) still shows at once.
   */
  fun remainingSeconds(state: NavigationUiState, extras: RouteExtras): Double? {
    val target = estimateSeconds(state, extras)
    val route = state.routeGeometry
    if (target == null || route == null) {
      shownArrival = null
      return target
    }
    val now = System.currentTimeMillis()
    val targetArrival = now + (target * 1000).toLong()
    val previous = shownArrival
    val arrival =
        if (previous == null || route !== shownFor) targetArrival
        else {
          val k = 1 - kotlin.math.exp(-((now - shownAt).coerceIn(0L, 5_000L) / 1000.0) / ETA_EASE_SECONDS)
          previous + ((targetArrival - previous) * k).toLong()
        }
    shownArrival = arrival
    shownAt = now
    shownFor = route
    return ((arrival - now) / 1000.0).coerceAtLeast(0.0)
  }

  private var shownArrival: Long? = null
  private var shownAt = 0L
  private var shownFor: List<GeographicCoordinate>? = null

  private fun estimateSeconds(state: NavigationUiState, extras: RouteExtras): Double? {
    val progress = state.progress ?: return null
    val eta = extras.eta ?: return progress.durationRemaining
    if (extras.etaAtDistance <= 0) return progress.durationRemaining
    return eta.travelSeconds * (progress.distanceRemaining / extras.etaAtDistance).coerceIn(0.0, 1.5)
  }

  /**
   * On a trip: the nearest incident or camera within 2 km ahead on the route (OpenStreetMap's
   * cameras and the ones marked by hand). With no trip: the nearest known camera within 600 m in
   * the direction the car is heading, so free driving gets camera alerts too.
   */
  fun hazardAhead(state: NavigationUiState, extras: RouteExtras): HazardAhead? {
    val progress = state.progress ?: return freeDriveCamera(state)
    val geometry = state.routeGeometry ?: return null
    val line = routeLine(geometry)
    val done = line.length - progress.distanceRemaining
    val marked =
        AppModule.cameras.all.value.mapNotNull { c ->
          val (along, off) = line.project(c.at)
          if (off <= 30.0) RoadFeature(RoadFeatureKind.SPEED_CAMERA, c.at, along = along) else null
        }
    return (extras.incidentsOnRoute + extras.signals.filter { it.kind == RoadFeatureKind.SPEED_CAMERA } + marked)
        .mapNotNull { f -> f.along?.let { a -> HazardAhead(f, a - done) } }
        .filter { it.distance in 0.0..2_000.0 }
        .minByOrNull { it.distance }
  }

  private var cachedLine: Pair<List<GeographicCoordinate>, RouteLine>? = null

  private fun routeLine(geometry: List<GeographicCoordinate>): RouteLine =
      cachedLine?.takeIf { it.first === geometry }?.second ?: RouteLine(geometry).also { cachedLine = geometry to it }

  private val _camerasAround = MutableStateFlow<List<GeographicCoordinate>>(emptyList())
  private var camerasAroundFrom: GeographicCoordinate? = null
  private var camerasJob: Job? = null

  /** Cameras near the car with no trip running, reloaded after every 2 km or so. */
  private fun refreshCamerasAround(at: GeographicCoordinate) {
    val last = camerasAroundFrom
    if ((last != null && metres(last, at) < 2_000.0) || camerasJob?.isActive == true) return
    camerasAroundFrom = at
    camerasJob = viewModelScope.launch {
      runCatching { traffic.camerasAround(at, 4_000.0) }.getOrNull()?.let { _camerasAround.value = it }
    }
  }

  private fun freeDriveCamera(state: NavigationUiState): HazardAhead? {
    val here = state.location ?: return null
    val heading = here.courseOverGround?.degrees?.toDouble() ?: return null
    refreshCamerasAround(here.coordinates)
    val marked = AppModule.cameras.all.value.filter { c -> c.heading == null || angleBetween(c.heading, heading) < 50.0 }.map { it.at }
    return (_camerasAround.value + marked)
        .map { it to metres(here.coordinates, it) }
        .filter { (at, d) -> d <= 600.0 && angleBetween(bearing(here.coordinates, at), heading) < 35.0 }
        .minByOrNull { it.second }
        ?.let { (at, d) -> HazardAhead(RoadFeature(RoadFeatureKind.SPEED_CAMERA, at), d) }
  }

  private val _cameraMarkedAt = MutableStateFlow(0L)
  /** When the Camera button last saved a camera (the HUD says so for a few seconds). */
  val cameraMarkedAt: StateFlow<Long> = _cameraMarkedAt.asStateFlow()

  /**
   * Marks a camera where the car is (the Camera button). The button is pressed a moment after
   * passing the camera, so the mark goes back two seconds of driving (at most 60 m).
   */
  fun markCamera() {
    val here = navigationUiState.value.location ?: return
    val heading = here.courseOverGround?.degrees?.toDouble()
    val back = ((here.speed?.value ?: 0.0) * 2.0).coerceAtMost(60.0)
    val at =
        if (heading == null || back < 1.0) here.coordinates
        else {
          val rad = Math.toRadians(heading)
          GeographicCoordinate(
              here.coordinates.lat - back * kotlin.math.cos(rad) / 111_320.0,
              here.coordinates.lng - back * kotlin.math.sin(rad) / (111_320.0 * kotlin.math.cos(Math.toRadians(here.coordinates.lat))),
          )
        }
    AppModule.cameras.mark(at, heading)
    _cameraMarkedAt.value = System.currentTimeMillis()
  }

  private val _cameraBeep = MutableStateFlow(prefs.getBoolean("camera_beep", false))
  /** A short beep as a camera comes within 500 m, like Radarbot (off unless turned on). */
  val cameraBeep: StateFlow<Boolean> = _cameraBeep.asStateFlow()

  fun setCameraBeep(on: Boolean) {
    _cameraBeep.value = on
    prefs.edit().putBoolean("camera_beep", on).apply()
  }

  private fun beep() {
    runCatching {
      val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90)
      tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 350)
      viewModelScope.launch {
        delay(600)
        tone.release()
      }
    }
  }

  // Here, after the camera state above: init blocks run in order, and a collector started on the
  // main thread runs its first round at once.
  init {
    viewModelScope.launch {
      // One beep per camera as it comes within 500 m, when the beep is on.
      val beeped = ArrayDeque<GeographicCoordinate>()
      navigationUiState.collect { state ->
        if (!_cameraBeep.value) return@collect
        val hazard = hazardAhead(state, _extras.value) ?: return@collect
        if (hazard.feature.kind != RoadFeatureKind.SPEED_CAMERA || hazard.distance > 500.0) return@collect
        if (beeped.any { metres(it, hazard.feature.at) < 50.0 }) return@collect
        beeped.addLast(hazard.feature.at)
        if (beeped.size > 20) beeped.removeFirst()
        beep()
      }
    }
  }

  /** Start: drive the previewed route. */
  fun startPreview() {
    val preview = _planner.value.preview ?: return
    begin(preview.route, preview.place)
    _planner.value = PlannerState()
  }

  /** Main thread only, like everything else that starts or stops Ferrostar. */
  private fun begin(given: Route, place: Place) {
    val route = given.safeForCar()
    // Set both ways: a test drive ended from the notification's Stop never turned the simulator
    // off, and the next real trip would then have been driven by it.
    if (_testDrive.value) locationProvider.enableSimulationOn(route) else locationProvider.disableSimulation()
    setDestination(place.name)
    destinationName = place.name
    _area.value = null
    AppModule.saved.addRecent(place)
    // The core's own state, not the UI's copy, which reaches the main thread a moment later.
    if (core.state.value.isNavigating()) core.replaceRoute(route = route) else core.startNavigation(route = route)
  }

  /**
   * Straight to driving, without a preview (used by the car and by saved-place shortcuts there).
   * [from] replaces the phone's position as the start (debug test drives).
   */
  fun startNavigation(destination: GeographicCoordinate, name: String?, from: GeographicCoordinate? = null) {
    _planner.value = _planner.value.copy(routing = true, error = null)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val route =
            core
                .getRoutes(
                    from?.let { UserLocation(it, 6.0, null, Instant.now(), null) } ?: origin,
                    listOf(Waypoint(coordinate = destination, kind = WaypointKind.BREAK)),
                )
                .first()
        withContext(Dispatchers.Main) {
          begin(route, Place(name ?: "Destination", "", destination))
          _planner.value = PlannerState()
        }
      } catch (e: Exception) {
        Log.w(TAG, "routing failed", e)
        _planner.value =
            _planner.value.copy(routing = false, error = routeError(e))
      }
    }
  }

  /**
   * A place asked for by name from outside the app, like "Hey Google, navigate to India Gate" in
   * Android Auto: the best search match near here, then straight to driving.
   */
  fun navigateToQuery(query: String) {
    viewModelScope.launch {
      val place =
          runCatching { AppModule.search.search(query, currentCoordinate).firstOrNull() }
              .onFailure { Log.w(TAG, "search for \"$query\" failed", it) }
              .getOrNull()
      if (place == null) {
        _planner.value = _planner.value.copy(error = "Couldn't find \"$query\".")
        return@launch
      }
      startNavigation(place.coordinate, place.name)
    }
  }

  override fun stopNavigation() {
    locationProvider.disableSimulation()
    core.stopNavigation()
    setDestination(null)
  }

  /** What went wrong with a route request, in words: the routing server refusing isn't the connection. */
  private fun routeError(e: Exception): String =
      when ((e as? InvalidStatusCodeException)?.statusCode) {
        400 -> "No drivable route to there."
        429 -> "The route server is busy. Try again in a minute."
        else -> "Couldn't find a route. Check the connection and try again."
      }

  companion object {
    private const val TAG = "OverworldViewModel"
    /** How quickly a new arrival estimate takes over (a time constant, in seconds). */
    private const val ETA_EASE_SECONDS = 20.0
  }
}
