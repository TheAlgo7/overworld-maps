package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/** Metres covered by one dp of map at this zoom and latitude (MapLibre's 512 dp world tile). */
fun metresPerDp(latitude: Double, zoom: Double): Double =
    40_075_016.686 * cos(latitude * PI / 180) / (512 * 2.0.pow(zoom))

private val STEPS = listOf(10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000)

/**
 * The bottom-left corner of GTA V's pause map: a thin scale bar ("0 ... 746ft", metric here) above
 * the area name in a black box ("WEST VINEWOOD"). Here the area is the neighbourhood at the map's
 * centre.
 */
@Composable
fun GtaMapCorner(spec: SkinSpec, area: String?, metresPerDp: Double, modifier: Modifier = Modifier) {
  Column(modifier) {
    val metres = STEPS.lastOrNull { it / metresPerDp <= 110 } ?: STEPS.first()
    val length = (metres / metresPerDp).coerceIn(24.0, 120.0).dp
    Canvas(Modifier.padding(start = 2.dp).width(length).height(8.dp)) {
      val w = 1.5.dp.toPx()
      val y = size.height - w / 2
      drawLine(Color.White, Offset(0f, y), Offset(size.width, y), w)
      drawLine(Color.White, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
      drawLine(Color.White, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
    }
    Row(Modifier.width(length + 40.dp)) {
      SkinText("0", spec.body, 13.sp, Color.White, Modifier.width(length - 10.dp))
      SkinText(if (metres >= 1000) "${metres / 1000} km" else "$metres m", spec.body, 13.sp, Color.White)
    }
    if (!area.isNullOrBlank()) {
      Box(Modifier.padding(top = 6.dp).background(Color(0xBB000000)).padding(horizontal = 10.dp, vertical = 5.dp)) {
        SkinText(area.uppercase(), spec.body, 15.sp, Color.White)
      }
    }
  }
}
