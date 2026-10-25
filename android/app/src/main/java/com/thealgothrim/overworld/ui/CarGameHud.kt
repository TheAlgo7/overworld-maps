package com.thealgothrim.overworld.ui

import com.thealgothrim.overworld.ui.skin.SkinRoundButton
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
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
import com.thealgothrim.overworld.ui.vi.ViTurnRow
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
  val lost by AppModule.viewModel.gpsLost.collectAsState()
  if (!uiState.isNavigating() || progress == null) {
    val estimate by AppModule.viewModel.estimatedKmh.collectAsState()
    // GPS speed, or the speed worked out from movement when a fix has none.
    val moving = rememberFreeDriving(uiState.location?.speed?.value ?: estimate?.div(3.6) ?: 0.0)
    if (preview == null) MapButtons(spec, uiState, Modifier.align(Alignment.BottomEnd).padding(bottom = 24.dp), trip = false)
    when {
      preview != null -> Appear(Modifier.align(Alignment.TopStart)) { PreviewCard(spec, preview, extras, Modifier) }
      lost -> Appear(Modifier.align(Alignment.TopStart)) { LostCard(spec, Modifier) }
      // Driving without a trip: the map, the speed and camera alerts, and where the car is, like
      // Google's free drive. The "Where to?" card comes back once the car is parked.
      moving -> {
        StreetLine(spec, listOfNotNull(area), Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
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
      else -> Appear(Modifier.align(Alignment.TopStart)) { ParkedCard(spec, Modifier) }
    }
    return
  }

  Appear(Modifier.align(Alignment.TopStart)) { TurnCard(spec, uiState, hazard, Modifier) }

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
      // With no fix the street would be the last one seen: say so instead.
      if (lost) LostCard(spec, Modifier)
      else StreetLine(spec, listOfNotNull(uiState.currentStepRoadName?.takeIf { it.isNotBlank() }, area), Modifier)
    }
    // Keeps the street name clear of the map buttons on the right.
    Spacer(Modifier.width(96.dp))
  }
  MapButtons(spec, uiState, Modifier.align(Alignment.BottomEnd).padding(bottom = 24.dp))
}

@Composable
private fun TurnCard(spec: SkinSpec, uiState: NavigationUiState, hazard: HazardAhead?, modifier: Modifier) {
  val content = uiState.visualInstruction?.primaryContent
  val distance = uiState.progress?.distanceToNextManeuver?.let(::formatDistance).orEmpty()
  val road = content?.text?.trim().orEmpty()
  val next = uiState.remainingSteps?.getOrNull(1)
  val then = next?.visualInstructions?.firstOrNull()?.primaryContent?.takeIf { next.distance < 500 }

  // One card, like Google's: the turn, then "Then", then any alert, split by hairlines.
  Column(modifier.width(IntrinsicSize.Max).widthIn(min = 300.dp, max = 440.dp).skinPanel(spec)) {
    if (spec.vi) {
      // GTA VI: the game's own card, the move in words over the distance and the pink arrow square on the right.
      val rerouting = uiState.isCalculatingNewRoute == true
      ViTurnRow(
          title = if (rerouting) "Rerouting" else content?.let(::viManeuver).orEmpty(),
          // Unnamed roads come through as a sentence ("Bear right."), which the title already says.
          line = if (rerouting) "" else listOf(distance, road.takeUnless { it.endsWith(".") }.orEmpty()).filter { it.isNotEmpty() }.joinToString("  ·  "),
          content = content,
          side = uiState.remainingSteps?.firstOrNull()?.drivingSide,
          titleSize = 30.sp,
          lineSize = 23.sp,
      )
    } else Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { content?.let { TurnArrow(it, uiState.remainingSteps?.firstOrNull()?.drivingSide, spec.fg) } }
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
  @Volatile var home: Rect? = null
  @Volatile var work: Rect? = null
  @Volatile var mute: Rect? = null
  @Volatile var zoomIn: Rect? = null
  @Volatile var zoomOut: Rect? = null
  @Volatile var overview: Rect? = null
  /** When the car screen was last touched (any tap on the map): brings up the buttons that hide while driving. */
  val touchedAt = mutableLongStateOf(0L)
  /** Whether the map follows the car (set by the car screen): the overview button shows the route or the car. */
  val following = mutableStateOf(true)
  /**
   * Whether Android Auto's own buttons (End, Nearby) are up, read from the surface's visible area
   * (see passTapsTo); null until the head unit has said. Ours come and go with them.
   */
  val hostButtonsUp = mutableStateOf<Boolean?>(null)
  /** A zoom of the driver's own is being held (see AutoZoomEffect)... */
  val zoomHeld = mutableStateOf(false)
  /** ...until re-centre sets this. */
  val zoomReleasedAt = mutableLongStateOf(0L)
  /** When the driver last zoomed: + or -, or a pinch. */
  val zoomedAt = mutableLongStateOf(0L)
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
  // Only when turned on in Settings, and then only for a few seconds after a touch on the screen,
  // standing still or not (that touch doesn't mark anything: hidden, it isn't a target).
  val enabled by AppModule.themeStore.carCamButton.collectAsState()
  val shown = enabled && (rememberTouchedRecently() || saved)
  AnimatedVisibility(shown, modifier, enter = fadeIn(), exit = fadeOut()) { CameraButtonFace(spec, saved) }
}

@Composable
private fun CameraButtonFace(spec: SkinSpec, saved: Boolean) {
  DisposableEffect(Unit) { onDispose { CarTapTargets.camera = null } }
  Row(
      Modifier.height(56.dp).skinPanel(spec).onGloballyPositioned { CarTapTargets.camera = it.boundsInRoot() }.padding(horizontal = 14.dp),
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

/** The street and area along the bottom, in each game's own lettering. */
@Composable
private fun StreetLine(spec: SkinSpec, parts: List<String>, modifier: Modifier) {
  if (parts.isEmpty()) return
  when {
    spec.gta -> Box(modifier) { GtaText(parts.joinToString("  |  "), 24.sp, align = TextAlign.Center) }
    // GTA VI: the minimap's clean grotesk, white with a thin dark edge.
    spec.vi -> Box(modifier) { ViText(parts.joinToString("  ·  "), 22.sp, font = Vi.sansSemiBold, align = TextAlign.Center) }
    else ->
        RdrText(
            parts.joinToString(",  ").uppercase(),
            20.sp,
            modifier.skinPanel(spec, elevated = false).padding(horizontal = 18.dp, vertical = 6.dp),
            color = Rdr.GreyLight,
            align = TextAlign.Center,
            spacing = 1.sp,
        )
  }
}

/** No GPS fix for a while: said plainly, like Google Maps, instead of a frozen arrow. */
@Composable
private fun LostCard(spec: SkinSpec, modifier: Modifier) {
  Row(modifier.widthIn(max = 420.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
    GameIconView(GameIcon.LOCATE, spec.sub, Modifier.size(32.dp))
    Spacer(Modifier.width(14.dp))
    Column {
      SkinText(spec.title("Location not found"), spec.title, spec.titleSize(if (spec.vi) 26.sp else 24.sp, 26.sp), spec.fg, spacing = spec.titleSpacing)
      SkinText("Waiting for GPS", spec.body, 18.sp, spec.sub, Modifier.padding(top = 2.dp))
    }
  }
}

/**
 * Parked with no trip: "Where to?" with Home and Work one tap away (pressed through map taps, see
 * CarTapTargets), like Google's suggestions. Without either saved, it says where to set them.
 */
@Composable
private fun ParkedCard(spec: SkinSpec, modifier: Modifier) {
  val saved by AppModule.saved.state.collectAsState()
  DisposableEffect(Unit) {
    onDispose {
      CarTapTargets.home = null
      CarTapTargets.work = null
    }
  }
  Column(modifier.widthIn(max = 420.dp).skinPanel(spec).padding(horizontal = 20.dp, vertical = 16.dp)) {
    SkinText(spec.title("Where to?"), spec.title, spec.titleSize(if (spec.vi) 32.sp else 28.sp, 32.sp), spec.fg, spacing = spec.titleSpacing)
    if (saved.home == null && saved.work == null) {
      SkinText("Tap Search or Nearby, or set Home and Work on your phone.", spec.body, 19.sp, spec.sub, Modifier.padding(top = 4.dp), maxLines = 2)
    } else {
      Row(Modifier.padding(top = 12.dp)) {
        if (saved.home != null) {
          SkinSecondaryButton(spec, spec.title("Home"), {}, Modifier.onGloballyPositioned { CarTapTargets.home = it.boundsInRoot() }, icon = GameIcon.HOME)
        }
        if (saved.home != null && saved.work != null) Spacer(Modifier.width(10.dp))
        if (saved.work != null) {
          SkinSecondaryButton(spec, spec.title("Work"), {}, Modifier.onGloballyPositioned { CarTapTargets.work = it.boundsInRoot() }, icon = GameIcon.WORK)
        }
      }
    }
  }
}

/**
 * Free driving (no trip) from the speed in m/s, held through slow traffic: crawling along, the speed
 * kept dipping under the line and the HUD flipped between the speedometer and "Where to?" with every
 * fix. On at once above [FREE_DRIVE_SPEED]; off only once the car has stood still for
 * [STOPPED_HOLD_MS]. That was 20 s, shorter than most Delhi red lights, so "Where to?" came back
 * at nearly every signal.
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
/** Three minutes standing still: parked, not waiting at a light. */
private const val STOPPED_HOLD_MS = 180_000L

/** The map buttons show for this long after a touch, as long as Android Auto keeps its own up (10 s). */
private const val CAM_SHOWN_MS = 10_000L

/**
 * A card that fades and settles in (a slight grow on a soft spring) when it appears, instead of
 * popping onto the car screen.
 */
@Composable
private fun Appear(modifier: Modifier, content: @Composable () -> Unit) {
  val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
  AnimatedVisibility(
      shown,
      modifier,
      enter = fadeIn(com.thealgothrim.overworld.map.layoutSpring()) + scaleIn(com.thealgothrim.overworld.map.layoutSpring(), initialScale = 0.94f),
      exit = fadeOut(),
  ) {
    content()
  }
}

/**
 * Whether the buttons that keep out of the way should show: like Google Maps, only for a few
 * seconds after a touch, moving or not (they used to stay whenever the car was under 8 km/h, which
 * in Delhi traffic was nearly always). Shown for [CAM_SHOWN_MS] after the screen is touched, and
 * gone early when Android Auto puts its own buttons away, so both leave together. Android Auto's
 * signal alone isn't trusted to show them: where its area never changes (split screen, the idle
 * strip) it read as "up" for good and kept ours on.
 */
@Composable
private fun rememberTouchedRecently(): Boolean {
  val hostUp by CarTapTargets.hostButtonsUp
  val touchedAt by CarTapTargets.touchedAt
  var touched by remember { mutableStateOf(false) }
  LaunchedEffect(touchedAt) {
    if (touchedAt == 0L) return@LaunchedEffect
    touched = true
    delay(CAM_SHOWN_MS)
    touched = false
  }
  // Android Auto's buttons went away after the touch: ours go too.
  var hostWentDown by remember { mutableStateOf(false) }
  LaunchedEffect(touchedAt) { hostWentDown = false }
  LaunchedEffect(hostUp) { if (hostUp == false && touched) hostWentDown = true }
  return touched && !hostWentDown
}

/**
 * The map's own buttons down the right, in the game's round style: sound, zoom in and out, and the
 * route overview (sound and overview on a trip only). Android Auto's were plain grey ones it draws
 * itself, and it ignores the colours an app asks for, so these replace them. Pressed through map taps (see
 * CarNavigationScreen); they keep out of the way while driving, except that once the map has been
 * moved (dragged, zoomed, the overview) the bottom one turns into re-centre and stays.
 */
@Composable
private fun MapButtons(spec: SkinSpec, uiState: NavigationUiState, modifier: Modifier, trip: Boolean = true) {
  val following by CarTapTargets.following
  val zoomHeld by CarTapTargets.zoomHeld
  val moved = !following || zoomHeld
  val shown = rememberTouchedRecently()
  val enter = fadeIn(com.thealgothrim.overworld.map.layoutSpring()) + slideInHorizontally(com.thealgothrim.overworld.map.layoutSpring()) { it / 2 }
  val exit = fadeOut() + slideOutHorizontally { it / 2 }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
    AnimatedVisibility(shown, enter = enter, exit = exit) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (trip) MapButton(spec, if (uiState.isMuted == true) GameIcon.SOUND_OFF else GameIcon.SOUND_ON) { CarTapTargets.mute = it }
        MapButton(spec, GameIcon.ZOOM_IN) { CarTapTargets.zoomIn = it }
        MapButton(spec, GameIcon.ZOOM_OUT) { CarTapTargets.zoomOut = it }
      }
    }
    // With no trip there's no route to show, so only re-centre.
    AnimatedVisibility(if (trip) shown || moved else moved, enter = enter, exit = exit) {
      MapButton(spec, if (moved) GameIcon.NAVIGATE else GameIcon.ROUTE) { CarTapTargets.overview = it }
    }
  }
}

@Composable
private fun MapButton(spec: SkinSpec, icon: GameIcon, target: (Rect?) -> Unit) {
  DisposableEffect(Unit) { onDispose { target(null) } }
  SkinRoundButton(spec, icon, {}, Modifier.size(60.dp).onGloballyPositioned { target(it.boundsInRoot()) })
}
