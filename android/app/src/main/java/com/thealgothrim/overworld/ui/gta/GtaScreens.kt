package com.thealgothrim.overworld.ui.gta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
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
import com.thealgothrim.overworld.ui.formatDistance
import com.thealgothrim.overworld.ui.formatDuration
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import uniffi.ferrostar.GeographicCoordinate

// ================================================================ driving HUD

/**
 * GTA V HUD while driving: help-text turn instruction (top-left), money-counter ETA (top-right),
 * waypoint distance and health/armour bars (bottom-left), "Street | Area" (bottom-right), a
 * mission-objective subtitle, the radar's N badge, and the interaction menu for trip controls.
 */
@Composable
fun BoxScope.GtaDriveHud(
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
  val destination = route?.lastOrNull()
  val routeLength = remember(route) { route?.let(::lengthMeters) ?: 0.0 }

  if (!menuOpen) GtaNorthBadge(mapBearing, inset = 4.dp, top = 150.dp, bottom = 150.dp)

  // Top-left: help text, or the interaction menu when open.
  Column(Modifier.align(Alignment.TopStart).widthIn(max = 300.dp)) {
    if (menuOpen) {
      GtaMenuHeader("OVERWORLD")
      GtaMenuSubheader("Trip", "${selected + 1}/5")
      val rows = 5
      fun pick(i: Int, action: () -> Unit) {
        selected = i
        action()
      }
      GtaMenuRow("Voice guidance", selected == 0, { pick(0, onMute) }, checked = uiState.isMuted != true)
      GtaMenuRow(if (following) "Route overview" else "Follow me", selected == 1, { pick(1, onOverview) })
      GtaMenuRow("Theme", selected == 2, { pick(2, onTheme) }, value = theme.name)
      GtaMenuRow("End trip", selected == 3, { pick(3) { menuOpen = false; onEnd() } }, labelColor = Gta.Red)
      GtaMenuRow("Close", selected == rows - 1, { pick(4) { menuOpen = false } })
    } else {
      // Narrow enough to clear the money-counter ETA on a phone.
      GtaHelpText(Modifier.widthIn(max = 250.dp)) {
        uiState.visualInstruction?.primaryContent?.let {
          Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { ManeuverImage(it, tint = Gta.White) }
          Spacer(Modifier.width(10.dp))
        }
        GtaHelpLine(
            if (uiState.isCalculatingNewRoute == true) "Recalculating route."
            else gtaInstruction(uiState, uiState.destination)
        )
      }
    }
  }

  // Top-right: the money counter, reused for time to go and arrival time.
  if (progress != null && !menuOpen) {
    Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
      GtaText(formatDuration(progress.durationRemaining).uppercase(), 34.sp, font = GameFonts.price, align = TextAlign.End)
      GtaText(arrivalClock(progress.durationRemaining), 22.sp, color = Gta.Health, font = GameFonts.price, align = TextAlign.End)
    }
  }

  // Mission objective subtitle.
  uiState.destination?.takeIf { it.isNotBlank() }?.let { dest ->
    GtaText(
        buildAnnotatedString {
          append("Go to ")
          withStyle(SpanStyle(color = Gta.Yellow)) { append(dest) }
          append(".")
        },
        19.sp,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
        font = GameFonts.menu,
        align = TextAlign.Center,
    )
  }

  // Bottom-left: waypoint distance and the bars.
  Column(Modifier.align(Alignment.BottomStart).width(170.dp)) {
    if (progress != null && destination != null) {
      GtaWaypointDistance(
          formatDistance(progress.distanceRemaining).replace(" ", ""),
          arrowDegrees = relativeBearing(uiState.location?.coordinates, destination, mapBearing()),
      )
      Spacer(Modifier.height(6.dp))
      val stepLength = uiState.remainingSteps?.firstOrNull()?.distance ?: 0.0
      GtaHudBars(
          green = if (routeLength > 0) (1 - progress.distanceRemaining / routeLength).toFloat() else 0f,
          blue = if (stepLength > 0) (1 - progress.distanceToNextManeuver / stepLength).toFloat() else 0f,
          modifier = Modifier.fillMaxWidth(),
      )
    }
  }

  // Bottom-right: interaction-menu prompt, then Street | Area.
  Column(Modifier.align(Alignment.BottomEnd).widthIn(max = 200.dp), horizontalAlignment = Alignment.End) {
    GtaInstructionalButtons(
        listOf(GtaPrompt("M", if (menuOpen) "Close" else "Menu") { menuOpen = !menuOpen; selected = 0 })
    )
    Spacer(Modifier.height(8.dp))
    val street = uiState.currentStepRoadName?.takeIf { it.isNotBlank() }
    val line = listOfNotNull(street, area).joinToString("  |  ")
    if (line.isNotEmpty()) GtaText(line, 21.sp, align = TextAlign.End, maxLines = 2)
  }
}

/** Turns Ferrostar's maneuver data into a GTA-style help-text sentence. */
private fun gtaInstruction(uiState: NavigationUiState, destination: String?): String {
  val content = uiState.visualInstruction?.primaryContent ?: return "Follow the route."
  // Named roads come through as the road name; unnamed ones as a sentence ("Bear left.").
  val road = content.text.trim().takeUnless { it.endsWith(".") }.orEmpty()
  val type = content.maneuverType?.name.orEmpty()
  val mod = content.maneuverModifier?.name.orEmpty()
  val side = when { "LEFT" in mod -> "left"; "RIGHT" in mod -> "right"; else -> "" }
  val dist = uiState.progress?.distanceToNextManeuver?.let(::formatDistance)
  if (type == "ARRIVE") {
    val where = destination?.takeIf { it.isNotBlank() } ?: road.ifBlank { "your destination" }
    return if (dist != null) "In $dist, arrive at $where." else "Arrive at $where."
  }
  val verb =
      when (type) {
        "DEPART" -> "head out"
        "MERGE" -> "merge"
        "ON_RAMP" -> "take the ramp"
        "OFF_RAMP" -> "take the exit"
        "FORK" -> "keep $side"
        "ROUNDABOUT", "ROTARY", "ROUNDABOUT_TURN" -> "at the roundabout, take the exit"
        "END_OF_ROAD" -> "turn $side"
        "CONTINUE", "NEW_NAME" -> if (mod.startsWith("SLIGHT")) "keep $side" else "continue"
        else ->
            when {
              mod == "U_TURN" -> "make a U-turn"
              mod.startsWith("SLIGHT") -> "bear $side"
              mod.startsWith("SHARP") -> "turn sharp $side"
              side.isNotEmpty() -> "turn $side"
              else -> "continue straight"
            }
      }.trim()
  val onto = if (road.isNotBlank()) " onto $road" else ""
  return if (dist != null) "In $dist, $verb$onto." else "${verb.replaceFirstChar { it.uppercase() }}$onto."
}

// ================================================================ planner

/** GTA V menu for choosing a destination: header, search row, results, options, description. */
@Composable
fun BoxScope.GtaPlanner(
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
) {
  val focus = LocalFocusManager.current
  var selected by remember(planner.destination) { mutableIntStateOf(0) }
  val destination = planner.destination

  Column(Modifier.align(Alignment.TopStart).fillMaxWidth().widthIn(max = 420.dp)) {
    GtaMenuHeader("OVERWORLD")
    if (destination == null) {
      val results = planner.results.take(7)
      val total = 1 + if (results.isEmpty()) 2 else results.size
      GtaMenuSubheader("Set waypoint", "${selected + 1}/$total")
      GtaSearchRow(planner.query, onQuery, planner.searching, onDone = { focus.clearFocus() })
      if (results.isNotEmpty()) {
        results.forEachIndexed { i, place ->
          GtaMenuRow(
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
        GtaDescription(results.getOrNull(selected - 1)?.detail?.ifBlank { null } ?: "Tap a place to set it as your waypoint.")
      } else {
        GtaMenuRow("Test drive", selected == 1, { selected = 1; onTestDrive(!testDrive) }, checked = testDrive)
        GtaMenuRow("Theme", selected == 2, { selected = 2; onTheme() }, value = theme.name)
        GtaDescription(
            if (planner.query.isNotBlank() && !planner.searching) "No places found."
            else "Search for a place, or long-press the map to set a waypoint."
        )
      }
    } else {
      GtaMenuSubheader("Waypoint", "${selected + 1}/4")
      GtaMenuRow(
          if (planner.routing) "Calculating route..." else "Set route to ${destination.name}",
          selected == 0,
          { selected = 0; onGo() },
      )
      GtaMenuRow("Test drive", selected == 1, { selected = 1; onTestDrive(!testDrive) }, checked = testDrive)
      GtaMenuRow("Theme", selected == 2, { selected = 2; onTheme() }, value = theme.name)
      GtaMenuRow("Clear waypoint", selected == 3, { selected = 3; onClear() })
      val details = listOf(destination.name, destination.detail).filter { it.isNotBlank() }.joinToString("\n")
      GtaDescription(planner.error ?: details, color = if (planner.error != null) Gta.Red else Gta.White)
    }
  }

  // Bottom-left: straight-line distance to the waypoint, like the radar readout.
  if (destination != null && here != null) {
    GtaWaypointDistance(
        formatDistance(distanceMeters(here, destination.coordinate)).replace(" ", ""),
        arrowDegrees = relativeBearing(here, destination.coordinate, 0.0),
        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
    )
  }

  GtaInstructionalButtons(
      listOf(GtaPrompt("L", "Locate", onLocate)),
      modifier = Modifier.align(Alignment.BottomEnd),
  )
}

@Composable
private fun GtaSearchRow(query: String, onQuery: (String) -> Unit, searching: Boolean, onDone: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().height(44.dp).background(Gta.RowSelected).padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    BasicTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        textStyle = TextStyle(color = Color.Black, fontFamily = GameFonts.menu, fontSize = 16.sp),
        cursorBrush = SolidColor(Gta.Waypoint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        modifier = Modifier.weight(1f),
        decorationBox = { inner ->
          if (query.isEmpty()) Text("Search for a place", color = Color(0xFF6E6E6E), fontFamily = GameFonts.menu, fontSize = 16.sp)
          inner()
        },
    )
    Text(if (searching) "..." else "<  Search  >", color = Color.Black, fontFamily = GameFonts.menu, fontSize = 16.sp)
  }
}

// ================================================================ geometry

private fun distanceMeters(a: GeographicCoordinate, b: GeographicCoordinate): Double {
  val r = 6_371_008.8
  val dLat = Math.toRadians(b.lat - a.lat)
  val dLng = Math.toRadians(b.lng - a.lng)
  val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
  return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

private fun lengthMeters(points: List<GeographicCoordinate>): Double =
    points.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }

/** Screen-relative direction to [to], given the map's current bearing. */
private fun relativeBearing(from: GeographicCoordinate?, to: GeographicCoordinate, mapBearing: Double): Float {
  if (from == null) return 0f
  val y = sin(Math.toRadians(to.lng - from.lng)) * cos(Math.toRadians(to.lat))
  val x =
      cos(Math.toRadians(from.lat)) * sin(Math.toRadians(to.lat)) -
          sin(Math.toRadians(from.lat)) * cos(Math.toRadians(to.lat)) * cos(Math.toRadians(to.lng - from.lng))
  val bearing = Math.toDegrees(atan2(y, x))
  return ((bearing - mapBearing + 360) % 360).toFloat()
}
