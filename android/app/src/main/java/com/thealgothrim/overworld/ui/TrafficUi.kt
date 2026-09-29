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
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.AppModule
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.gta.GtaText
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

/** Over the limit once the speed is this far past it (so 55 on a 50 road), like Google's warning. */
private const val OVER_BY = 5

/**
 * Speed while driving, floating on the map with no box: the current speed in outlined numerals
 * (like the street name) and the road's limit sign beside it, in the theme's own sign. Over the
 * limit, the sign's ring and the speed turn red. The speed comes from GPS, or on a test drive is
 * worked out from the simulated movement.
 */
@Composable
fun SpeedBadge(spec: SkinSpec, uiState: NavigationUiState, modifier: Modifier = Modifier, big: Boolean = false) {
  val estimate by AppModule.viewModel.estimatedKmh.collectAsState()
  val kmh = uiState.location?.speed?.value?.let { (it * 3.6).roundToInt().coerceAtLeast(0) } ?: estimate
  val limit = (uiState.currentAnnotation?.speed as? Speed.Value)?.let {
    if (it.unit.name.contains("MILE", ignoreCase = true)) (it.value * 1.609).roundToInt() else it.value.roundToInt()
  }
  if (kmh == null && limit == null) return
  val over = kmh != null && limit != null && kmh >= limit + OVER_BY
  val red = if (spec.gta) Color(0xFFE8262B) else Color(0xFFCC0000)
  // Car (big): speed and sign stand on the bottom line of the cards beside them. Phone: centred.
  Row(modifier.padding(horizontal = 4.dp), verticalAlignment = if (big) Alignment.Bottom else Alignment.CenterVertically) {
    if (kmh != null) {
      SpeedReadout(
          spec,
          kmh,
          color = if (over) red else if (spec.gta) Gta.White else Rdr.White,
          height = if (big) 66.dp else 56.dp,
          big = big,
      )
      if (limit != null) Spacer(Modifier.width(if (big) 12.dp else 10.dp))
    }
    if (limit != null) LimitSign(spec, limit, over, if (big) 66.dp else 56.dp, Modifier)
  }
}

/** Gaurav's signs (tools/signs/, cut by tools/make_limit_signs.py): (normal, over the limit). */
private val GTA_SIGNS =
    mapOf(
        10 to (R.drawable.limit_gta_10 to R.drawable.limit_gta_10_over),
        20 to (R.drawable.limit_gta_20 to R.drawable.limit_gta_20_over),
        30 to (R.drawable.limit_gta_30 to R.drawable.limit_gta_30_over),
        40 to (R.drawable.limit_gta_40 to R.drawable.limit_gta_40_over),
        50 to (R.drawable.limit_gta_50 to R.drawable.limit_gta_50_over),
        60 to (R.drawable.limit_gta_60 to R.drawable.limit_gta_60_over),
        70 to (R.drawable.limit_gta_70 to R.drawable.limit_gta_70_over),
        80 to (R.drawable.limit_gta_80 to R.drawable.limit_gta_80_over),
        90 to (R.drawable.limit_gta_90 to R.drawable.limit_gta_90_over),
        100 to (R.drawable.limit_gta_100 to R.drawable.limit_gta_100_over),
        110 to (R.drawable.limit_gta_110 to R.drawable.limit_gta_110_over),
        120 to (R.drawable.limit_gta_120 to R.drawable.limit_gta_120_over),
    )
private val RDR_SIGNS =
    mapOf(
        10 to (R.drawable.limit_rdr_10 to R.drawable.limit_rdr_10_over),
        20 to (R.drawable.limit_rdr_20 to R.drawable.limit_rdr_20_over),
        30 to (R.drawable.limit_rdr_30 to R.drawable.limit_rdr_30_over),
        40 to (R.drawable.limit_rdr_40 to R.drawable.limit_rdr_40_over),
        50 to (R.drawable.limit_rdr_50 to R.drawable.limit_rdr_50_over),
        60 to (R.drawable.limit_rdr_60 to R.drawable.limit_rdr_60_over),
        70 to (R.drawable.limit_rdr_70 to R.drawable.limit_rdr_70_over),
        80 to (R.drawable.limit_rdr_80 to R.drawable.limit_rdr_80_over),
        90 to (R.drawable.limit_rdr_90 to R.drawable.limit_rdr_90_over),
        100 to (R.drawable.limit_rdr_100 to R.drawable.limit_rdr_100_over),
        110 to (R.drawable.limit_rdr_110 to R.drawable.limit_rdr_110_over),
        120 to (R.drawable.limit_rdr_120 to R.drawable.limit_rdr_120_over),
    )

/**
 * Where the art puts its number, as shares of the sign (measured by tools/make_limit_signs.py):
 * centre x, centre y, ink height, ink width, for two- and three-digit limits.
 */
/** The GTA art's numbers are a heavy, wide sans; Barlow SemiBold is the closest bundled face. */
private val GTA_SIGN_FONT = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.barlow_semibold))

private data class NumberBox(val x: Float, val y: Float, val height: Float, val width: Float)
private val GTA_NUMBER = NumberBox(0.501f, 0.521f, 0.412f, 0.522f) to NumberBox(0.501f, 0.513f, 0.415f, 0.618f)
private val RDR_NUMBER = NumberBox(0.502f, 0.509f, 0.448f, 0.413f) to NumberBox(0.502f, 0.508f, 0.420f, 0.520f)

/**
 * The current speed over "KM/H", outlined like the street name. Drawn as one block centred by the
 * letters' own outlines, so it lines up with the limit sign and the time card beside it (text
 * line boxes carry uneven space above and below, which left it sitting high).
 */
@Composable
private fun SpeedReadout(spec: SkinSpec, kmh: Int, color: Color, height: Dp, big: Boolean) {
  val measurer = rememberTextMeasurer()
  val density = androidx.compose.ui.platform.LocalDensity.current
  val outline = with(density) { (if (spec.gta) 2.dp else 1.5.dp).toPx() }
  val number =
      measurer.measure(
          kmh.toString(),
          TextStyle(fontFamily = if (spec.gta) GameFonts.condensed else GameFonts.lino, fontSize = if (big) 38.sp else 30.sp),
      )
  val unit =
      measurer.measure(
          if (spec.gta) "km/h" else "KM/H",
          TextStyle(fontFamily = if (spec.gta) GameFonts.condensed else GameFonts.hapna, fontSize = if (big) 13.sp else 11.sp),
      )
  val numberInk = number.multiParagraph.getPathForRange(0, kmh.toString().length).getBounds()
  val unitInk = unit.multiParagraph.getPathForRange(0, unit.layoutInput.text.length).getBounds()
  val width = with(density) { (maxOf(numberInk.width, unitInk.width, 44.dp.toPx()) + outline * 2).toDp() }
  Canvas(Modifier.width(width).height(height)) {
    val gap = 4.dp.toPx()
    val block = numberInk.height + gap + unitInk.height
    // Car: the block's foot on the bottom line; phone: centred on the sign.
    val top = if (big) size.height - block - outline else (size.height - block) / 2
    val numberAt = Offset(size.width / 2 - numberInk.center.x, top - numberInk.top)
    val unitAt = Offset(size.width / 2 - unitInk.center.x, top + numberInk.height + gap - unitInk.top)
    val edge = Stroke(width = outline * 2, join = androidx.compose.ui.graphics.StrokeJoin.Round)
    drawText(number, Color.Black, numberAt, drawStyle = edge)
    // The stroke style sticks to the laid-out text, so the fill has to be asked for explicitly.
    drawText(number, color, numberAt, drawStyle = Fill)
    drawText(unit, Color.Black, unitAt, drawStyle = Stroke(width = 1.5.dp.toPx() * 2, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawText(unit, if (spec.gta) Gta.White else Rdr.GreyLight, unitAt, drawStyle = Fill)
  }
}

/**
 * The limit sign, exactly as Gaurav drew it for each game (10 to 120 km/h), fading to his red
 * version when over the limit. A limit his set doesn't have (25, 65...) is drawn on his sign with
 * the number painted out, at the size and place his numbers sit.
 */
@Composable
private fun LimitSign(spec: SkinSpec, limit: Int, over: Boolean, size: Dp, modifier: Modifier) {
  val redness by animateFloatAsState(if (over) 1f else 0f, tween(350), label = "limit")
  val art = (if (spec.gta) GTA_SIGNS else RDR_SIGNS)[limit]
  val blank =
      if (spec.gta) R.drawable.limit_blank_gta to R.drawable.limit_blank_gta_over
      else R.drawable.limit_blank_rdr to R.drawable.limit_blank_rdr_over
  val (normalId, overId) = art ?: blank
  val normal = ImageBitmap.imageResource(normalId)
  val red = ImageBitmap.imageResource(overId)
  val measurer = rememberTextMeasurer()
  Canvas(modifier.size(size)) {
    val d = this.size.minDimension.toInt()
    val box = androidx.compose.ui.unit.IntSize(d, d)
    drawImage(normal, dstSize = box, filterQuality = FilterQuality.High)
    if (redness > 0f) drawImage(red, dstSize = box, alpha = redness, filterQuality = FilterQuality.High)
    if (art != null) return@Canvas

    // An odd limit: the number fills exactly the box the art's numbers fill (height and width),
    // so it reads like the rest of the set: bold and wide for GTA, tall and narrow for Red Dead.
    val digits = limit.toString()
    val place = (if (spec.gta) GTA_NUMBER else RDR_NUMBER).let { if (digits.length > 2) it.second else it.first }
    val style =
        TextStyle(
            fontFamily = if (spec.gta) GTA_SIGN_FONT else GameFonts.lino,
            color = if (spec.gta) Color.White else Color(0xFFE9DDC6),
            fontSize = 100.sp,
        )
    val layout = measurer.measure(digits, style)
    val ink = layout.multiParagraph.getPathForRange(0, digits.length).getBounds()
    val sy = place.height * d / ink.height
    val sx = (place.width * d / ink.width).coerceIn(sy * 0.7f, sy * 1.3f)
    val at = Offset(place.x * d, place.y * d)
    scale(sx, sy, pivot = at) { drawText(layout, topLeft = at - ink.center) }
  }
}

