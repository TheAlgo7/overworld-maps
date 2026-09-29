package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import com.thealgothrim.overworld.ui.skin.spec

/**
 * The game HUD on the Android Auto screen. Ours is the left side, Android Auto's the right: turn
 * card and road alerts top-left, time card and speedometer bottom-left, street name along the
 * bottom. Android Auto's buttons, action strip and rail keep the right. Its own cards are not sent
 * in this mode.
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
    if (preview != null) PreviewCard(spec, preview, extras, Modifier.align(Alignment.TopStart))
    else IdleCard(spec, Modifier.align(Alignment.TopStart))
    return
  }

  TurnCard(spec, uiState, hazard, Modifier.align(Alignment.TopStart))

  Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    val left = remaining ?: progress.durationRemaining
    // Time card and speed panel share one height, so they sit as a pair.
    Row(Modifier.height(IntrinsicSize.Min)) {
      Column(
          Modifier.fillMaxHeight().widthIn(min = 230.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
      ) {
        SkinText(formatDuration(left).uppercase(), spec.big, 34.sp, spec.trafficColor(extras.eta))
        SkinText("${arrivalClock(left)}  ·  ${formatDistance(progress.distanceRemaining)}", spec.body, 19.sp, spec.fg)
        trafficNote(extras.eta)?.let { SkinText(it, spec.body, 16.sp, spec.sub) }
      }
      SpeedBadge(spec, uiState, Modifier.padding(start = 12.dp).fillMaxHeight(), big = true)
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
private fun TurnCard(spec: SkinSpec, uiState: NavigationUiState, hazard: HazardAhead?, modifier: Modifier) {
  val content = uiState.visualInstruction?.primaryContent
  val distance = uiState.progress?.distanceToNextManeuver?.let(::formatDistance).orEmpty()
  val road = content?.text?.trim().orEmpty()
  val next = uiState.remainingSteps?.getOrNull(1)
  val then = next?.visualInstructions?.firstOrNull()?.primaryContent?.takeIf { next.distance < 500 }

  // One card, like Google's: the turn, then "Then", then any alert, split by hairlines.
  Column(modifier.width(IntrinsicSize.Max).widthIn(min = 300.dp, max = 420.dp).skinPanel(spec)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { content?.let { ManeuverImage(it, tint = spec.fg) } }
      Spacer(Modifier.width(18.dp))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (uiState.isCalculatingNewRoute == true) {
          SkinText(spec.title("Rerouting"), spec.title, 28.sp, spec.fg)
        } else {
          if (spec.gta) GtaText(distance, 40.sp) else RdrText(distance.uppercase(), 40.sp, spacing = 1.sp)
          if (road.isNotEmpty()) SkinText(road, spec.body, 22.sp, spec.fg, maxLines = 2)
        }
      }
    }
    then?.let {
      CardDivider(spec)
      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SkinText("Then", spec.body, 18.sp, spec.sub)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { ManeuverImage(it, tint = spec.fg) }
      }
    }
    hazard?.let {
      CardDivider(spec)
      HazardRow(spec, it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), textSize = 20.sp, icon = 40.dp)
    }
  }
}

/**
 * Where the car screen's drawn buttons are, in surface pixels. Android Auto hands map taps to the
 * app (see passTapsTo); the car screen checks them against these.
 */
object CarTapTargets {
  @Volatile var start: Rect? = null
  @Volatile var cancel: Rect? = null
}

/** The phone's route preview on the car, in the theme: place, time, the road's details, Start. */
@Composable
private fun PreviewCard(spec: SkinSpec, preview: RoutePreview, extras: RouteExtras, modifier: Modifier) {
  val seconds = extras.eta?.travelSeconds ?: preview.durationSeconds
  DisposableEffect(Unit) {
    onDispose {
      CarTapTargets.start = null
      CarTapTargets.cancel = null
    }
  }
  Column(modifier.width(420.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 16.dp)) {
    SkinText(spec.title(preview.place.name), spec.title, if (spec.gta) 28.sp else 30.sp, spec.fg, maxLines = 2, spacing = if (spec.gta) 0.sp else 1.sp)
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
      SkinText(formatDuration(seconds).uppercase(), spec.big, 32.sp, spec.trafficColor(extras.eta))
      Spacer(Modifier.width(10.dp))
      SkinText("${formatDistance(preview.distanceMeters)}  ·  ${arrivalClock(seconds)}", spec.body, 19.sp, spec.fg, Modifier.padding(bottom = 3.dp))
    }
    val facts =
        listOfNotNull(
            preview.via?.let { "via $it" },
            extras.lightsOnRoute.takeIf { it > 0 }?.let { "$it traffic lights" },
            extras.markedIncidents.size.takeIf { it > 0 }?.let { "$it incident${if (it == 1) "" else "s"}" },
            trafficNote(extras.eta),
        )
    if (facts.isNotEmpty()) SkinText(facts.joinToString("  ·  "), spec.body, 17.sp, spec.sub, Modifier.padding(top = 2.dp), maxLines = 2)
    Row(Modifier.padding(top = 14.dp)) {
      SkinPrimaryButton(
          spec, spec.title("Start"), {},
          Modifier.weight(1f).onGloballyPositioned { CarTapTargets.start = it.boundsInRoot() },
          icon = GameIcon.NAVIGATE,
      )
      Spacer(Modifier.width(10.dp))
      SkinSecondaryButton(
          spec, spec.title("Cancel"), {},
          Modifier.onGloballyPositioned { CarTapTargets.cancel = it.boundsInRoot() },
      )
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
