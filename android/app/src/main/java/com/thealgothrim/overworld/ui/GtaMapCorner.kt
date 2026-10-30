package com.thealgothrim.overworld.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.vi.Vi
import com.thealgothrim.overworld.ui.skin.skinPanel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

/** Metres covered by one dp of map at this zoom and latitude (MapLibre's 512 dp world tile). */
fun metresPerDp(latitude: Double, zoom: Double): Double =
    40_075_016.686 * cos(latitude * PI / 180) / (512 * 2.0.pow(zoom))

private val STEPS = listOf(10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000)

/** The scale bar's longest. */
private val BAR_MAX = 84.dp

/**
 * The bottom-left corner of the GTA pause maps: the area name ("CONNAUGHT PLACE"), the
 * neighbourhood at the map's centre, over a scale bar with its distance beside it, in one box the
 * height of the locate button next to it: GTA V's black panel, GTA VI's glass.
 *
 * The bar is one crisp shape (ticks and line joined, square corners, on whole pixels) that slides
 * to its new length when the scale changes, the distance just after its end and the box easing to
 * fit. It used to carry a "0" and a distance under the ends in a box sized for the longest bar,
 * which made it taller than the button and left short bars adrift in it.
 */
@Composable
fun GtaMapCorner(spec: SkinSpec, area: String?, metresPerDp: Double, modifier: Modifier = Modifier) {
  val metres = STEPS.lastOrNull { it / metresPerDp <= BAR_MAX.value } ?: STEPS.first()
  val target = (metres / metresPerDp).toFloat().coerceIn(24f, BAR_MAX.value).dp
  val length by animateDpAsState(target, tween(220), label = "scale bar")
  val distance = if (metres >= 1000) "${metres / 1000} km" else "$metres m"
  Column(
      modifier.skinPanel(spec, elevated = false).animateContentSize(tween(220)).heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 7.dp),
      verticalArrangement = Arrangement.Center,
  ) {
    if (!area.isNullOrBlank()) {
      if (spec.vi) SkinText(area.uppercase(), Vi.condensed, 16.sp, Color.White, Modifier.widthIn(max = 200.dp), spacing = 0.8.sp)
      else SkinText(area.uppercase(), spec.body, 14.sp, Color.White, Modifier.widthIn(max = 200.dp))
      Spacer(Modifier.height(3.dp))
    }
    Row(verticalAlignment = Alignment.Bottom) {
      ScaleBar(length, Modifier.padding(bottom = 4.dp))
      Spacer(Modifier.width(8.dp))
      SkinText(distance, spec.body, 13.sp, Color.White)
    }
  }
}

/** Ticks up at both ends joined by the line, [length] long. */
@Composable
private fun ScaleBar(length: Dp, modifier: Modifier = Modifier) {
  Canvas(modifier.width(length).height(7.dp)) {
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
}
