package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.PlannerState
import com.thealgothrim.overworld.map.PARKED_NAME
import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.search.ParkingSpot
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.search.samePlace
import com.thealgothrim.overworld.search.samePoint
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.icon
import com.thealgothrim.overworld.ui.skin.sheetFloat
import uniffi.ferrostar.GeographicCoordinate

/** Names a found place gets when it has none of its own: saving one asks for a proper name. */
private val UNNAMED = setOf("Dropped pin", "Pinned point", "Destination", "Shared place")

/**
 * The place card: its name (yours, if you gave it one), address, distance, which of your lists
 * it's in and your note, with Directions and Save. Save opens the save panel in its place; the
 * parked car has its own card.
 */
@Composable
internal fun PlaceSheet(
    spec: SkinSpec,
    place: Place,
    planner: PlannerState,
    saved: SavedState,
    here: GeographicCoordinate?,
    onDirections: () -> Unit,
) {
  val parking = saved.parking
  if (parking != null && place.name == PARKED_NAME && parking.at.samePoint(place.coordinate)) {
    ParkingSheet(spec, parking, here)
    return
  }
  var saving by remember(place) { mutableStateOf(false) }
  SkinPanel(spec, Modifier.sheetFloat(spec).fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      if (saving) {
        SavePanel(spec, place, saved) { saving = false }
        return@Column
      }
      val mine = saved.savedFor(place)
      val shown = mine?.asPlace() ?: place
      SkinText(spec.title(shown.name), spec.title, spec.titleSize(if (spec.vi) 27.sp else 24.sp, 28.sp), spec.fg, maxLines = 2, spacing = spec.titleSpacing)
      if (shown.detail.isNotBlank()) SkinText(shown.detail, spec.body, 15.sp, spec.sub, Modifier.padding(top = 2.dp), maxLines = 2)
      val lists = saved.listsOf(place)
      val tags =
          listOfNotNull(
              here?.let { "${formatDistance(distanceMeters(it, place.coordinate))} away" },
              // By the spot, not the name: with Home saved from a dropped pin, every "Dropped pin" said Home.
              "Home".takeIf { saved.home?.samePlace(place) == true },
              "Work".takeIf { saved.work?.samePlace(place) == true },
              lists.takeIf { it.isNotEmpty() }?.let { l -> "Saved in " + l.joinToString(" & ") { listTitle(it.name, it.emoji) } },
              "Labelled".takeIf { mine?.label != null && lists.isEmpty() },
          )
      if (tags.isNotEmpty()) SkinText(tags.joinToString("  ·  "), spec.body, 14.sp, spec.good, Modifier.padding(top = 6.dp), maxLines = 2)
      mine?.note?.let { note ->
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
          GameIconView(GameIcon.NOTE, spec.sub, Modifier.size(18.dp).padding(top = 1.dp))
          Spacer(Modifier.width(8.dp))
          SkinText(note, spec.body, 15.sp, spec.fg, maxLines = 3)
        }
      }
      planner.error?.let { SkinText(it, spec.body, 14.sp, spec.accent, Modifier.padding(top = 8.dp), maxLines = 2) }
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinPrimaryButton(spec, if (planner.routing) "Finding route" else "Directions", onDirections, Modifier.weight(1f), GameIcon.ROUTE)
        SkinSecondaryButton(spec, if (mine != null) "Saved" else "Save", { saving = true }, icon = if (mine != null) GameIcon.STAR_FILLED else GameIcon.STAR)
      }
    }
  }
}

/** A list's name with its emoji in front, if it has one. */
fun listTitle(name: String, emoji: String?) = if (emoji.isNullOrBlank()) name else "$emoji $name"

/**
 * Save to: your own name for the place, Home or Work, each list lit in the accent when the place
 * is in it (a tap puts it in or takes it out), a note, and a new list of your own. The name and
 * note are kept on Done.
 */
@Composable
private fun SavePanel(spec: SkinSpec, place: Place, saved: SavedState, onDone: () -> Unit) {
  val store = AppModule.saved
  val mine = saved.savedFor(place)
  // The place as it was found, so its own name stays underneath yours.
  val found = mine?.place ?: place
  val unnamed = found.name in UNNAMED
  var label by remember(place) { mutableStateOf(mine?.label ?: "") }
  var note by remember(place) { mutableStateOf(mine?.note ?: "") }
  var newList by remember { mutableStateOf(false) }
  var listName by remember { mutableStateOf("") }
  var listEmoji by remember { mutableStateOf("") }
  var removing by remember { mutableStateOf(false) }
  val labelFocus = remember { FocusRequester() }
  // A dropped pin has no name of its own: the name box is where saving starts.
  LaunchedEffect(Unit) { if (unnamed) runCatching { labelFocus.requestFocus() } }

  fun finish() {
    store.setLabel(found, label)
    store.setNote(found, note)
    onDone()
  }

  Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      SkinText(spec.title("Save to"), spec.title, spec.titleSize(if (spec.vi) 24.sp else 22.sp, 24.sp), spec.fg, Modifier.weight(1f), spacing = spec.titleSpacing)
      Box(Modifier.size(32.dp).clickable { onDone() }, contentAlignment = Alignment.Center) { GameIconView(GameIcon.CLOSE, spec.fg, Modifier.size(22.dp)) }
    }
    Gap(10)
    SkinField(spec, label, { label = it }, if (unnamed) "Name this place, e.g. Bua's home" else "Your name for it (${found.name})", Modifier.focusRequester(labelFocus))
    Gap(10)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val isHome = saved.home?.samePlace(place) == true
      val isWork = saved.work?.samePlace(place) == true
      ChoiceChip(spec, "Home", isHome, { store.setHome(if (isHome) null else found) }, Modifier.weight(1f), GameIcon.HOME)
      ChoiceChip(spec, "Work", isWork, { store.setWork(if (isWork) null else found) }, Modifier.weight(1f), GameIcon.WORK)
    }
    Gap(10)
    val inLists = mine?.lists.orEmpty()
    saved.lists.forEach { list ->
      val on = list.id in inLists
      ListToggleRow(spec, list.name, list.emoji, list.icon, saved.inList(list.id).size, on) { store.setInList(found, list.id, !on) }
      Gap(6)
    }
    if (newList) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SkinField(spec, listEmoji, { listEmoji = it.take(2) }, "🙂", Modifier.width(64.dp))
        SkinField(spec, listName, { listName = it }, "List name, e.g. Food", Modifier.weight(1f))
      }
      Gap(8)
      SkinSecondaryButton(
          spec,
          "Create and add",
          {
            if (listName.isNotBlank()) {
              val id = store.createList(listName, listEmoji)
              store.setInList(found, id, true)
              listName = ""
              listEmoji = ""
              newList = false
            }
          },
          Modifier.fillMaxWidth(),
          GameIcon.LIST,
      )
    } else {
      SkinSecondaryButton(spec, "New list", { newList = true }, Modifier.fillMaxWidth(), GameIcon.LIST)
    }
    Gap(10)
    SkinField(spec, note, { note = it }, "Add a note: gate code, which floor, what to order", singleLine = false)
    Gap(14)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      if (mine != null) {
        SkinSecondaryButton(
            spec,
            if (removing) "Tap again" else "Unsave",
            {
              if (removing) {
                store.remove(found)
                onDone()
              } else removing = true
            },
            icon = GameIcon.DELETE,
        )
      }
      SkinPrimaryButton(spec, "Done", ::finish, Modifier.weight(1f))
    }
  }
}

/** A list in the save panel: lit in the accent when the place is in it. */
@Composable
private fun ListToggleRow(spec: SkinSpec, name: String, emoji: String?, icon: GameIcon, places: Int, on: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(if (spec.vi) 10.dp else 0.dp)
  val ink = if (on) spec.onAccent else spec.fg
  Row(
      Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(shape).background(if (on) spec.accent else spec.fg.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!emoji.isNullOrBlank()) SkinText(emoji, spec.body, 18.sp, ink) else GameIconView(icon, ink, Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    SkinText(name, spec.body, 17.sp, ink, Modifier.weight(1f))
    SkinText(if (on) "In list" else count(places, "place"), spec.body, 14.sp, if (on) ink else spec.sub)
  }
}

/**
 * The parked car, like Google's "You parked here": when it was left and near what, a time limit
 * with reminders, a note (level 2, bay 41), a walk back to it, and Clear.
 */
@Composable
internal fun ParkingSheet(spec: SkinSpec, spot: ParkingSpot, here: GeographicCoordinate?) {
  val vm = AppModule.viewModel
  var note by remember(spot.savedAt) { mutableStateOf(spot.note ?: "") }
  var clearing by remember { mutableStateOf(false) }
  SkinPanel(spec, Modifier.sheetFloat(spec).fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        GameIconView(GameIcon.PARKING, spec.accent, Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          SkinText(spec.title(PARKED_NAME), spec.title, spec.titleSize(if (spec.vi) 24.sp else 22.sp, 24.sp), spec.fg, spacing = spec.titleSpacing)
          SkinText(
              listOfNotNull("Saved ${agoText(spot.savedAt)}", spot.near?.let { "near $it" }, here?.let { "${formatDistance(distanceMeters(it, spot.at))} away" }).joinToString("  ·  "),
              spec.body, 14.sp, spec.sub, maxLines = 2,
          )
        }
      }
      Gap(12)
      SkinText(spot.until?.let { u -> if (u > System.currentTimeMillis()) "Time left: ${formatDuration((u - System.currentTimeMillis()) / 1000.0)}" else "Time's up" } ?: "Time limit", spec.body, 15.sp, if (spot.until != null && spot.until < System.currentTimeMillis()) spec.accent else spec.fg)
      Gap(6)
      val options = listOf<Int?>(null, 30, 60, 120, 240)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { m ->
          ChoiceChip(spec, m?.let { if (it < 60) "$it min" else "${it / 60} h" } ?: "Off", m == null && spot.until == null, { vm.setParkingTime(m) }, Modifier.weight(1f))
        }
      }
      Gap(10)
      SkinField(spec, note, { note = it; AppModule.saved.updateParking { p -> p.copy(note = it.ifBlank { null }) } }, "Note: level, bay number, landmark")
      Gap(14)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinPrimaryButton(spec, "Walk there", { vm.choose(Place(PARKED_NAME, spot.near ?: "", spot.at)); vm.directions(TravelMode.WALK) }, Modifier.weight(1f), GameIcon.WALK)
        SkinSecondaryButton(spec, if (clearing) "Tap again" else "Clear", { if (clearing) { vm.clearParking(); vm.clearDestination() } else clearing = true }, icon = GameIcon.DELETE)
      }
    }
  }
}

/** "12 min ago", "3 h ago", "yesterday", or the date. */
fun agoText(at: Long): String {
  val minutes = ((System.currentTimeMillis() - at) / 60_000).coerceAtLeast(0)
  return when {
    minutes < 1 -> "just now"
    minutes < 60 -> "$minutes min ago"
    minutes < 24 * 60 -> "${minutes / 60} h ago"
    minutes < 48 * 60 -> "yesterday"
    else -> java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH))
  }
}
