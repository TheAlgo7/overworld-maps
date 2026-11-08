package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.gta.GtaInstructionalButtons
import com.thealgothrim.overworld.ui.gta.GtaMenuHeader
import com.thealgothrim.overworld.ui.gta.GtaPrompt
import com.thealgothrim.overworld.ui.rdr.RdrMenuTitle
import com.thealgothrim.overworld.ui.rdr.RdrPrompt
import com.thealgothrim.overworld.ui.rdr.RdrPrompts
import com.thealgothrim.overworld.ui.vi.ViMenuHeader
import com.thealgothrim.overworld.ui.vi.ViPrompt
import com.thealgothrim.overworld.ui.vi.ViPrompts

/**
 * A full-screen game menu over the map, as the pause menu is drawn in each world: the dark scrim
 * that eats taps, the header (GTA V's banner, GTA VI's glass title, Red Dead's engraved one), the
 * page scrolling under it, and the Back prompt in the corner. Settings and You both use it.
 */
@Composable
fun PauseMenuFrame(theme: OverworldTheme, title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
        Skin.GTA -> GtaMenuHeader(title.uppercase())
        Skin.GTA6 -> ViMenuHeader(title, "Overworld", "")
        Skin.RDR -> RdrMenuTitle(title.uppercase(), "Overworld", "")
      }
      content()
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
