package com.thealgothrim.overworld

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
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
import com.thealgothrim.overworld.notify.Notices
import com.thealgothrim.overworld.routing.CurrentLevels
import com.thealgothrim.overworld.routing.LevelCue
import com.thealgothrim.overworld.routing.RoadLevels
import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.search.Nearby
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.samePlace
import com.thealgothrim.overworld.timeline.Fix
import com.thealgothrim.overworld.timeline.TimelineRecorder
import kotlinx.coroutines.async
import com.thealgothrim.overworld.theme.Daylight
import com.thealgothrim.overworld.theme.MapTime
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.RADAR
import com.thealgothrim.overworld.traffic.CameraChime
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    /** Stretches of the route on a flyover (metres along it): drawn over the bridge, the rest under. */
    val bridges: List<ClosedFloatingPointRange<Double>> = emptyList(),
    /** The route's flyover and underpass choices and the roads beside it (see RoadLevels). */
    val levels: RoadLevels? = null,
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

/**
 * The next flyover or underpass choice ahead ([cue]), [distance] metres away. [withTurn]: it's at
 * the route's next turn, so the card shows both ("Bear left" and "Don't take the flyover").
 */
data class CueAhead(val cue: LevelCue, val distance: Double, val withTurn: Boolean)

/** A route shown before driving, like Google Maps' directions preview. */
data class RoutePreview(
    val place: Place,
    val route: Route,
    val durationSeconds: Double,
    val distanceMeters: Double,
    /** The road the route spends longest on, for "via ...". */
    val via: String?,
    val mode: TravelMode = TravelMode.CAR,
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
    /** The mode the preview shows (its tab is lit). */
    val mode: TravelMode = TravelMode.CAR,
    /** Each mode's time for the previewed place, as the tabs show it: null while asking, NaN when there's no route. */
    val modeTimes: Map<TravelMode, Double> = emptyMap(),
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
  /** Whether location is allowed; the car's location screen closes itself when it turns true. */
  val locationAllowed: StateFlow<Boolean> = hasLocationPermission.asStateFlow()
  private val lastLocation = MutableStateFlow<UserLocation?>(null)
  /** Which way the last fixes moved, for when GPS gives no course of its own. */
  @Volatile private var movementHeading: Double? = null
  /** Fixes thrown out in a row by [plausible]. */
  private var rejected = 0
  /** Map screens in view right now (the phone app, the car screen). */
  private val watchers = MutableStateFlow(0)

  private val _testDrive = MutableStateFlow(false)
  /** When on, the next trip is driven by the simulator instead of GPS. */
  val testDrive: StateFlow<Boolean> = _testDrive.asStateFlow()

  private val _planner = MutableStateFlow(PlannerState())
  val planner: StateFlow<PlannerState> = _planner.asStateFlow()

  private var searchJob: Job? = null

  private val _area = MutableStateFlow<String?>(null)
  /** Neighbourhood around the driver, refreshed about every 45 s while driving, trip or not. */
  val area: StateFlow<String?> = _area.asStateFlow()

  private val _gpsLost = MutableStateFlow(false)
  /**
   * A map is on screen and location is allowed, but no fix has come for [GPS_LOST_MS]: the screens
   * say "Location not found" (like Google Maps) instead of a frozen arrow.
   */
  val gpsLost: StateFlow<Boolean> = _gpsLost.asStateFlow()
  @Volatile private var lastFixAt = SystemClock.elapsedRealtime()

  private val _locationOff = MutableStateFlow(false)
  /**
   * The phone's own location switch is off (not just a weak signal): the phone offers to turn it
   * on, the car says to turn it on when it's safe. Checked with [gpsLost].
   */
  val locationOff: StateFlow<Boolean> = _locationOff.asStateFlow()

  private fun locationSwitchOff(): Boolean =
      runCatching { AppModule.context.getSystemService(android.location.LocationManager::class.java)?.isLocationEnabled == false }.getOrDefault(false)

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
              val previous = lastLocation.value
              if (previous != null && !plausible(it, previous)) {
                rejected++
                // Three in a row is the car really being somewhere else (a tunnel exit, the app
                // coming back after a while): take it.
                if (rejected < 3) return@collect
              }
              rejected = 0
              // Standing at a light, GPS wanders a few metres; at a T-junction that put the arrow
              // on the crossing road. Standing still, the arrow stays where it stopped.
              val fix = if (previous != null && standingStill(it, previous)) it.copy(coordinates = previous.coordinates, courseOverGround = previous.courseOverGround) else it
              lastLocation.value?.let { previous ->
                if (metres(previous.coordinates, fix.coordinates) >= 8.0) movementHeading = bearing(previous.coordinates, fix.coordinates)
              }
              lastLocation.value = fix
              rememberFix(fix.coordinates)
            }
          }
    }
    viewModelScope.launch {
      var last: Pair<GeographicCoordinate, Long>? = null
      var smooth: Double? = null
      navigationUiState.collect { state ->
        val location = state.location
        // With or without a trip: free driving needs the speed too.
        if (location == null || location.speed != null) {
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
      var lookedAt: GeographicCoordinate? = null
      var seen: UserLocation? = null
      navigationUiState.collect { state ->
        val location = state.location ?: return@collect
        if (location !== seen) {
          seen = location
          lastFixAt = SystemClock.elapsedRealtime()
          // A fix means the signal is back, unless the location switch is off (a test drive's
          // made-up fixes still come then): the card stays until location is on again.
          if (_gpsLost.value && !locationSwitchOff()) _gpsLost.value = false
        }
        val at = location.coordinates
        val now = System.currentTimeMillis()
        // The area name shows while free driving too, so look it up whenever the car has moved on.
        val moved = lookedAt?.let { metres(it, at) > 250.0 } ?: true
        if (moved && now - lastLookup > 45_000) {
          lastLookup = now
          lookedAt = at
          launch { runCatching { AppModule.search.areaAt(at) }.getOrNull()?.let { _area.value = it } }
        }
      }
    }
    viewModelScope.launch {
      combine(hasLocationPermission, watchers) { granted, screens -> granted && screens > 0 }
          .distinctUntilChanged()
          .collectLatest { on ->
            if (!on) {
              _gpsLost.value = false
              return@collectLatest
            }
            // A fresh start gets the full wait before it counts as lost.
            lastFixAt = maxOf(lastFixAt, SystemClock.elapsedRealtime())
            while (true) {
              // A test drive's fixes are made up, so the phone's location switch doesn't matter to it:
              // with location off, "No location" sat over the arrow for the whole simulated trip.
              val simulated = _testDrive.value && navigationUiState.value.isNavigating()
              _locationOff.value = !simulated && locationSwitchOff()
              // Location switched off: said at once, not after the wait for a fix.
              _gpsLost.value = _locationOff.value || SystemClock.elapsedRealtime() - lastFixAt > GPS_LOST_MS
              delay(2_000)
            }
          }
    }
    viewModelScope.launch {
      // Each second of a trip as the app showed it, for a problem report (see Trace).
      var seen: UserLocation? = null
      navigationUiState.collect { state ->
        if (!state.isNavigating()) return@collect
        val location = state.location ?: return@collect
        if (location === seen) return@collect
        seen = location
        val extras = _extras.value
        val along = state.progress?.let { p -> (extras.levels?.length ?: state.routeGeometry?.let { routeLine(it).length })?.minus(p.distanceRemaining) }
        val cue = levelCueAhead(state, extras)
        com.thealgothrim.overworld.report.Trace.trip(
            location.coordinates,
            along,
            state.routeDeviation !is uniffi.ferrostar.RouteDeviation.NoDeviation,
            state.progress?.distanceToNextManeuver,
            state.visualInstruction?.primaryContent?.text,
            cue?.let { "${it.cue.kind.text} in ${it.distance.toInt()} m" },
        )
      }
    }
    viewModelScope.launch {
      // The Timeline: every new fix while Overworld runs, trip or not. A test drive's simulated
      // trip isn't a real drive, so it isn't kept.
      var seen: UserLocation? = null
      navigationUiState.collect { state ->
        val location = state.location ?: return@collect
        if (location === seen) return@collect
        seen = location
        val navigating = state.isNavigating()
        if (navigating && _testDrive.value) return@collect
        val car = carDark.value != null
        val phoneMode = AppModule.prefs.mode.value
        AppModule.timeline.fix(
            Fix(location.coordinates.lat, location.coordinates.lng, location.speed?.value, location.horizontalAccuracy, location.timestamp.toEpochMilli()),
            TimelineRecorder.Context(
                tripMode = if (navigating) _tripMode.value else null,
                tripTo = if (navigating) destinationName else null,
                vehicle = if (car || !phoneMode.vehicle) TravelMode.CAR else phoneMode,
            ),
        )
      }
    }
    viewModelScope.launch {
      var traced: List<GeographicCoordinate>? = null
      navigationUiState.map { it.routeGeometry }.collect { geometry ->
        if (geometry != null && geometry.size >= 2 && geometry != traced && navigationUiState.value.isNavigating()) {
          traced = geometry
          com.thealgothrim.overworld.report.Trace.route(com.thealgothrim.overworld.traffic.encodePolyline6(geometry))
        }
        if (geometry != null && geometry.size >= 2 && (geometry != extrasRoute || !extrasLive)) loadExtras(geometry, live = true)
        // Ferrostar reports no route as an empty list, not null. Without this the last trip's
        // lights and incidents stayed on the map, and its live traffic was re-fetched every
        // 150 s for as long as the app lived.
        if (geometry.isNullOrEmpty() && _planner.value.preview == null) {
          extrasJob?.cancel()
          extrasRoute = null
          extrasLive = false
          _extras.value = RouteExtras()
          CurrentLevels.value = null
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
                if (!_testDrive.value) AppModule.prefs.tripFinished()
                // The Timeline's stop begins here, under the trip's own name for the place.
                navigationUiState.value.location?.coordinates?.let { AppModule.timeline.arrived(destinationName ?: "Destination", it) }
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

  /** A map screen (phone, car or cluster) came into view, or left it. GPS runs while at least one is up. */
  fun mapInView(visible: Boolean) {
    watchers.update { (it + if (visible) 1 else -1).coerceAtLeast(0) }
  }

  /** Traffic as if the traffic service had found it, until its next refresh (debug builds' DEBUG_TRAFFIC). */
  internal fun showTraffic(spans: List<com.thealgothrim.overworld.traffic.TrafficSpan>) = _extras.update { it.copy(trafficSpans = spans) }

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
    // Your own places first, by your name for them, as Google lists labelled places: at once,
    // before any server answers.
    val own = savedMatches(query)
    _planner.value = _planner.value.copy(results = own)
    searchJob =
        viewModelScope.launch {
          delay(250)
          _planner.value = _planner.value.copy(searching = true)
          val results =
              runCatching { AppModule.search.search(query, currentCoordinate) }
                  // A search dropped for the next letter typed is not an empty answer: read as one,
                  // it blanked the list ("No places found") until the next search came back.
                  .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "search failed", it) }
                  .getOrDefault(emptyList())
          _planner.value = _planner.value.copy(results = own + results.filter { r -> own.none { it.samePlace(r) } }, searching = false)
        }
  }

  /** Home, Work and saved places matching [query], under your names, marked as saved. */
  fun savedMatches(query: String): List<Place> {
    if (query.isBlank()) return emptyList()
    val s = AppModule.saved.state.value
    val words = com.thealgothrim.overworld.search.fold(query)
    val labels = listOfNotNull(s.home?.let { "Home" to it }, s.work?.let { "Work" to it }).filter { (name, _) -> com.thealgothrim.overworld.search.fold(name).startsWith(words) }
    val own = labels.map { (name, p) -> Place(name, p.name, p.coordinate, note = "Saved") }
    val places = s.matching(query).map { it.asPlace().copy(note = "Saved") }
    return own + places.filter { p -> own.none { it.samePlace(p) } }
  }

  /** The nearest petrol pumps, toilets, parking and so on, closest first, in the search list. */
  fun searchNearby(kind: Nearby) {
    searchJob?.cancel()
    _planner.value = _planner.value.copy(query = kind.label, nearby = kind, results = emptyList(), searching = true, error = null)
    searchJob =
        viewModelScope.launch {
          val found = findNearby(kind)
          _planner.value = _planner.value.copy(results = found, searching = false)
        }
  }

  /**
   * Places of a kind for where the car is now: along the rest of the route on a trip, ahead of the
   * car when it is moving without one, otherwise simply nearest.
   */
  suspend fun findNearby(kind: Nearby): List<Place> {
    val state = navigationUiState.value
    val here = currentCoordinate ?: startPoint
    // Moving only by a recent fix: with the map covered (this list is on top of it) fixes pause, and
    // the last speed would otherwise hold for as long as the car stood.
    val fix = state.location
    val recent = fix != null && java.time.Duration.between(fix.timestamp, Instant.now()).seconds < 2 * FRESH_FIX_SECONDS
    val moving = recent && (fix?.speed?.value ?: estimatedKmh.value?.div(3.6) ?: 0.0) > 3.0
    val heading = if (moving) fix?.courseOverGround?.degrees?.toDouble() ?: movementHeading else null
    val ahead =
        state.routeGeometry?.takeIf { state.isNavigating() && it.size >= 2 }?.let { geometry ->
          val line = routeLine(geometry)
          val left = state.progress?.distanceRemaining ?: line.length
          line.slice((line.length - left).coerceAtLeast(0.0), line.length)
        }
    return runCatching { AppModule.search.nearby(kind, here, heading, ahead) }
        .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "nearby ${kind.name} failed", it) }
        .getOrDefault(emptyList())
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
    routeJob?.cancel()
    // Into Maps history: the search that found it (not a Nearby kind's name), and the place.
    val before = _planner.value
    if (before.nearby == null && before.query.isNotBlank()) AppModule.history.searched(before.query)
    AppModule.history.viewed(place)
    previews.clear()
    _planner.value = PlannerState(destination = place, mode = AppModule.prefs.mode.value)
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
    routeJob?.cancel()
    _planner.value = PlannerState()
  }

  /**
   * Where a route starts: the newest fix there is. The map's own fixes pause while another car
   * screen (Search, Nearby) covers it, so a fix older than a few seconds is checked against the
   * phone's latest one first; a route from a stale point is wrong from its first metre.
   */
  private suspend fun freshOrigin(): UserLocation {
    val known = lastLocation.value
    if (known != null && java.time.Duration.between(known.timestamp, Instant.now()).seconds < FRESH_FIX_SECONDS) return known
    val latest = withTimeoutOrNull(3_000) { runCatching { locationProvider.lastLocation() }.getOrNull() }?.toUserLocation()
    val best = listOfNotNull(known, latest).maxByOrNull { it.timestamp }
    return best ?: UserLocation(AppModule.defaultStart, 6.0, null, Instant.now(), null)
  }

  private fun noteStart(what: String, from: UserLocation, route: Route) {
    DriveLog.note(
        "$what from ${DriveLog.at(from.coordinates)} (fix ${java.time.Duration.between(from.timestamp, Instant.now()).seconds} s old, ±${from.horizontalAccuracy.toInt()} m) " +
            "to ${DriveLog.at(route.geometry.lastOrNull())}: ${"%.1f".format(route.distance / 1000)} km, routing server ${DriveLog.minutes(route.steps.sumOf { it.duration })}"
    )
  }

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

  /**
   * The map's time-of-day palette for themes that have one (GTA VI): "dusk", "night", or null for
   * the day palette. Follows the sun where the phone is, checked every minute, unless held at one
   * in Settings. Declared after [startPoint]'s saved fix, which it reads while starting up.
   */
  val mapVariant: StateFlow<String?> =
      combine(AppModule.themeStore.theme, AppModule.themeStore.mapTime, onTrip, everyMinute()) { theme, time, trip, _ -> if (trip && theme.hasRadar) RADAR else variantFor(theme, time) }
          .distinctUntilChanged()
          .stateIn(viewModelScope, SharingStarted.Eagerly, variantFor(AppModule.themeStore.theme.value, AppModule.themeStore.mapTime.value))

  /**
   * On a trip, GTA V and Red Dead turn from the pause map into the game's radar, the see-through
   * minimap the games show while you drive or ride (Gaurav's captures, 13 Nov).
   */
  private val onTrip: Flow<Boolean> get() = navigationUiState.map { it.isNavigating() }.distinctUntilChanged()

  private val carDarkScreen = MutableStateFlow<Boolean?>(null)
  private val carDarkCluster = MutableStateFlow<Boolean?>(null)

  /**
   * The car's day or night: what the main screen reports, the instrument cluster's only while there
   * is no main screen. Both used to write one value, and the cluster won: it opens after the main
   * screen and a cluster can say "night" all day, so on the 7 Nov drive (2:45 PM, sunny) Auto held
   * the night map for the whole trip.
   */
  private val carDark: StateFlow<Boolean?> =
      combine(carDarkScreen, carDarkCluster) { screen, cluster -> screen ?: cluster }
          .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  /**
   * The car's own day or night, as Android Auto reports it to the main screen or, with [cluster],
   * to the instrument cluster's (null: that screen is gone). See MapTime.carVariant.
   */
  fun setCarDark(dark: Boolean?, cluster: Boolean = false) {
    val target = if (cluster) carDarkCluster else carDarkScreen
    if (target.value == dark) return
    target.value = dark
    // In the drive log, so a report says what the car told the app and when.
    DriveLog.note("car: ${if (cluster) "cluster" else "screen"} reports ${when (dark) { true -> "night"; false -> "day"; null -> "nothing (closed)" }}")
  }

  /** The car's day or night as the map uses it (see [setCarDark]), for the report. */
  /** A car screen or cluster is up right now. */
  val carConnected: Boolean
    get() = carDark.value != null

  val carDarkNow: Boolean?
    get() = carDark.value

  /** [mapVariant] for the car screen, which keeps its own theme and time of day, and whose Auto follows the car. */
  val carMapVariant: StateFlow<String?> =
      combine(AppModule.themeStore.carTheme, AppModule.themeStore.carMapTime, carDark, onTrip, everyMinute()) { theme, time, dark, trip, _ ->
            if (trip && theme.hasRadar) RADAR else carVariantFor(theme, time, dark)
          }
          .distinctUntilChanged()
          .stateIn(viewModelScope, SharingStarted.Eagerly, carVariantFor(AppModule.themeStore.carTheme.value, AppModule.themeStore.carMapTime.value, carDark.value))

  private fun variantFor(theme: OverworldTheme, time: MapTime): String? =
      if (!theme.timeOfDay) null else time.variant(sunPhase())

  private fun carVariantFor(theme: OverworldTheme, time: MapTime, dark: Boolean?): String? =
      if (!theme.timeOfDay) null else time.carVariant(dark, sunPhase())

  private fun sunPhase(): String? {
    val at = startPoint
    return Daylight.phase(Daylight.sunAltitude(at.lat, at.lng, Instant.now()))
  }

  private fun everyMinute() = flow {
    while (true) {
      emit(Unit)
      delay(60_000)
    }
  }

  private fun rememberFix(at: GeographicCoordinate) {
    val last = savedFix
    if (last != null && metres(last, at) < 250.0) return
    savedFix = at
    prefs.edit().putString("last_fix", "${at.lat},${at.lng}").apply()
  }

  private var routeJob: Job? = null

  /** Each mode's preview for the place on screen, kept while it stays (switching tabs is instant). */
  private val previews = java.util.concurrent.ConcurrentHashMap<TravelMode, RoutePreview>()

  /**
   * Directions: the route in the chosen mode, shown with time, distance and the main road, and the
   * other modes' times beside it on their tabs, like Google Maps.
   */
  fun directions(only: TravelMode? = null) {
    val place = _planner.value.destination ?: return
    // Tapped again while the route is on its way: it would only be asked for twice.
    if (routeJob?.isActive == true) return
    AppModule.history.directions(place)
    // The car (and the phone remote driving it) asks for a car's route whatever the phone last used.
    val chosen = only ?: _planner.value.mode
    _planner.update { it.copy(routing = true, error = null, modeTimes = emptyMap(), mode = chosen) }
    routeJob =
        viewModelScope.launch(Dispatchers.IO) {
          // Only while the place is still the one on screen: closed or swapped for another while
          // the route was coming, its preview used to pop up anyway a moment later.
          fun stillShown(state: PlannerState) = state.destination?.coordinate == place.coordinate
          val from = freshOrigin()
          // A walk over this far isn't a walk: no tab time for it.
          val straight = metres(from.coordinates, place.coordinate)
          val modes = if (only != null) listOf(only) else TravelMode.entries.filter { it != TravelMode.WALK || straight < MAX_WALK_METRES || it == chosen }
          val jobs =
              modes.associateWith { mode ->
                async {
                  try {
                    previewFor(place, from, mode).also { p ->
                      previews[mode] = p
                      _planner.update { s -> if (stillShown(s)) s.copy(modeTimes = s.modeTimes + (mode to p.durationSeconds)) else s }
                    }
                  } catch (e: CancellationException) {
                    throw e
                  } catch (e: Exception) {
                    Log.w(TAG, "routing ${mode.name} failed", e)
                    _planner.update { s -> if (stillShown(s)) s.copy(modeTimes = s.modeTimes + (mode to Double.NaN)) else s }
                    if (mode == chosen) _planner.update { s -> if (stillShown(s)) s.copy(routing = false, error = routeError(e)) else s }
                    null
                  }
                }
              }
          val preview = jobs[chosen]?.await() ?: return@launch
          _planner.update { if (stillShown(it) && it.mode == chosen) it.copy(routing = false, preview = preview) else it }
          if (_planner.value.preview === preview) launch(Dispatchers.Main) { loadExtras(preview.route.geometry, live = false, mode = chosen) }
        }
  }

  /** The route to [place] in [mode], as a preview. */
  private suspend fun previewFor(place: Place, from: UserLocation, mode: TravelMode): RoutePreview {
    val route = AppModule.routes.routesFor(mode, from, listOf(Waypoint(coordinate = place.coordinate, kind = WaypointKind.BREAK))).first()
    noteStart("${mode.label.lowercase()} preview of ${place.name}", from, route)
    val via =
        route.steps
            .filter { !it.roadName.isNullOrBlank() }
            .groupBy { it.roadName!! }
            .maxByOrNull { (_, steps) -> steps.sumOf { it.distance } }
            ?.key
    return RoutePreview(place, route, route.steps.sumOf { it.duration }, route.distance, via, mode)
  }

  /** A mode's tab tapped in the preview: its route at once when it's in, else asked for now. */
  fun setPreviewMode(mode: TravelMode) {
    AppModule.prefs.setMode(mode)
    val state = _planner.value
    if (state.mode == mode) return
    val ready = previews[mode]?.takeIf { it.place.coordinate == state.destination?.coordinate }
    _planner.update { it.copy(mode = mode, preview = ready ?: it.preview, error = null) }
    if (ready != null) {
      loadExtras(ready.route.geometry, live = false, mode = mode)
      return
    }
    if (state.preview == null && state.destination != null && !state.routing) return
    val place = state.destination ?: return
    routeJob?.cancel()
    _planner.update { it.copy(routing = true) }
    routeJob =
        viewModelScope.launch(Dispatchers.IO) {
          try {
            val p = previewFor(place, freshOrigin(), mode)
            previews[mode] = p
            _planner.update { s -> if (s.destination?.coordinate == place.coordinate && s.mode == mode) s.copy(routing = false, preview = p, modeTimes = s.modeTimes + (mode to p.durationSeconds)) else s }
            if (_planner.value.preview === p) launch(Dispatchers.Main) { loadExtras(p.route.geometry, live = false, mode = mode) }
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            _planner.update { s -> if (s.mode == mode) s.copy(routing = false, error = routeError(e), modeTimes = s.modeTimes + (mode to Double.NaN)) else s }
          }
        }
  }

  /** A saved trip tapped: its place, its mode, its route preview. */
  fun startSavedTrip(trip: com.thealgothrim.overworld.search.SavedTrip) {
    choose(trip.to)
    AppModule.prefs.setMode(trip.mode)
    _planner.update { it.copy(mode = trip.mode) }
    directions()
  }

  /** Save the previewed route as a trip (You > Saved > Saved trips). */
  fun saveTrip() {
    val p = _planner.value.preview ?: return
    AppModule.saved.saveTrip(p.place, p.mode)
  }

  /** Where the car is parked: here, named by its street or area when that's found. */
  fun saveParking(at: GeographicCoordinate? = currentCoordinate) {
    val spot = at ?: return
    AppModule.saved.park(spot)
    Notices.scheduleParking(AppModule.context, null)
    viewModelScope.launch {
      val near = runCatching { AppModule.search.reverseInfo(spot) }.getOrNull()?.let { it.name ?: it.area }
      if (near != null) AppModule.saved.updateParking { if (it.at == spot) it.copy(near = near) else it }
    }
  }

  /** The parking time limit, [minutes] from now (null clears it), with its reminders. */
  fun setParkingTime(minutes: Int?) {
    val until = minutes?.let { System.currentTimeMillis() + it * 60_000L }
    AppModule.saved.updateParking { it.copy(until = until) }
    Notices.scheduleParking(AppModule.context, until)
  }

  fun clearParking() {
    AppModule.saved.clearParking()
    Notices.scheduleParking(AppModule.context, null)
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
  private fun loadExtras(route: List<GeographicCoordinate>, live: Boolean, mode: TravelMode = AppModule.routes.tripMode) {
    extrasJob?.cancel()
    val sameRoute = route == extrasRoute
    extrasRoute = route
    extrasLive = live
    if (!sameRoute) {
      _extras.value = RouteExtras()
      extrasLoaded.clear()
    }
    // On foot: no traffic, cameras or flyover choices to look for.
    if (!mode.vehicle) {
      if (live) CurrentLevels.value = null
      return
    }
    extrasJob =
        viewModelScope.launch {
          // Lights come from the routing server in about a second; cameras from Overpass, which can
          // be slow or busy, so each arrives on its own.
          // The routing server's answer carries the flyovers too.
          val lightsAndBridges: suspend (List<GeographicCoordinate>) -> List<RoadFeature> = { r ->
            traffic.routeAttributes(r).also { a -> _extras.update { e -> e.copy(bridges = a.bridges) } }.lights
          }
          for ((kind, fetch) in listOf(RoadFeatureKind.TRAFFIC_LIGHT to lightsAndBridges, RoadFeatureKind.SPEED_CAMERA to traffic::speedCameras)) {
            if (kind in extrasLoaded) continue
            launch {
              val found = runCatching { fetch(route) }.getOrNull() ?: return@launch
              extrasLoaded += kind
              _extras.update { e -> e.copy(signals = (e.signals.filter { it.kind != kind } + found).sortedBy { it.along ?: Double.MAX_VALUE }) }
            }
          }
          // Flyovers and underpasses from the map's own tiles (see RoadLevels). The preview already
          // found them for this route when it was previewed first; a trip then only hands them on.
          val known = _extras.value.levels?.takeIf { it.matches(route) }
          if (known != null) {
            if (live) CurrentLevels.value = known
          } else {
            if (live) CurrentLevels.value = null
            launch { refreshLevels(route, live, from = 0.0) }
          }
          val line = RouteLine(route)
          while (true) {
            // While driving, ask about the road still ahead so the time counts from here.
            val left = navigationUiState.value.progress?.distanceRemaining?.takeIf { live } ?: line.length
            val done = (line.length - left).coerceAtLeast(0.0)
            // A long trip is looked at 30 km at a time; the next stretch comes before the car needs it.
            _extras.value.levels?.let { lv -> if (live && lv.to < lv.length - 1.0 && lv.to - done < LEVELS_AHEAD_M) launch { refreshLevels(route, live, from = done) } }
            val ahead = if (done > 50.0) line.slice(done, line.length) else route
            val incidents = runCatching { traffic.incidents(route, from = done) }.getOrDefault(emptyList())
            val fresh = runCatching { traffic.routeTraffic(ahead) }.getOrNull()
            _extras.update { e ->
              e.copy(
                  incidents = incidents,
                  eta = fresh?.eta ?: e.eta,
                  // Kept with the time it belongs to: pairing an old time with the distance left
                  // now (a refresh that failed) showed the whole trip's time for the rest of it.
                  etaAtDistance = if (fresh != null) left else e.etaAtDistance,
                  trafficSpans = fresh?.spans?.map { it.copy(from = it.from + done, to = it.to + done) } ?: e.trafficSpans,
              )
            }
            val state = navigationUiState.value
            DriveLog.note(
                "${if (live) "trip" else "preview"} time: ${traffic.lastRouteTrafficSource} ${DriveLog.minutes(fresh?.eta?.travelSeconds)} for ${"%.1f".format(left / 1000)} km" +
                    (if (fresh == null) " (no answer, kept ${DriveLog.minutes(_extras.value.eta?.travelSeconds)})" else "") +
                    ", routing server ${DriveLog.minutes(state.progress?.durationRemaining)}, showing ${DriveLog.minutes(estimateSeconds(state, _extras.value))}" +
                    ", at ${DriveLog.at(state.location?.coordinates)}"
            )
            if (!live || !traffic.hasRouteTraffic) break
            delay(150_000)
          }
        }
  }

  private fun routeLength(route: List<GeographicCoordinate>) = RouteLine(route).length

  /** Works out [route]'s flyover and underpass choices from [from] metres along it, and hands them on. */
  private suspend fun refreshLevels(route: List<GeographicCoordinate>, live: Boolean, from: Double) {
    val started = System.currentTimeMillis()
    val found =
        runCatching { buildLevels(route, from) }
            .onFailure { if (it is CancellationException) throw it; Log.w(TAG, "road levels failed", it) }
            .getOrNull() ?: return
    // Swapped for another route meanwhile (a reroute, a cancelled preview): not this one's.
    if (extrasRoute != route) return
    _extras.update { it.copy(levels = found) }
    if (live) CurrentLevels.value = found
    DriveLog.note(
        "levels: ${found.cues.size} flyover or underpass choices in ${"%.1f".format((found.to - found.from) / 1000)} km " +
            "(${found.structures.size} on the route, ${found.parallels.size} beside it, ${System.currentTimeMillis() - started} ms)" +
            found.cues.take(8).joinToString("") { c -> "; ${c.kind.text} at ${"%.2f".format(c.at / 1000)} km" }
    )
  }

  private suspend fun buildLevels(route: List<GeographicCoordinate>, from: Double): RoadLevels =
      withContext(Dispatchers.Default) {
        val line = RouteLine(route)
        // A little behind the start too: a split can be up to a kilometre before its flyover.
        val start = (from - LEVELS_BEHIND_M).coerceAtLeast(0.0)
        val end = minOf(line.length, from + LEVELS_WINDOW_M)
        val roads = AppModule.tiles.roadsAlong(line.slice(start, end))
        RoadLevels.analyse(route, roads, maneuverAlongs(route), start, end)
      }

  /**
   * Where [route]'s turns are, metres along it. Each step's turn is at its end, so they're the step
   * ends counted back from the route's end (on a trip only the steps still ahead are known).
   */
  private fun maneuverAlongs(route: List<GeographicCoordinate>): List<Double> {
    val state = navigationUiState.value
    val steps =
        state.remainingSteps?.takeIf { state.isNavigating() && state.routeGeometry == route }
            ?: _planner.value.preview?.route?.takeIf { it.geometry == route }?.steps
            ?: return emptyList()
    var end = RouteLine(route).length
    val out = ArrayList<Double>()
    for (step in steps.reversed()) {
      out += end
      end -= RouteLine(step.geometry).length
    }
    // The last step's end is the destination, not a turn.
    return out.drop(1)
  }

  /**
   * The next flyover or underpass choice to show on the turn card: within [CUE_SHOW_M], and not past
   * the route's next turn (that turn's card comes first, the choice after it), unless it's at that
   * very turn, when the card shows both.
   */
  fun levelCueAhead(state: NavigationUiState, extras: RouteExtras): CueAhead? {
    if (!state.isNavigating()) return null
    val levels = extras.levels ?: return null
    val progress = state.progress ?: return null
    val along = levels.length - progress.distanceRemaining
    val cue = levels.cues.firstOrNull { it.at >= along - CUE_PASSED_M } ?: return null
    val distance = cue.at - along
    if (distance > CUE_SHOW_M) return null
    val withTurn = kotlin.math.abs(distance - progress.distanceToNextManeuver) <= CUE_ON_TURN_M
    if (!withTurn && distance > progress.distanceToNextManeuver + CUE_ON_TURN_M) return null
    return CueAhead(cue, distance.coerceAtLeast(0.0), withTurn)
  }

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
    // The first live-traffic time for a route lands at once too: it comes a second after the route
    // itself, and easing from the routing server's guess took half a minute (52 min sliding to 34
    // after a reroute).
    val newRoute = route !== shownFor
    val firstTraffic = extras.eta != null && (newRoute || !shownTraffic)
    shownTraffic = extras.eta != null
    val arrival =
        if (previous == null || newRoute || firstTraffic) targetArrival
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
  /** Whether the time shown for this route already came from live traffic. */
  private var shownTraffic = false

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
  fun hazardAhead(state: NavigationUiState, extras: RouteExtras, camerasOnly: Boolean = false): HazardAhead? {
    val progress = state.progress ?: return freeDriveCamera(state)
    val geometry = state.routeGeometry ?: return null
    val line = routeLine(geometry)
    val done = line.length - progress.distanceRemaining
    val marked =
        AppModule.cameras.all.value.mapNotNull { c ->
          val (along, off) = line.project(c.at)
          if (off <= 30.0) RoadFeature(RoadFeatureKind.SPEED_CAMERA, c.at, along = along) else null
        }
    return ((if (camerasOnly) emptyList() else extras.incidentsOnRoute) + extras.signals.filter { it.kind == RoadFeatureKind.SPEED_CAMERA } + marked)
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

  /**
   * Cameras near the car with no trip running, reloaded after every 3 km or so: each load covers
   * 4 km around and alerts look 600 m ahead, so 3 km still leaves room, for a third fewer requests
   * to the public camera server than every 2 km.
   */
  private fun refreshCamerasAround(at: GeographicCoordinate) {
    val last = camerasAroundFrom
    if ((last != null && metres(last, at) < 3_000.0) || camerasJob?.isActive == true) return
    camerasAroundFrom = at
    camerasJob = viewModelScope.launch {
      runCatching { traffic.camerasAround(at, 4_000.0) }.getOrNull()?.let { _camerasAround.value = it }
    }
  }

  private fun freeDriveCamera(state: NavigationUiState): HazardAhead? {
    val here = state.location ?: return null
    val heading = here.courseOverGround?.degrees?.toDouble() ?: movementHeading ?: return null
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

  private val _cameraBeep = MutableStateFlow(prefs.getBoolean("camera_beep", true))
  /** A short chime as a camera comes within 500 m, like Radarbot. On unless turned off (voice stays off). */
  val cameraBeep: StateFlow<Boolean> = _cameraBeep.asStateFlow()

  fun setCameraBeep(on: Boolean) {
    _cameraBeep.value = on
    prefs.edit().putBoolean("camera_beep", on).apply()
    // Switching it on plays it once, so it's known what to listen for.
    if (on) beep()
  }

  private fun beep() = CameraChime.play(AppModule.context)

  private val _wantedAt = MutableStateFlow(0L)
  /** When the car last passed a speed camera over the limit: GTA's wanted flash (ui/WantedFlash). */
  val wantedAt: StateFlow<Long> = _wantedAt.asStateFlow()

  /** Sets off the wanted flash (and notes it in the drive log). */
  fun wanted(note: String = "debug") {
    _wantedAt.value = System.currentTimeMillis()
    DriveLog.note("wanted: $note")
  }

  // Here, after the camera state above: init blocks run in order, and a collector started on the
  // main thread runs its first round at once.
  init {
    viewModelScope.launch {
      // A line a minute on how smoothly each map drew (see FrameStats), while driving or with the
      // car connected: a drive that felt laggy then shows which screen dropped frames.
      while (true) {
        delay(60_000)
        val line = com.thealgothrim.overworld.map.FrameStats.summary() ?: continue
        if (navigationUiState.value.isNavigating() || carDark.value != null) DriveLog.note("frames: $line")
      }
    }
    viewModelScope.launch {
      // One chime per camera as it comes within 500 m, when the chime is on. A camera left more
      // than a kilometre behind can chime again (the drive back, a second lap).
      val chimed = ArrayDeque<GeographicCoordinate>()
      // The wanted flash: once per camera, passed within 35 m at 5 km/h or more over the road's
      // limit (the margin at which the speed turns red), on a trip, where the route knows the limit.
      val caught = ArrayDeque<GeographicCoordinate>()
      navigationUiState.collect { state ->
        val here = state.location?.coordinates
        if (here != null) {
          chimed.removeAll { metres(it, here) > 1_000.0 }
          caught.removeAll { metres(it, here) > 1_000.0 }
        }
        val near = hazardAhead(state, _extras.value, camerasOnly = true)
        val kmh = state.location?.speed?.value?.times(3.6)
        val limit = (state.currentAnnotation?.speed as? com.stadiamaps.ferrostar.core.annotation.Speed.Value)?.let {
          if (it.unit.name.contains("MILE", ignoreCase = true)) it.value * 1.609 else it.value
        }
        if (near != null && near.distance <= 35.0 && kmh != null && limit != null && kmh >= limit + 5 && caught.none { metres(it, near.feature.at) < 50.0 }) {
          caught.addLast(near.feature.at)
          wanted("passed a camera at ${kmh.toInt()} km/h, limit ${limit.toInt()}")
        }
        if (!_cameraBeep.value) return@collect
        val camera = near ?: return@collect
        if (camera.distance > 500.0 || chimed.any { metres(it, camera.feature.at) < 50.0 }) return@collect
        chimed.addLast(camera.feature.at)
        beep()
      }
    }
  }

  /** Start: drive (or ride, or walk) the previewed route. */
  fun startPreview() {
    val preview = _planner.value.preview ?: return
    begin(preview.route, preview.place, preview.mode)
    _planner.value = PlannerState(mode = preview.mode)
  }

  private val _tripPlace = MutableStateFlow<GeographicCoordinate?>(null)
  /**
   * Where the trip is really going: the place itself, which can sit off the road the route ends on
   * (a building inside a campus). The waypoint marks it there, and Red Dead dashes the last bit.
   */
  val tripPlace: StateFlow<GeographicCoordinate?> = _tripPlace.asStateFlow()

  private val _tripMode = MutableStateFlow(TravelMode.CAR)
  /** How the trip being driven is made: a walk hides the speed and the car's alerts. */
  val tripMode: StateFlow<TravelMode> = _tripMode.asStateFlow()

  /** Main thread only, like everything else that starts or stops Ferrostar. */
  private fun begin(given: Route, place: Place, mode: TravelMode = TravelMode.CAR) {
    val route = given.safeForCar()
    // Set both ways: a test drive ended from the notification's Stop never turned the simulator
    // off, and the next real trip would then have been driven by it.
    if (_testDrive.value) locationProvider.enableSimulationOn(route) else locationProvider.disableSimulation()
    setDestination(place.name)
    destinationName = place.name
    _tripPlace.value = place.coordinate
    _area.value = null
    AppModule.saved.addRecent(place)
    // Reroutes come back in the same mode; a walk turns and strays at walking distances.
    AppModule.routes.tripMode = mode
    _tripMode.value = mode
    DriveLog.note("started ${if (_testDrive.value) "a test drive" else "a trip"} to ${place.name}${if (mode != TravelMode.CAR) " (${mode.label.lowercase()})" else ""}")
    // The core's own state, not the UI's copy, which reaches the main thread a moment later.
    val navigating = core.state.value.isNavigating()
    if (mode == TravelMode.WALK) {
      if (navigating) core.replaceRoute(route, AppModule.walkingConfig) else core.startNavigation(route, AppModule.walkingConfig)
    } else {
      if (navigating) core.replaceRoute(route = route) else core.startNavigation(route = route)
    }
  }

  /**
   * Straight to driving, without a preview (used by the car and by saved-place shortcuts there).
   * [from] replaces the phone's position as the start (debug test drives). The car always asks
   * for a car's route.
   */
  fun startNavigation(destination: GeographicCoordinate, name: String?, from: GeographicCoordinate? = null, fromHeading: Double? = null, mode: TravelMode = TravelMode.CAR) {
    _planner.value = _planner.value.copy(routing = true, error = null)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // A debug start with a heading starts as a moving car would, on its own carriageway.
        val start =
            from?.let {
              UserLocation(it, 6.0, fromHeading?.let { h -> uniffi.ferrostar.CourseOverGround(h.toInt().toUShort(), null) }, Instant.now(), fromHeading?.let { uniffi.ferrostar.Speed(10.0, null) })
            } ?: freshOrigin()
        val route = AppModule.routes.routesFor(mode, start, listOf(Waypoint(coordinate = destination, kind = WaypointKind.BREAK))).first()
        noteStart("trip to ${name ?: "a point"}", start, route)
        withContext(Dispatchers.Main) {
          begin(route, Place(name ?: "Destination", "", destination), mode)
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
   * On a trip: drive to [place] first, then on to where the trip was going, like Google Maps' Add
   * stop (a petrol pump picked from Nearby). [onDone] says whether the new route was found.
   */
  fun addStop(place: Place, onDone: (Boolean) -> Unit = {}) {
    val trip = core.state.value.tripState as? TripState.Navigating ?: return onDone(false)
    viewModelScope.launch(Dispatchers.IO) {
      val route =
          runCatching {
                core.getRoutes(trip.userLocation, listOf(Waypoint(coordinate = place.coordinate, kind = WaypointKind.BREAK)) + trip.remainingWaypoints).first()
              }
              .onFailure { Log.w(TAG, "routing via ${place.name} failed", it) }
              .getOrNull()
      withContext(Dispatchers.Main) {
        if (route == null || !core.state.value.isNavigating()) return@withContext onDone(false)
        val safe = route.safeForCar()
        if (_testDrive.value) locationProvider.enableSimulationOn(safe)
        core.replaceRoute(route = safe)
        AppModule.saved.addRecent(place)
        onDone(true)
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

  /**
   * Whether [fix] is believable after [previous]: not a vague fix (city canyons, flyovers) right
   * after a sharp one, and not a jump faster than any car. Those made the arrow leap off the road.
   */
  private fun plausible(fix: UserLocation, previous: UserLocation): Boolean {
    val seconds = java.time.Duration.between(previous.timestamp, fix.timestamp).toMillis() / 1000.0
    if (seconds <= 0.0) return true
    if (fix.horizontalAccuracy > VAGUE_FIX_METRES && previous.horizontalAccuracy < SHARP_FIX_METRES && seconds < FRESH_FIX_SECONDS) return false
    val slack = fix.horizontalAccuracy + previous.horizontalAccuracy
    return (metres(previous.coordinates, fix.coordinates) - slack) / seconds < MAX_CAR_SPEED
  }

  /** Both fixes say the car isn't moving, and [fix] is within GPS's own wobble of [previous]. */
  private fun standingStill(fix: UserLocation, previous: UserLocation): Boolean {
    val speed = fix.speed?.value ?: return false
    val before = previous.speed?.value ?: return false
    if (speed > STILL_SPEED || before > STILL_SPEED) return false
    return metres(previous.coordinates, fix.coordinates) < maxOf(STILL_WANDER_METRES, fix.horizontalAccuracy)
  }

  companion object {
    private const val TAG = "OverworldViewModel"
    /** How quickly a new arrival estimate takes over (a time constant, in seconds). */
    private const val ETA_EASE_SECONDS = 20.0
    /** A fix younger than this is where the car is; an older one is checked against the phone's latest. */
    private const val FRESH_FIX_SECONDS = 5L
    /** No fix for this long with a map on screen: "Location not found". */
    private const val GPS_LOST_MS = 12_000L
    /** About 3 km/h: under this the car is standing. */
    private const val STILL_SPEED = 0.8
    private const val STILL_WANDER_METRES = 20.0
    private const val VAGUE_FIX_METRES = 60.0
    private const val SHARP_FIX_METRES = 25.0
    /** 250 km/h, in m/s. */
    private const val MAX_CAR_SPEED = 70.0
    /** Further than this in a straight line, the preview doesn't offer a walk. */
    private const val MAX_WALK_METRES = 40_000.0
    /** Flyovers are worked out this far ahead at a time, and the next stretch when this little is left. */
    private const val LEVELS_WINDOW_M = 30_000.0
    private const val LEVELS_AHEAD_M = 10_000.0
    private const val LEVELS_BEHIND_M = 1_300.0
    /** A flyover choice shows on the card from this far out... */
    private const val CUE_SHOW_M = 900.0
    /** ...counts as at the route's turn within this of it... */
    private const val CUE_ON_TURN_M = 80.0
    /** ...and goes once the car is this far past it. */
    private const val CUE_PASSED_M = 15.0
  }
}
