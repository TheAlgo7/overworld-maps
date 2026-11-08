package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.map.PARKED_NAME
import com.thealgothrim.overworld.search.HistoryKind
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.SavedList
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.LIST_ICONS
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinRow
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.icon
import com.thealgothrim.overworld.ui.skin.spec
import java.time.LocalDate
import uniffi.ferrostar.GeographicCoordinate

/** The You page's tabs. */
enum class YouTab(val label: String) {
  SAVED("Saved"),
  TIMELINE("Timeline"),
  HISTORY("History"),
}

/** Inside Saved: the lists, or one list's places. */
private sealed interface SavedPage {
  data object Lists : SavedPage

  data class OneList(val id: String) : SavedPage

  data object Labelled : SavedPage

  data object Trips : SavedPage
}

/**
 * You, like Google Maps' You tab in the game menu's look (his pick, 8 Nov): SAVED (Home, Work, the
 * parked car, every list, Labelled and Saved trips), TIMELINE (where you've been, by day and
 * month) and HISTORY (searches, places looked at, directions).
 */
@Composable
fun YouScreen(
    theme: OverworldTheme,
    saved: SavedState,
    here: GeographicCoordinate?,
    start: YouTab,
    onChoose: (Place) -> Unit,
    onSearch: (String) -> Unit,
    onShowDay: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
  val spec = theme.spec
  var tab by rememberSaveable { mutableStateOf(start) }
  // Opened again on another tab (back from a day on the map, say): that tab.
  androidx.compose.runtime.LaunchedEffect(start) { tab = start }
  PauseMenuFrame(theme, "You", onClose) {
    GameTabs(spec, YouTab.entries.map { it.label }, tab.ordinal, { tab = YouTab.entries[it] })
    Gap(12)
    when (tab) {
      YouTab.SAVED -> SavedTab(spec, saved, here, onChoose, onClose)
      YouTab.TIMELINE -> TimelineTab(spec, onChoose, onShowDay)
      YouTab.HISTORY -> HistoryTab(spec, here, onChoose, onSearch)
    }
  }
}

@Composable
private fun SavedTab(spec: SkinSpec, saved: SavedState, here: GeographicCoordinate?, onChoose: (Place) -> Unit, onClose: () -> Unit) {
  var page by remember { mutableStateOf<SavedPage>(SavedPage.Lists) }
  fun away(p: Place) = here?.let { formatDistance(distanceMeters(it, p.coordinate)) }
  when (val p = page) {
    SavedPage.Lists -> {
      // Home and Work, side by side like Google's top of the You tab.
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PlaceTile(spec, "Home", saved.home?.name ?: "Set it from a place's Save", GameIcon.HOME, Modifier.weight(1f)) { saved.home?.let(onChoose) }
        PlaceTile(spec, "Work", saved.work?.name ?: "Set it from a place's Save", GameIcon.WORK, Modifier.weight(1f)) { saved.work?.let(onChoose) }
      }
      Gap(10)
      SkinPanel(spec, Modifier.fillMaxWidth()) {
        val parking = saved.parking
        if (parking != null) {
          val car = Place(PARKED_NAME, parking.near ?: "", parking.at)
          SkinRow(spec, PARKED_NAME, { onChoose(car) }, subtitle = "Saved ${agoText(parking.savedAt)}${parking.near?.let { " · near $it" } ?: ""}", icon = GameIcon.PARKING, trailing = away(car))
        } else {
          SkinRow(spec, "Save parking here", { here?.let { AppModule.viewModel.saveParking(it) } }, subtitle = if (here != null) "Your car's spot, with a time limit and a note" else "Waiting for your location", icon = GameIcon.PARKING)
        }
      }
      SkinSectionTitle(spec, "Your lists")
      SkinPanel(spec, Modifier.fillMaxWidth()) {
        saved.lists.forEachIndexed { i, list ->
          if (i > 0) SkinDivider(spec)
          ListRow(spec, list, saved.inList(list.id).size) { page = SavedPage.OneList(list.id) }
        }
        SkinDivider(spec)
        SkinRow(spec, "Labelled", { page = SavedPage.Labelled }, subtitle = "Private · ${count(saved.labelled.size + listOfNotNull(saved.home, saved.work).size, "place")}", icon = GameIcon.LABEL)
        SkinDivider(spec)
        SkinRow(spec, "Saved trips", { page = SavedPage.Trips }, subtitle = "Private · ${count(saved.trips.size, "trip")}", icon = GameIcon.ROUTE)
      }
      Gap(10)
      NewListForm(spec)
      if (saved.places.isNotEmpty()) {
        SkinSectionTitle(spec, "Recently saved")
        SkinPanel(spec, Modifier.fillMaxWidth()) {
          saved.places.sortedByDescending { it.savedAt }.take(5).forEachIndexed { i, sp ->
            if (i > 0) SkinDivider(spec)
            val place = sp.asPlace()
            SkinRow(spec, place.name, { onChoose(place) }, subtitle = saved.listsOf(sp.place).joinToString(" & ") { listTitle(it.name, it.emoji) }.ifBlank { "Labelled" }, icon = GameIcon.STAR_FILLED, trailing = away(place))
          }
        }
      }
    }
    is SavedPage.OneList -> {
      val list = saved.list(p.id)
      if (list == null) {
        page = SavedPage.Lists
        return
      }
      PageHeader(spec, listTitle(list.name, list.emoji)) { page = SavedPage.Lists }
      val places = saved.inList(list.id)
      SkinPanel(spec, Modifier.fillMaxWidth()) {
        if (places.isEmpty()) SkinText("Nothing here yet. Tap Save on any place and pick ${list.name}.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp), maxLines = 3)
        places.forEachIndexed { i, sp ->
          if (i > 0) SkinDivider(spec)
          val place = sp.asPlace()
          SkinRow(spec, place.name, { onChoose(place) }, subtitle = sp.note ?: place.detail, icon = if (sp.note != null) GameIcon.NOTE else list.icon, trailing = away(place))
        }
      }
      if (!list.builtIn) {
        Gap(10)
        EditListForm(spec, list) { page = SavedPage.Lists }
      }
    }
    SavedPage.Labelled -> {
      PageHeader(spec, "Labelled") { page = SavedPage.Lists }
      SkinPanel(spec, Modifier.fillMaxWidth()) {
        saved.home?.let { SkinRow(spec, "Home", { onChoose(it) }, subtitle = it.name, icon = GameIcon.HOME, trailing = away(it)) }
        saved.work?.let {
          if (saved.home != null) SkinDivider(spec)
          SkinRow(spec, "Work", { onChoose(it) }, subtitle = it.name, icon = GameIcon.WORK, trailing = away(it))
        }
        saved.labelled.forEachIndexed { i, sp ->
          if (i > 0 || saved.home != null || saved.work != null) SkinDivider(spec)
          val place = sp.asPlace()
          SkinRow(spec, place.name, { onChoose(place) }, subtitle = place.detail, icon = GameIcon.LABEL, trailing = away(place))
        }
        if (saved.labelled.isEmpty() && saved.home == null && saved.work == null) {
          SkinText("Give any place your own name from its Save button: \"Bua's home\", \"Gym\", \"The shop\".", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp), maxLines = 3)
        }
      }
    }
    SavedPage.Trips -> {
      PageHeader(spec, "Saved trips") { page = SavedPage.Lists }
      var removing by remember { mutableStateOf<String?>(null) }
      SkinPanel(spec, Modifier.fillMaxWidth()) {
        if (saved.trips.isEmpty()) SkinText("Save a route from its preview (the star beside Start) to start it again with one tap.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp), maxLines = 3)
        saved.trips.forEachIndexed { i, trip ->
          if (i > 0) SkinDivider(spec)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
              SkinRow(
                  spec,
                  trip.title,
                  {
                    onClose()
                    AppModule.viewModel.startSavedTrip(trip)
                  },
                  subtitle = "${trip.mode.label} · saved ${agoText(trip.savedAt)}",
                  icon = trip.mode.icon,
                  trailing = away(trip.to),
              )
            }
            Box(
                Modifier.size(44.dp).clickable {
                  if (removing == trip.id) AppModule.saved.removeTrip(trip.id) else removing = trip.id
                },
                contentAlignment = Alignment.Center,
            ) {
              GameIconView(GameIcon.DELETE, if (removing == trip.id) spec.accent else spec.sub, Modifier.size(20.dp))
            }
          }
        }
      }
    }
  }
}

/** A list in Saved: its picture, name and how many places. */
@Composable
private fun ListRow(spec: SkinSpec, list: SavedList, places: Int, onClick: () -> Unit) {
  SkinRow(spec, list.name, onClick, subtitle = "Private · ${count(places, "place")}", icon = list.icon)
}

@Composable
private fun PlaceTile(spec: SkinSpec, title: String, sub: String, icon: GameIcon, modifier: Modifier, onClick: () -> Unit) {
  val shape = RoundedCornerShape(if (spec.vi) 12.dp else 0.dp)
  Row(modifier.heightIn(min = 64.dp).clip(shape).background(spec.panel).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    GameIconView(icon, spec.accent, Modifier.size(24.dp))
    Spacer(Modifier.width(10.dp))
    Column {
      SkinText(spec.title(title), spec.title, spec.titleSize(17.sp, 18.sp), spec.fg, spacing = spec.titleSpacing)
      SkinText(sub, spec.body, 13.sp, spec.sub, maxLines = 1)
    }
  }
}

/** A sub-page's title with a way back to the lists. */
@Composable
private fun PageHeader(spec: SkinSpec, title: String, onBack: () -> Unit) {
  Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) { GameIconView(GameIcon.BACK, spec.fg, Modifier.size(24.dp)) }
    Spacer(Modifier.width(8.dp))
    SkinText(spec.title(title), spec.title, spec.titleSize(if (spec.vi) 24.sp else 21.sp, 23.sp), spec.fg, spacing = spec.titleSpacing)
  }
}

/** New list: a name and one of the world's icons for it. */
@Composable
private fun NewListForm(spec: SkinSpec) {
  var open by remember { mutableStateOf(false) }
  var name by remember { mutableStateOf("") }
  var icon by remember { mutableStateOf(GameIcon.LIST) }
  if (!open) {
    SkinSecondaryButton(spec, "New list", { open = true }, Modifier.fillMaxWidth(), GameIcon.LIST)
    return
  }
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp)) {
      SkinText(spec.title("New list"), spec.title, spec.titleSize(18.sp, 20.sp), spec.fg, spacing = spec.titleSpacing)
      Gap(8)
      SkinField(spec, name, { name = it }, "Name, e.g. Food or Parking")
      Gap(8)
      IconPicker(spec, LIST_ICONS, icon) { icon = it }
      Gap(10)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinSecondaryButton(spec, "Cancel", { open = false })
        SkinPrimaryButton(
            spec,
            "Create",
            {
              if (name.isNotBlank()) {
                AppModule.saved.createList(name, icon.name)
                name = ""
                icon = GameIcon.LIST
                open = false
              }
            },
            Modifier.weight(1f),
        )
      }
    }
  }
}

/** Rename one of your lists, change its icon, or delete it (two taps). */
@Composable
private fun EditListForm(spec: SkinSpec, list: SavedList, onDeleted: () -> Unit) {
  var name by remember(list.id) { mutableStateOf(list.name) }
  var icon by remember(list.id) { mutableStateOf(list.icon) }
  var deleting by remember { mutableStateOf(false) }
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(14.dp)) {
      SkinField(spec, name, { name = it }, "List name")
      Gap(8)
      IconPicker(spec, LIST_ICONS, icon) { icon = it }
      Gap(10)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinSecondaryButton(
            spec,
            if (deleting) "Tap again" else "Delete list",
            {
              if (deleting) {
                AppModule.saved.deleteList(list.id)
                onDeleted()
              } else deleting = true
            },
            icon = GameIcon.DELETE,
        )
        SkinPrimaryButton(spec, "Save", { if (name.isNotBlank()) AppModule.saved.editList(list.id, name, icon.name) }, Modifier.weight(1f), GameIcon.EDIT)
      }
    }
  }
}

/** Maps history: searches, places looked at and directions, newest first; remove one, or all. */
@Composable
private fun HistoryTab(spec: SkinSpec, here: GeographicCoordinate?, onChoose: (Place) -> Unit, onSearch: (String) -> Unit) {
  val entries by AppModule.history.entries.collectAsState()
  val keeping by AppModule.prefs.keepHistory.collectAsState()
  var filter by rememberSaveable { mutableStateOf<HistoryKind?>(null) }
  var clearing by remember { mutableStateOf(false) }
  val filters = listOf<HistoryKind?>(null) + HistoryKind.entries
  ChoiceRow(spec, filters, filter, { it?.label?.substringBefore(' ')?.let { w -> if (w == "Got") "Directions" else w } ?: "All" }, { filter = it })
  Gap(10)
  if (!keeping) SkinHelp(spec, "History isn't being kept. Turn it on in Settings > Privacy > Keep search history.")
  val shown = entries.filter { filter == null || it.kind == filter }
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    if (shown.isEmpty()) SkinText("Nothing here yet. Searches, places you look at and places you get directions to show up here.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp), maxLines = 3)
    shown.take(120).forEachIndexed { i, e ->
      if (i > 0) SkinDivider(spec)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
          val place = e.place
          SkinRow(
              spec,
              e.text,
              { if (place != null) onChoose(place) else onSearch(e.text) },
              subtitle = "${e.kind.label} ${agoText(e.at)}",
              icon = when (e.kind) { HistoryKind.SEARCHED -> GameIcon.SEARCH; HistoryKind.VIEWED -> GameIcon.PIN; HistoryKind.DIRECTIONS -> GameIcon.ROUTE },
              trailing = place?.let { p -> here?.let { formatDistance(distanceMeters(it, p.coordinate)) } },
          )
        }
        Box(Modifier.size(44.dp).clickable { AppModule.history.remove(e) }, contentAlignment = Alignment.Center) { GameIconView(GameIcon.CLOSE, spec.sub, Modifier.size(18.dp)) }
      }
    }
  }
  if (entries.isNotEmpty()) {
    Gap(10)
    SkinSecondaryButton(
        spec,
        if (clearing) "Tap again to clear all history" else "Clear all history",
        {
          if (clearing) {
            AppModule.history.clear()
            clearing = false
          } else clearing = true
        },
        Modifier.fillMaxWidth(),
        GameIcon.DELETE,
    )
  }
}
