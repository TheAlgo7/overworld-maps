package com.thealgothrim.overworld.ui.gta

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.theme.GameFonts
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** GTA V HUD colours (the game's HUD_COLOUR_* values) and panel tones. */
object Gta {
  val White = Color(0xFFFEFEFE)
  val Yellow = Color(0xFFF0C850)
  val Waypoint = Color(0xFFA44CF2)
  val WaypointDark = Color(0xFF522679)
  val Health = Color(0xFF359A47)
  val Armour = Color(0xFF5DB6E5)
  val Red = Color(0xFFEB2427)
  val Grey = Color(0xFFB5B5B5)
  /** Menu rows and HUD panels: black at ~73%. */
  val Panel = Color(0xBA000000)
  /** Help-text box. */
  val Help = Color(0xD0000000)
  val RowSelected = Color(0xFFF0F0F0)
}

/** HUD text: white with a hard black outline, the way GTA draws everything over the world. */
@Composable
fun GtaText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Gta.White,
    font: FontFamily = GameFonts.condensed,
    outline: Dp = 2.dp,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) = GtaText(AnnotatedString(text), size, modifier, color, font, outline, align, maxLines)

@Composable
fun GtaText(
    text: AnnotatedString,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Gta.White,
    font: FontFamily = GameFonts.condensed,
    outline: Dp = 2.dp,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
  val stroke = with(LocalDensity.current) { outline.toPx() * 2 }
  val base = TextStyle(fontFamily = font, fontSize = size, textAlign = align)
  Box(modifier) {
    // The outline pass ignores span colours so coloured words keep a black edge too.
    Text(
        AnnotatedString(text.text),
        style = base.copy(color = Color.Black, drawStyle = Stroke(width = stroke, join = StrokeJoin.Round)),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
    Text(text, style = base.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
  }
}

/** The top-left help-text box. */
@Composable
fun GtaHelpText(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(
      modifier.background(Gta.Help).padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      content = content,
  )
}

@Composable
fun GtaHelpLine(text: String, modifier: Modifier = Modifier) {
  Text(text, color = Gta.White, fontFamily = GameFonts.menu, fontSize = 16.sp, lineHeight = 20.sp, modifier = modifier)
}

// ---------------------------------------------------------------- menu

@Composable
fun GtaMenuHeader(title: String) {
  Box(
      Modifier.fillMaxWidth()
          .height(62.dp)
          .background(Brush.verticalGradient(listOf(Color(0xFF6B35AB), Gta.WaypointDark))),
      contentAlignment = Alignment.Center,
  ) {
    GtaText(title, 36.sp, font = GameFonts.price, outline = 2.dp)
  }
}

@Composable
fun GtaMenuSubheader(left: String, right: String = "") {
  Row(
      Modifier.fillMaxWidth().height(30.dp).background(Color.Black).padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(left.uppercase(), color = Gta.White, fontFamily = GameFonts.menu, fontSize = 13.sp, modifier = Modifier.weight(1f))
    Text(right, color = Gta.White, fontFamily = GameFonts.menu, fontSize = 13.sp)
  }
}

/** A menu row: white with black text when selected, otherwise translucent black with white text. */
@Composable
fun GtaMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    checked: Boolean? = null,
    labelColor: Color? = null,
    hint: String? = null,
) {
  val fg = if (selected) Color.Black else Gta.White
  Row(
      Modifier.fillMaxWidth()
          .height(42.dp)
          .background(if (selected) Gta.RowSelected else Gta.Panel)
          .clickable(onClick = onClick)
          .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        label,
        color = labelColor?.takeIf { !selected } ?: fg,
        fontFamily = GameFonts.menu,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    hint?.let {
      Text(
          it,
          color = if (selected) Color(0xFF555555) else Gta.Grey,
          fontFamily = GameFonts.menu,
          fontSize = 13.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 8.dp).widthIn(max = 140.dp),
      )
    }
    value?.let {
      Text(
          if (selected) "<  $it  >" else it,
          color = fg,
          fontFamily = GameFonts.menu,
          fontSize = 16.sp,
          maxLines = 1,
      )
    }
    checked?.let { GtaCheckbox(it, fg) }
  }
}

@Composable
private fun GtaCheckbox(checked: Boolean, color: Color) {
  Canvas(Modifier.size(18.dp)) {
    val w = size.width
    drawRect(color, style = Stroke(width = w * 0.12f))
    if (checked) {
      val tick =
          Path().apply {
            moveTo(w * 0.2f, w * 0.52f)
            lineTo(w * 0.42f, w * 0.74f)
            lineTo(w * 0.82f, w * 0.28f)
          }
      drawPath(tick, color, style = Stroke(width = w * 0.14f, cap = StrokeCap.Square, join = StrokeJoin.Miter))
    }
  }
}

/** The description box under a menu. */
@Composable
fun GtaDescription(text: String, color: Color = Gta.White) {
  Box(Modifier.padding(top = 6.dp).fillMaxWidth().background(Gta.Panel).padding(10.dp)) {
    Text(text, color = color, fontFamily = GameFonts.menu, fontSize = 14.sp, lineHeight = 18.sp)
  }
}

// ---------------------------------------------------------------- prompts

data class GtaPrompt(val key: String, val label: String, val onClick: () -> Unit)

/** Bottom-right instructional buttons: key caps with labels. */
@Composable
fun GtaInstructionalButtons(prompts: List<GtaPrompt>, modifier: Modifier = Modifier) {
  Row(
      modifier.background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    prompts.forEach { p ->
      Row(
          Modifier.clickable(onClick = p.onClick).padding(horizontal = 4.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
            Modifier.size(24.dp).background(Gta.White, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
          Text(p.key, color = Color.Black, fontFamily = GameFonts.condensed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(6.dp))
        Text(p.label, color = Gta.White, fontFamily = GameFonts.menu, fontSize = 15.sp)
      }
    }
  }
}

// ---------------------------------------------------------------- radar pieces

/** The bars under the radar: green (health) and blue (armour), here trip progress and the next turn. */
@Composable
fun GtaHudBars(green: Float, blue: Float, modifier: Modifier = Modifier) {
  Row(
      modifier.height(10.dp).background(Color(0x99000000)).padding(2.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Bar(Gta.Health, green, Modifier.weight(0.62f))
    Bar(Gta.Armour, blue, Modifier.weight(0.38f))
  }
}

@Composable
private fun Bar(color: Color, fraction: Float, modifier: Modifier) {
  Box(modifier.fillMaxHeight().background(color.copy(alpha = 0.3f))) {
    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
  }
}

/** The waypoint distance readout: a small arrow pointing at the destination, then the distance. */
@Composable
fun GtaWaypointDistance(text: String, arrowDegrees: Float, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(20.dp)) {
      rotate(arrowDegrees) {
        val w = size.width
        val arrow =
            Path().apply {
              moveTo(w * 0.5f, w * 0.08f)
              lineTo(w * 0.86f, w * 0.5f)
              lineTo(w * 0.62f, w * 0.5f)
              lineTo(w * 0.62f, w * 0.92f)
              lineTo(w * 0.38f, w * 0.92f)
              lineTo(w * 0.38f, w * 0.5f)
              lineTo(w * 0.14f, w * 0.5f)
              close()
            }
        drawPath(arrow, Color.Black, style = Stroke(width = w * 0.16f, join = StrokeJoin.Round))
        drawPath(arrow, Gta.White)
      }
    }
    Spacer(Modifier.width(6.dp))
    GtaText(text, 24.sp)
  }
}

/**
 * The radar's "N": a black disc that slides along the edge of the view so it always points north
 * as the map rotates. [bearing] is read lazily so only the badge recomposes when the camera turns.
 */
@Composable
fun GtaNorthBadge(bearing: () -> Double, inset: Dp, top: Dp, bottom: Dp, modifier: Modifier = Modifier) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    val density = LocalDensity.current
    val badge = 26.dp
    val w = with(density) { maxWidth.toPx() }
    val h = with(density) { maxHeight.toPx() }
    val bPx = with(density) { badge.toPx() }
    val left = with(density) { inset.toPx() }
    val topPx = with(density) { top.toPx() }
    val bottomPx = with(density) { bottom.toPx() }
    val cx = w / 2f
    val cy = (topPx + (h - bottomPx)) / 2f
    val halfW = w / 2f - left - bPx / 2f
    val halfH = (h - bottomPx - topPx) / 2f - bPx / 2f
    val rad = Math.toRadians(-bearing())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val t = min(if (abs(dx) < 1e-4f) Float.MAX_VALUE else halfW / abs(dx), if (abs(dy) < 1e-4f) Float.MAX_VALUE else halfH / abs(dy))
    val x = cx + dx * t - bPx / 2f
    val y = cy + dy * t - bPx / 2f
    Box(
        Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(badge)
            .background(Color.Black, CircleShape)
            .border(1.dp, Color(0x55FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
      Text("N", color = Gta.White, fontFamily = GameFonts.menu, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
  }
}

// ---------------------------------------------------------------- big message

/** The "mission passed" banner: a black band across the screen with big yellow Pricedown text. */
@Composable
fun GtaBigMessage(visible: Boolean, title: String, subtitle: String, modifier: Modifier = Modifier) {
  AnimatedVisibility(visible, modifier, enter = fadeIn(tween(300)), exit = fadeOut(tween(700))) {
    Box(
        Modifier.fillMaxWidth()
            .height(160.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.18f to Color(0xB3000000),
                    0.82f to Color(0xB3000000),
                    1f to Color.Transparent,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GtaText(title, 58.sp, color = Gta.Yellow, font = GameFonts.price, outline = 3.dp, align = TextAlign.Center)
        GtaText(subtitle, 20.sp, font = GameFonts.menu, align = TextAlign.Center)
      }
    }
  }
}
