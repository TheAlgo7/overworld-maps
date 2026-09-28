package com.thealgothrim.overworld.ui.rdr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.composeui.views.components.maneuver.ManeuverImage
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.PlannerState
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.arrivalClock
import com.thealgothrim.overworld.ui.distanceMeters
import com.thealgothrim.overworld.ui.formatDistance
import com.thealgothrim.overworld.ui.formatDuration
import com.thealgothrim.overworld.ui.lengthMeters
import com.thealgothrim.overworld.ui.turnSentence
import uniffi.ferrostar.GeographicCoordinate

// ================================================================ driving HUD

/**
 * RDR2 HUD while driving: help text on an ink band (top-left), time to go (top-right),
 * cores for trip progress and the next turn (bottom-left), "Ride to <place>." objective,
 * location and a menu prompt (bottom-right), the radar's N, and a pause-menu style trip menu.
 */
@Composable
fun BoxScope.RdrDriveHud(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    area: String?,
    mapBearing: () -> Double,
    following: Boolean,
    onMute: () -> Unit,
    onOverview: () -> Unit,
    onTheme: () -> Unit,
    onEnd: () -> Unit,
) {
  var menuOpen by remember { mutableStateOf(false) }
  var selected by remember { mutableIntStateOf(0) }
  val progress = uiState.progress
  val route = uiState.routeGeometry
  val routeLength = remember(route) { route?.let(::lengthMeters) ?: 0.0 }

  if (!menuOpen) RdrNorth(mapBearing, inset = 6.dp, top = 150.dp, bottom = 160.dp)

  Column(Modifier.align(Alignment.TopStart).widthIn(max = if (menuOpen) 330.dp else 270.dp)) {
    if (menuOpen) {
      Column(Modifier.inkBand().padding(start = 14.dp, end = 30.dp, top = 12.dp, bottom = 12.dp)) {
        RdrMenuTitle("OVERWORLD", "Journey", "${selected + 1}/5")
        fun pick(i: Int, action: () -> Unit) {
          selected = i
          action()
        }
        RdrMenuRow("Voice guidance", selected == 0, { pick(0, onMute) }, checked = uiState.isMuted != true)
        RdrMenuRow(if (following) "Route overview" else "Follow me", selected == 1, { pick(1, onOverview) })
        RdrMenuRow("Theme", selected == 2, { pick(2, onTheme) }, value = theme.name)
        RdrMenuRow("End journey", selected == 3, { pick(3) { menuOpen = false; onEnd() } }, labelColor = Rdr.Red)
        RdrMenuRow("Close", selected == 4, { pick(4) { menuOpen = false } })
      }
    } else {
      RdrHelpBox {
        uiState.visualInstruction?.primaryContent?.let {
          Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { ManeuverImage(it, tint = Rdr.White) }
          Spacer(Modifier.width(10.dp))
        }
        RdrHelpLine(if (uiState.isCalculatingNewRoute == true) "Finding a new trail." else turnSentence(uiState, uiState.destination))
      }
    }
  }

  if (progress != null && !menuOpen) {
    Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
      RdrText(formatDuration(progress.durationRemaining).uppercase(), 30.sp, align = TextAlign.End, spacing = 1.sp)
      RdrText(arrivalClock(progress.durationRemaining), 18.sp, color = Rdr.Objective, font = GameFonts.hapna, align = TextAlign.End)
    }
  }

  uiState.destination?.takeIf { it.isNotBlank() }?.let { dest ->
    RdrText(
        buildAnnotatedString {
          append("Ride to ")
          withStyle(SpanStyle(color = Rdr.Objective)) { append(dest) }
          append(".")
        },
        19.sp,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        font = GameFonts.hapna,
        align = TextAlign.Center,
    )
  }

  // Bottom-left: the cores and distance to go.
  if (progress != null) {
    Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
      RdrCore((if (routeLength > 0) 1 - progress.distanceRemaining / routeLength else 0.0).toFloat(), CoreIcon.ROUTE)
      Spacer(Modifier.width(8.dp))
      val stepLength = uiState.remainingSteps?.firstOrNull()?.distance ?: 0.0
      RdrCore((if (stepLength > 0) 1 - progress.distanceToNextManeuver / stepLength else 0.0).toFloat(), CoreIcon.TURN, ring = Rdr.Stamina)
      Spacer(Modifier.width(10.dp))
      RdrText(formatDistance(progress.distanceRemaining).uppercase(), 22.sp, spacing = 1.sp)
    }
  }

  Column(Modifier.align(Alignment.BottomEnd).padding(bottom = 60.dp).widthIn(max = 220.dp), horizontalAlignment = Alignment.End) {
    val street = uiState.currentStepRoadName?.takeIf { it.isNotBlank() }
    val line = listOfNotNull(street, area).joinToString(",  ")
    if (line.isNotEmpty()) RdrText(line.uppercase(), 15.sp, align = TextAlign.End, maxLines = 2, color = Rdr.GreyLight, spacing = 1.sp)
  }
  RdrPrompts(
      listOf(RdrPrompt("M", if (menuOpen) "Close" else "Menu") { menuOpen = !menuOpen; selected = 0 }),
      modifier = Modifier.align(Alignment.BottomEnd),
  )
}

// ================================================================ planner

/** RDR2 pause-menu style destination planner. */
@Composable
fun BoxScope.RdrPlanner(
    theme: OverworldTheme,
    planner: PlannerState,
    testDrive: Boolean,
    here: GeographicCoordinate?,
    onQuery: (String) -> Unit,
    onChoose: (Place) -> Unit,
    onClear: () -> Unit,
    onGo: () -> Unit,
    onTestDrive: (Boolean) -> Unit,
    onTheme: () -> Unit,
    onLocate: () -> Unit,
    carGameHud: Boolean = true,
    onCarHud: (Boolean) -> Unit = {},
) {
  val focus = LocalFocusManager.current
  var selected by remember(planner.destination) { mutableIntStateOf(0) }
  val destination = planner.destination

  Column(Modifier.align(Alignment.TopStart).fillMaxWidth().widthIn(max = 420.dp).inkBand().padding(start = 14.dp, end = 34.dp, top = 12.dp, bottom = 12.dp)) {
    if (destination == null) {
      val results = planner.results.take(7)
      val total = 1 + if (results.isEmpty()) 3 else results.size
      RdrMenuTitle("OVERWORLD", "Set a waypoint", "${selected + 1}/$total")
      RdrSearchRow(planner.query, onQuery, planner.searching, onDone = { focus.clearFocus() })
      if (results.isNotEmpty()) {
        results.forEachIndexed { i, place ->
          RdrMenuRow(
              place.name,
              selected == i + 1,
              {
                selected = i + 1
                focus.clearFocus()
                onChoose(place)
              },
              hint = place.detail.split(",").map { it.trim() }.firstOrNull { it.isNotBlank() },
          )
        }
        RdrDescription(results.getOrNull(selected - 1)?.detail?.ifBlank { null } ?: "Choose a place to mark it on your map.")
      } else {
        RdrMenuRow("Test drive", selected == 1, { selected = 1; onTestDrive(!testDrive) }, checked = testDrive)
        RdrMenuRow("Theme", selected == 2, { selected = 2; onTheme() }, value = theme.name)
        RdrMenuRow("Car screen", selected == 3, { selected = 3; onCarHud(!carGameHud) }, value = if (carGameHud) "Game HUD" else "Android Auto")
        RdrDescription(
            if (planner.query.isNotBlank() && !planner.searching) "Nothing by that name on the map."
            else "Search for a place, or press and hold the map to mark a waypoint."
        )
      }
    } else {
      RdrMenuTitle("OVERWORLD", "Waypoint", "${selected + 1}/4")
      RdrMenuRow(if (planner.routing) "Plotting a route" else "Ride to ${destination.name}", selected == 0, { selected = 0; onGo() })
      RdrMenuRow("Test drive", selected == 1, { selected = 1; onTestDrive(!testDrive) }, checked = testDrive)
      RdrMenuRow("Theme", selected == 2, { selected = 2; onTheme() }, value = theme.name)
      RdrMenuRow("Clear waypoint", selected == 3, { selected = 3; onClear() })
      val details = listOf(destination.name, destination.detail).filter { it.isNotBlank() }.joinToString("\n")
      RdrDescription(planner.error ?: details, color = if (planner.error != null) Rdr.Red else Rdr.GreyLight)
    }
  }

  if (destination != null && here != null) {
    RdrText(
        formatDistance(distanceMeters(here, destination.coordinate)).uppercase(),
        22.sp,
        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 8.dp),
        spacing = 1.sp,
    )
  }

  RdrPrompts(listOf(RdrPrompt("L", "Locate", onLocate)), modifier = Modifier.align(Alignment.BottomEnd))
}

@Composable
private fun RdrSearchRow(query: String, onQuery: (String) -> Unit, searching: Boolean, onDone: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().height(46.dp).roughBox().padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = Rdr.White, fontFamily = GameFonts.hapna, fontSize = 17.sp),
        cursorBrush = SolidColor(Rdr.Red),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        modifier = Modifier.weight(1f),
        decorationBox = { inner ->
          if (query.isEmpty()) Text("Search for a place", color = Rdr.Grey, fontFamily = GameFonts.hapna, fontSize = 17.sp)
          inner()
        },
    )
    RdrText(if (searching) "..." else "SEARCH", 16.sp, color = Rdr.Grey, spacing = 1.sp)
  }
}
