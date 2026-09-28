package com.thealgothrim.overworld.ui

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.PlannerState
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinChip
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinRoundButton
import com.thealgothrim.overworld.ui.skin.SkinRow
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import uniffi.ferrostar.GeographicCoordinate

/**
 * Not navigating: search bar with settings (top), shortcut chips, compass and locate buttons
 * (right), and a place sheet with Directions and Save (bottom). Same placement as Google Maps.
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
    onChoose: (Place) -> Unit,
    onClear: () -> Unit,
    onDirections: () -> Unit,
    onSettings: () -> Unit,
    onLocate: () -> Unit,
    onNorthUp: () -> Unit,
) {
  val focus = LocalFocusManager.current
  val destination = planner.destination
  var shortcutHint by remember { mutableStateOf<String?>(null) }

  fun openSaved(hint: String? = null) {
    shortcutHint = hint
    onSearchOpen(true)
  }

  // ---- top: search bar + settings, then chips or results
  Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      SearchBar(
          spec = spec,
          query = planner.query,
          placeName = destination?.name.takeIf { !searchOpen },
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
          modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.width(10.dp))
      SkinRoundButton(spec, GameIcon.SETTINGS, onSettings)
    }

    if (searchOpen) {
      Spacer(Modifier.height(8.dp))
      SkinPanel(spec, Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        if (planner.query.isBlank()) {
          SavedList(spec, saved, here, shortcutHint, onChoose)
        } else {
          val results = planner.results
          if (results.isEmpty()) {
            SkinText(if (planner.searching) "Searching…" else "No places found.", spec.body, 16.sp, spec.sub, Modifier.padding(16.dp))
          }
          results.forEachIndexed { i, place ->
            if (i > 0) SkinDivider(spec)
            SkinRow(
                spec,
                place.name,
                { focus.clearFocus(); onChoose(place) },
                subtitle = place.detail,
                icon = GameIcon.PIN,
                highlighted = i == 0,
                trailing = here?.let { formatDistance(distanceMeters(it, place.coordinate)) },
            )
          }
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
        SkinChip(spec, "Saved", GameIcon.STAR) { openSaved() }
      }
    }

    if (!searchOpen && rotated) {
      Spacer(Modifier.height(10.dp))
      SkinRoundButton(spec, GameIcon.NORTH, onNorthUp, Modifier.align(Alignment.End))
    }
  }

  // ---- bottom: locate button, then the place sheet
  if (!searchOpen) {
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
      SkinRoundButton(spec, GameIcon.LOCATE, onLocate, Modifier.align(Alignment.End).padding(end = 14.dp, bottom = 14.dp))
      if (destination != null) {
        PlaceSheet(spec, destination, planner, saved, here, onDirections)
      }
    }
  }
}

@Composable
private fun SearchBar(
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
          keyboardActions = KeyboardActions(onSearch = { }),
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

@Composable
private fun SavedList(spec: SkinSpec, saved: SavedState, here: GeographicCoordinate?, hint: String?, onChoose: (Place) -> Unit) {
  fun away(p: Place) = here?.let { formatDistance(distanceMeters(it, p.coordinate)) }
  hint?.let { SkinText(it, spec.body, 15.sp, spec.good, Modifier.padding(14.dp), maxLines = 3) }
  var rows = 0
  saved.home?.let { SkinRow(spec, "Home", { onChoose(it) }, subtitle = it.name, icon = GameIcon.HOME, trailing = away(it)); rows++ }
  saved.work?.let {
    if (rows > 0) SkinDivider(spec)
    SkinRow(spec, "Work", { onChoose(it) }, subtitle = it.name, icon = GameIcon.WORK, trailing = away(it))
    rows++
  }
  saved.starred.forEach {
    if (rows > 0) SkinDivider(spec)
    SkinRow(spec, it.name, { onChoose(it) }, subtitle = it.detail, icon = GameIcon.STAR_FILLED, trailing = away(it))
    rows++
  }
  if (saved.recents.isNotEmpty()) {
    SkinText(spec.title("Recent"), spec.title, if (spec.gta) 14.sp else 16.sp, spec.sub, Modifier.padding(start = 14.dp, top = 14.dp, bottom = 2.dp))
    saved.recents.forEachIndexed { i, p ->
      if (i > 0) SkinDivider(spec)
      SkinRow(spec, p.name, { onChoose(p) }, subtitle = p.detail, icon = GameIcon.RECENT, trailing = away(p))
    }
    rows++
  }
  if (rows == 0 && hint == null) {
    SkinText("Places you save and visit show up here.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp))
  }
}

/** The place sheet: name, address, distance, and Directions / Save. */
@Composable
private fun PlaceSheet(
    spec: SkinSpec,
    place: Place,
    planner: PlannerState,
    saved: SavedState,
    here: GeographicCoordinate?,
    onDirections: () -> Unit,
) {
  var saveMenu by remember(place) { mutableStateOf(false) }
  val store = AppModule.saved
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      SkinText(spec.title(place.name), spec.title, if (spec.gta) 24.sp else 28.sp, spec.fg, maxLines = 2, spacing = if (spec.gta) 0.sp else 1.sp)
      if (place.detail.isNotBlank()) SkinText(place.detail, spec.body, 15.sp, spec.sub, Modifier.padding(top = 2.dp), maxLines = 2)
      val tags =
          listOfNotNull(
              here?.let { "${formatDistance(distanceMeters(it, place.coordinate))} away" },
              "Home".takeIf { saved.home?.let { h -> h.name == place.name } == true },
              "Work".takeIf { saved.work?.let { w -> w.name == place.name } == true },
              "Saved".takeIf { saved.isStarred(place) },
          )
      if (tags.isNotEmpty()) SkinText(tags.joinToString("  ·  "), spec.body, 14.sp, spec.good, Modifier.padding(top = 6.dp))
      planner.error?.let { SkinText(it, spec.body, 14.sp, spec.accent, Modifier.padding(top = 8.dp), maxLines = 2) }
      Spacer(Modifier.height(14.dp))
      if (saveMenu) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SkinSecondaryButton(spec, "Home", { store.setHome(place); saveMenu = false }, Modifier.weight(1f), GameIcon.HOME)
          SkinSecondaryButton(spec, "Work", { store.setWork(place); saveMenu = false }, Modifier.weight(1f), GameIcon.WORK)
          SkinSecondaryButton(
              spec,
              if (saved.isStarred(place)) "Unsave" else "Star",
              { store.toggleStar(place); saveMenu = false },
              Modifier.weight(1f),
              if (saved.isStarred(place)) GameIcon.STAR_FILLED else GameIcon.STAR,
          )
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          SkinPrimaryButton(spec, if (planner.routing) "Finding route" else "Directions", onDirections, Modifier.weight(1f), GameIcon.ROUTE)
          SkinSecondaryButton(spec, "Save", { saveMenu = true }, icon = if (saved.isStarred(place)) GameIcon.STAR_FILLED else GameIcon.STAR)
        }
      }
    }
  }
}
