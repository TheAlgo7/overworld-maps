package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.PlannerState
import com.thealgothrim.overworld.map.PARKED_NAME
import com.thealgothrim.overworld.search.HistoryKind
import com.thealgothrim.overworld.search.Nearby
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinChip
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinRecenterButton
import com.thealgothrim.overworld.ui.skin.SkinRoundButton
import com.thealgothrim.overworld.ui.skin.SkinRow
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.icon
import com.thealgothrim.overworld.ui.skin.puck
import com.thealgothrim.overworld.ui.skin.skinPanel
import uniffi.ferrostar.GeographicCoordinate

/**
 * Not navigating: search bar with You and Settings (top), shortcut chips, compass and locate
 * buttons (right), and a place sheet with Directions and Save (bottom). Same placement as Google
 * Maps; You stands where Google puts your picture.
 */
@Composable
fun BoxScope.BrowseLayer(
    spec: SkinSpec,
    planner: PlannerState,
    saved: SavedState,
    here: GeographicCoordinate?,
    searchOpen: Boolean,
    onSearchOpen: (Boolean) -> Unit,
    rotated: Boolean,
    onQuery: (String) -> Unit,
    onNearby: (Nearby) -> Unit,
    onChoose: (Place) -> Unit,
    onClear: () -> Unit,
    onDirections: () -> Unit,
    onSettings: () -> Unit,
    onLayers: () -> Unit,
    onLocate: () -> Unit,
    /** You: saved lists, Timeline and history (his pick, 8 Nov: a button beside Settings). */
    onYou: () -> Unit = {},
    /** The map turns with the phone (locate tapped twice): the locate button shows the arrow. */
    compass: Boolean = false,
    /** The map follows the arrow; moved away, locate becomes the player's marker to come back to. */
    following: Boolean = true,
    onNorthUp: () -> Unit,
    mapArea: String? = null,
    metresPerDp: Double? = null,
) {
  val focus = LocalFocusManager.current
  val destination = planner.destination
  var shortcutHint by remember { mutableStateOf<String?>(null) }

  fun openSaved(hint: String? = null) {
    shortcutHint = hint
    onSearchOpen(true)
  }

  // ---- top: search bar, You and Settings, then chips or results
  Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      SearchBar(
          spec = spec,
          query = planner.query,
          placeName = destination?.let { saved.savedFor(it)?.name ?: it.name }.takeIf { !searchOpen },
          open = searchOpen,
          searching = planner.searching,
          onOpen = { onSearchOpen(true) },
          onBack = {
            focus.clearFocus()
            onQuery("")
            onSearchOpen(false)
          },
          onQuery = onQuery,
          onClear = {
            if (searchOpen) onQuery("") else onClear()
          },
          onSubmit = { AppModule.history.searched(it) },
          modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(8.dp))
      YouButton(spec, onYou)
      Spacer(Modifier.width(8.dp))
      // A dot when a newer Overworld is out (Settings says which and where).
      val update by AppModule.updates.available.collectAsState()
      val dot by AppModule.prefs.notifyUpdates.collectAsState()
      Box {
        SkinRoundButton(spec, GameIcon.SETTINGS, onSettings)
        if (update != null && dot) Box(Modifier.align(Alignment.TopEnd).size(14.dp).background(spec.accent, CircleShape).border(2.dp, Color.Black, CircleShape))
      }
    }

    if (searchOpen) {
      Spacer(Modifier.height(8.dp))
      SkinPanel(spec, Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        if (planner.query.isBlank()) {
          SavedList(spec, saved, here, shortcutHint, onChoose, onQuery = onQuery, onYou = onYou)
        } else {
          SearchResults(spec, planner, here) { focus.clearFocus(); onChoose(it) }
        }
      }
    } else if (destination == null) {
      Spacer(Modifier.height(10.dp))
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SkinChip(spec, "Home", GameIcon.HOME) {
          saved.home?.let(onChoose) ?: openSaved("Home isn't set yet. Search for it, then tap Save and choose Home.")
        }
        SkinChip(spec, "Work", GameIcon.WORK) {
          saved.work?.let(onChoose) ?: openSaved("Work isn't set yet. Search for it, then tap Save and choose Work.")
        }
        // The parked car, once saved: one tap to its card.
        saved.parking?.let { p -> SkinChip(spec, "My car", GameIcon.PARKING) { onChoose(Place(PARKED_NAME, p.near ?: "", p.at)) } }
        SkinChip(spec, "Saved", GameIcon.STAR, onYou)
        // Nearby in an emergency, closest first: fuel (or chargers), food, parking, toilets, hospitals, hotels.
        Nearby.shown().forEach { kind -> SkinChip(spec, kind.label, kind.icon) { onNearby(kind) } }
      }
      planner.error?.let {
        Spacer(Modifier.height(8.dp))
        SkinPanel(spec, Modifier.fillMaxWidth()) { SkinText(it, spec.body, 15.sp, spec.accent, Modifier.padding(14.dp), maxLines = 3) }
      }
    }

    // Right column under the search bar, like Google Maps: layers, then the compass when rotated.
    if (!searchOpen) {
      Spacer(Modifier.height(10.dp))
      Column(Modifier.align(Alignment.End), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinRoundButton(spec, GameIcon.LAYERS, onLayers)
        if (rotated) SkinRoundButton(spec, GameIcon.NORTH, onNorthUp)
      }
    }
  }

  // ---- bottom: locate button, then the place sheet
  if (!searchOpen) {
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      // The corner box and the locate button share a bottom edge; the map credit runs under both.
      Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Box(Modifier.weight(1f)) {
          if (!spec.rdr && destination == null && metresPerDp != null) GtaMapCorner(spec, mapArea, metresPerDp)
        }
        when {
          compass -> SkinRoundButton(spec, GameIcon.NAVIGATE, onLocate, tint = spec.accent)
          following -> SkinRoundButton(spec, GameIcon.LOCATE, onLocate, tint = spec.accent)
          else -> SkinRecenterButton(spec, onLocate)
        }
      }
      MapCredit(spec, Modifier.padding(start = 12.dp, bottom = 8.dp))
      if (destination != null) {
        PlaceSheet(spec, destination, planner, saved, here, onDirections)
      }
    }
  }
}

/** You: the player's own marker on a map button, where Google puts your profile picture. */
@Composable
private fun YouButton(spec: SkinSpec, onClick: () -> Unit) {
  Box(Modifier.size(52.dp).skinPanel(spec).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
    Image(ImageBitmap.imageResource(spec.puck), contentDescription = "You", modifier = Modifier.size(30.dp), filterQuality = FilterQuality.High)
  }
}

/** Search or Nearby results, best first, or why there are none. */
@Composable
internal fun SearchResults(spec: SkinSpec, planner: PlannerState, here: GeographicCoordinate?, onChoose: (Place) -> Unit) {
  val results = planner.results
  if (results.isEmpty()) {
    SkinText(
        when {
          planner.searching -> "Searching…"
          planner.nearby != null -> "Nothing found within 10 km."
          else -> "No places found. For a place only Google Maps knows, open it there, tap Share and pick Overworld."
        },
        spec.body, 16.sp, spec.sub, Modifier.padding(16.dp), maxLines = 3,
    )
  }
  results.forEachIndexed { i, place ->
    if (i > 0) SkinDivider(spec)
    SkinRow(
        spec,
        place.name,
        { onChoose(place) },
        // Along a trip the note ("On the way · 2.4 km ahead", "+3 min") leads; "Saved" marks your own.
        subtitle = listOfNotNull(place.note, place.detail.takeIf { it.isNotBlank() }).joinToString("  ·  "),
        icon = if (place.note == "Saved") GameIcon.STAR_FILLED else planner.nearby?.icon ?: GameIcon.PIN,
        highlighted = i == 0,
        trailing = here?.let { formatDistance(distanceMeters(it, place.coordinate)) },
    )
  }
}

@Composable
internal fun SearchBar(
    spec: SkinSpec,
    query: String,
    placeName: String?,
    open: Boolean,
    searching: Boolean,
    onOpen: () -> Unit,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /** The keyboard's search key: the query goes into history. */
    onSubmit: (String) -> Unit = {},
) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(open) { if (open) runCatching { focusRequester.requestFocus() } }
  Row(
      modifier.height(54.dp).skinPanel(spec).padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(24.dp).then(if (open) Modifier.clickable(onClick = onBack) else Modifier)) {
      GameIconView(if (open) GameIcon.BACK else GameIcon.SEARCH, spec.fg, Modifier.size(24.dp))
    }
    Spacer(Modifier.width(12.dp))
    if (open) {
      BasicTextField(
          value = query,
          onValueChange = onQuery,
          singleLine = true,
          textStyle = TextStyle(color = spec.fg, fontFamily = spec.body, fontSize = 17.sp),
          cursorBrush = SolidColor(spec.accent),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
          modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { },
          decorationBox = { inner ->
            if (query.isEmpty()) SkinText("Search here", spec.body, 17.sp, spec.sub)
            inner()
          },
      )
      if (searching) SkinText("…", spec.body, 17.sp, spec.sub)
    } else {
      Box(Modifier.weight(1f).clickable(onClick = onOpen)) {
        SkinText(placeName ?: "Search here", spec.body, 17.sp, if (placeName != null) spec.fg else spec.sub)
      }
    }
    if ((open && query.isNotEmpty()) || (!open && placeName != null)) {
      Spacer(Modifier.width(8.dp))
      Box(Modifier.size(24.dp).clickable(onClick = onClear)) { GameIconView(GameIcon.CLOSE, spec.fg, Modifier.size(24.dp)) }
    }
  }
}

/**
 * The empty search box: Home, Work and the parked car, then recent searches and places from Maps
 * history (a search fills the box again), and a way into everything saved.
 */
@Composable
internal fun SavedList(
    spec: SkinSpec,
    saved: SavedState,
    here: GeographicCoordinate?,
    hint: String?,
    onChoose: (Place) -> Unit,
    onQuery: (String) -> Unit = {},
    onYou: (() -> Unit)? = null,
) {
  fun away(p: Place) = here?.let { formatDistance(distanceMeters(it, p.coordinate)) }
  hint?.let { SkinText(it, spec.body, 15.sp, spec.good, Modifier.padding(14.dp), maxLines = 3) }
  var rows = 0
  saved.home?.let {
    SkinRow(spec, "Home", { onChoose(it) }, subtitle = it.name, icon = GameIcon.HOME, trailing = away(it))
    rows++
  }
  saved.work?.let {
    if (rows > 0) SkinDivider(spec)
    SkinRow(spec, "Work", { onChoose(it) }, subtitle = it.name, icon = GameIcon.WORK, trailing = away(it))
    rows++
  }
  val parking = saved.parking
  if (parking != null) {
    if (rows > 0) SkinDivider(spec)
    val car = Place(PARKED_NAME, parking.near ?: "", parking.at)
    SkinRow(spec, PARKED_NAME, { onChoose(car) }, subtitle = "Saved ${agoText(parking.savedAt)}${parking.near?.let { " · near $it" } ?: ""}", icon = GameIcon.PARKING, trailing = away(car))
    rows++
  } else if (here != null) {
    if (rows > 0) SkinDivider(spec)
    SkinRow(spec, "Save parking here", { AppModule.viewModel.saveParking(here) }, subtitle = "Find your car again later", icon = GameIcon.PARKING)
    rows++
  }
  val history by AppModule.history.entries.collectAsState()
  val recent = remember(history) { AppModule.history.recentForSearch(8) }
  val recentTitle: @Composable () -> Unit = {
    SkinText(spec.title("Recent"), spec.title, spec.titleSize(if (spec.vi) 16.sp else 14.sp, 16.sp), spec.sub, Modifier.padding(start = 14.dp, top = 14.dp, bottom = 2.dp), spacing = if (spec.vi) spec.titleSpacing else androidx.compose.ui.unit.TextUnit.Unspecified)
  }
  if (recent.isNotEmpty()) {
    recentTitle()
    recent.forEachIndexed { i, e ->
      if (i > 0) SkinDivider(spec)
      val place = e.place
      if (place == null) SkinRow(spec, e.text, { onQuery(e.text) }, subtitle = "Searched ${agoText(e.at)}", icon = GameIcon.SEARCH)
      else SkinRow(spec, place.name, { onChoose(place) }, subtitle = place.detail.ifBlank { e.kind.label }, icon = if (e.kind == HistoryKind.DIRECTIONS) GameIcon.ROUTE else GameIcon.RECENT, trailing = away(place))
    }
    rows++
  } else if (saved.recents.isNotEmpty()) {
    recentTitle()
    saved.recents.forEachIndexed { i, p ->
      if (i > 0) SkinDivider(spec)
      SkinRow(spec, p.name, { onChoose(p) }, subtitle = p.detail, icon = GameIcon.RECENT, trailing = away(p))
    }
    rows++
  }
  if (onYou != null && (saved.places.isNotEmpty() || saved.trips.isNotEmpty())) {
    SkinDivider(spec)
    SkinRow(spec, "Your saved places", onYou, subtitle = "${count(saved.places.size, "place")} in ${count(saved.lists.count { l -> saved.inList(l.id).isNotEmpty() }, "list")}", icon = GameIcon.STAR_FILLED)
    rows++
  }
  if (rows == 0 && hint == null) {
    SkinText("Places you save and visit show up here.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp))
  }
}
