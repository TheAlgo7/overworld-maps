package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

fun formatDistance(meters: Double): String =
    if (meters < 950) "${max(10, (meters / 10).roundToInt() * 10)} m"
    else "%.1f km".format(meters / 1000)

fun formatDuration(seconds: Double): String {
  val min = (seconds / 60).roundToInt().coerceAtLeast(1)
  return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}

/** Arrival time on a 12-hour clock, e.g. "10:05 pm". */
fun arrivalClock(seconds: Double): String =
    LocalTime.now().plusSeconds(seconds.toLong()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase()

/** Red Dead's paper: faint grain and a warm vignette over the map. [strength] scales both. */
@Composable
fun PaperOverlay(strength: Float = 1f) {
  val grain = remember { paperGrain() }
  Canvas(Modifier.fillMaxSize()) {
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.22f * strength)
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color(0x6B4A3016).copy(alpha = 0.42f * strength),
            center = Offset(size.width / 2f, size.height * 0.45f),
            radius = size.maxDimension * 0.62f,
        )
    )
  }
}

private fun paperGrain(): ImageBitmap {
  val n = 160
  val rnd = Random(7)
  val pixels = IntArray(n * n) {
    val a = (rnd.nextFloat() * rnd.nextFloat() * 150).toInt()
    (a shl 24) or (0x55 shl 16) or (0x3B shl 8) or 0x1A
  }
  return android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
      .asImageBitmap()
}
