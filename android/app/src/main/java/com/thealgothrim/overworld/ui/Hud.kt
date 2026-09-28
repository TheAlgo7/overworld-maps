package com.thealgothrim.overworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stadiamaps.ferrostar.composeui.views.components.maneuver.ManeuverImage
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.UiFont
import com.thealgothrim.overworld.theme.family
import com.thealgothrim.overworld.theme.style
import com.thealgothrim.overworld.theme.weight
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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

fun arrivalClock(seconds: Double): String =
    LocalTime.now().plusSeconds(seconds.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))

fun OverworldTheme.hudText(size: TextUnit, shadow: Boolean = false) =
    TextStyle(
        fontFamily = font.family,
        fontWeight = font.weight,
        fontStyle = font.style,
        fontSize = size,
        shadow =
            if (shadow) Shadow(if (dark) Color.Black.copy(alpha = 0.85f) else hudBg, Offset(0f, 1f), 8f)
            else null,
    )

fun uiText(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = UiFont, fontWeight = weight, fontSize = size)

/** A themed card: HUD surface, neutral hairline, no accent strips. */
fun Modifier.hudCard(theme: OverworldTheme, radius: Int = 16): Modifier =
    this.shadow(8.dp, RoundedCornerShape(radius.dp), clip = false)
        .background(theme.hudBg, RoundedCornerShape(radius.dp))
        .border(1.dp, theme.hudBorder, RoundedCornerShape(radius.dp))

@Composable
fun TurnBanner(theme: OverworldTheme, uiState: NavigationUiState, modifier: Modifier = Modifier) {
  val instruction = uiState.visualInstruction
  val toNext = uiState.progress?.distanceToNextManeuver
  Row(
      modifier.fillMaxWidth().hudCard(theme).padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
      instruction?.primaryContent?.let { ManeuverImage(it, tint = theme.hudAccent) }
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      if (uiState.isCalculatingNewRoute == true) {
        Text(theme.display("Rerouting"), color = theme.hudFg, style = theme.hudText(26.sp))
      } else {
        // Units stay lowercase even in uppercase themes: "200 M" would read as millions.
        Text(
            toNext?.let(::formatDistance) ?: "",
            color = theme.hudFg,
            style = theme.hudText(32.sp),
        )
        Text(
            instruction?.primaryContent?.text ?: "Follow the route",
            color = theme.hudSub,
            style = theme.hudText(18.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
fun TripBar(
    theme: OverworldTheme,
    uiState: NavigationUiState,
    onEnd: () -> Unit,
    onMute: () -> Unit,
    onOverview: () -> Unit,
    onTheme: () -> Unit,
    following: Boolean,
    modifier: Modifier = Modifier,
) {
  val progress = uiState.progress
  Column(modifier.fillMaxWidth().hudCard(theme).padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
          progress?.let { formatDuration(it.durationRemaining) } ?: "",
          color = theme.hudAccent,
          style = theme.hudText(26.sp),
      )
      Spacer(Modifier.width(14.dp))
      Text(
          progress?.let { formatDistance(it.distanceRemaining) } ?: "",
          color = theme.hudSub,
          style = theme.hudText(20.sp),
      )
      Spacer(Modifier.weight(1f))
      Text(
          progress?.let { arrivalClock(it.durationRemaining) } ?: "",
          color = theme.hudGood,
          style = theme.hudText(20.sp),
      )
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      HudButton(theme, if (uiState.isMuted == true) "Sound on" else "Mute", onMute, Modifier.weight(1f))
      HudButton(theme, if (following) "Overview" else "Follow", onOverview, Modifier.weight(1.2f))
      HudButton(theme, "Theme", onTheme, Modifier.weight(1f))
      HudButton(theme, "End", onEnd, Modifier.weight(1f), strong = true)
    }
  }
}

@Composable
fun HudButton(
    theme: OverworldTheme,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
) {
  val shape = RoundedCornerShape(12.dp)
  Box(
      modifier
          .background(if (strong) theme.hudAccent else theme.hudFg.copy(alpha = 0.08f), shape)
          .border(1.dp, if (strong) theme.hudAccent else theme.hudBorder, shape)
          .clickable(onClick = onClick)
          .padding(vertical = 10.dp),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        theme.display(label),
        color = if (strong) contrastOn(theme.hudAccent) else theme.hudFg,
        style = theme.hudText(17.sp),
    )
  }
}

fun contrastOn(c: Color): Color =
    if (0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue > 0.55f) Color(0xFF111111) else Color.White

/** Frontier's paper: faint grain and a warm vignette over the map (phone only, never in the car). */
@Composable
fun PaperOverlay() {
  val grain = remember { paperGrain() }
  Canvas(Modifier.fillMaxSize()) {
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = 0.22f)
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color(0x6B4A3016),
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
