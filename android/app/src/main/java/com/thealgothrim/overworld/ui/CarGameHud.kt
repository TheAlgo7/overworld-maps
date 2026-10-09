package com.thealgothrim.overworld.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.ui.skin.GameIconView
import kotlinx.coroutines.delay
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
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.HazardAhead
import com.thealgothrim.overworld.RouteExtras
import com.thealgothrim.overworld.RoutePreview
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.ui.gta.GtaText
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrText
import com.thealgothrim.overworld.ui.vi.Vi
import com.thealgothrim.overworld.ui.vi.ViText
import com.thealgothrim.overworld.ui.vi.ViTurnTile
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
    val estimate by AppModule.viewModel.estimatedKmh.collectAsState()
    // GPS speed, or the speed worked out from movement when a fix has none.
    val moving = rememberFreeDriving(uiState.location?.speed?.value ?: estimate?.div(3.6) ?: 0.0)
    when {
      preview != null -> PreviewCard(spec, preview, extras, Modifier.align(Alignment.TopStart))
      // Driving without a trip: the map, the speed and camera alerts, like Google's free drive.
      // The "Where to?" card comes back when the car stops.
      moving -> {
        hazard?.let { HazardRow(spec, it, Modifier.align(Alignment.TopStart).widthIn(max = 420.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 14.dp), textSize = 20.sp, icon = 40.dp) }
        val speed = rememberSpeedNow(uiState)
        // The speedometer in the same corner as on a trip, over the limit sign and + Cam.
        Column(Modifier.align(Alignment.BottomStart)) {
          Speedometer(spec, speed, Modifier.padding(start = 6.dp, bottom = 8.dp))
          Row(verticalAlignment = Alignment.Bottom) {
            SpeedLimitSign(spec, speed, Modifier.padding(end = 12.dp))
            CameraButton(spec, Modifier)
          }
        }
      }
      else -> IdleCard(spec, Modifier.align(Alignment.TopStart))
    }
    return
  }

  TurnCard(spec, uiState, hazard, Modifier.align(Alignment.TopStart))

  Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
    val left = remaining ?: progress.durationRemaining
    val speed = rememberSpeedNow(uiState)
    // The speedometer stands on the time card, like a car's speedometer over its trip computer;
    // the limit sign and + Cam stand on the card's bottom line, like everything along the bottom.
    Row(verticalAlignment = Alignment.Bottom) {
      Column {
        Speedometer(spec, speed, Modifier.padding(start = 6.dp, bottom = 8.dp))
        Column(
            Modifier.widthIn(min = 230.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
          SkinText(formatDuration(left).uppercase(), spec.big, 34.sp, spec.trafficColor(extras.eta))
          SkinText("${arrivalClock(left)}  ·  ${formatDistance(progress.distanceRemaining)}", spec.body, 19.sp, spec.fg)
          trafficNote(extras.eta)?.let { SkinText(it, spec.body, 16.sp, spec.sub) }
        }
      }
      SpeedLimitSign(spec, speed, Modifier.padding(start = 12.dp))
      CameraButton(spec, Modifier.padding(start = 12.dp))
    }
    Box(Modifier.weight(1f).padding(bottom = 6.dp), contentAlignment = Alignment.Center) {
      val street = listOfNotNull(uiState.currentStepRoadName?.takeIf { it.isNotBlank() }, area)
      if (street.isNotEmpty()) {
        if (spec.gta) {
          GtaText(street.joinToString("  |  "), 24.sp, align = TextAlign.Center)
        } else if (spec.vi) {
          // GTA VI: the minimap's clean grotesk, white with a thin dark edge.
          ViText(street.joinToString("  ·  "), 22.sp, font = Vi.sansSemiBold, align = TextAlign.Center)
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
    Row(Modifier.fillMaxWidth().padding(horizontal = if (spec.vi) 16.dp else 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      if (spec.vi) {
        // GTA VI: the arrow, dark on a pink tile in the route's colour.
        content?.let { ViTurnTile(it, uiState.remainingSteps?.firstOrNull()?.drivingSide, 66.dp) }
      } else {
        Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { content?.let { TurnArrow(it, uiState.remainingSteps?.firstOrNull()?.drivingSide, spec.fg) } }
      }
      Spacer(Modifier.width(18.dp))
      Column(verticalArrangement = Arrangement.spacedBy(if (spec.vi) 2.dp else 4.dp)) {
        if (uiState.isCalculatingNewRoute == true) {
          SkinText(spec.title("Rerouting"), spec.title, if (spec.vi) 30.sp else 28.sp, spec.fg, spacing = if (spec.vi) spec.titleSpacing else androidx.compose.ui.unit.TextUnit.Unspecified)
        } else {
          when {
            spec.gta -> GtaText(distance, 40.sp)
            // The mission HUD's bold condensed numbers.
            spec.vi -> SkinText(distance, Vi.condensedBold, 42.sp, spec.fg, spacing = 0.4.sp)
            else -> RdrText(distance.uppercase(), 40.sp, spacing = 1.sp)
          }
          if (road.isNotEmpty()) SkinText(road, if (spec.vi) Vi.sansMedium else spec.body, 22.sp, spec.fg, maxLines = 2)
        }
      }
    }
    then?.let {
      CardDivider(spec)
      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (spec.vi) SkinText("THEN", Vi.condensed, 19.sp, spec.sub, spacing = 0.8.sp) else SkinText("Then", spec.body, 18.sp, spec.sub)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { TurnArrow(it, next?.drivingSide, spec.fg) }
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
  @Volatile var camera: Rect? = null
}

/**
 * Marks a speed camera where the car is: OpenStreetMap knows few of Delhi's, so the ones passed
 * daily fill in from here. Says "Saved" for a few seconds after a tap. Pressed through map taps
 * (see CarNavigationScreen).
 */
@Composable
private fun CameraButton(spec: SkinSpec, modifier: Modifier) {
  val markedAt by AppModule.viewModel.cameraMarkedAt.collectAsState()
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(markedAt) {
    now = System.currentTimeMillis()
    delay(3_000)
    now = System.currentTimeMillis()
  }
  val saved = markedAt > 0 && now - markedAt < 3_000
  DisposableEffect(Unit) { onDispose { CarTapTargets.camera = null } }
  Row(
      modifier.height(56.dp).skinPanel(spec).onGloballyPositioned { CarTapTargets.camera = it.boundsInRoot() }.padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    GameIconView(GameIcon.CAMERA, spec.fg, Modifier.size(28.dp))
    Spacer(Modifier.width(8.dp))
    SkinText(spec.title(if (saved) "Saved" else "+ Cam"), spec.title, if (spec.vi) 20.sp else 18.sp, if (saved) spec.good else spec.fg, spacing = if (spec.vi) spec.titleSpacing else androidx.compose.ui.unit.TextUnit.Unspecified)
  }
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
    SkinText(spec.title(preview.place.name), spec.title, spec.titleSize(if (spec.vi) 31.sp else 28.sp, 30.sp), spec.fg, maxLines = 2, spacing = spec.titleSpacing)
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
    SkinText(spec.title("Where to?"), spec.title, spec.titleSize(if (spec.vi) 32.sp else 28.sp, 32.sp), spec.fg, spacing = spec.titleSpacing)
    SkinText("Tap Search or Nearby, or pick a place on your phone.", spec.body, 19.sp, spec.sub, Modifier.padding(top = 4.dp), maxLines = 2)
  }
}

/**
 * Free driving (no trip) from the speed in m/s, held through slow traffic: crawling along, the speed
 * kept dipping under the line and the HUD flipped between the speedometer and "Where to?" with every
 * fix. On at once above [FREE_DRIVE_SPEED]; off only once the car has stood still for
 * [STOPPED_HOLD_MS] (a long red light, parked).
 */
@Composable
private fun rememberFreeDriving(speed: Double): Boolean {
  val fast = speed > FREE_DRIVE_SPEED
  val stopped = speed < STOPPED_SPEED
  var driving by remember { mutableStateOf(fast) }
  LaunchedEffect(fast, stopped) {
    if (fast) driving = true
    else if (stopped) {
      delay(STOPPED_HOLD_MS)
      driving = false
    }
  }
  return driving
}

/** About 11 km/h: faster than this with no trip, the car is free driving. */
private const val FREE_DRIVE_SPEED = 3.0
/** About 4 km/h: slower than this the car is standing. */
private const val STOPPED_SPEED = 1.0
private const val STOPPED_HOLD_MS = 20_000L
