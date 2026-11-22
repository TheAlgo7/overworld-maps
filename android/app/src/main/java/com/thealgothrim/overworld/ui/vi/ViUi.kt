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
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.asAndroidPath
import com.thealgothrim.overworld.theme.RADAR
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.draw.drawWithCache
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
  /**
   * Panels: the slate of the game's map cards and menus (measured from the PS5 captures, 22 Nov:
   * the place card #262634, the pause and Game menus #20212C), nearly solid.
   */
  val Glass = Color(0xF2262634)
  /** The pause menu's own panel. */
  val Slate = Color(0xFF20212C)
  /** The selected row and other raised pieces. */
  val GlassRaised = Color(0xF8303140)
  val Hairline = Color(0x1AFFFFFF)
  /** Text on the silver selection bar. */
  val OnSilver = Color(0xFF24232C)
  /**
   * The selected row: a bar of white that holds for most of the row, then greys out to the right
   * (the pause menu's Map row: #E7E7E7, #DEDEDE at 60%, #A9A8AD, #6E6F76 at the end).
   */
  val Silver = Brush.horizontalGradient(0f to Color(0xFFE9E9EC), 0.55f to Color(0xFFDEDEDF), 0.8f to Color(0xFFA9A8AD), 1f to Color(0xFF6E6F76))
  /** Settings sits on violet: #744374 at the top, #5F3D66, #4A2F56 at the bottom. */
  val Violet = Brush.verticalGradient(0f to Color(0xFF744374), 0.45f to Color(0xFF5F3D66), 1f to Color(0xFF4A2F56))
  /** Text and arrows on pink. */
  val Ink = Color(0xFF17121B)

  /** Small corners like the minimap's frame; the menus themselves are square. */
  val corner = 6.dp

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
  return this.then(if (elevated) Modifier.outerShadow(shape) else Modifier)
      .clip(shape)
      .background(color)
      .border(1.dp, Vi.Hairline, shape)
}

/**
 * A soft shadow drawn only outside [shape]. An elevation shadow is cast under the whole box, so
 * through see-through glass it shows up as a faint, offset copy of the panel.
 */
fun Modifier.outerShadow(shape: Shape, blur: Dp = 8.dp, drop: Dp = 2.dp, color: Color = Color(0x4D000000)): Modifier =
    drawWithCache {
      val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
      val paint =
          android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.BLACK
            setShadowLayer(blur.toPx(), 0f, drop.toPx(), color.toArgb())
          }
      onDrawBehind { clipPath(path, ClipOp.Difference) { drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) } } }
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

/** The menu's title as the game sets it ("SETTINGS", "GAME"): big white condensed capitals. */
@Composable
fun ViMenuHeader(title: String, subtitle: String, count: String) {
  Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
    ViText(title.uppercase(), 42.sp, font = Vi.condensedBold, outline = 0.dp, spacing = 0.5.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
      Text(subtitle.uppercase(), color = Vi.Sub, fontFamily = Vi.condensed, fontSize = 15.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
      Text(count, color = Vi.Sub, fontFamily = Vi.condensed, fontSize = 15.sp, letterSpacing = 1.sp)
    }
  }
}

/**
 * A menu row as the game draws them: flat on the menu's background with a hairline under it; the
 * selected one is the silver bar with dark text. Values sit on the right ("‹ HIGH ›" when picked);
 * on/off settings use the pink switch with its word beside it.
 */
@Composable
fun ViMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    checked: Boolean? = null,
    labelColor: Color? = null,
) {
  val ink = if (selected) Vi.OnSilver else Vi.White
  Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth()
            .height(52.dp)
            .then(if (selected) Modifier.background(Vi.Silver) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          label,
          color = labelColor ?: ink,
          fontFamily = Vi.sans,
          fontSize = 17.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      value?.let {
        Text(
            if (selected) "‹  $it  ›" else it,
            color = if (selected) Vi.OnSilver else Vi.Sub,
            fontFamily = Vi.sans,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
        )
      }
      checked?.let {
        Text(if (it) "On" else "Off", color = if (selected) Vi.OnSilver else Vi.Sub, fontFamily = Vi.sans, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        ViSwitch(it)
      }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Vi.Hairline))
  }
}

/** The game's switch: a pink pill with a white knob when on, an outlined grey one when off. */
@Composable
fun ViSwitch(on: Boolean, modifier: Modifier = Modifier) {
  Canvas(modifier.size(width = 40.dp, height = 24.dp)) {
    val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
    val knob = size.height * 0.36f
    if (on) {
      drawRoundRect(Vi.Pink, cornerRadius = r)
      drawCircle(Color.White, knob, Offset(size.width - size.height / 2f, size.height / 2f))
    } else {
      drawRoundRect(Color(0x33FFFFFF), cornerRadius = r)
      drawRoundRect(Vi.Sub, cornerRadius = r, style = Stroke(width = 1.5.dp.toPx()))
      drawCircle(Vi.Sub, knob, Offset(size.height / 2f, size.height / 2f))
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

/**
 * Interaction prompts, bottom right, the way the map screen lists them ("RECENTER ⓛ"): the label in
 * condensed capitals, then the key in a white ring. A faint shadow keeps them readable over a map.
 */
@Composable
fun ViPrompts(prompts: List<ViPrompt>, modifier: Modifier = Modifier) {
  // A slate backing, so rows scrolling under the prompt never run into it.
  Column(
      modifier.background(Vi.Slate.copy(alpha = 0.92f), RoundedCornerShape(Vi.corner)).padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    prompts.forEach { p ->
      Row(Modifier.clickable(onClick = p.onClick).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ViText(p.label.uppercase(), 19.sp, font = Vi.condensedBold, outline = 1.dp, spacing = 0.6.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(26.dp).border(2.dp, Vi.White, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
          Text(p.key, color = Vi.White, fontFamily = Vi.sansSemiBold, fontSize = 12.sp)
        }
      }
    }
  }
}

// ---------------------------------------------------------------- map light

/**
 * The light across GTA VI's minimap, measured in Rockstar's frames (top band against the band by
 * the player): by day the far side of the map sits about a tenth darker; at golden hour it's lit
 * warm instead and the near side falls into shade; at night the map is flat. Drawn over the map,
 * under the HUD. [variant]: the map's palette. Only the minimap ("radar", "radar-dusk") is lit
 * this way; the pause map is flat.
 */
@Composable
fun ViMapLight(variant: String?) {
  val dusk =
      when (variant) {
        RADAR -> false
        "$RADAR-dusk" -> true
        else -> return
      }
  Canvas(Modifier.fillMaxSize()) {
    val brush =
        if (dusk)
            Brush.verticalGradient(
                0f to Color(0x1FFFF0E2),
                0.42f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to Color(0x14000000),
            )
        else Brush.verticalGradient(0f to Color(0x1F000000), 0.45f to Color.Transparent)
    drawRect(brush)
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

/**
 * The top of GTA VI's turn card, laid out like the game's own (its driving footage): the move in
 * words, bold, the distance under it, and the arrow dark on a pink square flush with the card's
 * right edge, as tall as the text. The card's own clip rounds the square's outer corners.
 */
@Composable
fun ViTurnRow(
    title: String,
    line: String,
    content: VisualInstructionContent?,
    side: DrivingSide?,
    titleSize: TextUnit,
    lineSize: TextUnit,
    modifier: Modifier = Modifier,
) {
  // The row's height changes only when its second line comes or goes (or the type's size does),
  // so the row keeps it between the distance's changes: see TurnRowPolicy.
  val density = LocalDensity.current
  val policy = remember(line.isEmpty(), titleSize, lineSize, density.density, density.fontScale) { TurnRowPolicy() }
  Layout(
      content = {
        // The lines are the row's own children, with no Column or padding round them:
        // TurnRowPolicy stacks them and keeps their margins (see there for why).
        Text(title, style = TextStyle(fontFamily = Vi.sansSemiBold, fontSize = titleSize, color = Vi.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (line.isNotEmpty()) Text(line, style = TextStyle(fontFamily = Vi.sans, fontSize = lineSize, color = Vi.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.background(Vi.Pink), contentAlignment = Alignment.Center) {
          content?.let { Box(Modifier.fillMaxSize(0.58f), contentAlignment = Alignment.Center) { TurnArrow(it, side, Vi.Ink) } }
        }
      },
      modifier = modifier.fillMaxWidth(),
      measurePolicy = policy,
  )
}

/**
 * [ViTurnRow]'s layout: the text on the left inside its margins, the square on the right as tall as
 * the row. It was a Row with height(IntrinsicSize.Min) inside a card that took its width from
 * width(IntrinsicSize.Max), and between them every line of text was laid out several times over
 * whenever the distance changed, which is at every GPS fix: 5 to 6 ms of layout on the car screen
 * each second, on top of the map's own frame (traces of 14 Nov). Two things made it so: asking a
 * row for its height before measuring it lays its text out once more, and asking anything wrapped
 * in a padding modifier for its width works out its height too, which lays the text out again.
 * So: the lines are one line each and as tall at any width, the row's height from the last time
 * says how wide the square will be, the margins are added here and not by a modifier, and each line
 * is laid out once.
 */
private class TurnRowPolicy : MeasurePolicy {
  /** The row's height when last measured, px; 0 before the first time. */
  private var rowHeight = 0

  private fun Density.across() = 18.dp.roundToPx() + 16.dp.roundToPx()

  private fun Density.above() = 12.dp.roundToPx()

  private fun Density.upright() = above() + 13.dp.roundToPx()

  private fun Density.between() = 1.dp.roundToPx()

  /** The square's side: the row's height. Asked of the lines (all but the last child) only the first time. */
  private fun Density.side(children: List<IntrinsicMeasurable>, width: Int): Int {
    if (rowHeight > 0) return rowHeight
    val lines = children.dropLast(1)
    return lines.sumOf { it.minIntrinsicHeight(width) } + between() * (lines.size - 1).coerceAtLeast(0) + upright()
  }

  override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
    val reserved = side(measurables, constraints.maxWidth) + across()
    val room = Constraints(maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - reserved).coerceAtLeast(0) else Constraints.Infinity)
    val lines = measurables.dropLast(1).map { it.measure(room) }
    val height = lines.sumOf { it.height } + between() * (lines.size - 1).coerceAtLeast(0) + upright()
    rowHeight = height
    val square = measurables.last().measure(Constraints.fixed(height, height))
    val width = ((lines.maxOfOrNull { it.width } ?: 0) + across() + square.width).coerceIn(constraints.minWidth, constraints.maxWidth)
    return layout(width, height) {
      var y = above()
      lines.forEach {
        it.place(18.dp.roundToPx(), y)
        y += it.height + between()
      }
      square.place(width - square.width, 0)
    }
  }

  override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
      (measurables.dropLast(1).maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0) + across() + side(measurables, Constraints.Infinity)

  override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
      (measurables.dropLast(1).maxOfOrNull { it.minIntrinsicWidth(Constraints.Infinity) } ?: 0) + across() + side(measurables, Constraints.Infinity)

  override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = side(measurables, width)

  override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int = side(measurables, width)
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
