package com.thealgothrim.overworld.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.AppIcon
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.BuildConfig
import com.thealgothrim.overworld.notify.Notices
import com.thealgothrim.overworld.prefs.EngineType
import com.thealgothrim.overworld.prefs.GuidanceVolume
import com.thealgothrim.overworld.prefs.TimelineKeep
import com.thealgothrim.overworld.prefs.Units
import com.thealgothrim.overworld.prefs.VoiceLevel
import com.thealgothrim.overworld.prefs.WalkPace
import com.thealgothrim.overworld.rememberLockedPhoneSetup
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.theme.THEMES
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.gta.GtaDescription
import com.thealgothrim.overworld.ui.gta.GtaMenuRow
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrDescription
import com.thealgothrim.overworld.ui.rdr.RdrMenuRow
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.spec
import com.thealgothrim.overworld.ui.vi.Vi
import com.thealgothrim.overworld.ui.vi.ViDescription
import com.thealgothrim.overworld.ui.vi.ViMenuRow

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

/**
 * The pause menu's tabs, one per group of Google Maps' settings (his pick, 8 Nov: seven tabs in a
 * row that slides sideways, like GTA V's pause menu).
 */
private enum class SettingsTab(val label: String) {
  DISPLAY("Display"),
  NAVIGATION("Navigation"),
  VEHICLE("Vehicle"),
  PRIVACY("Privacy"),
  OFFLINE("Offline"),
  ALERTS("Alerts"),
  ABOUT("About"),
}

/** A switch's state in words: the games' menus say On and Off, no tick boxes (his 7 Nov ask). */
private fun onOff(on: Boolean) = if (on) "On" else "Off"

private const val PRIVACY_URL = "https://overworld.thealgothrim.com/privacy"
private const val SITE_URL = "https://overworld.thealgothrim.com"
private const val NOTICES_URL = "https://github.com/TheAlgo7/overworld-maps/blob/main/THIRD_PARTY_NOTICES.txt"

/**
 * Settings, drawn as the game's pause menu with seven tabs across the top that slide sideways:
 * Display, Navigation, Vehicle, Privacy, Offline, Alerts, About (Google Maps' seven groups). Each
 * tab fits about one screen; choices are pictures or chips lit in the world's accent, switches say
 * On and Off. A tap on a row explains it right under the row; rows that forget something ask for
 * a second tap.
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
    /** Offline > Download this area: Settings closes and the map shows the area to download. */
    onDownloadArea: () -> Unit = {},
) {
  val store = AppModule.themeStore
  val prefs = AppModule.prefs
  val spec = theme.spec
  val context = LocalContext.current
  var tab by rememberSaveable { mutableStateOf(SettingsTab.DISPLAY) }
  var selected by remember { mutableStateOf<String?>(null) }
  val update by AppModule.updates.available.collectAsState()
  // A debug build's adb command picks the tab (DebugUi).
  LaunchedEffect(Unit) {
    DebugUi.settingsTab.collect { t ->
      if (t != null && t >= 0) {
        tab = SettingsTab.entries[t.coerceIn(0, SettingsTab.entries.lastIndex)]
        DebugUi.settingsTab.value = null
      }
    }
  }

  fun tap(r: Setting) {
    // Forgetting something takes two taps: the first picks the row and says what a second will do.
    if ((r.clears || r.confirm) && selected != r.label) {
      selected = r.label
      return
    }
    selected = r.label
    r.onClick()
  }

  fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

  PauseMenuFrame(theme, "Settings", onClose) {
    // A newer Overworld is out: above the tabs, so it's seen whichever tab is open.
    update?.let { u ->
      Rows(theme.skin, listOf(Setting("Update available", "A newer Overworld is out. Tap to get it.", u.label) { AppModule.updates.open(u) }), selected, ::tap)
      Spacer(Modifier.height(10.dp))
    }
    GameTabs(spec, SettingsTab.entries.map { it.label }, tab.ordinal, {
      tab = SettingsTab.entries[it]
      selected = null
    }, scroll = true)
    Spacer(Modifier.height(12.dp))
    when (tab) {
      SettingsTab.DISPLAY -> DisplayTab(theme, spec, selected, ::tap)
      SettingsTab.NAVIGATION -> NavigationTab(theme, spec, uiState, onMute, selected, ::tap)
      SettingsTab.VEHICLE -> VehicleTab(theme, spec, selected, ::tap)
      SettingsTab.PRIVACY -> PrivacyTab(theme, spec, saved, selected, ::tap)
      SettingsTab.OFFLINE -> OfflineTab(theme, spec, selected, ::tap, onDownloadArea)
      SettingsTab.ALERTS -> AlertsTab(theme, spec, selected, ::tap)
      SettingsTab.ABOUT -> {
        val live = AppModule.traffic.hasLiveTraffic
        Rows(
            theme.skin,
            listOf(
                Setting("Overworld", "Version ${BuildConfig.VERSION_NAME}. Free, no ads, no account. Made by The Algothrim.", BuildConfig.VERSION_NAME) {},
                Setting(
                    "Support Overworld",
                    "Overworld is free, with no ads and no subscription, and stays that way. If you'd like to put something toward its running costs, the website has a page for it. " +
                        "Nothing in the app changes either way. Tap again to open it in your browser.",
                    confirm = true,
                ) {
                  AppModule.prefs.answerSupport()
                  open(com.thealgothrim.overworld.prefs.SupportAsk.URL)
                },
                Setting(
                    "Report a problem",
                    "Tap again to email the developer what the app saw: your settings, its drive diary and where you drove in the last few hours, " +
                        "with moments you marked on the car screen. Your email app opens with the files attached; take the track file off if you'd rather not share where you went. Nothing is sent until you press Send.",
                    confirm = true,
                ) { com.thealgothrim.overworld.report.ProblemReport.send(context) },
                Setting(
                    "Terms of use",
                    "Overworld is a fan-made map for personal use. Watch the road, follow road signs and the law over the app, and only touch the phone when it's safe. Routes, traffic and speed limits can be wrong or out of date. Game names and looks belong to their owners; Overworld isn't made or endorsed by them.",
                ) {},
                Setting("Privacy policy", "What Overworld keeps on this phone (saved places, history, Timeline) and what it asks map services. Tap again to open it.", confirm = true) { open(PRIVACY_URL) },
                Setting("Open-source licences", "The open map data, fonts and libraries Overworld is built on, and their licences. Tap again to open them.", confirm = true) { open(NOTICES_URL) },
                Setting(
                    "Map data and credits",
                    "Map data © OpenStreetMap contributors (ODbL), tiles by OpenFreeMap, routing by Valhalla (FOSSGIS), search by TomTom and Photon (komoot), traffic lights from Valhalla and speed cameras from Overpass, Red Dead's contour lines from Terrain Tiles (Mapzen, on AWS; SRTM, USGS), navigation by Ferrostar (Stadia Maps)." +
                        if (live) " Live traffic by TomTom." else " Live traffic is off: add a free TomTom key (tomtomKey in local.properties) for traffic, incidents and live travel times.",
                ) {},
                Setting("Website", "overworld.thealgothrim.com: guides, the worlds, what's new. Tap again to open it.", confirm = true) { open(SITE_URL) },
                Setting("Test drive", "Simulates the trip along the route instead of using GPS. For trying things out without driving.", onOff(testDrive)) { onTestDrive(!testDrive) },
            ),
            selected,
            ::tap,
        )
      }
    }
    // On every tab, at the foot: the row in About was seven tabs deep, where nobody looks.
    SupportLine(spec)
  }
}

@Composable
private fun DisplayTab(theme: OverworldTheme, spec: SkinSpec, selected: String?, tap: (Setting) -> Unit) {
  val store = AppModule.themeStore
  val details by store.details.collectAsState()
  val mapTime by store.mapTime.collectAsState()
  val variant by AppModule.viewModel.mapVariant.collectAsState()
  val units by AppModule.prefs.units.collectAsState()
  val live = AppModule.traffic.hasLiveTraffic
  // The worlds as pictures: one tap switches (it was a row tapped over and over to cycle).
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    THEMES.forEach { t -> ThemeCard(spec, t, selected = t.id == theme.id, modifier = Modifier.weight(1f)) { store.select(t.id) } }
  }
  if (theme.timeOfDay) {
    Gap(10)
    val now = (variant ?: "day").replaceFirstChar { it.uppercase() }
    LabeledChoices(spec, if (mapTime == com.thealgothrim.overworld.theme.MapTime.AUTO) "Time of day: by the sun, $now now" else "Time of day", com.thealgothrim.overworld.theme.MapTime.entries, mapTime, { it.label }) { store.setMapTime(it) }
  }
  LabeledChoices(spec, "Distance units", Units.entries, units, { if (it == Units.AUTO) "Auto (${if (it.miles) "mi" else "km"})" else it.label }) { AppModule.prefs.setUnits(it) }
  // The launcher icon, as pictures like the worlds (his ask, 13 Nov): it follows the world unless
  // one is picked by hand; GTA VI has two, plain and with Macca the Gator.
  val icons = AppModule.appIcons
  val iconChoice by icons.choice.collectAsState()
  val iconNow = iconChoice.forWorld(theme.id)
  Column(Modifier.padding(vertical = 6.dp)) {
    SkinText(spec.title("App icon"), spec.title, spec.titleSize(14.sp, 16.sp), spec.sub, Modifier.padding(start = 2.dp, bottom = 6.dp), spacing = spec.titleSpacing)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      AppIcon.entries.forEach { i -> AppIconTile(spec, i, selected = i == iconNow, modifier = Modifier.weight(1f)) { icons.pick(i, theme.id) } }
    }
  }
  Gap(4)
  Rows(
      theme.skin,
      listOfNotNull(
          Setting(
              "Icon follows the world",
              "On: picking a world switches the app's icon to that world's (GTA VI keeps the one you picked, plain or Macca). Off: the icon stays as picked above. " +
                  "The new icon shows once you leave the app, and waits until a trip ends.",
              onOff(iconChoice.followsWorld),
          ) { icons.setFollowsWorld(!iconChoice.followsWorld, theme.id) },
          Setting("Place icons", "The game's shop, food and petrol icons on the map. Tap one there to go to it.", onOff(details.placeIcons)) { store.setDetails(details.copy(placeIcons = !details.placeIcons)) },
          if (theme.buildings3d) Setting("3D buildings", "Buildings stand up like on the GTA VI minimap. Off lays them flat, which is lighter work for the phone.", onOff(details.buildings3d)) { store.setDetails(details.copy(buildings3d = !details.buildings3d)) } else null,
          Setting("Traffic lights", "Signals along your route, from OpenStreetMap.", onOff(details.signals)) { store.setDetails(details.copy(signals = !details.signals)) },
          if (live) Setting("Incidents", "Accidents, road works, closures and jams, with alerts ahead.", onOff(details.incidents)) { store.setDetails(details.copy(incidents = !details.incidents)) } else null,
          if (live) Setting("Traffic on every road", "Your route always shows its own traffic. This colours all roads: amber slow, red jammed.", onOff(details.traffic)) { store.setDetails(details.copy(traffic = !details.traffic)) } else null,
      ),
      selected,
      tap,
  )
}

@Composable
private fun NavigationTab(theme: OverworldTheme, spec: SkinSpec, uiState: NavigationUiState, onMute: () -> Unit, selected: String?, tap: (Setting) -> Unit) {
  val prefs = AppModule.prefs
  val vm = AppModule.viewModel
  val cameraBeep by vm.cameraBeep.collectAsState()
  val muted = uiState.isMuted ?: AppModule.ttsObserver.isMuted
  val volume by prefs.volume.collectAsState()
  val avoidTolls by prefs.avoidTolls.collectAsState()
  val avoidHighways by prefs.avoidHighways.collectAsState()
  val avoidFerries by prefs.avoidFerries.collectAsState()
  val fuelSaver by prefs.fuelSaver.collectAsState()
  val northUp by prefs.northUp.collectAsState()
  val showLimit by prefs.showLimit.collectAsState()
  val showSpeed by prefs.showSpeed.collectAsState()
  val useGoogle by AppModule.themeStore.useGoogle.collectAsState()
  // Google's three mute states over the app's two switches: the voice, and the camera beep.
  val level = when { !muted -> VoiceLevel.ON; cameraBeep -> VoiceLevel.ALERTS; else -> VoiceLevel.MUTED }
  LabeledChoices(spec, "Voice", VoiceLevel.entries, level, { it.label }) { l ->
    val wantVoice = l == VoiceLevel.ON
    if (wantVoice == muted) onMute()
    vm.setCameraBeep(l != VoiceLevel.MUTED)
  }
  LabeledChoices(spec, "Guidance volume", GuidanceVolume.entries, volume, { it.label }) { prefs.setVolume(it) }
  Column(Modifier.fillMaxWidth()) {
    SkinHelp(spec, "Route options: kept off unless there's no other way.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ChoiceChip(spec, "No tolls", avoidTolls, { prefs.setAvoidTolls(!avoidTolls) }, Modifier.weight(1f))
      ChoiceChip(spec, "No highways", avoidHighways, { prefs.setAvoidHighways(!avoidHighways) }, Modifier.weight(1f))
      ChoiceChip(spec, "No ferries", avoidFerries, { prefs.setAvoidFerries(!avoidFerries) }, Modifier.weight(1f))
    }
  }
  Gap(6)
  Rows(
      theme.skin,
      listOf(
          Setting("Play test sound", "The camera beep, then a turn in the voice, at the volume above.") { AppModule.testSound() },
          Setting("Fuel-efficient routes", "Of two routes about as quick (within 5% or two minutes), take the shorter one: less road, less fuel.", onOff(fuelSaver)) { prefs.setFuelSaver(!fuelSaver) },
          Setting("Speed limit sign", "The road's limit beside your speed, in the world's own sign.", onOff(showLimit)) { prefs.setShowLimit(!showLimit) },
          Setting("Speedometer", "Your speed on the map while driving.", onOff(showSpeed)) { prefs.setShowSpeed(!showSpeed) },
          Setting("Keep map north up", "The phone's map stays north up on a trip instead of turning with the road. The car screen always turns with the road.", onOff(northUp)) { prefs.setNorthUp(!northUp) },
          Setting(
              "Google routes and search",
              if (useGoogle) "Routes, traffic times and place search come from Google Maps, laid onto the game map. Off uses only open map data."
              else "Only open map data: OpenStreetMap routes and search, TomTom traffic.",
              onOff(useGoogle),
          ) { AppModule.themeStore.setUseGoogle(!useGoogle) },
      ),
      selected,
      tap,
  )
}

@Composable
private fun VehicleTab(theme: OverworldTheme, spec: SkinSpec, selected: String?, tap: (Setting) -> Unit) {
  val prefs = AppModule.prefs
  val store = AppModule.themeStore
  val engine by prefs.engine.collectAsState()
  val pace by prefs.walkPace.collectAsState()
  val carGameHud by store.carGameHud.collectAsState()
  val carCamButton by store.carCamButton.collectAsState()
  val (lockedReady, setUpLocked) = rememberLockedPhoneSetup()
  SkinSectionTitle(spec, "Engine type")
  Gap(6)
  ChoiceRow(spec, EngineType.entries, engine, { if (it == EngineType.ELECTRIC) "EV" else it.label }, { prefs.setEngine(it) }) { e ->
    when (e) {
      EngineType.ELECTRIC -> GameIcon.CHARGING
      EngineType.CNG -> GameIcon.CNG
      else -> GameIcon.FUEL
    }
  }
  SkinHelp(spec, if (engine == EngineType.ELECTRIC) "Nearby finds charging stations instead of petrol pumps." else "Nearby's fuel button looks for ${engine.label.lowercase()}.")
  SkinSectionTitle(spec, "Android Auto")
  Rows(
      theme.skin,
      listOf(
          Setting(
              "Car screen",
              if (carGameHud) "Android Auto shows the game HUD: turn, time and street drawn in the theme." else "Android Auto shows its own turn and ETA cards, in the theme's colour.",
              if (carGameHud) "Game HUD" else "Android Auto",
          ) { store.setCarGameHud(!carGameHud) },
          Setting("+ Cam on the car screen", "A button on the car's map for marking a speed camera as you pass it. It shows for a few seconds after you touch the screen, then hides again.", onOff(carCamButton)) {
            store.setCarCamButton(!carCamButton)
          },
          Setting(
              "Phone locked",
              if (lockedReady) "Keeps navigating with the phone locked in your pocket: the battery saver leaves the app alone. Tap to change it in Android's settings."
              else "Tap to keep it navigating with the phone locked: say yes when Android asks to let it run in the background.",
              if (lockedReady) "Works" else "Set up",
          ) { setUpLocked() },
      ),
      selected,
      tap,
  )
  SkinSectionTitle(spec, "Walking")
  Gap(6)
  ChoiceRow(spec, WalkPace.entries, pace, { it.label }, { prefs.setWalkPace(it) })
  SkinHelp(spec, "How fast walking routes reckon you walk: ${pace.kmh} km/h. Android Auto always plans a car's route.")
}

@Composable
private fun PrivacyTab(theme: OverworldTheme, spec: SkinSpec, saved: SavedState, selected: String?, tap: (Setting) -> Unit) {
  val prefs = AppModule.prefs
  val context = LocalContext.current
  val timeline by prefs.timeline.collectAsState()
  val keep by prefs.timelineKeep.collectAsState()
  val keepHistory by prefs.keepHistory.collectAsState()
  val history by AppModule.history.entries.collectAsState()
  val marked by AppModule.cameras.all.collectAsState()
  val located = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
  Rows(
      theme.skin,
      listOf(
          Setting(
              "Location",
              if (located) "Overworld sees where you are while it's open, for the map, trips and Timeline. Tap again to change it in Android's settings."
              else "Overworld can't see where you are. Tap again to allow it in Android's settings.",
              if (located) "Allowed" else "Off",
              confirm = true,
          ) { runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
          Setting(
              "Timeline",
              "Keeps your drives and the places you stop at while Overworld is open, on this phone only, never uploaded. See it in You > Timeline.",
              onOff(timeline),
          ) {
            if (timeline) AppModule.timeline.flush()
            prefs.setTimeline(!timeline)
          },
      ),
      selected,
      tap,
  )
  LabeledChoices(spec, "Keep Timeline for", TimelineKeep.entries, keep, { it.label }) {
    prefs.setTimelineKeep(it)
    AppModule.timeline.startUp()
  }
  Rows(
      theme.skin,
      listOf(
          Setting("Delete all Timeline", "Tap again to delete every drive and stop Timeline has kept. This can't be undone.", clears = true) { AppModule.timeline.deleteAll() },
          Setting("Keep search history", "Searches, places you look at and directions are kept on this phone for Search and You > History.", onOff(keepHistory)) { prefs.setKeepHistory(!keepHistory) },
          Setting("Clear search history", if (history.isNotEmpty()) "Tap again to clear all ${history.size} searches and places from history." else "History is empty.", history.size.toString(), clears = history.isNotEmpty()) { AppModule.history.clear() },
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
              "Recent destinations",
              if (saved.recents.isNotEmpty()) "Places you've driven to, for the car's Where to card. Tap again to clear them; saved places stay." else "Places you drive to show up on the car's Where to card.",
              saved.recents.size.toString(),
              clears = saved.recents.isNotEmpty(),
          ) { AppModule.saved.clearRecents() },
          Setting(
              "Marked cameras",
              if (marked.isNotEmpty()) "Speed cameras you added with + Cam in the car, or Mark a camera here on the phone. Tap again to remove them all."
              else "None yet. Turn on + Cam on the car screen (Vehicle) and tap it as you pass one; it beeps there from then on.",
              marked.size.toString(),
              clears = marked.isNotEmpty(),
          ) { AppModule.cameras.clear() },
      ),
      selected,
      tap,
  )
}

@Composable
private fun OfflineTab(theme: OverworldTheme, spec: SkinSpec, selected: String?, tap: (Setting) -> Unit, onDownloadArea: () -> Unit) {
  val offline = AppModule.offline
  val prefs = AppModule.prefs
  LaunchedEffect(Unit) { offline.refresh() }
  val areas by offline.areas.collectAsState()
  val wifi by prefs.offlineWifiOnly.collectAsState()
  val auto by prefs.offlineAutoUpdate.collectAsState()
  var renaming by remember { mutableStateOf<Long?>(null) }
  var newName by remember { mutableStateOf("") }
  SkinPrimaryButton(spec, "Download this area", onDownloadArea, Modifier.fillMaxWidth(), GameIcon.DOWNLOAD)
  SkinHelp(spec, "The map draws without internet inside downloaded areas, in every world. Routes and search still need a connection.")
  if (areas.isNotEmpty()) SkinSectionTitle(spec, "Downloaded")
  areas.forEach { a ->
    val status =
        when {
          a.error != null && !a.complete -> a.error
          a.downloading -> "Downloading ${(a.progress * 100).toInt()}%"
          a.complete -> "${a.megabytes} MB · saved ${agoText(a.savedAt)}"
          else -> "Paused at ${(a.progress * 100).toInt()}%"
        }
    Rows(theme.skin, listOf(Setting(a.name, "Tap ${com.thealgothrim.overworld.offline.offlineAction(a.downloading, a.complete)}, Rename or Delete below.", status) {}), selected, tap)
    if (selected == a.name) {
      Gap(6)
      if (renaming == a.id) {
        SkinField(spec, newName, { newName = it }, "Area name")
        Gap(6)
        SkinPrimaryButton(spec, "Rename", { if (newName.isNotBlank()) offline.rename(a.id, newName.trim()); renaming = null }, Modifier.fillMaxWidth(), GameIcon.EDIT)
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // An unfinished area carries on from where it stopped; only a finished one is fetched again.
          val action = com.thealgothrim.overworld.offline.offlineAction(a.downloading, a.complete)
          ChoiceChip(
              spec, action, false,
              {
                when (action) {
                  "Pause" -> offline.pause(a.id)
                  "Resume" -> offline.resume(a.id)
                  else -> offline.update(a.id)
                }
              },
              Modifier.weight(1f), GameIcon.DOWNLOAD,
          )
          ChoiceChip(spec, "Rename", false, { renaming = a.id; newName = a.name }, Modifier.weight(1f), GameIcon.EDIT)
          ChoiceChip(spec, "Delete", false, { offline.delete(a.id) }, Modifier.weight(1f), GameIcon.DELETE)
        }
      }
      Gap(6)
    }
  }
  Gap(6)
  Rows(
      theme.skin,
      listOf(
          Setting("Download over Wi-Fi only", "Downloads wait for Wi-Fi so they don't use mobile data.", onOff(wifi)) { prefs.setOfflineWifiOnly(!wifi) },
          Setting("Auto-update offline maps", "Areas older than a month download again by themselves when the app opens (on Wi-Fi when that's set).", onOff(auto)) { prefs.setOfflineAutoUpdate(!auto) },
      ),
      selected,
      tap,
  )
}

@Composable
private fun AlertsTab(theme: OverworldTheme, spec: SkinSpec, selected: String?, tap: (Setting) -> Unit) {
  val prefs = AppModule.prefs
  val context = LocalContext.current
  var allowed by remember { mutableStateOf(Notices.allowed(context)) }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = Notices.allowed(context) }
  val parking by prefs.notifyParking.collectAsState()
  val recap by prefs.notifyRecap.collectAsState()
  val offline by prefs.notifyOffline.collectAsState()
  val updates by prefs.notifyUpdates.collectAsState()
  val wantedFlash by prefs.wantedFlash.collectAsState()
  if (!allowed) {
    SkinSecondaryButton(
        spec,
        "Allow notifications",
        {
          if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
          else runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        Modifier.fillMaxWidth(),
        GameIcon.BELL,
    )
    SkinHelp(spec, "Android isn't showing Overworld's notifications. The trip's own notification needs them too.")
    Gap(6)
  }
  Rows(
      theme.skin,
      listOf(
          Setting("Parking reminders", "When your parked car has a time limit: 10 minutes before it runs out, and when it does.", onOff(parking)) {
            prefs.setNotifyParking(!parking)
            Notices.scheduleParking(context, if (!parking) AppModule.saved.state.value.parking?.until else null)
          },
          Setting("Timeline recap", "Once a month: last month's distance, trips and places, from your Timeline.", onOff(recap)) { prefs.setNotifyRecap(!recap) },
          Setting("Offline map ready", "When an area you downloaded has finished.", onOff(offline)) { prefs.setNotifyOffline(!offline) },
          Setting("Updates", "The dot on Settings when a newer Overworld is out.", onOff(updates)) { prefs.setNotifyUpdates(!updates) },
          Setting(
              "Wanted flash",
              "In GTA V and GTA VI: pass a speed camera over the limit and the map flashes red and blue for a moment, like a police chase in the games.",
              onOff(wantedFlash),
          ) { prefs.setWantedFlash(!wantedFlash) },
          Setting("Android's notification settings", "Sounds and how each kind shows, in Android's own settings. Tap again to open them.", confirm = true) {
            runCatching { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
          },
      ),
      selected,
      tap,
  )
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
