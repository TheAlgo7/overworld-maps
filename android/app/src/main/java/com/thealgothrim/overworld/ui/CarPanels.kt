package com.thealgothrim.overworld.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.search.Nearby
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.theme.MapTime
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.THEMES
import com.thealgothrim.overworld.traffic.metres
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.icon
import com.thealgothrim.overworld.ui.skin.skinPanel
import com.thealgothrim.overworld.ui.vi.Vi
import kotlinx.coroutines.delay

/** A list on the car screen: Settings, Saved places, Nearby kinds, or the places of one kind. */
sealed interface CarPanel {
  data object Settings : CarPanel

  data object Saved : CarPanel

  data object NearbyKinds : CarPanel

  /** [places] is null while they load. */
  data class NearbyList(val kind: Nearby, val places: List<Place>?) : CarPanel

  /** What a search on Android Auto's keyboard page found; [places] is null while it looks. */
  data class Results(val query: String, val places: List<Place>?) : CarPanel
}

/** The panel open on the car screen, if any (set by CarNavigationScreen). */
object CarPanels {
  var open by mutableStateOf<CarPanel?>(null)

  /** The open panel's scroll and a scope that can animate it, set while the panel shows. */
  @Volatile internal var scroll: ScrollState? = null
  @Volatile internal var scope: CoroutineScope? = null
  private var glide: Job? = null

  /**
   * A drag on the car screen while a panel is open: the panel's list moves, the map under it stays
   * (7 Nov: the Settings list stood still and the map slid about behind it). [distanceY] is Android
   * Auto's scroll distance in surface pixels, finger travel reversed. Main thread.
   */
  fun scrollBy(distanceY: Float) {
    glide?.cancel()
    scroll?.dispatchRawDelta(distanceY)
  }

  /** A flick while a panel is open: the list glides on and slows, like a phone's. [velocityY] is the finger's, px/s. */
  fun fling(velocityY: Float) {
    val state = scroll ?: return
    val scope = scope ?: return
    glide?.cancel()
    if (kotlin.math.abs(velocityY) < MIN_FLING) return
    glide = scope.launch { state.animateScrollBy(-velocityY * FLING_TAU, tween(FLING_MS, easing = LinearOutSlowInEasing)) }
  }

  private const val MIN_FLING = 250f
  /** Seconds: a flick carries on for about its speed times this, as on the map (see CarMapGestures). */
  private const val FLING_TAU = 0.325f
  private const val FLING_MS = 700
}

/** What the panel's and the themed strip's taps do, beyond the settings the panel changes itself. */
interface CarPanelActions {
  fun showNearby()

  fun showSaved()

  fun showSettings()

  fun search()

  fun endTrip()

  fun close()

  fun back()

  fun nearby(kind: Nearby)

  fun pickNearby(place: Place)

  fun pickSaved(place: Place)
}

/**
 * The car's Settings, Saved and Nearby in the game's own look, on the left where the rest of
 * Overworld's controls are, in place of the "Where to?" card or the trip's cards. Android
 * Auto's lists can't be restyled: an app may only tint a few of their colours. So this is drawn on
 * the map like the HUD, and its taps arrive as map taps (see CarNavigationScreen), checked against
 * the areas each part records here. Sized for the HUD's 500 dp tall room; the HUD scale shrinks it
 * on smaller screens.
 */
@Composable
fun CarPanelView(panel: CarPanel, theme: OverworldTheme, spec: SkinSpec, actions: CarPanelActions, modifier: Modifier = Modifier) {
  DisposableEffect(Unit) { onDispose { CarTapTargets.panelTargets.clear() } }
  // Each panel opens at its top; a drag on the screen scrolls it (see CarPanels.scrollBy).
  val scroll = remember(panel::class) { ScrollState(0) }
  val scope = rememberCoroutineScope()
  DisposableEffect(scroll, scope) {
    CarPanels.scroll = scroll
    CarPanels.scope = scope
    onDispose {
      if (CarPanels.scroll === scroll) CarPanels.scroll = null
      if (CarPanels.scope === scope) CarPanels.scope = null
    }
  }
  Column(
      // As tall as its rows, from the top: two saved places don't need the screen's height. Taller
      // than the room (Settings on the Curvv: its last row was cut off), the rows under the header
      // scroll, with a bar down the side to say so.
      modifier.widthUpTo(PANEL_WIDTH, COLUMN_SHARE).skinPanel(spec).carTap("panel") {}.padding(horizontal = 18.dp, vertical = 14.dp),
  ) {
    val title =
        when (panel) {
          CarPanel.Settings -> "Settings"
          CarPanel.Saved -> "Saved"
          CarPanel.NearbyKinds -> "Nearby"
          is CarPanel.NearbyList -> panel.kind.label
          is CarPanel.Results -> "\u201C${panel.query}\u201D"
        }
    Header(spec, title, back = panel is CarPanel.NearbyList, actions)
    Spacer(Modifier.height(10.dp))
    Column(
        // The drag arrives from Android Auto (see CarPanels.scrollBy), not as touches on this view.
        Modifier.weight(1f, fill = false).scrollBar(scroll, spec).verticalScroll(scroll, enabled = false),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
    when (panel) {
      CarPanel.Settings -> SettingsBody(spec, theme)
      CarPanel.Saved -> SavedBody(spec, actions)
      CarPanel.NearbyKinds -> KindsBody(spec, actions)
      is CarPanel.NearbyList -> PlacesBody(spec, panel.places, panel.kind.icon, "Looking nearby…", "Nothing found within 10 km.", actions)
      is CarPanel.Results -> PlacesBody(spec, panel.places, GameIcon.PIN, "Searching…", "Nothing found. Try another name or a plus code.", actions)
    }
    }
  }
}

/**
 * A thin bar down the right edge while the list runs past the panel, so it's plain there's more
 * below. Before verticalScroll in the chain, so it's drawn over the visible part.
 */
private fun Modifier.scrollBar(state: ScrollState, spec: SkinSpec): Modifier = drawWithContent {
  drawContent()
  val max = state.maxValue
  if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
  val view = size.height
  val bar = (view * view / (view + max)).coerceAtLeast(24.dp.toPx())
  val top = (view - bar) * (state.value.toFloat() / max)
  val w = 3.dp.toPx()
  drawRoundRect(spec.fg.copy(alpha = 0.35f), topLeft = Offset(size.width - w, top), size = Size(w, bar), cornerRadius = CornerRadius(w / 2))
}

@Composable
private fun Header(spec: SkinSpec, title: String, back: Boolean, actions: CarPanelActions) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (back) {
      PanelButton(spec, "back", GameIcon.BACK, actions::back)
      Spacer(Modifier.width(12.dp))
    }
    SkinText(spec.title(title), spec.title, spec.titleSize(if (spec.vi) 30.sp else 28.sp, 32.sp), spec.fg, Modifier.weight(1f), spacing = spec.titleSpacing)
    PanelButton(spec, "close", GameIcon.CLOSE, actions::close)
  }
}

@Composable
private fun PanelButton(spec: SkinSpec, key: String, icon: GameIcon, onTap: () -> Unit, targets: TapMap = CarTapTargets.panelTargets) {
  Box(
      Modifier.size(48.dp).clip(shape(spec)).background(pressedTint(spec, key, spec.fg.copy(alpha = 0.12f))).carTap(key, targets, onTap),
      contentAlignment = Alignment.Center,
  ) {
    GameIconView(icon, spec.fg, Modifier.size(26.dp))
  }
}

@Composable
private fun SettingsBody(spec: SkinSpec, theme: OverworldTheme) {
  val store = AppModule.themeStore
  val details by store.carDetails.collectAsState()
  val time by store.carMapTime.collectAsState()
  val gameHud by store.carGameHud.collectAsState()
  val camButton by store.carCamButton.collectAsState()
  val beep by AppModule.viewModel.cameraBeep.collectAsState()
  val live = AppModule.traffic.hasLiveTraffic

  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    THEMES.forEach { t ->
      Box(Modifier.weight(1f).carTap("theme-${t.id}") { store.selectCar(t.id) }) {
        ThemeCard(spec, t, selected = t.id == theme.id, modifier = Modifier.fillMaxWidth(), accentName = true) {}
      }
    }
  }
  if (theme.variants.isNotEmpty()) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      MapTime.entries.forEach { t ->
        val on = t == time
        Box(
            Modifier.weight(1f).height(42.dp).clip(shape(spec))
                .background(if (on) spec.accent else pressedTint(spec, "time-$t", spec.fg.copy(alpha = 0.1f)))
                .carTap("time-$t") { store.setCarMapTime(t) },
            contentAlignment = Alignment.Center,
        ) {
          SkinText(spec.title(t.label), spec.title, 18.sp, if (on) spec.onAccent else spec.fg, spacing = spec.titleSpacing)
        }
      }
    }
  }
  // Two columns of switches, each lit in the theme's accent while on. On the Curvv they run past the
  // bottom of the panel, which then scrolls.
  val toggles =
      buildList<Triple<String, Boolean, () -> Unit>> {
        add(Triple("Place icons", details.placeIcons) { store.setCarDetails(details.copy(placeIcons = !details.placeIcons)) })
        if (theme.buildings3d) add(Triple("3D buildings", details.buildings3d) { store.setCarDetails(details.copy(buildings3d = !details.buildings3d)) })
        if (live) add(Triple("Traffic everywhere", details.traffic) { store.setCarDetails(details.copy(traffic = !details.traffic)) })
        add(Triple("Traffic lights", details.signals) { store.setCarDetails(details.copy(signals = !details.signals)) })
        if (live) add(Triple("Incidents", details.incidents) { store.setCarDetails(details.copy(incidents = !details.incidents)) })
        add(Triple("Game HUD", gameHud) { store.setCarGameHud(!gameHud) })
        add(Triple("Camera beep", beep) { AppModule.viewModel.setCameraBeep(!beep) })
        add(Triple("+ Cam button", camButton) { store.setCarCamButton(!camButton) })
      }
  toggles.chunked(2).forEach { pair ->
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      pair.forEach { (label, on, flip) -> ToggleTile(spec, label, on, flip, Modifier.weight(1f)) }
      if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
  }
  MarkTile(spec)
}

/**
 * A switch as one tile: the theme's accent while on, dim while off, no tick box (his 7 Nov ask:
 * the ticks read as clutter and the state should be the colour). Flips the moment Android Auto's
 * tap arrives; anything slow it sets off (a map style rebuild) happens behind it.
 */
@Composable
private fun ToggleTile(spec: SkinSpec, label: String, on: Boolean, flip: () -> Unit, modifier: Modifier) {
  val key = "toggle-$label"
  val rest = if (on) spec.accent else spec.fg.copy(alpha = 0.08f)
  Row(
      modifier.height(46.dp).clip(shape(spec)).background(pressedTint(spec, key, rest)).carTap(key, onTap = flip).padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    SkinText(label, spec.body, 17.sp, if (on) spec.onAccent else spec.fg, Modifier.weight(1f))
  }
}

/**
 * Marks this moment for a problem report (see ProblemReport): where the car is, what the screen
 * showed, the time. Says "Marked" for a few seconds after.
 */
@Composable
private fun MarkTile(spec: SkinSpec) {
  val key = "mark"
  var markedAt by remember { mutableStateOf(0L) }
  LaunchedEffect(markedAt) {
    if (markedAt == 0L) return@LaunchedEffect
    delay(MARKED_MS)
    markedAt = 0L
  }
  val marked = markedAt != 0L
  Row(
      Modifier.fillMaxWidth().height(46.dp).clip(shape(spec))
          .background(pressedTint(spec, key, if (marked) spec.accent else spec.fg.copy(alpha = 0.08f)))
          .carTap(key) {
            com.thealgothrim.overworld.report.ProblemReport.mark("car screen")
            markedAt = System.currentTimeMillis()
          }
          .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    GameIconView(GameIcon.FLAG, if (marked) spec.onAccent else spec.fg, Modifier.size(22.dp))
    Spacer(Modifier.width(12.dp))
    SkinText(if (marked) "Marked. It goes in the next report." else "Something wrong? Mark this moment", spec.body, 17.sp, if (marked) spec.onAccent else spec.fg)
  }
}

private const val MARKED_MS = 4_000L

@Composable
private fun SavedBody(spec: SkinSpec, actions: CarPanelActions) {
  val saved by AppModule.saved.state.collectAsState()
  val entries =
      buildList {
            saved.home?.let { add(Triple("Home", it, GameIcon.HOME)) }
            saved.work?.let { add(Triple("Work", it, GameIcon.WORK)) }
            saved.starred.forEach { add(Triple(it.name, it, GameIcon.STAR_FILLED)) }
            saved.recents.filter { it.name !in UNNAMED }.forEach { add(Triple(it.name, it, GameIcon.RECENT)) }
          }
          // One row per spot, as on the "Where to?" card.
          .fold(emptyList<Triple<String, Place, GameIcon>>()) { kept, next ->
            if (kept.any { metres(it.second.coordinate, next.second.coordinate) < SAME_SPOT_M }) kept else kept + next
          }
          .take(MAX_ROWS)
  if (entries.isEmpty()) SkinText(NO_SAVED_PLACES, spec.body, 18.sp, spec.sub, maxLines = 2)
  entries.forEach { (title, place, icon) ->
    PlaceRow(spec, "saved-${place.coordinate.lat},${place.coordinate.lng}", title, if (title != place.name) place.name else place.detail, away(place), icon) { actions.pickSaved(place) }
  }
}

@Composable
private fun KindsBody(spec: SkinSpec, actions: CarPanelActions) {
  Nearby.shown().chunked(3).forEach { row ->
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      row.forEach { kind ->
        val key = "kind-${kind.name}"
        Column(
            Modifier.weight(1f).height(118.dp).clip(shape(spec)).background(pressedTint(spec, key, spec.fg.copy(alpha = 0.08f))).carTap(key) { actions.nearby(kind) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
          GameIconView(kind.icon, spec.fg, Modifier.size(44.dp))
          Spacer(Modifier.height(10.dp))
          SkinText(kind.label, spec.body, 17.sp, spec.fg)
        }
      }
      repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
  }
}

@Composable
private fun PlacesBody(spec: SkinSpec, places: List<Place>?, icon: GameIcon, loading: String, empty: String, actions: CarPanelActions) {
  when {
    places == null -> SkinText(loading, spec.body, 18.sp, spec.sub)
    places.isEmpty() -> SkinText(empty, spec.body, 18.sp, spec.sub, maxLines = 2)
    else ->
        places.take(MAX_ROWS).forEach { place ->
          // Along a trip the note ("On the way · 2.4 km ahead", "+3 min") says more than the distance.
          PlaceRow(spec, "place-${place.coordinate.lat},${place.coordinate.lng}", place.name, place.detail, place.note ?: away(place), icon) { actions.pickNearby(place) }
        }
  }
}

@Composable
private fun PlaceRow(
    spec: SkinSpec,
    key: String,
    title: String,
    subtitle: String?,
    trailing: String?,
    icon: GameIcon,
    targets: TapMap = CarTapTargets.panelTargets,
    onTap: () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(shape(spec)).background(pressedTint(spec, key, Color.Transparent)).carTap(key, targets, onTap).padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    GameIconView(icon, spec.sub, Modifier.size(28.dp))
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      SkinText(title, spec.body, 19.sp, spec.fg)
      subtitle?.takeIf { it.isNotBlank() }?.let { SkinText(it, spec.body, 14.sp, spec.sub) }
    }
    trailing?.let {
      Spacer(Modifier.width(10.dp))
      SkinText(it, spec.body, 15.sp, spec.sub)
    }
  }
}

/** Straight-line distance from the car, or from its last known spot before the first fix. */
private fun away(place: Place): String = formatDistance(metres(AppModule.viewModel.currentCoordinate ?: AppModule.viewModel.startPoint, place.coordinate))

private fun shape(spec: SkinSpec) = RoundedCornerShape(if (spec.vi) 10.dp else 0.dp)

/** [rest], or the accent's glow for a moment after the part under [key] was tapped. */
@Composable
private fun pressedTint(spec: SkinSpec, key: String, rest: Color): Color {
  val pressed by CarTapTargets.pressed
  var lit by remember { mutableStateOf(false) }
  LaunchedEffect(pressed) {
    if (pressed?.first != key) return@LaunchedEffect
    lit = true
    delay(PRESS_MS)
    lit = false
  }
  return if (lit) spec.accent.copy(alpha = 0.45f) else rest
}

/**
 * [max] wide, or [share] of the room when that's less. (fillMaxWidth(share).widthIn(max) never
 * capped anything: the share fixes the width first, and on the Curvv's full screen the card was 40%
 * of it.)
 */
private fun Modifier.widthUpTo(max: Dp, share: Float): Modifier = layout { measurable, constraints ->
  val width = minOf(max.roundToPx(), (constraints.maxWidth * share).toInt()).coerceAtLeast(constraints.minWidth)
  val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
  layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private typealias TapMap = java.util.concurrent.ConcurrentHashMap<String, Pair<androidx.compose.ui.geometry.Rect, () -> Unit>>

/** Records this part's area (surface pixels) as a tap target for the car screen, in [targets]. */
@Composable
private fun Modifier.carTap(key: String, targets: TapMap = CarTapTargets.panelTargets, onTap: () -> Unit): Modifier {
  val action by rememberUpdatedState(onTap)
  DisposableEffect(key) { onDispose { targets.remove(key) } }
  return onGloballyPositioned { targets[key] = it.boundsInRoot() to { action() } }
}

/**
 * Overworld's own controls, all in one column on the left like Google's one card: parked, the
 * "Where to?" card ([CarWhereToCard]); driving, this row of Nearby, Saved and Settings (and End on a
 * trip), which comes and goes with a touch like Android Auto's buttons. The open panel's button is
 * lit, and a second tap closes it. Android Auto keeps one button of its own top right (search):
 * a map screen must have one.
 */
@Composable
fun CarMenuRow(spec: SkinSpec, trip: Boolean, actions: CarPanelActions, gated: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
  val open = CarPanels.open
  val targets = if (gated) CarTapTargets.stripTargets else CarTapTargets.cardTargets
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    MenuChip(spec, targets, "Nearby", GameIcon.FOOD, open is CarPanel.NearbyKinds || open is CarPanel.NearbyList, compact, actions::showNearby)
    if (!trip) MenuChip(spec, targets, "Saved", GameIcon.STAR, open == CarPanel.Saved, compact, actions::showSaved)
    MenuChip(spec, targets, "Settings", GameIcon.SETTINGS, open == CarPanel.Settings, compact, actions::showSettings)
    if (trip) MenuChip(spec, targets, "End", GameIcon.CLOSE, false, compact, actions::endTrip)
  }
}

/** [CarMenuRow] that shows for a while after a touch, with Android Auto's own buttons. */
@Composable
fun CarMenuReveal(spec: SkinSpec, trip: Boolean, actions: CarPanelActions, modifier: Modifier = Modifier) {
  androidx.compose.animation.AnimatedVisibility(
      rememberTouchedRecently(),
      modifier,
      enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(HOST_FADE_MS)),
      exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(HOST_FADE_MS)),
  ) {
    DisposableEffect(Unit) { onDispose { CarTapTargets.stripTargets.clear() } }
    CarMenuRow(spec, trip, actions, gated = true)
  }
}

@Composable
private fun MenuChip(
    spec: SkinSpec,
    targets: java.util.concurrent.ConcurrentHashMap<String, Pair<androidx.compose.ui.geometry.Rect, () -> Unit>>,
    label: String,
    icon: GameIcon,
    lit: Boolean,
    /** Icon only, a square button: the compact "Where to?" card. */
    compact: Boolean,
    onTap: () -> Unit,
) {
  val key = "menu-$label"
  val action by rememberUpdatedState(onTap)
  DisposableEffect(key) { onDispose { targets.remove(key) } }
  val modifier = Modifier.height(52.dp).onGloballyPositioned { targets[key] = it.boundsInRoot() to { action() } }
  val pressed by CarTapTargets.pressed
  var dip by remember { mutableStateOf(false) }
  LaunchedEffect(pressed) {
    if (pressed?.first != key) return@LaunchedEffect
    dip = true
    delay(PRESS_MS)
    dip = false
  }
  when {
    compact ->
        Box(
            modifier.width(56.dp).clip(shape(spec)).background(if (lit || dip) spec.accent.copy(alpha = 0.55f) else spec.fg.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
          GameIconView(icon, spec.fg, Modifier.size(26.dp))
        }
    // On the cards' own surface, so they read over any map (see-through buttons vanished on GTA
    // VI's pale daytime map) and sit as one with the turn card above them.
    else ->
        Row(
            modifier.skinPanel(spec).then(if (lit || dip) Modifier.background(spec.accent.copy(alpha = 0.55f), shape(spec)) else Modifier).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          GameIconView(icon, spec.fg, Modifier.size(22.dp))
          Spacer(Modifier.width(10.dp))
          SkinText(spec.title(label), spec.title, spec.titleSize(19.sp, 21.sp), spec.fg, spacing = spec.titleSpacing)
        }
  }
}

/**
 * Parked with no trip: "Where to?" with search, Home, Work and the last places, each one tap to go,
 * like Google's card of suggestions, and the menu along its foot. Its taps arrive as map taps.
 */
@Composable
fun CarWhereToCard(spec: SkinSpec, actions: CarPanelActions, modifier: Modifier = Modifier) {
  val saved by AppModule.saved.state.collectAsState()
  DisposableEffect(Unit) { onDispose { CarTapTargets.cardTargets.clear() } }
  val places =
      buildList {
            saved.home?.let { add(Triple("Home", it, GameIcon.HOME)) }
            saved.work?.let { add(Triple("Work", it, GameIcon.WORK)) }
            // Trips started without a name ("Destination") say nothing on a card of suggestions.
            saved.recents.filter { it.name !in UNNAMED }.forEach { add(Triple(it.name, it, GameIcon.RECENT)) }
          }
          // One row per spot: a recent "Chhatarpur" a few metres from Home is Home.
          .fold(emptyList<Triple<String, Place, GameIcon>>()) { kept, next ->
            if (kept.any { metres(it.second.coordinate, next.second.coordinate) < SAME_SPOT_M }) kept else kept + next
          }
          .take(CARD_PLACES)
  // Up to the panel's width, and no more than about 60% of the map: beside Spotify it took most of it.
  Column(modifier.widthUpTo(CARD_WIDTH, CARD_SHARE).skinPanel(spec).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      SkinText(spec.title("Where to?"), spec.title, spec.titleSize(if (spec.vi) 30.sp else 28.sp, 32.sp), spec.fg, Modifier.weight(1f), spacing = spec.titleSpacing)
      PanelButton(spec, "card-search", GameIcon.SEARCH, actions::search, CarTapTargets.cardTargets)
    }
    if (places.isEmpty()) SkinText("Search for a place, or tap Nearby.", spec.body, 18.sp, spec.sub, maxLines = 2)
    places.forEach { (title, place, icon) ->
      // The distance under the name, not beside it: the card stays narrow, like Google's.
      val sub = listOfNotNull((if (title != place.name) place.name else place.detail).takeIf { it.isNotBlank() }, away(place)).joinToString("  ·  ")
      PlaceRow(spec, "card-${place.coordinate.lat},${place.coordinate.lng}", title, sub, null, icon, CarTapTargets.cardTargets) {
        actions.pickSaved(place)
      }
    }
    CarMenuRow(spec, trip = false, actions = actions, gated = false, modifier = Modifier.padding(top = 4.dp), compact = true)
  }
}

/**
 * Our buttons' fade. Android Auto draws its own the instant a finger lands; ours are told a few
 * frames later, so they fade in and out quicker than its to arrive and leave with it.
 */
internal const val HOST_FADE_MS = 70

/** The car's empty Saved list: what shows there, never "go to your phone" (Android for Cars rule VI-1). */
internal const val NO_SAVED_PLACES = "Home, Work and starred places show up here."

private val PANEL_WIDTH = 560.dp
/** Home, Work and recent places on the "Where to?" card. */
private const val CARD_PLACES = 4
/** The left column's most of the map's width. */
private const val COLUMN_SHARE = 0.6f
private val UNNAMED = setOf("Destination", "Dropped pin")
private const val SAME_SPOT_M = 250.0
/** The "Where to?" card: about a quarter of the Curvv's map, as compact as Google's card. */
private val CARD_WIDTH = 300.dp
private const val CARD_SHARE = 0.4f
private const val MAX_ROWS = 6
private const val PRESS_MS = 350L
