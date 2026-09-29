package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.THEMES
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.spec

/**
 * Google Maps' Layers sheet, in the theme: pick a map theme from preview cards (one tap switches),
 * and turn map details on or off: live traffic, traffic lights, incidents.
 */
@Composable
fun BoxScope.MapLayersSheet(theme: OverworldTheme, onClose: () -> Unit) {
  val spec = theme.spec
  val store = AppModule.themeStore
  val details by store.details.collectAsState()
  val live = AppModule.traffic.hasLiveTraffic

  // Tap outside to close.
  Box(
      Modifier.fillMaxSize()
          .background(Color(0x66000000))
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
  )
  SkinPanel(
      spec,
      Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          // GTA panels are see-through; over the locate button and credit that reads as a glitch.
          .background(Color.Black)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
  ) {
    // The panel reaches the screen edge; its content stays above the gesture bar.
    Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        SkinText(spec.title("Map theme"), spec.title, if (spec.gta) 20.sp else 24.sp, spec.fg, Modifier.weight(1f), spacing = if (spec.gta) 0.sp else 1.sp)
        Box(Modifier.size(36.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
          GameIconView(GameIcon.CLOSE, spec.fg, Modifier.size(22.dp))
        }
      }
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        THEMES.forEach { t ->
          ThemeCard(spec, t, selected = t.id == theme.id, modifier = Modifier.weight(1f)) { store.select(t.id) }
        }
      }
      Spacer(Modifier.height(18.dp))
      SkinText(spec.title("Map details"), spec.title, if (spec.gta) 16.sp else 19.sp, spec.sub, spacing = if (spec.gta) 0.sp else 1.sp)
      Spacer(Modifier.height(4.dp))
      ToggleRow(
          spec, "Traffic on every road",
          if (live) "Your route always shows its own traffic. This colours all roads: amber slow, red jammed." else "Needs a free TomTom key (see Settings, About).",
          checked = details.traffic && live, enabled = live,
      ) { store.setDetails(details.copy(traffic = !details.traffic)) }
      SkinDivider(spec)
      ToggleRow(spec, "Traffic lights", "Signals along your route, from OpenStreetMap.", checked = details.signals) {
        store.setDetails(details.copy(signals = !details.signals))
      }
      SkinDivider(spec)
      ToggleRow(
          spec, "Incidents",
          if (live) "Accidents, road works, closures and jams, with alerts ahead." else "Needs a free TomTom key.",
          checked = details.incidents && live, enabled = live,
      ) { store.setDetails(details.copy(incidents = !details.incidents)) }
    }
    }
  }
}

@Composable
private fun ThemeCard(spec: SkinSpec, t: OverworldTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val frame = if (selected) spec.accent else spec.fg.copy(alpha = 0.25f)
  Column(modifier.clickable(onClick = onClick)) {
    Box(Modifier.fillMaxWidth().aspectRatio(400f / 260f).border(if (selected) 3.dp else 1.dp, frame)) {
      Image(
          painterResource(if (t.id == "rdr2") R.drawable.theme_rdr2 else R.drawable.theme_gta5),
          contentDescription = t.name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().padding(if (selected) 3.dp else 1.dp),
      )
      if (selected) {
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).background(spec.accent), contentAlignment = Alignment.Center) {
          Tick(Color.White)
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    SkinText(t.name, spec.title, if (spec.gta) 16.sp else 19.sp, if (selected) spec.fg else spec.sub, spacing = if (spec.gta) 0.sp else 1.sp)
  }
}

@Composable
private fun ToggleRow(spec: SkinSpec, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().heightIn(min = 58.dp).then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      SkinText(title, spec.body, 17.sp, if (enabled) spec.fg else spec.sub)
      SkinText(subtitle, spec.body, 13.sp, spec.sub, maxLines = 2)
    }
    Spacer(Modifier.width(12.dp))
    Box(
        Modifier.size(26.dp).border(2.dp, if (enabled) spec.fg else spec.sub).then(if (checked) Modifier.background(spec.accent) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
      if (checked) Tick(Color.White)
    }
  }
}

@Composable
private fun Tick(color: Color) {
  Canvas(Modifier.size(16.dp)) {
    val s = size.width
    val tick = Path().apply { moveTo(s * 0.15f, s * 0.55f); lineTo(s * 0.42f, s * 0.8f); lineTo(s * 0.88f, s * 0.22f) }
    drawPath(tick, color, style = Stroke(width = s * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

/** The OpenStreetMap credit, fixed in place (MapLibre's own (i) button moved around). */
@Composable
fun MapCredit(spec: SkinSpec, modifier: Modifier = Modifier) {
  Text(
      "© OpenStreetMap",
      color = if (spec.gta) Color.White.copy(alpha = 0.55f) else Rdr.OffBlack.copy(alpha = 0.55f),
      fontSize = 10.sp,
      modifier = modifier.padding(horizontal = 4.dp),
  )
}
