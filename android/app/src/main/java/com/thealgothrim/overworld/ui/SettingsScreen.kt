package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.BuildConfig
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.theme.MapTime
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
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
    val checked: Boolean? = null,
    /** It forgets something: the first tap only picks the row and says so, the second does it. */
    val clears: Boolean = false,
    val onClick: () -> Unit,
)

private data class Section(val title: String, val rows: List<Setting>)

/**
 * Settings, drawn as the game's pause menu, in four groups: the map, driving, saved places and
 * about. A tap explains the row right under it; rows that forget something ask for a second tap.
 */
@Composable
fun SettingsScreen(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    testDrive: Boolean,
    saved: SavedState,
    onTestDrive: (Boolean) -> Unit,
    onMute: () -> Unit,
    onLayers: () -> Unit,
    onClose: () -> Unit,
) {
  val store = AppModule.themeStore
  val carGameHud by store.carGameHud.collectAsState()
  val mapTime by store.mapTime.collectAsState()
  val details by store.details.collectAsState()
  val variant by AppModule.viewModel.mapVariant.collectAsState()
  val cameraBeep by AppModule.viewModel.cameraBeep.collectAsState()
  val marked by AppModule.cameras.all.collectAsState()
  val live = AppModule.traffic.hasLiveTraffic
  var selected by remember { mutableStateOf<String?>(null) }

  val detailsOn =
      listOf(details.traffic && live, details.signals, details.incidents && live, theme.buildings3d && details.buildings3d).count { it }
  val sections =
      listOf(
          Section(
              "Map",
              listOfNotNull(
                  Setting("Theme", "${theme.blurb} Tap for the next game's map.", value = theme.name) { store.cycle() },
                  // Themes lit by the time of day (GTA VI): by the sun, or held at one.
                  if (theme.variants.isEmpty()) null
                  else
                      Setting(
                          "Time of day",
                          if (mapTime == MapTime.AUTO)
                              "Follows the sun, like the game's minimap: pale by day, warm at golden hour, violet at night. It's ${(variant ?: "day").replaceFirstChar { it.uppercase() }} now. Tap to hold one."
                          else "Held at ${mapTime.label.lowercase()} whatever the hour. Tap to step through Auto, Day, Dusk and Night.",
                          value = mapTime.label,
                      ) { store.setMapTime(mapTime.next()) },
                  Setting(
                      "Map details",
                      "Traffic on every road, traffic lights and incidents${if (theme.buildings3d) ", and 3D buildings" else ""}. Tap to switch them in Layers.",
                      value = "$detailsOn on",
                  ) { onLayers() },
              ),
          ),
          Section(
              "Driving",
              listOf(
                  Setting(
                      "Car screen",
                      if (carGameHud) "Android Auto shows the game HUD: turn, time and street drawn in the theme."
                      else "Android Auto shows its own turn and ETA cards, in the theme's colour.",
                      value = if (carGameHud) "Game HUD" else "Android Auto",
                  ) { store.setCarGameHud(!carGameHud) },
                  Setting("Voice guidance", "Spoken turn instructions while driving. Off unless you turn it on.", checked = !(uiState.isMuted ?: AppModule.ttsObserver.isMuted)) { onMute() },
                  Setting("Camera beep", "Two quick notes as a speed camera comes within 500 m, with a trip or without. Plays through the car like a turn prompt.", checked = cameraBeep) {
                    AppModule.viewModel.setCameraBeep(!cameraBeep)
                  },
                  Setting("Test drive", "Simulates the trip along the route instead of using GPS. For trying things out without driving.", checked = testDrive) {
                    onTestDrive(!testDrive)
                  },
              ),
          ),
          Section(
              "Places",
              listOf(
                  Setting(
                      "Home",
                      if (saved.home != null) "Tap again to forget it. Set a new one from a place's Save button." else "Search for your home, tap Save, then Home.",
                      value = saved.home?.name ?: "Not set",
                      clears = saved.home != null,
                  ) { AppModule.saved.setHome(null) },
                  Setting(
                      "Work",
                      if (saved.work != null) "Tap again to forget it. Set a new one from a place's Save button." else "Search for your work place, tap Save, then Work.",
                      value = saved.work?.name ?: "Not set",
                      clears = saved.work != null,
                  ) { AppModule.saved.setWork(null) },
                  Setting(
                      "Recent places",
                      if (saved.recents.isNotEmpty()) "The places you've driven to, listed in Search. Tap again to clear them; starred places stay." else "Places you drive to show up in Search.",
                      value = saved.recents.size.toString(),
                      clears = saved.recents.isNotEmpty(),
                  ) { AppModule.saved.clearRecents() },
                  Setting(
                      "Marked cameras",
                      if (marked.isNotEmpty()) "Speed cameras you added with + Cam in the car, or Mark a camera here on the phone. Tap again to remove them all."
                      else "None yet. Tap + Cam in the car as you pass one and it beeps there from then on.",
                      value = marked.size.toString(),
                      clears = marked.isNotEmpty(),
                  ) { AppModule.cameras.clear() },
              ),
          ),
          Section(
              "About",
              listOf(
                  Setting(
                      "Overworld",
                      "Map data © OpenStreetMap contributors (ODbL), tiles by OpenFreeMap, routing by Valhalla (FOSSGIS), search by TomTom and Photon (komoot), traffic lights from Valhalla and speed cameras from Overpass, navigation by Ferrostar (Stadia Maps)." +
                          if (live) " Live traffic by TomTom." else " Live traffic is off: add a free TomTom key (tomtomKey in local.properties) for traffic, incidents and live travel times.",
                      value = BuildConfig.VERSION_NAME,
                  ) {},
              ),
          ),
      )

  fun tap(r: Setting) {
    // Forgetting something takes two taps: the first picks the row and says what a second will do.
    if (r.clears && selected != r.label) {
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
      sections.forEach { section ->
        SectionTitle(theme.skin, section.title)
        section.rows.forEach { r ->
          val isSelected = selected == r.label
          // A row that will forget something on the next tap turns red.
          val armed = isSelected && r.clears
          when (theme.skin) {
            Skin.GTA -> {
              GtaMenuRow(r.label, isSelected, { tap(r) }, value = r.value, checked = r.checked, labelColor = if (armed) Gta.Red else null)
              if (isSelected) GtaDescription(r.description, color = if (armed) Gta.Red else Gta.White)
            }
            Skin.GTA6 -> {
              ViMenuRow(r.label, isSelected, { tap(r) }, value = r.value, checked = r.checked, labelColor = if (armed) Vi.Red else null)
              if (isSelected) ViDescription(r.description, color = if (armed) Vi.Red else Vi.White)
            }
            Skin.RDR -> {
              RdrMenuRow(r.label, isSelected, { tap(r) }, value = r.value, checked = r.checked, labelColor = if (armed) Rdr.Red else null)
              if (isSelected) RdrDescription(r.description, color = if (armed) Rdr.Red else Rdr.GreyLight)
            }
          }
        }
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
