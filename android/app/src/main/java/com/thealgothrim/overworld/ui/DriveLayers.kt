package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.HazardAhead
import com.thealgothrim.overworld.RouteExtras
import com.thealgothrim.overworld.RoutePreview
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.gta.GtaHudBars
import com.thealgothrim.overworld.ui.gta.GtaText
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrText
import com.thealgothrim.overworld.ui.vi.Vi
import com.thealgothrim.overworld.ui.vi.ViProgress
import com.thealgothrim.overworld.ui.vi.ViText
import com.thealgothrim.overworld.ui.vi.ViTurnTile
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinRoundButton
import com.thealgothrim.overworld.ui.skin.SkinRow
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.sheetFloat
import com.thealgothrim.overworld.ui.skin.skinPanel

// ================================================================ route preview

/** Directions preview: back and the destination on top, time, distance and Start at the bottom. */
@Composable
fun BoxScope.PreviewLayer(
    spec: SkinSpec,
    preview: RoutePreview,
    extras: RouteExtras,
    testDrive: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
  Row(
      Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp).skinPanel(spec).padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(28.dp).clickable(onClick = onBack)) { GameIconView(GameIcon.BACK, spec.fg, Modifier.size(26.dp)) }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      SkinText("Your location", spec.body, 14.sp, spec.sub)
      SkinText(preview.place.name, spec.body, 18.sp, spec.fg)
    }
  }

  SkinPanel(spec, Modifier.align(Alignment.BottomCenter).sheetFloat(spec).fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      // Live-traffic time when TomTom is set up, otherwise the routing engine's estimate.
      val seconds = extras.eta?.travelSeconds ?: preview.durationSeconds
      Row(verticalAlignment = Alignment.Bottom) {
        SkinText(formatDuration(seconds).uppercase(), spec.big, 34.sp, spec.trafficColor(extras.eta))
        Spacer(Modifier.width(10.dp))
        SkinText("(${formatDistance(preview.distanceMeters)})", spec.body, 18.sp, spec.sub, Modifier.padding(bottom = 4.dp))
      }
      val facts =
          listOfNotNull(
              preview.via?.let { "via $it" },
              trafficNote(extras.eta),
              extras.lightsOnRoute.takeIf { it > 0 }?.let { "$it traffic light${if (it == 1) "" else "s"}" },
              extras.markedIncidents.size.takeIf { it > 0 }?.let { "$it incident${if (it == 1) "" else "s"} on route" },
          )
      if (facts.isNotEmpty()) SkinText(facts.joinToString("  ·  "), spec.body, 16.sp, spec.fg, Modifier.padding(top = 4.dp), maxLines = 2)
      SkinText(
          if (testDrive) "Test drive is on: the trip will be simulated. Turn it off in Settings."
          else "Arrive at ${arrivalClock(seconds)}${if (extras.eta == null) "  ·  typical traffic" else "  ·  live traffic"}",
          spec.body, 14.sp, if (testDrive) spec.accent else spec.sub, Modifier.padding(top = 4.dp), maxLines = 2,
      )
      Spacer(Modifier.height(14.dp))
      SkinPrimaryButton(spec, if (testDrive) "Start test drive" else "Start", onStart, Modifier.fillMaxWidth(), GameIcon.NAVIGATE)
    }
  }
}

// ================================================================ navigating

/**
 * Driving: turn banner on top, sound and overview buttons on the right, re-centre and the street
 * name above a bottom sheet with time to go, distance and arrival, like Google Maps navigation.
 */
@Composable
fun BoxScope.NavigationLayer(
    spec: SkinSpec,
    uiState: NavigationUiState,
    extras: RouteExtras,
    remaining: Double?,
    hazard: HazardAhead?,
    onLayers: () -> Unit,
    area: String?,
    following: Boolean,
    onRecenter: () -> Unit,
    onOverview: () -> Unit,
    onMute: () -> Unit,
    onEnd: () -> Unit,
    onSettings: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val progress = uiState.progress

  // ---- top: turn banner, then map buttons on the right
  Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
    TurnBanner(spec, uiState, hazard)
    Spacer(Modifier.height(12.dp))
    Column(Modifier.align(Alignment.End), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      SkinRoundButton(spec, if (uiState.isMuted == true) GameIcon.SOUND_OFF else GameIcon.SOUND_ON, onMute)
      SkinRoundButton(spec, if (following) GameIcon.ROUTE else GameIcon.NAVIGATE, if (following) onOverview else onRecenter)
      SkinRoundButton(spec, GameIcon.LAYERS, onLayers)
    }
  }

  // ---- bottom: re-centre + street, then the trip sheet
  Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
      MapCredit(spec, Modifier.align(Alignment.BottomEnd))
      if (!following) {
        Row(
            Modifier.align(Alignment.CenterStart).skinPanel(spec).clickable(onClick = onRecenter).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          GameIconView(GameIcon.NAVIGATE, spec.fg, Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          SkinText(spec.title("Re-centre"), spec.title, if (spec.vi) 18.sp else 16.sp, spec.fg, spacing = if (spec.vi) spec.titleSpacing else TextUnit.Unspecified)
        }
      }
      if (following) {
        // Speed (when there is one) on the left, the street centred in whatever room is left.
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          SpeedBadge(spec, uiState)
          val street = listOfNotNull(uiState.currentStepRoadName?.takeIf { it.isNotBlank() }, area)
          Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            if (street.isNotEmpty()) {
              when {
                spec.gta -> GtaText(street.joinToString("  |  "), 21.sp, align = TextAlign.Center)
                spec.vi -> ViText(street.joinToString("  ·  "), 18.sp, font = Vi.sansSemiBold, align = TextAlign.Center)
                else -> RdrText(street.joinToString(",  ").uppercase(), 16.sp, color = Rdr.GreyLight, align = TextAlign.Center, spacing = 1.sp)
              }
            }
          }
        }
      }
    }

    if (progress != null) {
      SkinPanel(spec, Modifier.sheetFloat(spec).fillMaxWidth()) {
        TripProgressBar(spec, uiState)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          SheetIconButton(spec, GameIcon.CLOSE, onEnd, tint = spec.accent)
          Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val left = remaining ?: progress.durationRemaining
            SkinText(formatDuration(left).uppercase(), spec.big, 32.sp, spec.trafficColor(extras.eta))
            SkinText(
                "${formatDistance(progress.distanceRemaining)}  ·  ${arrivalClock(left)}",
                spec.body, 16.sp, spec.sub,
            )
          }
          SheetIconButton(spec, if (expanded) GameIcon.CHEVRON_DOWN else GameIcon.CHEVRON_UP, { expanded = !expanded })
        }
        if (expanded) {
          SkinDivider(spec)
          SkinRow(spec, if (uiState.isMuted == true) "Unmute voice" else "Mute voice", onMute, icon = if (uiState.isMuted == true) GameIcon.SOUND_ON else GameIcon.SOUND_OFF)
          SkinRow(spec, "Route overview", { expanded = false; onOverview() }, icon = GameIcon.ROUTE)
          SkinRow(spec, "Mark a camera here", { expanded = false; com.thealgothrim.overworld.AppModule.viewModel.markCamera() }, icon = GameIcon.CAMERA)
          SkinRow(spec, "Settings", onSettings, icon = GameIcon.SETTINGS)
          SkinRow(spec, "End trip", onEnd, icon = GameIcon.CLOSE)
        }
      }
    }
  }
}

@Composable
private fun SheetIconButton(spec: SkinSpec, icon: GameIcon, onClick: () -> Unit, tint: Color = spec.fg) {
  Box(
      Modifier.size(48.dp)
          .then(
              when {
                spec.vi -> Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF))
                spec.gta -> Modifier.background(Color(0x26FFFFFF))
                else -> Modifier.background(Color(0x33000000))
              }
          )
          .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    GameIconView(icon, tint, Modifier.size(24.dp))
  }
}

/** The turn banner, in the game's HUD box. Adds a "Then" strip when the next turn follows quickly. */
@Composable
private fun TurnBanner(spec: SkinSpec, uiState: NavigationUiState, hazard: HazardAhead?) {
  val content = uiState.visualInstruction?.primaryContent
  val distance = uiState.progress?.distanceToNextManeuver?.let(::formatDistance).orEmpty()
  val road = content?.text?.trim().orEmpty()
  val next = uiState.remainingSteps?.getOrNull(1)
  val then = next?.visualInstructions?.firstOrNull()?.primaryContent?.takeIf { next.distance < 500 }

  // One card, like Google's: the turn, then "Then", then any alert, split by hairlines.
  Column(Modifier.fillMaxWidth().skinPanel(spec)) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (spec.vi) {
        // GTA VI: the arrow on a pink tile.
        content?.let { ViTurnTile(it, uiState.remainingSteps?.firstOrNull()?.drivingSide, 56.dp) }
      } else {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
          content?.let { TurnArrow(it, uiState.remainingSteps?.firstOrNull()?.drivingSide, if (spec.gta) Gta.White else Rdr.White) }
        }
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (uiState.isCalculatingNewRoute == true) {
          SkinText(spec.title("Rerouting"), spec.title, 26.sp, spec.fg, spacing = if (spec.vi) spec.titleSpacing else TextUnit.Unspecified)
        } else {
          when {
            spec.gta -> GtaText(distance, 34.sp)
            spec.vi -> SkinText(distance, Vi.condensedBold, 34.sp, spec.fg, spacing = 0.4.sp)
            else -> RdrText(distance.uppercase(), 34.sp, spacing = 1.sp)
          }
          if (road.isNotEmpty()) SkinText(road, if (spec.vi) Vi.sansMedium else spec.body, 19.sp, spec.fg, maxLines = 2)
        }
      }
    }
    then?.let {
      CardDivider(spec)
      Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (spec.vi) SkinText("THEN", Vi.condensed, 16.sp, spec.sub, spacing = 0.8.sp) else SkinText("Then", spec.body, 15.sp, spec.sub)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { TurnArrow(it, next?.drivingSide, spec.fg) }
      }
    }
    hazard?.let {
      CardDivider(spec)
      HazardRow(spec, it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
    }
  }
}

/**
 * Trip progress along the top of the sheet: GTA V's health/armour bars, GTA VI's thin pink line,
 * or Red Dead's red rule.
 */
@Composable
private fun TripProgressBar(spec: SkinSpec, uiState: NavigationUiState) {
  val progress = uiState.progress ?: return
  val routeLength = remember(uiState.routeGeometry) { uiState.routeGeometry?.let(::lengthMeters) ?: 0.0 }
  // Against the whole trip, kept from its first route: a new route after a detour starts where the
  // car is, and measured against that the bar dropped back to empty.
  val tripLength = remember(uiState.routeGeometry?.lastOrNull()) { routeLength }
  val done = if (tripLength > 0) (1 - progress.distanceRemaining / tripLength).toFloat().coerceIn(0f, 1f) else 0f
  if (spec.vi) {
    ViProgress(done, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
  } else if (spec.gta) {
    val stepLength = uiState.remainingSteps?.firstOrNull()?.distance ?: 0.0
    val turn = if (stepLength > 0) (1 - progress.distanceToNextManeuver / stepLength).toFloat() else 0f
    GtaHudBars(done, turn, Modifier.fillMaxWidth())
  } else {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
      val y = size.height / 2f
      drawLine(Rdr.GreyDark, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
      drawLine(Rdr.Red, Offset(0f, y), Offset(size.width * done, y), strokeWidth = 3.dp.toPx())
      val c = Offset(size.width * done, y)
      val r = 5.dp.toPx()
      val d = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
      drawPath(d, Rdr.Red)
      drawPath(d, Rdr.White, style = Stroke(width = 1.dp.toPx()))
    }
  }
}
