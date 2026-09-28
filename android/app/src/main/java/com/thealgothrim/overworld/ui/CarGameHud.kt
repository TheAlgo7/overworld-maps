package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.composeui.views.components.maneuver.ManeuverImage
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.gta.GtaText
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrText
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import com.thealgothrim.overworld.ui.skin.spec

/**
 * The game HUD on the Android Auto screen, in the same places Google Maps uses there: the turn card
 * top-left, the ETA card bottom-left and the street name along the bottom. Android Auto's own
 * cards are not sent in this mode; its buttons on the right and its bottom bar stay as they are.
 */
@Composable
fun BoxScope.CarGameHud(theme: OverworldTheme, uiState: NavigationUiState, area: String?) {
  val spec = theme.spec
  val progress = uiState.progress
  if (!uiState.isNavigating() || progress == null) {
    IdleCard(spec, Modifier.align(Alignment.TopStart))
    return
  }

  TurnCard(spec, uiState, Modifier.align(Alignment.TopStart))

  Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    Column(Modifier.widthIn(min = 230.dp).skinPanel(spec).padding(horizontal = 18.dp, vertical = 12.dp)) {
      SkinText(formatDuration(progress.durationRemaining).uppercase(), spec.big, 34.sp, spec.good)
      SkinText("${arrivalClock(progress.durationRemaining)}  ·  ${formatDistance(progress.distanceRemaining)}", spec.body, 19.sp, spec.fg)
    }
    Box(Modifier.weight(1f).padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
      val street = listOfNotNull(uiState.currentStepRoadName?.takeIf { it.isNotBlank() }, area)
      if (street.isNotEmpty()) {
        if (spec.gta) {
          GtaText(street.joinToString("  |  "), 24.sp, align = TextAlign.Center)
        } else {
          RdrText(
              street.joinToString(",  ").uppercase(),
              20.sp,
              Modifier.skinPanel(spec, elevated = false).padding(horizontal = 18.dp, vertical = 6.dp),
              color = Rdr.GreyLight,
              align = TextAlign.Center,
              spacing = 1.sp,
          )
        }
      }
    }
    // Keeps the street name clear of Android Auto's map buttons on the right.
    Spacer(Modifier.width(96.dp))
  }
}

@Composable
private fun TurnCard(spec: SkinSpec, uiState: NavigationUiState, modifier: Modifier) {
  val content = uiState.visualInstruction?.primaryContent
  val distance = uiState.progress?.distanceToNextManeuver?.let(::formatDistance).orEmpty()
  val road = content?.text?.trim().orEmpty()
  val next = uiState.remainingSteps?.getOrNull(1)
  val then = next?.visualInstructions?.firstOrNull()?.primaryContent?.takeIf { next.distance < 500 }

  Column(modifier.widthIn(min = 300.dp, max = 420.dp)) {
    Row(Modifier.skinPanel(spec).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { content?.let { ManeuverImage(it, tint = spec.fg) } }
      Spacer(Modifier.width(16.dp))
      Column {
        if (uiState.isCalculatingNewRoute == true) {
          SkinText(spec.title("Rerouting"), spec.title, 28.sp, spec.fg)
        } else {
          if (spec.gta) GtaText(distance, 40.sp) else RdrText(distance.uppercase(), 40.sp, spacing = 1.sp)
          if (road.isNotEmpty()) SkinText(road, spec.body, 22.sp, spec.fg, maxLines = 2)
        }
      }
    }
    then?.let {
      Row(
          Modifier.background(if (spec.gta) Color(0xF2111111) else Color(0xF2211B16)).padding(horizontal = 18.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        SkinText("Then", spec.body, 18.sp, spec.sub)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { ManeuverImage(it, tint = spec.fg) }
      }
    }
  }
}

@Composable
private fun IdleCard(spec: SkinSpec, modifier: Modifier) {
  Column(modifier.widthIn(max = 420.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 16.dp)) {
    SkinText(spec.title("Where to?"), spec.title, if (spec.gta) 28.sp else 32.sp, spec.fg, spacing = if (spec.gta) 0.sp else 1.sp)
    SkinText("Pick a place on your phone, or open Saved.", spec.body, 19.sp, spec.sub, Modifier.padding(top = 4.dp), maxLines = 2)
  }
}
