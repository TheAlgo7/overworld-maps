package com.thealgothrim.overworld.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.HazardAhead
import com.thealgothrim.overworld.OverworldViewModel
import com.thealgothrim.overworld.RouteExtras
import com.thealgothrim.overworld.RoutePreview
import com.thealgothrim.overworld.search.Nearby
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.theme.MapTime
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.THEMES
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.SkinChip
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinFitText
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.icon
import com.thealgothrim.overworld.ui.skin.skinPanel
import com.thealgothrim.overworld.ui.skin.spec

/** Whether Android Auto is showing the app on a car screen right now. */
@Composable
fun rememberCarProjecting(): Boolean {
  val context = LocalContext.current
  var projecting by remember { mutableStateOf(false) }
  DisposableEffect(context) {
    val type = CarConnection(context).type
    val watch = Observer<Int> { projecting = it == CarConnection.CONNECTION_TYPE_PROJECTION }
    type.observeForever(watch)
    onDispose { type.removeObserver(watch) }
  }
  return projecting || CarRemote.forced
}

/** Debug builds can show the remote without a car (DebugDriveReceiver's DEBUG_REMOTE). */
object CarRemote {
  var forced by mutableStateOf(false)
}

/**
 * The phone while Android Auto is on: a remote for the car screen instead of a second map, like
 * Google Maps does, with more in it. Where to (search, Home, Work, saved, nearby), the route preview
 * with Start, the trip (turn, time left, End, voice, a stop on the way), and the car screen's own
 * settings, all a thumb away from the dashboard mount. Dressed in the car's theme. "Phone map"
 * brings the map back for a passenger.
 */
@Composable
fun RemoteScreen(viewModel: OverworldViewModel, onShowMap: () -> Unit) {
  val store = AppModule.themeStore
  val theme by store.carTheme.collectAsState()
  val spec = theme.spec
  val variant by viewModel.carMapVariant.collectAsState()
  val uiState by viewModel.navigationUiState.collectAsState()
  val planner by viewModel.planner.collectAsState()
  val saved by AppModule.saved.state.collectAsState()
  val extras by viewModel.extras.collectAsState()
  val testDrive by viewModel.testDrive.collectAsState()
  val navigating = uiState.isNavigating()
  val here = uiState.location?.coordinates
  val focus = LocalFocusManager.current
  var searchOpen by remember { mutableStateOf(false) }
  var added by remember { mutableStateOf<String?>(null) }

  fun closeSearch() {
    focus.clearFocus()
    searchOpen = false
    viewModel.onQueryChange("")
  }
  BackHandler(!searchOpen && planner.preview == null && planner.destination != null) { viewModel.clearDestination() }
  BackHandler(!searchOpen && planner.preview != null) { viewModel.cancelPreview() }
  BackHandler(searchOpen) { closeSearch() }

  // A pick: on a trip it becomes a stop on the way, otherwise the place card with Directions.
  val pick: (Place) -> Unit = { place ->
    closeSearch()
    if (navigating) {
      added = "Adding ${place.name}…"
      viewModel.addStop(place) { ok -> added = if (ok) "Stop added: ${place.name}" else "Couldn't route via ${place.name}" }
    } else viewModel.choose(place)
  }

  Box(Modifier.fillMaxSize().background(theme.pageFor(variant))) {
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Header(spec, theme, onShowMap)
      when {
        navigating -> Trip(spec, uiState, extras, viewModel.remainingSeconds(uiState, extras), viewModel.hazardAhead(uiState, extras), viewModel::toggleMute, viewModel::stopNavigation)
        planner.preview != null -> Preview(spec, planner.preview!!, extras, testDrive, viewModel::cancelPreview, viewModel::startPreview)
      }

      if (planner.preview == null) {
        SearchBar(
            spec = spec,
            query = planner.query,
            placeName = planner.destination?.name.takeIf { !searchOpen && !navigating },
            open = searchOpen,
            searching = planner.searching,
            onOpen = { searchOpen = true },
            onBack = ::closeSearch,
            onQuery = viewModel::onQueryChange,
            onClear = { if (searchOpen) viewModel.onQueryChange("") else viewModel.clearDestination() },
            modifier = Modifier.fillMaxWidth(),
        )
        if (searchOpen) {
          SkinPanel(spec, Modifier.fillMaxWidth()) {
            if (planner.query.isBlank()) SavedList(spec, saved, here, if (navigating) "Pick a place to stop at on the way." else null, pick)
            else SearchResults(spec, planner, here, pick)
          }
        } else {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!navigating) {
              saved.home?.let { home -> SkinChip(spec, "Home", GameIcon.HOME) { pick(home) } }
              saved.work?.let { work -> SkinChip(spec, "Work", GameIcon.WORK) { pick(work) } }
              SkinChip(spec, "Saved", GameIcon.STAR) { searchOpen = true }
            }
            // On a trip these look along the rest of the route, and a pick becomes a stop.
            Nearby.shown().forEach { kind ->
              SkinChip(spec, kind.label, kind.icon) {
                searchOpen = true
                viewModel.searchNearby(kind)
              }
            }
          }
          (added?.takeIf { navigating } ?: planner.error)?.let { note ->
            SkinPanel(spec, Modifier.fillMaxWidth()) { SkinText(note, spec.body, 15.sp, spec.accent, Modifier.padding(14.dp), maxLines = 3) }
          }
          // The remote plans the car's trips: always a car's route.
          planner.destination?.takeIf { !navigating }?.let { PlaceSheet(spec, it, planner, saved, here, { viewModel.directions(com.thealgothrim.overworld.routing.TravelMode.CAR) }) }
        }
      }

      if (!searchOpen) CarScreenSettings(spec, theme)
      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
private fun Header(spec: SkinSpec, theme: OverworldTheme, onShowMap: () -> Unit) {
  // In a panel: the page is the map's ground colour, pale in GTA VI's day and Red Dead's paper.
  Column(Modifier.fillMaxWidth().skinPanel(spec).padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Red Dead's wide type cut it to "CAR REM…" beside the button (8 Nov sweep): it steps down instead.
      SkinFitText(spec.title("Car remote"), spec.title, spec.titleSize(if (spec.vi) 28.sp else 26.sp, 30.sp), spec.fg, Modifier.weight(1f), spacing = spec.titleSpacing, minSize = 18.sp)
      Spacer(Modifier.width(10.dp))
      SkinSecondaryButton(spec, "Phone map", onShowMap, icon = GameIcon.LAYERS)
    }
    // Android for Cars rule VI-1: the phone is looked at only when it's safe. Its own full-width
    // line, so it's never cut short.
    SkinText("Android Auto is on. Only look at your phone when it's safe.", spec.body, 14.sp, spec.sub, Modifier.padding(top = 4.dp), maxLines = 3)
  }
}

@Composable
private fun Trip(
    spec: SkinSpec,
    uiState: NavigationUiState,
    extras: RouteExtras,
    remaining: Double?,
    hazard: HazardAhead?,
    onMute: () -> Unit,
    onEnd: () -> Unit,
) {
  TurnBanner(spec, uiState, hazard)
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      remaining?.let { seconds ->
        Row(verticalAlignment = Alignment.Bottom) {
          SkinText(formatDuration(seconds).uppercase(), spec.big, 34.sp, spec.trafficColor(extras.eta))
          uiState.progress?.distanceRemaining?.let {
            Spacer(Modifier.width(10.dp))
            SkinText("(${formatDistance(it)})", spec.body, 18.sp, spec.sub, Modifier.padding(bottom = 4.dp))
          }
        }
        SkinText(listOfNotNull("Arrive at ${arrivalClock(seconds)}", trafficNote(extras.eta)).joinToString("  ·  "), spec.body, 14.sp, spec.sub, Modifier.padding(top = 2.dp))
      }
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinPrimaryButton(spec, "End trip", onEnd, Modifier.weight(1f), GameIcon.CLOSE)
        val muted = uiState.isMuted == true
        SkinSecondaryButton(spec, if (muted) "Voice off" else "Voice on", onMute, icon = if (muted) GameIcon.SOUND_OFF else GameIcon.SOUND_ON)
      }
    }
  }
}

@Composable
private fun Preview(spec: SkinSpec, preview: RoutePreview, extras: RouteExtras, testDrive: Boolean, onCancel: () -> Unit, onStart: () -> Unit) {
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      SkinText(spec.title(preview.place.name), spec.title, spec.titleSize(if (spec.vi) 24.sp else 22.sp, 26.sp), spec.fg, maxLines = 2, spacing = spec.titleSpacing)
      val seconds = extras.eta?.travelSeconds ?: preview.durationSeconds
      Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
        SkinText(formatDuration(seconds).uppercase(), spec.big, 34.sp, spec.trafficColor(extras.eta))
        Spacer(Modifier.width(10.dp))
        SkinText("(${formatDistance(preview.distanceMeters)})", spec.body, 18.sp, spec.sub, Modifier.padding(bottom = 4.dp))
      }
      val facts = listOfNotNull(preview.via?.let { "via $it" }, trafficNote(extras.eta), "on the car screen now")
      SkinText(facts.joinToString("  ·  "), spec.body, 15.sp, spec.sub, Modifier.padding(top = 2.dp), maxLines = 2)
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinPrimaryButton(spec, if (testDrive) "Start test drive" else "Start", onStart, Modifier.weight(1f), GameIcon.NAVIGATE)
        SkinSecondaryButton(spec, "Cancel", onCancel, icon = GameIcon.CLOSE)
      }
    }
  }
}

/** The car screen's own settings (the same ones as its Settings list), with theme cards. */
@Composable
private fun CarScreenSettings(spec: SkinSpec, theme: OverworldTheme) {
  val store = AppModule.themeStore
  val details by store.carDetails.collectAsState()
  val time by store.carMapTime.collectAsState()
  val gameHud by store.carGameHud.collectAsState()
  val camButton by store.carCamButton.collectAsState()
  val beep by AppModule.viewModel.cameraBeep.collectAsState()
  val themedLists by store.carThemedLists.collectAsState()
  val live = AppModule.traffic.hasLiveTraffic
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      SkinText(spec.title("Car screen"), spec.title, spec.titleSize(if (spec.vi) 22.sp else 20.sp, 24.sp), spec.fg, spacing = spec.titleSpacing)
      SkinText("Only the car's map. The phone keeps its own.", spec.body, 13.sp, spec.sub)
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        THEMES.forEach { t -> ThemeCard(spec, t, selected = t.id == theme.id, modifier = Modifier.weight(1f)) { store.selectCar(t.id) } }
      }
      if (theme.variants.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SkinText("Time of day. Auto follows the car's day and night.", spec.body, 15.sp, spec.sub, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MapTime.entries.forEach { t ->
            if (t == time) SkinPrimaryButton(spec, t.label, {}, Modifier.weight(1f))
            else SkinSecondaryButton(spec, t.label, { store.setCarMapTime(t) }, Modifier.weight(1f))
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      ToggleRow(spec, "Place icons", "Shops, food and petrol on the car's map.", checked = details.placeIcons) {
        store.setCarDetails(details.copy(placeIcons = !details.placeIcons))
      }
      SkinDivider(spec)
      ToggleRow(
          spec, "Traffic on every road",
          if (live) "Your route always shows its own." else "Needs a free TomTom key.",
          checked = details.traffic && live, enabled = live,
      ) { store.setCarDetails(details.copy(traffic = !details.traffic)) }
      SkinDivider(spec)
      ToggleRow(spec, "Traffic lights", "Signals along your route.", checked = details.signals) { store.setCarDetails(details.copy(signals = !details.signals)) }
      SkinDivider(spec)
      ToggleRow(spec, "Incidents", if (live) "Accidents, works and closures, with alerts." else "Needs a free TomTom key.", checked = details.incidents && live, enabled = live) {
        store.setCarDetails(details.copy(incidents = !details.incidents))
      }
      if (theme.buildings3d) {
        SkinDivider(spec)
        ToggleRow(spec, "3D buildings", "Off lays them flat.", checked = details.buildings3d) { store.setCarDetails(details.copy(buildings3d = !details.buildings3d)) }
      }
      SkinDivider(spec)
      ToggleRow(spec, "Game HUD", "Turn, time and street in the game's style. Off uses Android Auto's cards.", checked = gameHud) { store.setCarGameHud(!gameHud) }
      SkinDivider(spec)
      ToggleRow(spec, "Camera beep", "Two notes as a speed camera comes within 500 m.", checked = beep) { AppModule.viewModel.setCameraBeep(!beep) }
      SkinDivider(spec)
      ToggleRow(spec, "+ Cam button", "Mark a speed camera as you pass it.", checked = camButton) { store.setCarCamButton(!camButton) }
      SkinDivider(spec)
      ToggleRow(
          spec, "Themed car lists",
          "Settings, Saved and Nearby in the game's look over the map. Off uses Android Auto's plain lists.",
          checked = themedLists,
      ) { store.setCarThemedLists(!themedLists) }
    }
  }
}
