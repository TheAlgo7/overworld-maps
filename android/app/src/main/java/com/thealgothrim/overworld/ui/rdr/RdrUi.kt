package com.thealgothrim.overworld.ui.rdr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** RDR2 UI colours from the game's colors.xml. */
object Rdr {
  val White = Color(0xFFE6E6E6)
  val GreyLight = Color(0xFFD5D3D2)
  val Grey = Color(0xFFACA8A6)
  val GreyDark = Color(0xFF5D5B5A)
  val OffBlack = Color(0xFF1B1A1A)
  val Red = Color(0xFFCC0000)
  val RedDark = Color(0xFF7A0E1D)
  val Objective = Color(0xFFFEF390)
  val Stamina = Color(0xFF56A8D3)
  val DeadEye = Color(0xFFFF9F74)
  val Ink = Color(0xC8080706)
}

private val shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 1.5f), 6f)

/** HUD text over the map: off-white with a soft ink shadow. */
@Composable
fun RdrText(
    text: AnnotatedString,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Rdr.White,
    font: FontFamily = GameFonts.lino,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    spacing: TextUnit = TextUnit.Unspecified,
) {
  Text(
      text,
      modifier = modifier,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(fontFamily = font, fontSize = size, color = color, textAlign = align, shadow = shadow, letterSpacing = spacing),
  )
}

@Composable
fun RdrText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Rdr.White,
    font: FontFamily = GameFonts.lino,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    spacing: TextUnit = TextUnit.Unspecified,
) = RdrText(AnnotatedString(text), size, modifier, color, font, align, maxLines, spacing)

/** The ink-roller band behind RDR2 HUD panels: solid ink that feathers out at the far edge. */
fun Modifier.inkBand(fromLeft: Boolean = true): Modifier =
    this.background(
        if (fromLeft) Brush.horizontalGradient(0f to Rdr.Ink, 0.8f to Rdr.Ink, 1f to Color.Transparent)
        else Brush.horizontalGradient(0f to Color.Transparent, 0.2f to Rdr.Ink, 1f to Rdr.Ink)
    )

/** Engraved divider: a thin rule with a small diamond in the middle. */
@Composable
fun RdrDivider(modifier: Modifier = Modifier, color: Color = Rdr.Grey) {
  Canvas(modifier.fillMaxWidth().height(10.dp)) {
    val y = size.height / 2f
    drawLine(color.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    val c = Offset(size.width / 2f, y)
    val r = 4.dp.toPx()
    val diamond =
        Path().apply {
          moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
        }
    drawPath(diamond, Rdr.OffBlack)
    drawPath(diamond, color, style = Stroke(width = 1.dp.toPx()))
  }
}

/** Top-left help text: Hapna Slab on an ink band. */
@Composable
fun RdrHelpBox(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  Row(
      modifier.inkBand().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 34.dp),
      verticalAlignment = Alignment.CenterVertically,
      content = content,
  )
}

@Composable
fun RdrHelpLine(text: String) {
  Text(text, color = Rdr.White, fontFamily = GameFonts.hapna, fontSize = 16.sp, lineHeight = 21.sp)
}

// ---------------------------------------------------------------- menu

@Composable
fun RdrMenuTitle(title: String, subtitle: String, count: String = "") {
  RdrText(title, 34.sp, spacing = 2.sp)
  RdrDivider(Modifier.padding(vertical = 4.dp))
  Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
    Text(subtitle.uppercase(), color = Rdr.Grey, fontFamily = GameFonts.hapna, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
    Text(count, color = Rdr.Grey, fontFamily = GameFonts.hapna, fontSize = 13.sp)
  }
}

/** A menu row. The selected row gets RDR2's rough off-white selection box. */
@Composable
fun RdrMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    checked: Boolean? = null,
    labelColor: Color? = null,
    hint: String? = null,
) {
  Row(
      Modifier.fillMaxWidth()
          .height(44.dp)
          .then(if (selected) Modifier.roughBox() else Modifier)
          .clickable(onClick = onClick)
          .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    RdrText(label.uppercase(), 19.sp, Modifier.weight(1f), color = labelColor ?: if (selected) Color.White else Rdr.GreyLight)
    hint?.let {
      Text(it, color = Rdr.Grey, fontFamily = GameFonts.hapna, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
    }
    value?.let { RdrText(if (selected) "<  ${it.uppercase()}  >" else it.uppercase(), 17.sp, color = Rdr.White) }
    checked?.let { RdrTickBox(it) }
  }
}

/** The selection box: a dark fill with a hand-drawn looking off-white frame. */
fun Modifier.roughBox(): Modifier =
    this.drawBehind {
      drawRect(Color(0x66000000))
      val w = 1.4.dp.toPx()
      val jitter = listOf(Offset(0f, 0f), Offset(1.2f, -0.8f), Offset(-0.9f, 1f))
      jitter.forEachIndexed { i, o ->
        drawRect(
            Rdr.White.copy(alpha = if (i == 0) 0.95f else 0.35f),
            topLeft = Offset(o.x + w / 2, o.y + w / 2),
            size = Size(size.width - w, size.height - w),
            style = Stroke(width = w),
        )
      }
    }

@Composable
private fun RdrTickBox(checked: Boolean) {
  Canvas(Modifier.size(20.dp)) {
    val s = size.width
    drawRect(Rdr.White, style = Stroke(width = s * 0.09f))
    if (checked) {
      val tick = Path().apply { moveTo(s * 0.2f, s * 0.55f); lineTo(s * 0.42f, s * 0.76f); lineTo(s * 0.84f, s * 0.24f) }
      drawPath(tick, Rdr.Red, style = Stroke(width = s * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
  }
}

@Composable
fun RdrDescription(text: String, color: Color = Rdr.GreyLight) {
  Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
    RdrDivider()
    Text(text, color = color, fontFamily = GameFonts.hapna, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
  }
}

// ---------------------------------------------------------------- prompts

data class RdrPrompt(val key: String, val label: String, val onClick: () -> Unit)

/** Bottom-right interaction prompts: a light key cap and an uppercase label, on an ink band. */
@Composable
fun RdrPrompts(prompts: List<RdrPrompt>, modifier: Modifier = Modifier) {
  Row(
      modifier.inkBand(fromLeft = false).padding(start = 28.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    prompts.forEach { p ->
      Row(Modifier.clickable(onClick = p.onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(Rdr.White, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
          Text(p.key, color = Rdr.OffBlack, fontFamily = GameFonts.hapna, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        RdrText(p.label.uppercase(), 17.sp)
      }
    }
  }
}

// ---------------------------------------------------------------- cores

enum class CoreIcon { ROUTE, TURN }

/** An RDR2 core: dark disc, off-white icon, and a ring meter around it. */
@Composable
fun RdrCore(fraction: Float, icon: CoreIcon, modifier: Modifier = Modifier, ring: Color = Rdr.White) {
  Canvas(modifier.size(46.dp)) {
    val s = size.minDimension
    val c = Offset(size.width / 2f, size.height / 2f)
    val stroke = s * 0.09f
    drawCircle(Color(0x99000000), s / 2f, c)
    drawCircle(Rdr.GreyDark.copy(alpha = 0.7f), s / 2f - stroke / 2f, c, style = Stroke(width = stroke))
    drawArc(
        ring,
        startAngle = -90f,
        sweepAngle = 360f * fraction.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(s - stroke, s - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Butt),
    )
    when (icon) {
      CoreIcon.ROUTE -> drawRouteIcon(c, s)
      CoreIcon.TURN -> drawTurnIcon(c, s)
    }
  }
}

private fun DrawScope.drawRouteIcon(c: Offset, s: Float) {
  // A small waypoint flag on a staff.
  val w = s * 0.07f
  drawLine(Rdr.White, Offset(c.x - s * 0.1f, c.y + s * 0.2f), Offset(c.x - s * 0.1f, c.y - s * 0.2f), strokeWidth = w, cap = StrokeCap.Round)
  val flag = Path().apply {
    moveTo(c.x - s * 0.1f, c.y - s * 0.2f); lineTo(c.x + s * 0.18f, c.y - s * 0.1f); lineTo(c.x - s * 0.1f, c.y); close()
  }
  drawPath(flag, Rdr.Red)
}

private fun DrawScope.drawTurnIcon(c: Offset, s: Float) {
  val w = s * 0.08f
  val arrow = Path().apply {
    moveTo(c.x - s * 0.12f, c.y + s * 0.2f); lineTo(c.x - s * 0.12f, c.y - s * 0.02f); lineTo(c.x + s * 0.14f, c.y - s * 0.02f)
  }
  drawPath(arrow, Rdr.White, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
  val head = Path().apply {
    moveTo(c.x + s * 0.05f, c.y - s * 0.12f); lineTo(c.x + s * 0.17f, c.y - s * 0.02f); lineTo(c.x + s * 0.05f, c.y + s * 0.08f)
  }
  drawPath(head, Rdr.White, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------------------------------------------------------------- north

/** The radar's north letter: a plain "N" that slides along the view edge as the map turns. */
@Composable
fun RdrNorth(bearing: () -> Double, inset: Dp, top: Dp, bottom: Dp) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val d = LocalDensity.current
    val badge = with(d) { 26.dp.toPx() }
    val w = with(d) { maxWidth.toPx() }
    val h = with(d) { maxHeight.toPx() }
    val left = with(d) { inset.toPx() }
    val t = with(d) { top.toPx() }
    val b = with(d) { bottom.toPx() }
    val cx = w / 2f
    val cy = (t + (h - b)) / 2f
    val halfW = w / 2f - left - badge / 2f
    val halfH = (h - b - t) / 2f - badge / 2f
    val rad = Math.toRadians(-bearing())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val k = min(if (abs(dx) < 1e-4f) Float.MAX_VALUE else halfW / abs(dx), if (abs(dy) < 1e-4f) Float.MAX_VALUE else halfH / abs(dy))
    Box(
        Modifier.offset { IntOffset((cx + dx * k - badge / 2f).roundToInt(), (cy + dy * k - badge / 2f).roundToInt()) }.size(26.dp),
        contentAlignment = Alignment.Center,
    ) {
      RdrText("N", 20.sp, color = Rdr.Grey)
    }
  }
}

// ---------------------------------------------------------------- big message

/** Mission-complete style title across an ink band. */
@Composable
fun RdrBigMessage(visible: Boolean, title: String, subtitle: String, modifier: Modifier = Modifier) {
  AnimatedVisibility(visible, modifier, enter = fadeIn(tween(400)), exit = fadeOut(tween(800))) {
    Box(
        Modifier.fillMaxWidth()
            .height(170.dp)
            .background(Brush.horizontalGradient(0f to Color.Transparent, 0.15f to Rdr.Ink, 0.85f to Rdr.Ink, 1f to Color.Transparent)),
        contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RdrText(title, 56.sp, align = TextAlign.Center, spacing = 3.sp)
        RdrDivider(Modifier.width(220.dp).padding(vertical = 2.dp), color = Rdr.Red)
        RdrText(subtitle, 19.sp, font = GameFonts.hapna, color = Rdr.GreyLight, align = TextAlign.Center)
      }
    }
  }
}
