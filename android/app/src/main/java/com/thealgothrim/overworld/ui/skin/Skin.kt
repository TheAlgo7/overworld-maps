package com.thealgothrim.overworld.ui.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.rdr.Rdr

/** Colours and type for the app chrome, taken from the game the theme is based on. */
data class SkinSpec(
    val skin: Skin,
    val fg: Color,
    val sub: Color,
    val accent: Color,
    val accentDark: Color,
    val good: Color,
    val panel: Color,
    val title: FontFamily,
    val body: FontFamily,
    /** Big numbers: time to go and similar. */
    val big: FontFamily,
    /** Small HUD numbers: distances. */
    val number: FontFamily,
    val upperTitles: Boolean,
) {
  val gta get() = skin == Skin.GTA

  fun title(text: String) = if (upperTitles) text.uppercase() else text
}

val OverworldTheme.spec: SkinSpec
  get() =
      when (skin) {
        Skin.GTA ->
            SkinSpec(
                skin, Gta.White, Gta.Grey, hudAccent, Gta.WaypointDark, hudGood, Color(0xD9000000),
                title = GameFonts.menu, body = GameFonts.menu, big = GameFonts.price, number = GameFonts.condensed,
                upperTitles = false,
            )
        Skin.RDR ->
            SkinSpec(
                skin, Rdr.White, Rdr.Grey, hudAccent, Rdr.RedDark, hudGood, Color(0xEB0E0C0A),
                title = GameFonts.lino, body = GameFonts.hapna, big = GameFonts.lino, number = GameFonts.lino,
                upperTitles = true,
            )
      }

// ---------------------------------------------------------------- text

@Composable
fun SkinText(
    text: String,
    font: FontFamily,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    spacing: TextUnit = TextUnit.Unspecified,
) {
  Text(
      text,
      modifier = modifier,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
      style = TextStyle(fontFamily = font, fontSize = size, color = color, letterSpacing = spacing, lineHeight = size * 1.2f),
  )
}

// ---------------------------------------------------------------- surfaces

/** A HUD panel: square black box for GTA, ink with an engraved inner rule for Red Dead. */
fun Modifier.skinPanel(spec: SkinSpec, elevated: Boolean = true): Modifier =
    this.then(if (elevated) Modifier.shadow(10.dp, RoundedCornerShape(0.dp), clip = false) else Modifier)
        .background(spec.panel)
        .then(
            if (spec.gta) Modifier
            else
                Modifier.drawBehind {
                  val inset = 4.dp.toPx()
                  drawRect(
                      Rdr.Grey.copy(alpha = 0.35f),
                      topLeft = Offset(inset, inset),
                      size = Size(size.width - 2 * inset, size.height - 2 * inset),
                      style = Stroke(width = 1.dp.toPx()),
                  )
                }
        )

@Composable
fun SkinPanel(spec: SkinSpec, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.skinPanel(spec), content = content)
}

/** Round map button (locate, compass, sound, overview, settings). */
@Composable
fun SkinRoundButton(spec: SkinSpec, icon: GameIcon, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = spec.fg) {
  Box(
      modifier
          .size(52.dp)
          .shadow(8.dp, CircleShape, clip = false)
          .background(if (spec.gta) Color(0xE6000000) else Rdr.OffBlack, CircleShape)
          .then(if (spec.gta) Modifier else Modifier.border(1.5.dp, Rdr.Grey.copy(alpha = 0.7f), CircleShape))
          .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    GameIconView(icon, tint, Modifier.size(26.dp))
  }
}

/** Primary action (Directions, Start): the theme's route colour. */
@Composable
fun SkinPrimaryButton(spec: SkinSpec, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GameIcon? = null) {
  Row(
      modifier
          .height(50.dp)
          .background(spec.accent)
          .then(if (spec.gta) Modifier else Modifier.border(1.5.dp, Rdr.White.copy(alpha = 0.8f)))
          .clickable(onClick = onClick)
          .padding(horizontal = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    icon?.let {
      GameIconView(it, Color.White, Modifier.size(22.dp))
      Spacer(Modifier.width(8.dp))
    }
    SkinText(spec.title(label), spec.title, if (spec.gta) 18.sp else 21.sp, Color.White, spacing = if (spec.gta) 0.sp else 1.sp)
  }
}

@Composable
fun SkinSecondaryButton(spec: SkinSpec, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GameIcon? = null) {
  Row(
      modifier
          .height(50.dp)
          .background(if (spec.gta) Color(0x26FFFFFF) else Color(0x33000000))
          .border(1.dp, spec.fg.copy(alpha = if (spec.gta) 0.25f else 0.6f))
          .clickable(onClick = onClick)
          .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    icon?.let {
      GameIconView(it, spec.fg, Modifier.size(22.dp))
      Spacer(Modifier.width(8.dp))
    }
    SkinText(spec.title(label), spec.title, if (spec.gta) 17.sp else 20.sp, spec.fg, spacing = if (spec.gta) 0.sp else 1.sp)
  }
}

/**
 * A list row (search results, saved places). The highlighted row takes the game's menu cursor:
 * GTA's white bar with black text, or Red Dead's rough off-white frame.
 */
@Composable
fun SkinRow(
    spec: SkinSpec,
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: GameIcon? = null,
    highlighted: Boolean = false,
    trailing: String? = null,
) {
  val fg = if (highlighted && spec.gta) Color.Black else spec.fg
  val sub = if (highlighted && spec.gta) Color(0xFF555555) else spec.sub
  Row(
      Modifier.fillMaxWidth()
          .heightIn(min = 56.dp)
          .then(
              when {
                highlighted && spec.gta -> Modifier.background(Gta.RowSelected)
                highlighted -> Modifier.background(Color(0x40000000)).border(1.5.dp, Rdr.White.copy(alpha = 0.9f))
                else -> Modifier
              }
          )
          .clickable(onClick = onClick)
          .padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    icon?.let {
      GameIconView(it, if (highlighted && spec.gta) Color.Black else spec.sub, Modifier.size(22.dp))
      Spacer(Modifier.width(14.dp))
    }
    Column(Modifier.weight(1f)) {
      SkinText(title, spec.body, 17.sp, fg)
      if (!subtitle.isNullOrBlank()) SkinText(subtitle, spec.body, 14.sp, sub)
    }
    trailing?.let { SkinText(it, spec.body, 14.sp, sub, Modifier.padding(start = 8.dp)) }
  }
}

/** Divider between rows: a hairline for GTA, an engraved rule with a diamond for Red Dead. */
@Composable
fun SkinDivider(spec: SkinSpec, modifier: Modifier = Modifier) {
  Canvas(modifier.fillMaxWidth().height(if (spec.gta) 1.dp else 9.dp)) {
    val y = size.height / 2f
    if (spec.gta) {
      drawLine(Color(0x33FFFFFF), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    } else {
      drawLine(Rdr.Grey.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
      val c = Offset(size.width / 2f, y)
      val r = 3.5.dp.toPx()
      val d = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
      drawPath(d, Rdr.OffBlack)
      drawPath(d, Rdr.Grey, style = Stroke(width = 1.dp.toPx()))
    }
  }
}

/** A small chip under the search bar (Home, Work, Saved). */
@Composable
fun SkinChip(spec: SkinSpec, label: String, icon: GameIcon, onClick: () -> Unit) {
  Row(
      Modifier.height(38.dp)
          .shadow(6.dp, RoundedCornerShape(if (spec.gta) 0.dp else 2.dp), clip = false)
          .background(spec.panel)
          .then(if (spec.gta) Modifier else Modifier.border(1.dp, Rdr.Grey.copy(alpha = 0.5f)))
          .clickable(onClick = onClick)
          .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    GameIconView(icon, spec.fg, Modifier.size(18.dp))
    Spacer(Modifier.width(7.dp))
    SkinText(spec.title(label), spec.title, if (spec.gta) 15.sp else 17.sp, spec.fg, spacing = if (spec.gta) 0.sp else 1.sp)
  }
}

@Composable
fun RowScope.SkinRowSpacer() = Spacer(Modifier.weight(1f))

// ---------------------------------------------------------------- icons

enum class GameIcon {
  SEARCH, SETTINGS, LOCATE, CLOSE, BACK, STAR, STAR_FILLED, HOME, WORK, RECENT, PIN, SOUND_ON, SOUND_OFF,
  ROUTE, CHEVRON_UP, CHEVRON_DOWN, NORTH, NAVIGATE, CAR,
}

/** Simple line icons drawn in code, so they match any theme and stay sharp on the car screen. */
@Composable
fun GameIconView(icon: GameIcon, color: Color, modifier: Modifier = Modifier) {
  Canvas(modifier) { drawGameIcon(icon, color) }
}

fun DrawScope.drawGameIcon(icon: GameIcon, color: Color) {
  val u = size.minDimension / 24f
  fun p(x: Float, y: Float) = Offset(x * u, y * u)
  val w = 2.2f * u
  val line = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
  fun path(vararg pts: Pair<Float, Float>, close: Boolean = false) =
      Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
        if (close) close()
      }
  when (icon) {
    GameIcon.SEARCH -> {
      drawCircle(color, 6.5f * u, p(10f, 10f), style = line)
      drawLine(color, p(15f, 15f), p(20.5f, 20.5f), strokeWidth = w * 1.2f, cap = StrokeCap.Round)
    }
    GameIcon.SETTINGS -> {
      drawCircle(color, 6.3f * u, p(12f, 12f), style = line)
      drawCircle(color, 2.4f * u, p(12f, 12f), style = line)
      for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val c = kotlin.math.cos(a).toFloat()
        val s = kotlin.math.sin(a).toFloat()
        drawLine(color, p(12f + c * 7.4f, 12f + s * 7.4f), p(12f + c * 9.8f, 12f + s * 9.8f), strokeWidth = w * 1.3f, cap = StrokeCap.Square)
      }
    }
    GameIcon.LOCATE -> {
      drawCircle(color, 6f * u, p(12f, 12f), style = line)
      drawCircle(color, 2.4f * u, p(12f, 12f))
      drawLine(color, p(12f, 1.5f), p(12f, 5f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(12f, 19f), p(12f, 22.5f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(1.5f, 12f), p(5f, 12f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(19f, 12f), p(22.5f, 12f), strokeWidth = w, cap = StrokeCap.Round)
    }
    GameIcon.CLOSE -> {
      drawLine(color, p(6f, 6f), p(18f, 18f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(18f, 6f), p(6f, 18f), strokeWidth = w, cap = StrokeCap.Round)
    }
    GameIcon.BACK -> drawPath(path(19f to 12f, 5f to 12f, 11f to 6f, 5f to 12f, 11f to 18f), color, style = line)
    GameIcon.STAR, GameIcon.STAR_FILLED -> {
      val star =
          Path().apply {
            for (i in 0 until 10) {
              val r = if (i % 2 == 0) 9.5f else 4f
              val a = Math.toRadians(-90.0 + i * 36.0)
              val x = 12f + r * kotlin.math.cos(a).toFloat()
              val y = 12.8f + r * kotlin.math.sin(a).toFloat()
              if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u)
            }
            close()
          }
      if (icon == GameIcon.STAR_FILLED) drawPath(star, color) else drawPath(star, color, style = line)
    }
    GameIcon.HOME -> {
      drawPath(path(3f to 11.5f, 12f to 3.5f, 21f to 11.5f), color, style = line)
      drawPath(path(5.5f to 10f, 5.5f to 20.5f, 18.5f to 20.5f, 18.5f to 10f), color, style = line)
      drawPath(path(10f to 20.5f, 10f to 14.5f, 14f to 14.5f, 14f to 20.5f), color, style = line)
    }
    GameIcon.WORK -> {
      drawRoundRect(color, p(3f, 8f), Size(18f * u, 12f * u), style = line)
      drawPath(path(8.5f to 8f, 8.5f to 4.5f, 15.5f to 4.5f, 15.5f to 8f), color, style = line)
      drawLine(color, p(3f, 13f), p(21f, 13f), strokeWidth = w * 0.8f)
    }
    GameIcon.RECENT -> {
      drawCircle(color, 8.5f * u, p(12f, 12f), style = line)
      drawPath(path(12f to 7f, 12f to 12f, 15.5f to 14.5f), color, style = line)
    }
    GameIcon.PIN -> {
      val pin =
          Path().apply {
            moveTo(12f * u, 22f * u)
            cubicTo(6f * u, 15f * u, 4.5f * u, 12f * u, 4.5f * u, 9.5f * u)
            cubicTo(4.5f * u, 5f * u, 8f * u, 2f * u, 12f * u, 2f * u)
            cubicTo(16f * u, 2f * u, 19.5f * u, 5f * u, 19.5f * u, 9.5f * u)
            cubicTo(19.5f * u, 12f * u, 18f * u, 15f * u, 12f * u, 22f * u)
            close()
          }
      drawPath(pin, color, style = line)
      drawCircle(color, 2.6f * u, p(12f, 9.5f))
    }
    GameIcon.SOUND_ON, GameIcon.SOUND_OFF -> {
      drawPath(path(3f to 9f, 7.5f to 9f, 12.5f to 4.5f, 12.5f to 19.5f, 7.5f to 15f, 3f to 15f, close = true), color)
      if (icon == GameIcon.SOUND_ON) {
        drawArc(color, -50f, 100f, false, p(9f, 7f), Size(8f * u, 10f * u), style = line)
        drawArc(color, -55f, 110f, false, p(9f, 3.5f), Size(12.5f * u, 17f * u), style = line)
      } else {
        drawLine(color, p(15.5f, 9f), p(21.5f, 15f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, p(21.5f, 9f), p(15.5f, 15f), strokeWidth = w, cap = StrokeCap.Round)
      }
    }
    GameIcon.ROUTE -> {
      drawCircle(color, 2.6f * u, p(6f, 19f))
      drawCircle(color, 2.6f * u, p(18f, 5f))
      drawPath(path(6f to 16f, 6f to 12f, 18f to 12f, 18f to 8f), color, style = line)
    }
    GameIcon.CHEVRON_UP -> drawPath(path(5f to 15f, 12f to 8f, 19f to 15f), color, style = line)
    GameIcon.CHEVRON_DOWN -> drawPath(path(5f to 9f, 12f to 16f, 19f to 9f), color, style = line)
    GameIcon.NORTH -> {
      drawPath(path(12f to 3f, 17f to 20f, 12f to 16f, 7f to 20f, close = true), color, style = line)
      drawPath(path(12f to 3f, 12f to 16f, 7f to 20f, close = true), color)
    }
    GameIcon.NAVIGATE -> drawPath(path(12f to 2.5f, 20f to 21f, 12f to 16.5f, 4f to 21f, close = true), color)
    GameIcon.CAR -> {
      drawPath(path(3f to 16f, 3f to 12f, 6f to 6f, 18f to 6f, 21f to 12f, 21f to 16f, close = true), color, style = line)
      drawCircle(color, 1.8f * u, p(7.5f, 16.5f))
      drawCircle(color, 1.8f * u, p(16.5f, 16.5f))
    }
  }
}
