package com.thealgothrim.overworld.ui.algo

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.R
import com.thealgothrim.overworld.ui.vi.outerShadow

/**
 * Algothrim's interface: Gaurav's own world (30 Nov 2026), drawn from his portfolio,
 * thealgothrim.com. A black stage lit in crimson (#C9183C): near-black panels with a faint white
 * hairline, rounded like the site's cards and fully round like its buttons, crimson for what
 * matters and a soft crimson glow behind the page the way the site's hero is lit. Titles in
 * Unbounded (the site's headings), everything else in Montserrat (its body text).
 */
object Algo {
  val White = Color(0xFFF2F2F2)
  /** The site's body text, hsl(0, 0%, 75%). */
  val Sub = Color(0xFFBFBFBF)
  val Faint = Color(0xFF8C8C8C)
  val Crimson = Color(0xFFC9183C)
  val CrimsonDeep = Color(0xFFA01330)
  /** Crimson for small text: #C9183C only passes as large text on black (the site's own rule). */
  val CrimsonText = Color(0xFFEC4D6E)
  /** Near-black panels, the site's container colour (hsl(0, 0%, 3%)), nearly solid. */
  val Panel = Color(0xF20A0A0B)
  val Raised = Color(0xFF16161A)
  val Black = Color(0xFF000000)
  val Hairline = Color(0x1AFFFFFF)

  /** The site's cards are rounded; its buttons are fully round. */
  val corner = 18.dp

  /** Unbounded: the site's headings ("Gaurav Kumar", "Creative Vault"). */
  val display = FontFamily(Font(R.font.unbounded_semibold, FontWeight.SemiBold))
  val displayBold = FontFamily(Font(R.font.unbounded_bold, FontWeight.Bold))

  /** Montserrat: the site's body text. */
  val sans =
      FontFamily(
          Font(R.font.montserrat_regular, FontWeight.Normal),
          Font(R.font.montserrat_medium, FontWeight.Medium),
          Font(R.font.montserrat_semibold, FontWeight.SemiBold),
          Font(R.font.montserrat_bold, FontWeight.Bold),
      )
  val sansMedium = FontFamily(Font(R.font.montserrat_medium, FontWeight.Medium))
  val sansSemiBold = FontFamily(Font(R.font.montserrat_semibold, FontWeight.SemiBold))

  /** The selected row: crimson that holds, then fades into the panel, like "Creative" on the site. */
  val Selected = Brush.horizontalGradient(0f to Crimson, 0.6f to Color(0xFFB0153A), 1f to Color(0x00A01330))

  /** The shared HUD pieces (turn card, progress, "Arrived") in Algothrim's colours and type. */
  val look =
      com.thealgothrim.overworld.ui.vi.RoundLook(
          fg = White, sub = Sub, tile = Crimson, onTile = Color.White, heavy = displayBold, text = sansMedium, street = sansSemiBold,
          label = sansSemiBold, upperLabels = false, big = displayBold, bigSpacing = 0.sp, upperBig = false,
          band = Color(0xD9000000), glow = Crimson, track = Color(0x26FFFFFF),
      )

  /** The menus' backdrop: black, lit from the top corner by a soft crimson glow (the hero's light). */
  val Stage =
      Brush.radialGradient(
          0f to Color(0x66C9183C),
          0.45f to Color(0x1FC9183C),
          1f to Color.Transparent,
          center = Offset(0f, 0f),
          radius = 1400f,
      )
}

/** An Algothrim panel: rounded near-black with a hairline edge and a soft shadow. */
fun Modifier.algoGlass(corner: Dp = Algo.corner, elevated: Boolean = true, color: Color = Algo.Panel): Modifier {
  val shape = RoundedCornerShape(corner)
  return this.then(if (elevated) Modifier.outerShadow(shape, color = Color(0x80000000)) else Modifier)
      .clip(shape)
      .background(color)
      .border(1.dp, Algo.Hairline, shape)
}

/**
 * The thin crimson line under the site's navigation pill: a short glowing stroke along the bottom
 * edge, brightest in the middle. For the piece that's active (the turn card, the open menu).
 */
fun Modifier.algoUnderline(): Modifier =
    drawBehind {
      val y = size.height - 1.dp.toPx()
      drawLine(
          Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Algo.Crimson, 1f to Color.Transparent),
          Offset(size.width * 0.12f, y),
          Offset(size.width * 0.88f, y),
          strokeWidth = 1.5.dp.toPx(),
      )
    }

// ---------------------------------------------------------------- menu

/**
 * A menu's title as the site sets its section headings: two tones, the first word in white
 * Unbounded and the second in crimson ("Creative Vault", "My Work"). One-word titles take a small
 * crimson line under them instead.
 */
@Composable
fun AlgoMenuHeader(title: String, subtitle: String) {
  Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp)) {
    Text(subtitle, color = Algo.CrimsonText, fontFamily = Algo.display, fontSize = 15.sp, maxLines = 1)
    Spacer(Modifier.height(2.dp))
    Text(title, color = Algo.White, fontFamily = Algo.display, fontSize = 34.sp, lineHeight = 38.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(10.dp))
    Box(Modifier.width(44.dp).height(3.dp).background(Algo.Crimson, RoundedCornerShape(2.dp)))
  }
}

/**
 * A menu row: flat on the page with a hairline under it; the selected one is the crimson bar with
 * white text, its value framed "‹ › " like the other worlds'. On/off settings use the crimson switch
 * with its word beside it.
 */
@Composable
fun AlgoMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    checked: Boolean? = null,
    labelColor: Color? = null,
) {
  Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth()
            .height(52.dp)
            .then(if (selected) Modifier.clip(RoundedCornerShape(12.dp)).background(Algo.Selected) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          label,
          color = labelColor ?: Algo.White,
          fontFamily = Algo.sans,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
          fontSize = 16.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      value?.let {
        Text(
            if (selected) "‹  $it  ›" else it,
            color = if (selected) Algo.White else Algo.Sub,
            fontFamily = Algo.sans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 190.dp),
        )
      }
      checked?.let {
        Text(if (it) "On" else "Off", color = if (selected) Algo.White else Algo.Sub, fontFamily = Algo.sans, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        AlgoSwitch(it, onCrimson = selected)
      }
    }
    if (!selected) Box(Modifier.fillMaxWidth().height(1.dp).background(Algo.Hairline))
  }
}

/** The switch: a crimson pill with a white knob when on (white on a crimson row), outlined grey when off. */
@Composable
fun AlgoSwitch(on: Boolean, modifier: Modifier = Modifier, onCrimson: Boolean = false) {
  Canvas(modifier.size(width = 40.dp, height = 24.dp)) {
    val r = CornerRadius(size.height / 2f)
    val knob = size.height * 0.36f
    if (on) {
      drawRoundRect(if (onCrimson) Color.White else Algo.Crimson, cornerRadius = r)
      drawCircle(if (onCrimson) Algo.Crimson else Color.White, knob, Offset(size.width - size.height / 2f, size.height / 2f))
    } else {
      drawRoundRect(Color(0x1FFFFFFF), cornerRadius = r)
      drawRoundRect(Algo.Sub, cornerRadius = r, style = Stroke(width = 1.5.dp.toPx()))
      drawCircle(Algo.Sub, knob, Offset(size.height / 2f, size.height / 2f))
    }
  }
}

/** The description under a menu: a near-black card in Montserrat. */
@Composable
fun AlgoDescription(text: String, color: Color = Algo.Sub) {
  Box(Modifier.padding(top = 8.dp).fillMaxWidth().algoGlass(corner = 14.dp, elevated = false, color = Algo.Raised).padding(14.dp)) {
    Text(text, color = color, fontFamily = Algo.sans, fontSize = 14.sp, lineHeight = 20.sp)
  }
}

// ---------------------------------------------------------------- prompts

data class AlgoPrompt(val key: String, val label: String, val onClick: () -> Unit)

/**
 * Prompts bottom right, as the site's secondary buttons: a dark pill with a grey edge and the word in
 * Montserrat, the key in a small crimson disc.
 */
@Composable
fun AlgoPrompts(prompts: List<AlgoPrompt>, modifier: Modifier = Modifier) {
  Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    prompts.forEach { p ->
      Row(
          Modifier.clip(RoundedCornerShape(percent = 50))
              .background(Algo.Panel)
              .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(percent = 50))
              .clickable(onClick = p.onClick)
              .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(p.label, color = Algo.White, fontFamily = Algo.sansSemiBold, fontSize = 15.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(26.dp).background(Algo.Crimson, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
          Text(p.key, color = Color.White, fontFamily = Algo.sansSemiBold, fontSize = 12.sp)
        }
      }
    }
  }
}

// ---------------------------------------------------------------- stage

/** The black stage with the crimson glow from the top corner, behind a full-screen menu. */
@Composable
fun AlgoStage(modifier: Modifier = Modifier) {
  Box(modifier.fillMaxSize().background(Algo.Black).background(Algo.Stage))
}
