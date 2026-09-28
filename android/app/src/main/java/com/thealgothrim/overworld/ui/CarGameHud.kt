package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.thealgothrim.overworld.HazardAhead
import com.thealgothrim.overworld.RouteExtras
import com.thealgothrim.overworld.RoutePreview
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.gta.GtaText
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrText
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import com.thealgothrim.overworld.ui.skin.spec

/** Room kept clear for Android Auto's own buttons: the action strip on top, map buttons on the right. */
private val TOP_CLEAR = 84.dp
private val RIGHT_CLEAR = 92.dp

/**
 * The game HUD on the Android Auto screen, placed where Google Maps puts things on a right-hand
 * drive car like the Curvv: turn card and alerts top-right by the driver, time card bottom-right,
 * speed bottom-left, street along the bottom. Android Auto's cards are not sent in this mode.
 */
@Composable
fun BoxScope.CarGameHud(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    area: String?,
    extras: RouteExtras,
    remaining: Double?,
    hazard: HazardAhead?,
    preview: RoutePreview?,
) {
  val spec = theme.spec
  val progress = uiState.progress
  if (!uiState.isNavigating() || progress == null) {
    if (preview != null) PreviewCard(spec, preview, extras, Modifier.align(Alignment.TopEnd).padding(top = TOP_CLEAR, end = RIGHT_CLEAR))
    else IdleCard(spec, Modifier.align(Alignment.TopEnd).padding(top = TOP_CLEAR, end = RIGHT_CLEAR))
    return
  }

  Column(Modifier.align(Alignment.TopEnd).padding(top = TOP_CLEAR, end = RIGHT_CLEAR), horizontalAlignment = Alignment.End) {
    TurnCard(spec, uiState)
    hazard?.let { HazardStrip(spec, it, Modifier.padding(top = 8.dp).widthIn(min = 300.dp, max = 440.dp), textSize = 20.sp, icon = 40.dp) }
  }

  SpeedBadge(spec, uiState, Modifier.align(Alignment.BottomStart), big = true)

  val street = listOfNotNull(uiState.currentStepRoadName?.takeIf { it.isNotBlank() }, area)
  if (street.isNotEmpty()) {
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp, end = 280.dp)) {
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

  val left = remaining ?: progress.durationRemaining
  Column(
      Modifier.align(Alignment.BottomEnd).padding(end = RIGHT_CLEAR).widthIn(min = 260.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 12.dp)
  ) {
    SkinText(formatDuration(left).uppercase(), spec.big, 36.sp, spec.trafficColor(extras.eta))
    SkinText("${formatDistance(progress.distanceRemaining)}  ·  ${arrivalClock(left)}", spec.body, 20.sp, spec.fg)
    trafficNote(extras.eta)?.let { SkinText(it, spec.body, 16.sp, spec.sub) }
  }
}

@Composable
private fun TurnCard(spec: SkinSpec, uiState: NavigationUiState) {
  val content = uiState.visualInstruction?.primaryContent
  val distance = uiState.progress?.distanceToNextManeuver?.let(::formatDistance).orEmpty()
  val road = content?.text?.trim().orEmpty()
  val next = uiState.remainingSteps?.getOrNull(1)
  val then = next?.visualInstructions?.firstOrNull()?.primaryContent?.takeIf { next.distance < 500 }

  Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
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

/** Mirrors the phone's route preview, like Google's card: place, time, arrival; Start in the strip. */
@Composable
private fun PreviewCard(spec: SkinSpec, preview: RoutePreview, extras: RouteExtras, modifier: Modifier) {
  val seconds = extras.eta?.travelSeconds ?: preview.durationSeconds
  Column(modifier.widthIn(min = 320.dp, max = 460.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 16.dp)) {
    SkinText(spec.title(preview.place.name), spec.title, if (spec.gta) 28.sp else 32.sp, spec.fg, maxLines = 2, spacing = if (spec.gta) 0.sp else 1.sp)
    if (preview.place.detail.isNotBlank()) SkinText(preview.place.detail, spec.body, 18.sp, spec.sub, maxLines = 1)
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
      SkinText(formatDuration(seconds).uppercase(), spec.big, 32.sp, spec.trafficColor(extras.eta))
      Spacer(Modifier.width(10.dp))
      SkinText("${formatDistance(preview.distanceMeters)}  ·  ${arrivalClock(seconds)}", spec.body, 19.sp, spec.fg, Modifier.padding(bottom = 3.dp))
    }
    val facts =
        listOfNotNull(
            trafficNote(extras.eta),
            extras.lightsOnRoute.takeIf { it > 0 }?.let { "$it traffic lights" },
            extras.incidentsOnRoute.size.takeIf { it > 0 }?.let { "$it incident${if (it == 1) "" else "s"}" },
        )
    if (facts.isNotEmpty()) SkinText(facts.joinToString("  ·  "), spec.body, 17.sp, spec.sub, Modifier.padding(top = 4.dp))
  }
}

@Composable
private fun IdleCard(spec: SkinSpec, modifier: Modifier) {
  Column(modifier.widthIn(max = 440.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 16.dp)) {
    SkinText(spec.title("Where to?"), spec.title, if (spec.gta) 28.sp else 32.sp, spec.fg, spacing = if (spec.gta) 0.sp else 1.sp)
    SkinText("Pick a place on your phone, or open Saved.", spec.body, 19.sp, spec.sub, Modifier.padding(top = 4.dp), maxLines = 2)
  }
}
