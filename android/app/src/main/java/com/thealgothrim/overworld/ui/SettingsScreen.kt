package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.BuildConfig
import com.thealgothrim.overworld.rememberLockedPhoneSetup
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.theme.MapTime
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.theme.THEMES
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.gta.GtaDescription
import com.thealgothrim.overworld.ui.gta.GtaInstructionalButtons
import com.thealgothrim.overworld.ui.gta.GtaMenuHeader
import com.thealgothrim.overworld.ui.gta.GtaMenuRow
import com.thealgothrim.overworld.ui.gta.GtaMenuSubheader
import com.thealgothrim.overworld.ui.gta.GtaPrompt
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrDescription
import com.thealgothrim.overworld.ui.rdr.RdrDivider
import com.thealgothrim.overworld.ui.rdr.RdrMenuRow
import com.thealgothrim.overworld.ui.rdr.RdrMenuTitle
import com.thealgothrim.overworld.ui.rdr.RdrPrompt
import com.thealgothrim.overworld.ui.rdr.RdrPrompts
import com.thealgothrim.overworld.ui.rdr.RdrText
import com.thealgothrim.overworld.ui.rdr.roughBox
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.spec
import com.thealgothrim.overworld.ui.vi.Vi
import com.thealgothrim.overworld.ui.vi.ViDescription
import com.thealgothrim.overworld.ui.vi.ViMenuHeader
import com.thealgothrim.overworld.ui.vi.ViMenuRow
import com.thealgothrim.overworld.ui.vi.ViPrompt
import com.thealgothrim.overworld.ui.vi.ViPrompts

private data class Setting(
    val label: String,
    val description: String,
    val value: String? = null,
    /** It forgets something: the first tap only picks the row and says so, the second does it. */
    val clears: Boolean = false,
    /** Like [clears] (two taps, the first explains) but nothing is lost, so the row doesn't turn red. */
    val confirm: Boolean = false,
    val onClick: () -> Unit,
)

/** The pause menu's tabs, like GTA V's across the top. */
private enum class SettingsTab(val label: String) {
  MAP("Map"),
  DRIVING("Driving"),
  PLACES("Places"),
  MORE("More"),
}

/** A switch's state in words: the games' menus say On and Off, no tick boxes (his 7 Nov ask). */
private fun onOff(on: Boolean) = if (on) "On" else "Off"

/**
 * Settings, drawn as the game's pause menu with its tabs across the top: Map, Driving, Places,
 * More. It was one list of about 17 rows, and the theme a row tapped over and over to cycle,
 * re-skinning the whole page under the finger at each tap (7 Nov: "overwhelming", "a big task").
 * Now each tab fits on one screen, and Map opens first with the three worlds as pictures: one tap.
 * A tap on a row explains it right under the row; rows that forget something ask for a second tap.
 */
@Composable
fun SettingsScreen(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    testDrive: Boolean,
    saved: SavedState,
    onTestDrive: (Boolean) -> Unit,
    onMute: () -> Unit,
    onClose: () -> Unit,
) {
  val store = AppModule.themeStore
  val spec = theme.spec
  val carGameHud by store.carGameHud.collectAsState()
  val carCamButton by store.carCamButton.collectAsState()
  val useGoogle by store.useGoogle.collectAsState()
  val mapTime by store.mapTime.collectAsState()
  val details by store.details.collectAsState()
  val variant by AppModule.viewModel.mapVariant.collectAsState()
  val cameraBeep by AppModule.viewModel.cameraBeep.collectAsState()
  val marked by AppModule.cameras.all.collectAsState()
  val live = AppModule.traffic.hasLiveTraffic
  val update by AppModule.updates.available.collectAsState()
  val (lockedReady, setUpLocked) = rememberLockedPhoneSetup()
  var tab by rememberSaveable { mutableStateOf(SettingsTab.MAP) }
  var selected by remember { mutableStateOf<String?>(null) }
  val context = androidx.compose.ui.platform.LocalContext.current

  val mapRows =
      listOfNotNull(
          Setting("Place icons", "The game's shop, food and petrol icons on the map. Tap one there to go to it.", onOff(details.placeIcons)) {
            store.setDetails(details.copy(placeIcons = !details.placeIcons))
          },
          if (theme.buildings3d)
              Setting("3D buildings", "Buildings stand up like on the GTA VI minimap. Off lays them flat, which is lighter work for the phone.", onOff(details.buildings3d)) {
                store.setDetails(details.copy(buildings3d = !details.buildings3d))
              }
          else null,
          Setting("Traffic lights", "Signals along your route, from OpenStreetMap.", onOff(details.signals)) { store.setDetails(details.copy(signals = !details.signals)) },
          if (live) Setting("Incidents", "Accidents, road works, closures and jams, with alerts ahead.", onOff(details.incidents)) { store.setDetails(details.copy(incidents = !details.incidents)) }
          else null,
          if (live)
              Setting("Traffic on every road", "Your route always shows its own traffic. This colours all roads: amber slow, red jammed.", onOff(details.traffic)) {
                store.setDetails(details.copy(traffic = !details.traffic))
              }
          else null,
      )
  val drivingRows =
      listOf(
          Setting("Voice guidance", "Spoken turn instructions while driving. Off unless you turn it on.", onOff(!(uiState.isMuted ?: AppModule.ttsObserver.isMuted))) { onMute() },
          Setting("Camera beep", "Two quick notes as a speed camera comes within 500 m, with a trip or without. Plays through the car like a turn prompt.", onOff(cameraBeep)) {
            AppModule.viewModel.setCameraBeep(!cameraBeep)
          },
          Setting(
              "Google routes and search",
              if (useGoogle) "Routes, traffic times and place search come from Google Maps, laid onto the game map. Off uses only open map data."
              else "Only open map data: OpenStreetMap routes and search, TomTom traffic.",
              onOff(useGoogle),
          ) { store.setUseGoogle(!useGoogle) },
          Setting(
              "Phone locked",
              if (lockedReady) "Keeps navigating with the phone locked in your pocket: the battery saver leaves the app alone. Tap to change it in Android's settings."
              else "Tap to keep it navigating with the phone locked: say yes when Android asks to let it run in the background.",
              if (lockedReady) "Works" else "Set up",
          ) { setUpLocked() },
          Setting("Test drive", "Simulates the trip along the route instead of using GPS. For trying things out without driving.", onOff(testDrive)) { onTestDrive(!testDrive) },
      )
  val carRows =
      listOf(
          Setting(
              "Car screen",
              if (carGameHud) "Android Auto shows the game HUD: turn, time and street drawn in the theme."
              else "Android Auto shows its own turn and ETA cards, in the theme's colour.",
              if (carGameHud) "Game HUD" else "Android Auto",
          ) { store.setCarGameHud(!carGameHud) },
          Setting(
              "+ Cam on the car screen",
              "A button on the car's map for marking a speed camera as you pass it. It shows for a few seconds after you touch the screen, then hides again.",
              onOff(carCamButton),
          ) { store.setCarCamButton(!carCamButton) },
      )
  val placeRows =
      listOf(
          Setting(
              "Home",
              if (saved.home != null) "Tap again to forget it. Set a new one from a place's Save button." else "Search for your home, tap Save, then Home.",
              saved.home?.name ?: "Not set",
              clears = saved.home != null,
          ) { AppModule.saved.setHome(null) },
          Setting(
              "Work",
              if (saved.work != null) "Tap again to forget it. Set a new one from a place's Save button." else "Search for your work place, tap Save, then Work.",
              saved.work?.name ?: "Not set",
              clears = saved.work != null,
          ) { AppModule.saved.setWork(null) },
          Setting(
              "Recent places",
              if (saved.recents.isNotEmpty()) "The places you've driven to, listed in Search. Tap again to clear them; starred places stay." else "Places you drive to show up in Search.",
              saved.recents.size.toString(),
              clears = saved.recents.isNotEmpty(),
          ) { AppModule.saved.clearRecents() },
          Setting(
              "Marked cameras",
              if (marked.isNotEmpty()) "Speed cameras you added with + Cam in the car, or Mark a camera here on the phone. Tap again to remove them all."
              else "None yet. Turn on + Cam on the car screen (Driving) and tap it as you pass one, or use Mark a camera here on the phone; it beeps there from then on.",
              marked.size.toString(),
              clears = marked.isNotEmpty(),
          ) { AppModule.cameras.clear() },
      )
  val moreRows =
      listOf(
          Setting(
              "Report a problem",
              "Tap again to email the developer what the app saw: your settings, its drive diary and where you drove in the last few hours, " +
                  "with moments you marked on the car screen. Your email app opens with the files attached; take the track file off if you'd rather not share where you went. Nothing is sent until you press Send.",
              confirm = true,
          ) { com.thealgothrim.overworld.report.ProblemReport.send(context) },
          Setting(
              "Overworld",
              "Map data © OpenStreetMap contributors (ODbL), tiles by OpenFreeMap, routing by Valhalla (FOSSGIS), search by TomTom and Photon (komoot), traffic lights from Valhalla and speed cameras from Overpass, navigation by Ferrostar (Stadia Maps)." +
                  if (live) " Live traffic by TomTom." else " Live traffic is off: add a free TomTom key (tomtomKey in local.properties) for traffic, incidents and live travel times.",
              BuildConfig.VERSION_NAME,
          ) {},
      )

  fun tap(r: Setting) {
    // Forgetting something takes two taps: the first picks the row and says what a second will do.
    if ((r.clears || r.confirm) && selected != r.label) {
      selected = r.label
      return
    }
    selected = r.label
    r.onClick()
  }

  // Scrim that eats taps so the map underneath doesn't move.
  val scrim =
      when (theme.skin) {
        Skin.GTA -> Color(0xEB000000)
        // GTA VI's menu has no solid header, so the map's own controls mustn't show through.
        Skin.GTA6 -> Color(0xFA141320)
        Skin.RDR -> Color(0xF0080706)
      }
  Box(
      Modifier.fillMaxSize()
          .background(scrim)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
          .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(Modifier.align(Alignment.TopStart).fillMaxWidth().widthIn(max = 480.dp).padding(16.dp).verticalScroll(rememberScrollState())) {
      when (theme.skin) {
        Skin.GTA -> GtaMenuHeader("SETTINGS")
        Skin.GTA6 -> ViMenuHeader("Settings", "Overworld", "")
        Skin.RDR -> RdrMenuTitle("SETTINGS", "Overworld", "")
      }
      // A newer Overworld is out: above the tabs, so it's seen whichever tab is open.
      update?.let { u ->
        Rows(theme.skin, listOf(Setting("Update available", "A newer Overworld is out. Tap to get it.", u.label) { AppModule.updates.open(u) }), selected, ::tap)
        Spacer(Modifier.height(10.dp))
      }
      SettingsTabs(spec, tab) {
        tab = it
        selected = null
      }
      Spacer(Modifier.height(12.dp))
      when (tab) {
        SettingsTab.MAP -> {
          // The worlds as pictures: one tap switches (it was a row tapped over and over to cycle).
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            THEMES.forEach { t -> ThemeCard(spec, t, selected = t.id == theme.id, modifier = Modifier.weight(1f), accentName = true) { store.select(t.id) } }
          }
          if (theme.variants.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            TimeOfDay(spec, mapTime, variant) { store.setMapTime(it) }
          }
          Spacer(Modifier.height(12.dp))
          Rows(theme.skin, mapRows, selected, ::tap)
        }
        SettingsTab.DRIVING -> {
          Rows(theme.skin, drivingRows, selected, ::tap)
          SectionTitle(theme.skin, "On the car screen")
          Rows(theme.skin, carRows, selected, ::tap)
        }
        SettingsTab.PLACES -> Rows(theme.skin, placeRows, selected, ::tap)
        SettingsTab.MORE -> Rows(theme.skin, moreRows, selected, ::tap)
      }
      // Room for the Back prompt below the last row.
      Spacer(Modifier.height(72.dp))
    }
    when (theme.skin) {
      Skin.GTA -> GtaInstructionalButtons(listOf(GtaPrompt("B", "Back", onClose)), Modifier.align(Alignment.BottomEnd).padding(16.dp))
      Skin.GTA6 -> ViPrompts(listOf(ViPrompt("B", "Back", onClose)), Modifier.align(Alignment.BottomEnd).padding(16.dp))
      Skin.RDR -> RdrPrompts(listOf(RdrPrompt("B", "Back", onClose)), Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp))
    }
  }
}

/** A tab's rows in the game's menu style, each explained under it when tapped. */
@Composable
private fun Rows(skin: Skin, rows: List<Setting>, selected: String?, tap: (Setting) -> Unit) {
  rows.forEach { r ->
    val isSelected = selected == r.label
    // A row that will forget something on the next tap turns red.
    val armed = isSelected && r.clears
    when (skin) {
      Skin.GTA -> {
        GtaMenuRow(r.label, isSelected, { tap(r) }, value = r.value, labelColor = if (armed) Gta.Red else null)
        if (isSelected) GtaDescription(r.description, color = if (armed) Gta.Red else Gta.White)
      }
      Skin.GTA6 -> {
        ViMenuRow(r.label, isSelected, { tap(r) }, value = r.value, labelColor = if (armed) Vi.Red else null)
        if (isSelected) ViDescription(r.description, color = if (armed) Vi.Red else Vi.White)
      }
      Skin.RDR -> {
        RdrMenuRow(r.label, isSelected, { tap(r) }, value = r.value, labelColor = if (armed) Rdr.Red else null)
        if (isSelected) RdrDescription(r.description, color = if (armed) Rdr.Red else Rdr.GreyLight)
      }
    }
  }
}

/**
 * The tabs across the top of the pause menu: GTA V's white tab with black letters on a row of dark
 * ones, GTA VI's pink-filled pill among glass ones, Red Dead's rough frame round the open one.
 */
@Composable
private fun SettingsTabs(spec: SkinSpec, open: SettingsTab, onOpen: (SettingsTab) -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (spec.gta) 2.dp else 6.dp)) {
    SettingsTab.entries.forEach { t ->
      val on = t == open
      val tabModifier = Modifier.weight(1f).height(40.dp)
      when {
        spec.gta ->
            Box(
                tabModifier.background(if (on) Gta.RowSelected else Gta.Panel).clickable { onOpen(t) },
                contentAlignment = Alignment.Center,
            ) {
              Text(t.label.uppercase(), color = if (on) Color.Black else Gta.White, fontFamily = spec.title, fontSize = 14.sp, maxLines = 1)
            }
        spec.vi -> {
          val shape = RoundedCornerShape(10.dp)
          Box(
              tabModifier.clip(shape).background(if (on) Vi.Pink else Vi.Glass).border(1.dp, if (on) Vi.Pink else Vi.Hairline, shape).clickable { onOpen(t) },
              contentAlignment = Alignment.Center,
          ) {
            Text(t.label.uppercase(), color = if (on) Vi.Ink else Vi.White, fontFamily = Vi.condensed, fontSize = 16.sp, letterSpacing = 0.8.sp, maxLines = 1)
          }
        }
        else ->
            Box(
                tabModifier.then(if (on) Modifier.roughBox() else Modifier).clickable { onOpen(t) },
                contentAlignment = Alignment.Center,
            ) {
              RdrText(t.label.uppercase(), 16.sp, color = if (on) Color.White else Rdr.Grey, spacing = 1.sp)
            }
      }
    }
  }
}

/** GTA VI's time of day as four chips, the one in use lit in the theme's colour. */
@Composable
private fun TimeOfDay(spec: SkinSpec, time: MapTime, variant: String?, onPick: (MapTime) -> Unit) {
  val shape = RoundedCornerShape(if (spec.vi) 10.dp else 0.dp)
  Column {
    // Auto says what the sun makes it now.
    val now = (variant ?: "day").replaceFirstChar { it.uppercase() }
    SkinText(spec.title(if (time == MapTime.AUTO) "Time of day: by the sun, $now now" else "Time of day"), spec.body, 14.sp, spec.sub)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      MapTime.entries.forEach { t ->
        val on = t == time
        Box(
            Modifier.weight(1f).height(40.dp).clip(shape).background(if (on) spec.accent else spec.fg.copy(alpha = 0.1f)).clickable { onPick(t) },
            contentAlignment = Alignment.Center,
        ) {
          SkinText(spec.title(t.label), spec.title, 16.sp, if (on) spec.onAccent else spec.fg, spacing = spec.titleSpacing)
        }
      }
    }
  }
}

/** A group's title: GTA V's black subheader bar, GTA VI's grey capitals, Red Dead's engraved rule. */
@Composable
private fun SectionTitle(skin: Skin, title: String) {
  when (skin) {
    Skin.GTA ->
        Box(Modifier.padding(top = 10.dp)) { GtaMenuSubheader(title) }
    Skin.GTA6 ->
        Text(
            title.uppercase(),
            color = Vi.Sub,
            fontFamily = Vi.condensed,
            fontSize = 15.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        )
    Skin.RDR ->
        Column(Modifier.padding(top = 14.dp)) {
          RdrText(title.uppercase(), 16.sp, color = Rdr.Grey, spacing = 2.sp)
          RdrDivider()
        }
  }
}
