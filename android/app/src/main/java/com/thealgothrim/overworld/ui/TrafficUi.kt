package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.gta.GtaText
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.annotation.Speed
import com.thealgothrim.overworld.HazardAhead
import com.thealgothrim.overworld.map.RoadFeaturePainter
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import com.thealgothrim.overworld.traffic.TrafficEta
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import kotlin.math.roundToInt

/** Time-to-go colour: the theme's normal colour when clear, amber / red with traffic, like Google. */
fun SkinSpec.trafficColor(eta: TrafficEta?): Color =
    when (eta?.level) {
      null -> fg // no live data: don't claim the roads are clear
      1 -> Color(0xFFFFB020)
      2 -> if (gta) Color(0xFFFF4B3E) else Color(0xFFE0301E)
      else -> good
    }

/** "+6 min of traffic", or null when clear or unknown. */
fun trafficNote(eta: TrafficEta?): String? {
  val delay = eta?.delaySeconds ?: return null
  if (delay < 60) return null
  return "+${formatDuration(delay)} of traffic"
}

/** A line between the parts of one HUD card (turn, "Then", alert), like Google's turn card. */
@Composable
fun CardDivider(spec: SkinSpec) {
  Box(
      Modifier.fillMaxWidth()
          .padding(horizontal = if (spec.gta) 0.dp else 5.dp)
          .height(1.dp)
          .background(if (spec.gta) Color(0x24FFFFFF) else Rdr.Grey.copy(alpha = 0.35f))
  )
}

/**
 * The alert row at the foot of the turn card: "Stationary traffic ahead", then "500 m  ·  +4 min".
 * Drawn inside the card (no panel of its own) so the card reads as one piece.
 */
@Composable
fun HazardRow(spec: SkinSpec, hazard: HazardAhead, modifier: Modifier = Modifier, textSize: TextUnit = 17.sp, icon: Dp = 34.dp) {
  val feature = hazard.feature
  val painter = RoadFeaturePainter(feature.kind, spec.skin)
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(icon)) { with(painter) { draw(size) } }
    Spacer(Modifier.width(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      val what =
          when (feature.kind) {
            RoadFeatureKind.SPEED_CAMERA -> "Speed camera"
            // TomTom names the queue ("Stationary traffic", "Queuing traffic"); say that instead of
            // "Traffic jam" plus the same thing again underneath.
            RoadFeatureKind.JAM -> feature.description?.takeIf { it.isNotBlank() && it.length < 28 } ?: "Traffic jam"
            else -> feature.kind.label
          }
      SkinText("$what ahead", spec.body, textSize, spec.fg)
      val delay = feature.delaySeconds.takeIf { it >= 60 }?.let { "+${formatDuration(it)}" }
      SkinText(listOfNotNull(formatDistance(hazard.distance), delay).joinToString("  ·  "), spec.body, textSize * 0.8f, spec.sub)
    }
  }
}

/**
 * Speed while driving, floating on the map with no box: the current speed in outlined numerals
 * (like the street name) and the road's limit sign beside it. The sign is the Indian regulatory
 * sign (white face, red ring): crisp with a black outline for GTA V, an inked parchment stamp
 * for Red Dead 2. Over the limit, the speed turns red. Without a speed reading (a test drive) only
 * the sign shows, never "--".
 */
@Composable
fun SpeedBadge(spec: SkinSpec, uiState: NavigationUiState, modifier: Modifier = Modifier, big: Boolean = false) {
  val kmh = uiState.location?.speed?.value?.let { (it * 3.6).roundToInt().coerceAtLeast(0) }
  val limit = (uiState.currentAnnotation?.speed as? Speed.Value)?.let {
    if (it.unit.name.contains("MILE", ignoreCase = true)) (it.value * 1.609).roundToInt() else it.value.roundToInt()
  }
  if (kmh == null && limit == null) return
  val over = kmh != null && limit != null && kmh > limit + 3
  val red = if (spec.gta) Color(0xFFE03232) else Color(0xFFCC0000)
  Row(modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    if (kmh != null) {
      Column(Modifier.widthIn(min = if (big) 48.dp else 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GtaText(
            kmh.toString(),
            if (big) 38.sp else 30.sp,
            color = if (over) red else if (spec.gta) Gta.White else Rdr.White,
            font = if (spec.gta) GameFonts.condensed else GameFonts.lino,
            outline = if (spec.gta) 2.dp else 1.5.dp,
        )
        GtaText(
            if (spec.gta) "km/h" else "KM/H",
            if (big) 13.sp else 11.sp,
            color = if (spec.gta) Gta.White else Rdr.GreyLight,
            font = if (spec.gta) GameFonts.condensed else GameFonts.hapna,
            outline = 1.5.dp,
        )
      }
      if (limit != null) Spacer(Modifier.width(if (big) 12.dp else 10.dp))
    }
    if (limit != null) LimitSign(spec, limit, if (big) 58.dp else 48.dp, Modifier)
  }
}


@Composable
private fun LimitSign(spec: SkinSpec, limit: Int, size: Dp, modifier: Modifier) {
  val ink = Color(0xFF2B2622)
  // Red Dead's stamp sits slightly askew, as if pressed by hand.
  Box(modifier.size(size).then(if (spec.gta) Modifier else Modifier.rotate(-5f)), contentAlignment = Alignment.Center) {
    Canvas(Modifier.matchParentSize()) {
      val r = this.size.minDimension / 2
      val px = 1.dp.toPx()
      drawCircle(Color.Black.copy(alpha = 0.35f), r, center + Offset(0f, 1.5f * px))
      if (spec.gta) {
        // Crisp, with the black outline GTA's blips carry: outline, white margin, red ring, face.
        drawCircle(Color.Black, r)
        drawCircle(Color.White, r - 1.5f * px)
        drawCircle(Color(0xFFE3141B), r - 3f * px)
        drawCircle(Color.White, r * 0.76f)
      } else {
        // Parchment stamp: aged paper, a dried-blood ring laid down twice by hand, ink lines.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFEFE2C4), Color(0xFFD3BD90)), center = center, radius = r), r)
        val ring = r * 0.82f
        val width = r * 0.22f
        drawCircle(Color(0xFF9A1F1F), ring, style = Stroke(width))
        drawCircle(Color(0xFF6E1414).copy(alpha = 0.55f), ring, center + Offset(0.7f * px, -0.5f * px), style = Stroke(width * 0.35f))
        drawCircle(ink.copy(alpha = 0.85f), ring - width / 2, style = Stroke(0.9f * px))
        drawCircle(ink, r - 0.6f * px, style = Stroke(1.2f * px))
      }
    }
    val digits = limit.toString()
    val measurer = rememberTextMeasurer()
    val style =
        TextStyle(
            fontFamily = if (spec.gta) spec.number else spec.title,
            fontSize = (size.value * if (digits.length > 2) 0.34f else 0.42f).sp,
            color = if (spec.gta) Color(0xFF111111) else ink,
        )
    // Centre the digits by their drawn outline, not their line box: the fonts sit their digits
    // low in the line, which left the number off centre on the sign.
    Canvas(Modifier.matchParentSize()) {
      val layout = measurer.measure(digits, style)
      val ink = layout.multiParagraph.getPathForRange(0, digits.length).getBounds()
      drawText(layout, topLeft = center - ink.center)
    }
  }
}

