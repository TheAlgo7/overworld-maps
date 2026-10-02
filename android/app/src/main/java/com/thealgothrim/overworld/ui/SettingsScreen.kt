package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.search.SavedState
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
import com.thealgothrim.overworld.ui.rdr.RdrMenuRow
import com.thealgothrim.overworld.ui.rdr.RdrMenuTitle
import com.thealgothrim.overworld.ui.rdr.RdrPrompt
import com.thealgothrim.overworld.ui.rdr.RdrPrompts

private data class Setting(
    val label: String,
    val description: String,
    val value: String? = null,
    val checked: Boolean? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/** Settings, drawn as the game's pause menu: theme, car screen, voice, test drive, saved places. */
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
  val cameraBeep by AppModule.viewModel.cameraBeep.collectAsState()
  val marked by AppModule.cameras.all.collectAsState()
  var selected by remember { mutableIntStateOf(0) }

  val rows =
      listOf(
          Setting("Map theme", "${theme.blurb} Tap to choose a theme and map details.", value = theme.name) { onLayers() },
          Setting(
              "Car screen",
              if (carGameHud) "Android Auto shows the game HUD: turn, time and street drawn in the theme."
              else "Android Auto shows its own turn and ETA cards, in the theme's colour.",
              value = if (carGameHud) "Game HUD" else "Android Auto",
          ) { store.setCarGameHud(!carGameHud) },
          Setting("Voice guidance", "Spoken turn instructions while driving. Off unless you turn it on.", checked = !(uiState.isMuted ?: AppModule.ttsObserver.isMuted)) { onMute() },
          Setting("Camera beep", "Two quick notes as a speed camera comes within 500 m, on a trip or just driving. Plays through the car like a turn prompt. Voice guidance stays separate.", checked = cameraBeep) {
            AppModule.viewModel.setCameraBeep(!cameraBeep)
          },
          Setting(
              "Cameras you marked",
              "Added with the + Cam button on the car screen (or Mark a camera here while driving on the phone). Tap to remove them all.",
              value = marked.size.toString(),
              danger = true,
          ) { AppModule.cameras.clear() },
          Setting(
              "Test drive",
              "Simulates the trip along the route instead of using GPS. For trying things out without driving.",
              checked = testDrive,
          ) { onTestDrive(!testDrive) },
          Setting(
              "Home",
              if (saved.home != null) "Tap to clear. Set a new one from a place's Save button." else "Not set. Search for your home, tap Save, then Home.",
              value = saved.home?.name ?: "Not set",
          ) { AppModule.saved.setHome(null) },
          Setting(
              "Work",
              if (saved.work != null) "Tap to clear. Set a new one from a place's Save button." else "Not set. Search for your work place, tap Save, then Work.",
              value = saved.work?.name ?: "Not set",
          ) { AppModule.saved.setWork(null) },
          Setting(
              "Clear recent places",
              "Removes the recent destinations list. Starred places stay.",
              value = saved.recents.size.toString(),
              danger = true,
          ) { AppModule.saved.clearRecents() },
          Setting(
              "About",
              "Overworld 0.3. Map data © OpenStreetMap contributors (ODbL), tiles by OpenFreeMap, routing by Valhalla (FOSSGIS), search by TomTom and Photon (komoot), traffic lights from Valhalla and speed cameras from Overpass, navigation by Ferrostar (Stadia Maps)." +
                  if (AppModule.traffic.hasLiveTraffic) " Live traffic by TomTom." else " Live traffic is off: add a free TomTom key (tomtomKey in local.properties) to turn on traffic, incidents and live travel times.",
          ) {},
      )
  val current = rows[selected.coerceIn(0, rows.lastIndex)]

  // Scrim that eats taps so the map underneath doesn't move.
  val scrim = if (theme.skin == Skin.GTA) Color(0xD6000000) else Color(0xE8080706)
  Box(
      Modifier.fillMaxSize()
          .background(scrim)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
          .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(Modifier.align(Alignment.TopStart).fillMaxWidth().widthIn(max = 480.dp).padding(16.dp).verticalScroll(rememberScrollState())) {
      if (theme.skin == Skin.GTA) {
        GtaMenuHeader("SETTINGS")
        GtaMenuSubheader("Overworld", "${selected + 1}/${rows.size}")
        rows.forEachIndexed { i, r ->
          GtaMenuRow(
              r.label, selected == i,
              { selected = i; r.onClick() },
              value = r.value, checked = r.checked, labelColor = if (r.danger) Gta.Red else null,
          )
        }
        GtaDescription(current.description)
      } else {
        RdrMenuTitle("SETTINGS", "Overworld", "${selected + 1}/${rows.size}")
        rows.forEachIndexed { i, r ->
          RdrMenuRow(
              r.label, selected == i,
              { selected = i; r.onClick() },
              value = r.value, checked = r.checked, labelColor = if (r.danger) Rdr.Red else null,
          )
        }
        RdrDescription(current.description)
      }
    }
    if (theme.skin == Skin.GTA) {
      GtaInstructionalButtons(listOf(GtaPrompt("B", "Back", onClose)), Modifier.align(Alignment.BottomEnd).padding(16.dp))
    } else {
      RdrPrompts(listOf(RdrPrompt("B", "Back", onClose)), Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp))
    }
  }
}
