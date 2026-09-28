package com.thealgothrim.overworld.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.composeui.views.components.maneuver.ManeuverImage
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.gta.GtaHelpText
import com.thealgothrim.overworld.ui.gta.GtaText
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrHelpBox
import com.thealgothrim.overworld.ui.rdr.RdrText
import com.thealgothrim.overworld.ui.rdr.inkBand
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The game HUD on the Android Auto screen, used instead of Android Auto's own turn and ETA cards.
 * Kept to what a driver needs at a glance: the next turn with its distance, time to go and
 * arrival, and the current street. Drawn inside the area Android Auto leaves free.
 */
@Composable
fun BoxScope.CarGameHud(theme: OverworldTheme, uiState: NavigationUiState, area: String?) {
  val progress = uiState.progress ?: return
  val content = uiState.visualInstruction?.primaryContent
  val distance = formatDistance(progress.distanceToNextManeuver)
  val road = content?.text?.trim()?.takeUnless { it.endsWith(".") }.orEmpty()
  val street = listOfNotNull(uiState.currentStepRoadName?.takeIf { it.isNotBlank() }, area)

  if (theme.id == "frontier") {
    RdrHelpBox(Modifier.align(Alignment.TopStart).widthIn(max = 400.dp)) {
      content?.let {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { ManeuverImage(it, tint = Rdr.White) }
        Spacer(Modifier.width(14.dp))
      }
      Column {
        RdrText(distance.uppercase(), 40.sp, spacing = 1.sp)
        if (road.isNotEmpty()) Text(road, color = Rdr.GreyLight, fontFamily = GameFonts.hapna, fontSize = 22.sp, maxLines = 1)
      }
    }
    Column(
        Modifier.align(Alignment.TopEnd).padding(end = 64.dp).inkBand(fromLeft = false).padding(start = 30.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.End,
    ) {
      RdrText(formatDuration(progress.durationRemaining).uppercase(), 36.sp, align = TextAlign.End, spacing = 1.sp)
      RdrText(arrivalClock(progress.durationRemaining), 22.sp, color = Rdr.Objective, font = GameFonts.hapna, align = TextAlign.End)
    }
    if (street.isNotEmpty()) {
      RdrText(
          street.joinToString(",  ").uppercase(),
          21.sp,
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .padding(bottom = 14.dp)
                  .background(Brush.horizontalGradient(0f to Color.Transparent, 0.15f to Rdr.Ink, 0.85f to Rdr.Ink, 1f to Color.Transparent))
                  .padding(horizontal = 40.dp, vertical = 5.dp),
          color = Rdr.GreyLight,
          align = TextAlign.Center,
          spacing = 1.sp,
      )
    }
  } else {
    GtaHelpText(Modifier.align(Alignment.TopStart).widthIn(max = 400.dp)) {
      content?.let {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { ManeuverImage(it, tint = Gta.White) }
        Spacer(Modifier.width(12.dp))
      }
      Column {
        GtaText(distance, 36.sp)
        if (road.isNotEmpty()) Text(road, color = Gta.White, fontFamily = GameFonts.menu, fontSize = 20.sp, maxLines = 1)
      }
    }
    Column(Modifier.align(Alignment.TopEnd).padding(end = 64.dp), horizontalAlignment = Alignment.End) {
      GtaText(formatDuration(progress.durationRemaining).uppercase(), 40.sp, font = GameFonts.price, align = TextAlign.End)
      GtaText(arrivalClock(progress.durationRemaining), 25.sp, color = Gta.Health, font = GameFonts.price, align = TextAlign.End)
    }
    if (street.isNotEmpty()) {
      GtaText(
          street.joinToString("  |  "),
          25.sp,
          modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
          align = TextAlign.Center,
      )
    }
  }
}
