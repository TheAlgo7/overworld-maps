package com.thealgothrim.overworld.ui.vi

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.ui.TurnArrow
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.VisualInstructionContent

/**
 * GTA VI's interface, from Rockstar's official "An Extended Look" (2026-08-27): the mission HUD's
 * condensed capitals over dark slate boxes with a pink edge, the minimap's clean grotesk ("1.82 mi")
 * with a thin dark outline, and the phone's rounded glass cards. Pink is the route and waypoint
 * colour (#FB74A5); mint is the ally blip.
 */
object Vi {
  val White = Color(0xFFF1F3F7)
  val Sub = Color(0xFFA1A6B4)
  val Pink = Color(0xFFFB74A5)
  val PinkDeep = Color(0xFF9E3D66)
  val Mint = Color(0xFF4BE3C4)
  val Red = Color(0xFFFF4D6D)
  /** Panels: dark slate glass, like the mission HUD's counter boxes. */
  val Glass = Color(0xDB1C1B26)
  /** The selected row and other raised pieces. */
  val GlassRaised = Color(0xF02B2A38)
  val Hairline = Color(0x24FFFFFF)
  /** Text and arrows on pink. */
  val Ink = Color(0xFF17121B)

  val corner = 14.dp

  /** Inter: the minimap's distance and the phone's notifications are set in a grotesk like it. */
  val sans =
      FontFamily(
          Font(R.font.inter_regular, FontWeight.Normal),
          Font(R.font.inter_medium, FontWeight.Medium),
          Font(R.font.inter_semibold, FontWeight.SemiBold),
          Font(R.font.inter_bold, FontWeight.Bold),
      )
  val sansMedium = FontFamily(Font(R.font.inter_medium, FontWeight.Medium))
  val sansSemiBold = FontFamily(Font(R.font.inter_semibold, FontWeight.SemiBold))

  /** Barlow Condensed: the mission HUD's condensed capitals ("TARGETS REMAINING", "00:35.12"). */
  val condensed = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold))
  val condensedBold = FontFamily(Font(R.font.barlow_condensed_bold, FontWeight.Bold))
}

/** A GTA VI panel: rounded dark glass with a hairline edge and a soft shadow. */
fun Modifier.viGlass(corner: Dp = Vi.corner, elevated: Boolean = true, color: Color = Vi.Glass): Modifier {
  val shape = RoundedCornerShape(corner)
  return this.then(if (elevated) Modifier.shadow(12.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
      .clip(shape)
      .background(color)
      .border(1.dp, Vi.Hairline, shape)
}

/**
 * Text over the map: white with a thin dark outline, the way the minimap prints its distance.
 * [font] defaults to the grotesk; the condensed face for counters and timers.
 */
@Composable
fun ViText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Vi.White,
    font: FontFamily = Vi.sansMedium,
    outline: Dp = 1.5.dp,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    spacing: TextUnit = TextUnit.Unspecified,
) {
  val stroke = with(LocalDensity.current) { outline.toPx() * 2 }
  val base = TextStyle(fontFamily = font, fontSize = size, textAlign = align, letterSpacing = spacing)
  Box(modifier) {
    Text(
        AnnotatedString(text),
        style = base.copy(color = Color(0xE6000000), drawStyle = Stroke(width = stroke, join = StrokeJoin.Round)),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
    Text(text, style = base.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
  }
}

// ---------------------------------------------------------------- menu

/** The pause menu's title: condensed capitals with a short pink rule under them. */
@Composable
fun ViMenuHeader(title: String, subtitle: String, count: String) {
  Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
    ViText(title.uppercase(), 40.sp, font = Vi.condensedBold, outline = 0.dp, spacing = 1.sp)
    Box(Modifier.padding(top = 2.dp, bottom = 8.dp).width(54.dp).height(3.dp).background(Vi.Pink, RoundedCornerShape(2.dp)))
    Row(Modifier.fillMaxWidth()) {
      Text(subtitle.uppercase(), color = Vi.Sub, fontFamily = Vi.condensed, fontSize = 15.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
      Text(count, color = Vi.Sub, fontFamily = Vi.condensed, fontSize = 15.sp, letterSpacing = 1.sp)
    }
  }
}

/** A menu row: glass, the selected one raised with a pink edge, values in condensed capitals. */
@Composable
fun ViMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    checked: Boolean? = null,
    labelColor: Color? = null,
) {
  val shape = RoundedCornerShape(10.dp)
  Row(
      Modifier.fillMaxWidth()
          .padding(vertical = 3.dp)
          .height(48.dp)
          .clip(shape)
          .background(if (selected) Vi.GlassRaised else Vi.Glass)
          .border(1.dp, if (selected) Vi.Pink.copy(alpha = 0.55f) else Vi.Hairline, shape)
          .clickable(onClick = onClick),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) Vi.Pink else Color.Transparent))
    Spacer(Modifier.width(12.dp))
    Text(
        label,
        color = labelColor ?: Vi.White,
        fontFamily = Vi.sansMedium,
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    value?.let {
      Text(
          if (selected) "‹  ${it.uppercase()}  ›" else it.uppercase(),
          color = if (selected) Vi.White else Vi.Sub,
          fontFamily = Vi.condensed,
          fontSize = 17.sp,
          letterSpacing = 0.6.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = 190.dp),
      )
    }
    checked?.let { ViCheck(it) }
    Spacer(Modifier.width(14.dp))
  }
}

/** A rounded check box: pink with a dark tick when on. */
@Composable
fun ViCheck(checked: Boolean, modifier: Modifier = Modifier) {
  Canvas(modifier.size(22.dp)) {
    val s = size.width
    val r = androidx.compose.ui.geometry.CornerRadius(s * 0.28f)
    if (checked) {
      drawRoundRect(Vi.Pink, cornerRadius = r)
      val tick = Path().apply { moveTo(s * 0.24f, s * 0.53f); lineTo(s * 0.43f, s * 0.71f); lineTo(s * 0.77f, s * 0.31f) }
      drawPath(tick, Vi.Ink, style = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    } else {
      drawRoundRect(Vi.Sub, cornerRadius = r, style = Stroke(width = s * 0.09f))
    }
  }
}

/** The description under a menu: a glass box in the grotesk. */
@Composable
fun ViDescription(text: String, color: Color = Vi.White) {
  Box(Modifier.padding(top = 8.dp).fillMaxWidth().viGlass(elevated = false).padding(14.dp)) {
    Text(text, color = color, fontFamily = Vi.sans, fontSize = 14.sp, lineHeight = 19.sp)
  }
}

// ---------------------------------------------------------------- prompts

data class ViPrompt(val key: String, val label: String, val onClick: () -> Unit)

/** Interaction prompts, bottom right: a white round key with the label in condensed capitals. */
@Composable
fun ViPrompts(prompts: List<ViPrompt>, modifier: Modifier = Modifier) {
  Row(
      modifier.viGlass(corner = 22.dp).padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    prompts.forEach { p ->
      Row(Modifier.clickable(onClick = p.onClick).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp).background(Vi.White, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
          Text(p.key, color = Vi.Ink, fontFamily = Vi.sansSemiBold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(p.label.uppercase(), color = Vi.White, fontFamily = Vi.condensed, fontSize = 17.sp, letterSpacing = 0.8.sp)
      }
    }
  }
}

// ---------------------------------------------------------------- turn

/** GTA VI's turn arrow: dark, on a rounded pink tile in the route's colour. */
@Composable
fun ViTurnTile(content: VisualInstructionContent, side: DrivingSide?, size: Dp, modifier: Modifier = Modifier) {
  Box(modifier.size(size).background(Vi.Pink, RoundedCornerShape(size * 0.24f)), contentAlignment = Alignment.Center) {
    Box(Modifier.size(size * 0.72f), contentAlignment = Alignment.Center) { TurnArrow(content, side, Vi.Ink) }
  }
}

// ---------------------------------------------------------------- bars

/** Trip progress: a thin pink line on a faint track. */
@Composable
fun ViProgress(fraction: Float, modifier: Modifier = Modifier) {
  Canvas(modifier.fillMaxWidth().height(4.dp)) {
    val y = size.height / 2f
    val w = size.height
    drawLine(Color(0x33FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = w, cap = StrokeCap.Round)
    if (fraction > 0f) drawLine(Vi.Pink, Offset(0f, y), Offset(size.width * fraction.coerceIn(0f, 1f), y), strokeWidth = w, cap = StrokeCap.Round)
  }
}

// ---------------------------------------------------------------- big message

private val glow = Shadow(Vi.Pink.copy(alpha = 0.9f), Offset.Zero, 28f)

/**
 * "ARRIVED", in the style of the GTA VI title cards: wide-spaced white capitals glowing pink over a
 * dark band, the place underneath.
 */
@Composable
fun ViBigMessage(visible: Boolean, title: String, subtitle: String, modifier: Modifier = Modifier) {
  AnimatedVisibility(visible, modifier, enter = fadeIn(tween(300)), exit = fadeOut(tween(700))) {
    Box(
        Modifier.fillMaxWidth()
            .height(160.dp)
            .background(Brush.horizontalGradient(0f to Color.Transparent, 0.18f to Color(0xC21C1B26), 0.82f to Color(0xC21C1B26), 1f to Color.Transparent)),
        contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title.uppercase(),
            style = TextStyle(fontFamily = Vi.sansSemiBold, fontSize = 40.sp, letterSpacing = 9.sp, color = Vi.White, shadow = glow, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = TextStyle(fontFamily = Vi.sans, fontSize = 18.sp, color = Vi.White.copy(alpha = 0.85f), textAlign = TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
