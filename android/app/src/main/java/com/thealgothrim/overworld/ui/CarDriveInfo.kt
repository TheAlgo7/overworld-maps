package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import com.thealgothrim.overworld.ui.vi.outerShadow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.HazardAhead
import com.thealgothrim.overworld.RouteExtras
import com.thealgothrim.overworld.map.RoadFeaturePainter
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.traffic.RoadFeatureKind
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.skinPanel
import com.thealgothrim.overworld.ui.skin.spec
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the car screen draws on the map beside Android Auto's own cards (every Play build): the
 * drive's own facts and nothing else, as Android Auto's rule NF-2 allows ("speed limit, road
 * obstructions, etc. can be drawn on the safe area of the map"), in the world's own art: the
 * speedometer in the game's numerals, Gaurav's limit signs, and what's ahead on a card in the
 * world's panel. A speed camera ahead is a wanted star (Red Dead: a lawman's badge), lit once the
 * car passes one over the limit, the old HUD's red and blue flash as a still picture: Android Auto
 * allows nothing on the screen to move while driving but the map itself (rule SA-1).
 *
 * In Android Auto's card column, just above its time card, where Google Maps keeps its speed.
 */
@Composable
fun CarDriveInfo(theme: OverworldTheme, uiState: NavigationUiState, extras: RouteExtras, modifier: Modifier = Modifier) {
  val spec = theme.spec
  val speed = rememberSpeedNow(uiState)
  val hazard = AppModule.viewModel.hazardAhead(uiState, extras)
  val wanted = rememberWanted()
  DisposableEffect(Unit) { onDispose { CarHudBlocks.bottom = null } }
  // One low row: the speed, the limit, and what's ahead as a small chip beside them. A full card
  // ("Speed camera ahead") was too big for a glance (Gaurav, 21 Nov).
  Row(modifier.onGloballyPositioned { CarHudBlocks.bottom = it.boundsInRoot() }, verticalAlignment = Alignment.CenterVertically) {
    Speedometer(spec, speed, Modifier.padding(start = 6.dp, bottom = 4.dp))
    Spacer(Modifier.width(14.dp))
    SpeedLimitSign(spec, speed)
    when {
      // Just passed a camera over the limit: the star lit, whatever is ahead now.
      wanted -> AlertChip(spec, null, wanted = true, Modifier.padding(start = 14.dp))
      hazard != null -> AlertChip(spec, hazard, wanted = false, Modifier.padding(start = 14.dp))
    }
  }
}

/**
 * Liquid glass for what Overworld draws over the car map: the world's dark tint, a sheen across the
 * top, a rim lit from above that fades down the sides, and a soft shadow outside it only. Tinted
 * rather than blurred: the map is a GL surface Compose can't blur behind, and a solid-enough tint
 * keeps the text readable at a glance over any map.
 */
fun Modifier.carGlass(skin: Skin, shape: Shape = RoundedCornerShape(20.dp)): Modifier {
  val tint =
      when (skin) {
        Skin.GTA -> Color(0xA6101012)
        Skin.GTA6 -> Color(0x9E24122C)
        Skin.RDR -> Color(0xB01C1712)
      }
  return this.outerShadow(shape, blur = 14.dp, drop = 3.dp, color = Color(0x66000000))
      .clip(shape)
      .background(tint)
      .background(Brush.verticalGradient(0f to Color(0x2EFFFFFF), 0.45f to Color(0x08FFFFFF), 1f to Color.Transparent))
      .border(1.dp, Brush.verticalGradient(0f to Color(0x8CFFFFFF), 0.5f to Color(0x26FFFFFF), 1f to Color(0x14FFFFFF)), shape)
}

/**
 * What's ahead, small: its picture and how far (a jam: how much later). [wanted]: a camera was just
 * passed over the limit, so the star is lit and there's no distance.
 */
@Composable
private fun AlertChip(spec: SkinSpec, hazard: HazardAhead?, wanted: Boolean, modifier: Modifier = Modifier) {
  val kind = hazard?.feature?.kind ?: RoadFeatureKind.SPEED_CAMERA
  Row(
      modifier.clip(RoundedCornerShape(CHIP_CORNER)).background(spec.panel.copy(alpha = 0.85f)).padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (kind == RoadFeatureKind.SPEED_CAMERA) {
      Canvas(Modifier.size(30.dp)) { drawWantedBadge(spec, lit = wanted) }
    } else {
      val painter = RoadFeaturePainter(kind, spec.skin)
      Canvas(Modifier.size(30.dp)) { with(painter) { draw(size) } }
    }
    if (hazard != null && !wanted) {
      Spacer(Modifier.width(8.dp))
      val delay = hazard.feature.delaySeconds.takeIf { it >= 60 && kind == RoadFeatureKind.JAM }?.let { "+${formatDuration(it)}" }
      // Trimmed to the glyphs and centred: with the font's own padding the digits sat high.
      androidx.compose.material3.Text(
          delay ?: formatDistance(hazard.distance),
          maxLines = 1,
          style =
              androidx.compose.ui.text.TextStyle(
                  fontFamily = spec.body,
                  fontSize = 18.sp,
                  color = spec.fg,
                  lineHeight = 18.sp,
                  platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                  lineHeightStyle =
                      androidx.compose.ui.text.style.LineHeightStyle(
                          alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                          trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                      ),
              ),
      )
    }
  }
}

/**
 * True for [WANTED_SHOWN_MS] after the car passes a speed camera over the limit (the view model's
 * wantedAt, which set off the old HUD's flash). A still change, not an animation.
 */
@Composable
private fun rememberWanted(): Boolean {
  val at by AppModule.viewModel.wantedAt.collectAsState()
  val on by AppModule.prefs.wantedFlash.collectAsState()
  val lit by
      produceState(false, at) {
        val left = WANTED_SHOWN_MS - (System.currentTimeMillis() - at)
        if (at == 0L || left <= 0) {
          value = false
          return@produceState
        }
        value = true
        delay(left)
        value = false
      }
  return on && lit
}

/** How long the lit star stays after a camera is passed over the limit. */
private const val WANTED_SHOWN_MS = 4_000L

/**
 * GTA's wanted star (five points, white in the game's black edge; hollow while only a camera is
 * ahead, filled once wanted), or for Red Dead a lawman's six-point badge with its round tips, its
 * bounty red once wanted.
 */
private fun DrawScope.drawWantedBadge(spec: SkinSpec, lit: Boolean) {
  val rdr = spec.skin == Skin.RDR
  val points = if (rdr) 6 else 5
  val outer = size.minDimension * (if (rdr) 0.40f else 0.47f)
  val inner = outer * (if (rdr) 0.55f else 0.42f)
  val centre = Offset(size.width / 2f, size.height / 2f + if (rdr) 0f else size.height * 0.03f)
  val star = Path()
  for (i in 0 until points * 2) {
    val r = if (i % 2 == 0) outer else inner
    val a = -PI / 2 + i * PI / points
    val p = Offset(centre.x + (r * cos(a)).toFloat(), centre.y + (r * sin(a)).toFloat())
    if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
  }
  star.close()
  val edge = Color.Black.copy(alpha = 0.85f)
  val ink = if (rdr) Color(0xFFCC0000) else spec.fg
  val line = size.minDimension * 0.07f
  // The dark edge first, a little wider, so the star reads on any map.
  drawPath(star, edge, style = Stroke(width = line * 2.2f, join = StrokeJoin.Round))
  if (lit) drawPath(star, ink) else drawPath(star, edge.copy(alpha = 0.35f))
  drawPath(star, if (lit) ink else spec.fg, style = Stroke(width = line, join = StrokeJoin.Round))
  if (rdr) {
    // The badge's round tips.
    for (i in 0 until points) {
      val a = -PI / 2 + i * 2 * PI / points
      val tip = Offset(centre.x + (outer * 1.08f * cos(a)).toFloat(), centre.y + (outer * 1.08f * sin(a)).toFloat())
      drawCircle(edge, radius = line * 1.9f, center = tip)
      drawCircle(if (lit) ink else spec.fg, radius = line * 1.25f, center = tip)
    }
  }
}

private val CHIP_CORNER = 12.dp
