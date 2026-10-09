package com.thealgothrim.overworld.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.vi.Vi
import com.thealgothrim.overworld.ui.vi.viGlass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/** Metres covered by one dp of map at this zoom and latitude (MapLibre's 512 dp world tile). */
fun metresPerDp(latitude: Double, zoom: Double): Double =
    40_075_016.686 * cos(latitude * PI / 180) / (512 * 2.0.pow(zoom))

private val STEPS = listOf(10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000)

/** The scale bar's longest; the corner keeps this width, so it doesn't jump as the map zooms. */
private val BAR_MAX = 104.dp

/**
 * The bottom-left corner of the GTA pause maps: a scale bar ("0 ... 100 m") over the area name
 * ("CONNAUGHT PLACE"), the neighbourhood at the map's centre, in one box: GTA V's black, GTA VI's
 * glass.
 *
 * The bar is one crisp shape (ticks and line joined, square corners, on whole pixels) that slides
 * to its new length when the scale changes; "0" lines up with its left end and the distance with
 * its right end.
 */
@Composable
fun GtaMapCorner(spec: SkinSpec, area: String?, metresPerDp: Double, modifier: Modifier = Modifier) {
  val metres = STEPS.lastOrNull { it / metresPerDp <= BAR_MAX.value } ?: STEPS.first()
  val target = (metres / metresPerDp).toFloat().coerceIn(40f, BAR_MAX.value).dp
  val length by animateDpAsState(target, tween(220), label = "scale bar")
  val distance = if (metres >= 1000) "${metres / 1000} km" else "$metres m"
  val box = if (spec.vi) Modifier.viGlass(corner = 10.dp) else Modifier.background(Color(0xBB000000))
  Column(modifier.then(box).padding(horizontal = 12.dp, vertical = 9.dp).widthIn(min = BAR_MAX)) {
    ScaleBar(spec, length, distance)
    if (!area.isNullOrBlank()) {
      if (spec.vi) {
        SkinText(area.uppercase(), Vi.condensed, 17.sp, Color.White, Modifier.padding(top = 5.dp).widthIn(max = 220.dp), spacing = 0.8.sp)
      } else {
        SkinText(area.uppercase(), spec.body, 15.sp, Color.White, Modifier.padding(top = 5.dp).widthIn(max = 220.dp))
      }
    }
  }
}

/** The bar with "0" under its left end and [distance] right-aligned under its right end. */
@Composable
private fun ScaleBar(spec: SkinSpec, length: Dp, distance: String) {
  Layout(
      content = {
        Canvas(Modifier) {
          // A whole-pixel stroke on half-pixel centres (odd widths) or whole pixels (even widths),
          // so the line and the ticks land crisply instead of smearing over two rows.
          val w = 1.5.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()
          val half = w / 2f
          val right = size.width.roundToInt() - half
          val bottom = size.height.roundToInt() - half
          val bar =
              Path().apply {
                moveTo(half, 0f)
                lineTo(half, bottom)
                lineTo(right, bottom)
                lineTo(right, 0f)
              }
          drawPath(bar, Color.White, style = Stroke(width = w, cap = StrokeCap.Butt, join = StrokeJoin.Miter))
        }
        SkinText("0", spec.body, 13.sp, Color.White)
        SkinText(distance, spec.body, 13.sp, Color.White)
      }
  ) { measurables, _ ->
    val barWidth = length.roundToPx()
    val barHeight = 7.dp.roundToPx()
    val gap = 3.dp.roundToPx()
    val bar = measurables[0].measure(Constraints.fixed(barWidth, barHeight))
    val zero = measurables[1].measure(Constraints())
    val label = measurables[2].measure(Constraints())
    // A short bar has no room for both labels; the distance is the one that matters.
    val showZero = barWidth >= zero.width + label.width + 6.dp.roundToPx()
    layout(maxOf(BAR_MAX.roundToPx(), label.width), barHeight + gap + maxOf(zero.height, label.height)) {
      bar.place(0, 0)
      if (showZero) zero.place(0, barHeight + gap)
      label.place((barWidth - label.width).coerceAtLeast(0), barHeight + gap)
    }
  }
}
