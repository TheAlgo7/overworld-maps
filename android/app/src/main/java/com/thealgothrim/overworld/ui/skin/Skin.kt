package com.thealgothrim.overworld.ui.skin

import com.thealgothrim.overworld.search.Nearby
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.theme.GameFonts
import com.thealgothrim.overworld.theme.OverworldTheme
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.vi.Vi
import com.thealgothrim.overworld.ui.vi.outerShadow
import com.thealgothrim.overworld.ui.vi.viGlass

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
  /** GTA V. GTA VI ([vi]) and Red Dead ([rdr]) each have their own look. */
  val gta get() = skin == Skin.GTA
  val vi get() = skin == Skin.GTA6
  val rdr get() = skin == Skin.RDR

  /** Title letter spacing: Red Dead's engraved capitals spread out, GTA VI's condensed ones a little. */
  val titleSpacing: TextUnit
    get() =
        when (skin) {
          Skin.RDR -> 1.sp
          Skin.GTA6 -> 0.6.sp
          Skin.GTA -> 0.sp
        }

  /** Title sizes are set for GTA's fonts; Red Dead's serif reads smaller, so it goes up a size. */
  fun titleSize(gta: TextUnit, rdr: TextUnit): TextUnit = if (this.rdr) rdr else gta

  fun title(text: String) = if (upperTitles) text.uppercase() else text

  /** Text on the accent colour: GTA VI's dark ink on its pink, white on the others' purple and red. */
  val onAccent: Color
    get() = if (vi) Vi.Ink else Color.White
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
        Skin.GTA6 ->
            SkinSpec(
                skin, Vi.White, Vi.Sub, hudAccent, Vi.PinkDeep, hudGood, Vi.Glass,
                title = Vi.condensed, body = Vi.sans, big = Vi.condensedBold, number = Vi.condensed,
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

/**
 * A HUD panel: square black box for GTA V, ink with an engraved inner rule for Red Dead, rounded
 * dark glass for GTA VI.
 */
fun Modifier.skinPanel(spec: SkinSpec, elevated: Boolean = true): Modifier =
    if (spec.vi) this.viGlass(elevated = elevated)
    else this.then(if (elevated) Modifier.shadow(10.dp, RoundedCornerShape(0.dp), clip = false) else Modifier)
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

/**
 * Room around a bottom sheet: GTA V's and Red Dead's run edge to edge; GTA VI's float off the
 * edges like its top card, rounded all round.
 */
fun Modifier.sheetFloat(spec: SkinSpec): Modifier = if (spec.vi) this.padding(start = 12.dp, end = 12.dp, bottom = 12.dp) else this

@Composable
fun SkinPanel(spec: SkinSpec, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.skinPanel(spec), content = content)
}

/** Round map button (locate, compass, sound, overview, settings). */
@Composable
fun SkinRoundButton(spec: SkinSpec, icon: GameIcon, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = spec.fg) {
  Box(
      modifier.size(52.dp).mapButtonFace(spec, CircleShape).clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
  ) {
    GameIconView(icon, tint, Modifier.size(26.dp))
  }
}

/**
 * A map button with its word beside the icon, in the round buttons' solid face (Re-centre, like
 * Google Maps' pill): a word reads at a glance where a lone icon in the corner didn't.
 */
@Composable
fun SkinPillButton(spec: SkinSpec, icon: GameIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(percent = 50)
  Row(
      modifier.height(52.dp).mapButtonFace(spec, shape).clickable(onClick = onClick).padding(start = 18.dp, end = 22.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    GameIconView(icon, spec.fg, Modifier.size(24.dp))
    Spacer(Modifier.width(10.dp))
    SkinText(spec.title(label), spec.title, spec.titleSize(if (spec.vi) 19.sp else 18.sp, 21.sp), spec.fg, spacing = spec.titleSpacing)
  }
}

/** The player's own marker (his art for each game, the one drawn on the map). */
val SkinSpec.puck: Int
  get() =
      when (skin) {
        Skin.GTA -> com.thealgothrim.overworld.R.drawable.puck_gta5
        Skin.RDR -> com.thealgothrim.overworld.R.drawable.puck_rdr2
        Skin.GTA6 -> com.thealgothrim.overworld.R.drawable.puck_gta6
      }

/**
 * Re-centre: the player's own marker on the map buttons' face, ringed in the route colour, so it
 * reads as "back to me" at a glance where a crosshair in the corner didn't. With [label], a pill
 * (the car, and the phone on a trip); without, a round button. [size] is the button's height.
 */
@Composable
fun SkinRecenterButton(spec: SkinSpec, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, size: Dp = 52.dp) {
  val shape = if (label != null) RoundedCornerShape(percent = 50) else CircleShape
  val ring = (size.value / 20f).coerceAtLeast(2f).dp
  Row(
      modifier
          .height(size)
          .then(if (label == null) Modifier.width(size) else Modifier)
          .mapButtonFace(spec, shape)
          .border(ring, spec.accent, shape)
          .clickable(onClick = onClick)
          .padding(start = if (label != null) size * 0.22f else 0.dp, end = if (label != null) size * 0.4f else 0.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = if (label == null) Arrangement.Center else Arrangement.Start,
  ) {
    Image(
        ImageBitmap.imageResource(spec.puck),
        contentDescription = label ?: "Re-centre",
        modifier = Modifier.size(size * 0.56f),
        filterQuality = FilterQuality.High,
    )
    if (label != null) {
      Spacer(Modifier.width(size * 0.18f))
      SkinText(spec.title(label), spec.title, spec.titleSize(if (spec.vi) 19.sp else 18.sp, 21.sp), spec.fg, spacing = spec.titleSpacing)
    }
  }
}

/** The solid face the map's own buttons share, per game. */
private fun Modifier.mapButtonFace(spec: SkinSpec, shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.then(if (spec.vi) Modifier.outerShadow(shape, blur = 6.dp, drop = 1.5.dp) else Modifier.shadow(8.dp, shape, clip = false))
        .background(
            when (spec.skin) {
              Skin.GTA -> Color(0xE6000000)
              Skin.GTA6 -> Vi.Glass
              Skin.RDR -> Rdr.OffBlack
            },
            shape,
        )
        .then(
            when (spec.skin) {
              Skin.GTA -> Modifier
              Skin.GTA6 -> Modifier.border(1.dp, Vi.Hairline, shape)
              Skin.RDR -> Modifier.border(1.5.dp, Rdr.Grey.copy(alpha = 0.7f), shape)
            }
        )
        .clip(shape)

/** Primary action (Directions, Start): the theme's route colour. GTA VI's pink takes dark text. */
@Composable
fun SkinPrimaryButton(spec: SkinSpec, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GameIcon? = null) {
  val ink = if (spec.vi) Vi.Ink else Color.White
  Row(
      modifier
          .height(50.dp)
          .then(
              when (spec.skin) {
                Skin.GTA -> Modifier.background(spec.accent)
                Skin.GTA6 -> Modifier.clip(RoundedCornerShape(12.dp)).background(spec.accent)
                Skin.RDR -> Modifier.background(spec.accent).border(1.5.dp, Rdr.White.copy(alpha = 0.8f))
              }
          )
          .clickable(onClick = onClick)
          .padding(horizontal = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    icon?.let {
      GameIconView(it, ink, Modifier.size(22.dp))
      Spacer(Modifier.width(8.dp))
    }
    ButtonLabel(spec, label, spec.titleSize(if (spec.vi) 20.sp else 18.sp, 21.sp), ink)
  }
}

@Composable
fun SkinSecondaryButton(spec: SkinSpec, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GameIcon? = null) {
  Row(
      modifier
          .height(50.dp)
          .then(
              when (spec.skin) {
                Skin.GTA -> Modifier.background(Color(0x26FFFFFF)).border(1.dp, spec.fg.copy(alpha = 0.25f))
                Skin.GTA6 -> Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                Skin.RDR -> Modifier.background(Color(0x33000000)).border(1.dp, spec.fg.copy(alpha = 0.6f))
              }
          )
          .clickable(onClick = onClick)
          .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    icon?.let {
      GameIconView(it, spec.fg, Modifier.size(22.dp))
      Spacer(Modifier.width(8.dp))
    }
    ButtonLabel(spec, label, spec.titleSize(if (spec.vi) 19.sp else 17.sp, 20.sp), spec.fg)
  }
}

/**
 * A button's label, a size smaller rather than cut short when the button is narrow: Red Dead's wide
 * type left "SHOW ON …" on Timeline's half-width button (8 Nov sweep).
 */
@Composable
private fun ButtonLabel(spec: SkinSpec, label: String, size: TextUnit, color: Color) =
    SkinFitText(spec.title(label), spec.title, size, color, spacing = spec.titleSpacing)

/** One line that steps down in size to fit its width (to [minSize]) instead of ending in "…". */
@Composable
fun SkinFitText(
    text: String,
    font: FontFamily,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: TextUnit = TextUnit.Unspecified,
    minSize: TextUnit = 11.sp,
) {
  androidx.compose.foundation.text.BasicText(
      text,
      modifier,
      softWrap = false,
      maxLines = 1,
      style = TextStyle(fontFamily = font, color = color, letterSpacing = spacing),
      autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = size, stepSize = 0.5.sp),
  )
}

/**
 * A list row (search results, saved places). The highlighted row takes the game's menu cursor:
 * GTA V's white bar with black text, Red Dead's rough off-white frame, or GTA VI's raised glass
 * with a pink edge.
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
                highlighted && spec.vi ->
                    Modifier.background(Vi.GlassRaised).drawBehind { drawRect(Vi.Pink, size = Size(3.dp.toPx(), size.height)) }
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

/**
 * Divider between rows: a hairline for GTA (inset on GTA VI's glass), an engraved rule with a
 * diamond for Red Dead.
 */
@Composable
fun SkinDivider(spec: SkinSpec, modifier: Modifier = Modifier) {
  Canvas(modifier.fillMaxWidth().height(if (spec.rdr) 9.dp else 1.dp)) {
    val y = size.height / 2f
    if (spec.vi) {
      val inset = 14.dp.toPx()
      drawLine(Color(0x1FFFFFFF), Offset(inset, y), Offset(size.width - inset, y), strokeWidth = 1.dp.toPx())
    } else if (spec.gta) {
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
          .then(
              when (spec.skin) {
                Skin.GTA -> Modifier.shadow(6.dp, RoundedCornerShape(0.dp), clip = false).background(spec.panel)
                Skin.GTA6 -> Modifier.viGlass(corner = 19.dp)
                Skin.RDR -> Modifier.shadow(6.dp, RoundedCornerShape(2.dp), clip = false).background(spec.panel).border(1.dp, Rdr.Grey.copy(alpha = 0.5f))
              }
          )
          .clickable(onClick = onClick)
          .padding(horizontal = if (spec.vi) 14.dp else 12.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    GameIconView(icon, spec.fg, Modifier.size(18.dp))
    Spacer(Modifier.width(7.dp))
    SkinText(spec.title(label), spec.title, spec.titleSize(if (spec.vi) 17.sp else 15.sp, 17.sp), spec.fg, spacing = spec.titleSpacing)
  }
}

@Composable
fun RowScope.SkinRowSpacer() = Spacer(Modifier.weight(1f))

// ---------------------------------------------------------------- icons

enum class GameIcon {
  SEARCH, SETTINGS, LOCATE, CLOSE, BACK, STAR, STAR_FILLED, HOME, WORK, RECENT, PIN, SOUND_ON, SOUND_OFF,
  ROUTE, OVERVIEW, CHEVRON_UP, CHEVRON_DOWN, NORTH, NAVIGATE, CAR, LAYERS,
  FUEL, FOOD, PARKING, TOILET, HOSPITAL, HOTEL, CAMERA, ZOOM_IN, ZOOM_OUT,
  /** "Mark this moment" for a problem report, and the Want to go list. */
  FLAG,
  // In the generated sets already (tools/make_icons.py names them so): drawn in code without them.
  EDIT, DELETE, SHARE, NEARBY, SPEEDOMETER, ADD_STOP, CHARGING, CNG,
  // Saved lists, Timeline, travel modes and Settings (0.6). Batches 11-12 of each world's icon chat
  // (Ongoing Apps Assets\Overworld Maps\Icon Generation) bring their art; until then, drawn in code.
  HEART, SUITCASE, LABEL, LIST, NOTE, TIMELINE, CALENDAR, BIKE, WALK, OFFLINE, DOWNLOAD, BELL, SHIELD, INFO, STATS,
  /** You: a person (13 Nov). The player's arrow was the re-centre button's too, twice on one screen. */
  PROFILE,
}

/**
 * The icon for a saved list: Google's four defaults have their own; yours show the game icon you
 * picked (stored by name in the list's `emoji` field, kept for old saves), else a list. Emoji
 * looked pasted onto the game menus (his call, 9 Nov), so an old emoji also shows as a list.
 */
val com.thealgothrim.overworld.search.SavedList.icon: GameIcon
  get() =
      when (kind) {
        com.thealgothrim.overworld.search.ListKind.FAVOURITES -> GameIcon.HEART
        com.thealgothrim.overworld.search.ListKind.WANT_TO_GO -> GameIcon.FLAG
        com.thealgothrim.overworld.search.ListKind.TRAVEL_PLANS -> GameIcon.SUITCASE
        com.thealgothrim.overworld.search.ListKind.STARRED -> GameIcon.STAR_FILLED
        com.thealgothrim.overworld.search.ListKind.CUSTOM -> emoji?.let { e -> GameIcon.entries.firstOrNull { it.name == e } } ?: GameIcon.LIST
      }

/** The pictures your own lists can take, all from the world's icon set. */
val LIST_ICONS =
    listOf(
        GameIcon.LIST, GameIcon.FOOD, GameIcon.HOTEL, GameIcon.FUEL, GameIcon.PARKING, GameIcon.HOSPITAL,
        GameIcon.HOME, GameIcon.WORK, GameIcon.CAR, GameIcon.BIKE, GameIcon.WALK, GameIcon.NOTE, GameIcon.CALENDAR, GameIcon.LABEL,
    )

/** The icon for a travel mode. */
val com.thealgothrim.overworld.routing.TravelMode.icon: GameIcon
  get() =
      when (this) {
        com.thealgothrim.overworld.routing.TravelMode.CAR -> GameIcon.CAR
        com.thealgothrim.overworld.routing.TravelMode.BIKE -> GameIcon.BIKE
        com.thealgothrim.overworld.routing.TravelMode.WALK -> GameIcon.WALK
      }

/** The icon for a kind of nearby place. */
val Nearby.icon: GameIcon
  get() =
      when (this) {
        Nearby.FUEL -> if (com.thealgothrim.overworld.AppModule.prefs.engine.value == com.thealgothrim.overworld.prefs.EngineType.CNG) GameIcon.CNG else GameIcon.FUEL
        Nearby.CHARGING -> GameIcon.CHARGING
        Nearby.FOOD -> GameIcon.FOOD
        Nearby.PARKING -> GameIcon.PARKING
        Nearby.TOILETS -> GameIcon.TOILET
        Nearby.HOSPITAL -> GameIcon.HOSPITAL
        Nearby.HOTEL -> GameIcon.HOTEL
      }

/**
 * The game's own icon from its generated set where a build has it ([IconArt]), tinted by [color]:
 * its white takes the colour and its black edge stays. Else simple line icons drawn in code, so
 * they match any theme and stay sharp on the car screen.
 */
@Composable
fun GameIconView(icon: GameIcon, color: Color, modifier: Modifier = Modifier) {
  val skin = LocalIconSkin.current
  val art = remember(skin, icon) { skin?.let { IconArt.get(it, icon) } }
  if (art != null) {
    Image(art, contentDescription = null, modifier = modifier, colorFilter = ColorFilter.tint(color, BlendMode.Modulate), filterQuality = FilterQuality.High)
  } else {
    Canvas(modifier) { drawGameIcon(icon, color) }
  }
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
    GameIcon.ZOOM_IN -> {
      drawLine(color, p(5f, 12f), p(19f, 12f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(12f, 5f), p(12f, 19f), strokeWidth = w, cap = StrokeCap.Round)
    }
    GameIcon.ZOOM_OUT -> drawLine(color, p(5f, 12f), p(19f, 12f), strokeWidth = w, cap = StrokeCap.Round)
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
    GameIcon.PROFILE -> {
      // Head and shoulders, filled, like the account picture Google puts in this spot.
      drawCircle(color, 4.4f * u, p(12f, 8f))
      val shoulders =
          Path().apply {
            moveTo(3.5f * u, 21f * u)
            cubicTo(3.5f * u, 16f * u, 7.5f * u, 13.6f * u, 12f * u, 13.6f * u)
            cubicTo(16.5f * u, 13.6f * u, 20.5f * u, 16f * u, 20.5f * u, 21f * u)
            close()
          }
      drawPath(shoulders, color)
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
    // Drawn alike; the generated sets give the overview its own (a route in a frame).
    GameIcon.ROUTE, GameIcon.OVERVIEW -> {
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
    GameIcon.LAYERS -> {
      // Two stacked map sheets, like Google Maps' layers button.
      drawPath(path(12f to 3.5f, 21f to 8.5f, 12f to 13.5f, 3f to 8.5f, close = true), color, style = line)
      drawPath(path(3f to 12.5f, 12f to 17.5f, 21f to 12.5f), color, style = line)
      drawPath(path(3f to 16.5f, 12f to 21.5f, 21f to 16.5f), color, style = line)
    }
    GameIcon.CAR -> {
      drawPath(path(3f to 16f, 3f to 12f, 6f to 6f, 18f to 6f, 21f to 12f, 21f to 16f, close = true), color, style = line)
      drawCircle(color, 1.8f * u, p(7.5f, 16.5f))
      drawCircle(color, 1.8f * u, p(16.5f, 16.5f))
    }
    GameIcon.FUEL -> {
      // A pump with its window and the hose curling down from the side.
      drawPath(path(4f to 21f, 4f to 4.5f, 13f to 4.5f, 13f to 21f, close = true), color, style = line)
      drawLine(color, p(2.5f, 21f), p(14.5f, 21f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(6.5f to 7f, 10.5f to 7f, 10.5f to 10.5f, 6.5f to 10.5f, close = true), color)
      drawPath(path(13f to 9f, 17f to 9f, 18.5f to 11f, 18.5f to 17.5f, 20f to 18.5f, 20f to 7.5f, 17.5f to 5f), color, style = line)
    }
    GameIcon.FOOD -> {
      // Fork and knife.
      drawLine(color, p(7.5f, 3f), p(7.5f, 21f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(4.5f to 3f, 4.5f to 8.5f, 10.5f to 8.5f, 10.5f to 3f), color, style = line)
      drawPath(path(16.5f to 21f, 16.5f to 3f, 19.5f to 6f, 19.5f to 12f, 16.5f to 12f), color, style = line)
    }
    GameIcon.PARKING -> {
      drawRoundRect(color, p(3f, 3f), Size(18f * u, 18f * u), CornerRadius(3f * u), style = line)
      drawPath(path(9.5f to 17.5f, 9.5f to 7f, 13f to 7f), color, style = line)
      drawArc(color, -90f, 180f, false, p(10.5f, 7f), Size(5f * u, 5f * u), style = line)
      drawLine(color, p(9.5f, 12f), p(13f, 12f), strokeWidth = w, cap = StrokeCap.Round)
    }
    GameIcon.TOILET -> {
      // Two figures, as on Indian public toilet signs.
      drawCircle(color, 2f * u, p(7f, 4.5f))
      drawPath(path(4.5f to 21f, 4.5f to 9f, 9.5f to 9f, 9.5f to 21f), color, style = line)
      drawCircle(color, 2f * u, p(17f, 4.5f))
      drawPath(path(14.5f to 15.5f, 17f to 8.5f, 19.5f to 15.5f, close = true), color, style = line)
      drawLine(color, p(17f, 15.5f), p(17f, 21f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(12f, 3f), p(12f, 21f), strokeWidth = w * 0.7f, cap = StrokeCap.Round)
    }
    GameIcon.HOSPITAL -> {
      drawRoundRect(color, p(3f, 3f), Size(18f * u, 18f * u), CornerRadius(3f * u), style = line)
      drawLine(color, p(12f, 7f), p(12f, 17f), strokeWidth = w * 1.3f, cap = StrokeCap.Round)
      drawLine(color, p(7f, 12f), p(17f, 12f), strokeWidth = w * 1.3f, cap = StrokeCap.Round)
    }
    GameIcon.HOTEL -> {
      // A bed: headboard, mattress, pillow.
      drawPath(path(3f to 19f, 3f to 6f), color, style = line)
      drawPath(path(3f to 14f, 21f to 14f, 21f to 19f), color, style = line)
      drawPath(path(3f to 11f, 21f to 11f, 21f to 14f), color, style = line)
      drawCircle(color, 1.8f * u, p(7f, 8.5f))
    }
    GameIcon.FLAG -> {
      // A pennant on its pole.
      drawLine(color, p(6f, 3f), p(6f, 21f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(6f to 4f, 19f to 8f, 6f to 12.5f, close = true), color)
    }
    GameIcon.CAMERA -> {
      // A speed camera on its post.
      drawRoundRect(color, p(3f, 6f), Size(13f * u, 8f * u), CornerRadius(1.5f * u), style = line)
      drawPath(path(16f to 8f, 21f to 6.5f, 21f to 13.5f, 16f to 12f), color, style = line)
      drawLine(color, p(9.5f, 14f), p(9.5f, 21f), strokeWidth = w, cap = StrokeCap.Round)
      drawCircle(color, 1.4f * u, p(7f, 10f))
    }
    GameIcon.EDIT -> {
      drawPath(path(5f to 19f, 5.5f to 15f, 16f to 4.5f, 19.5f to 8f, 9f to 18.5f, close = true), color, style = line)
      drawLine(color, p(13.5f, 7f), p(17f, 10.5f), strokeWidth = w * 0.8f)
    }
    GameIcon.DELETE -> {
      drawLine(color, p(4f, 6.5f), p(20f, 6.5f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(9f to 6.5f, 9f to 3.5f, 15f to 3.5f, 15f to 6.5f), color, style = line)
      drawPath(path(6f to 6.5f, 7.2f to 21f, 16.8f to 21f, 18f to 6.5f), color, style = line)
    }
    GameIcon.SHARE -> {
      drawPath(path(8f to 9f, 5f to 9f, 5f to 21f, 19f to 21f, 19f to 9f, 16f to 9f), color, style = line)
      drawLine(color, p(12f, 2.5f), p(12f, 14.5f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(8f to 6.5f, 12f to 2.5f, 16f to 6.5f), color, style = line)
    }
    GameIcon.NEARBY -> {
      drawCircle(color, 2.6f * u, p(12f, 12f))
      drawCircle(color, 6f * u, p(12f, 12f), style = line)
      drawCircle(color, 9.5f * u, p(12f, 12f), style = Stroke(width = w * 0.7f))
    }
    GameIcon.SPEEDOMETER -> {
      drawArc(color, 180f, 180f, false, p(3f, 5f), Size(18f * u, 18f * u), style = line)
      drawLine(color, p(12f, 14f), p(17f, 8.5f), strokeWidth = w, cap = StrokeCap.Round)
      drawCircle(color, 1.8f * u, p(12f, 14f))
    }
    GameIcon.ADD_STOP -> {
      drawPath(path(9f to 21f, 3.5f to 13f, 3.5f to 8f, 6f to 4f, 12f to 4f, 14.5f to 8f, 14.5f to 13f, close = true), color, style = line)
      drawLine(color, p(19f, 8f), p(19f, 16f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(15f, 12f), p(23f, 12f), strokeWidth = w, cap = StrokeCap.Round)
    }
    GameIcon.CHARGING -> {
      // A plug with a bolt.
      drawRoundRect(color, p(6f, 7f), Size(12f * u, 9f * u), CornerRadius(2f * u), style = line)
      drawLine(color, p(9.5f, 3f), p(9.5f, 7f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(14.5f, 3f), p(14.5f, 7f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(12f, 16f), p(12f, 21f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(12.8f to 8.5f, 10.5f to 12f, 13.5f to 12f, 11.2f to 15f), color, style = Stroke(width = w * 0.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    GameIcon.CNG -> {
      // A gas cylinder with a small flame.
      drawRoundRect(color, p(7f, 6f), Size(10f * u, 16f * u), CornerRadius(4f * u), style = line)
      drawLine(color, p(10f, 3f), p(14f, 3f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(12f, 3f), p(12f, 6f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(12f to 11f, 14f to 14.5f, 12f to 18f, 10f to 14.5f, close = true), color)
    }
    GameIcon.HEART -> {
      val heart =
          Path().apply {
            moveTo(12f * u, 20.5f * u)
            cubicTo(4f * u, 14.5f * u, 2.5f * u, 11f * u, 2.5f * u, 8f * u)
            cubicTo(2.5f * u, 5f * u, 5f * u, 3f * u, 7.5f * u, 3f * u)
            cubicTo(9.5f * u, 3f * u, 11f * u, 4.2f * u, 12f * u, 6f * u)
            cubicTo(13f * u, 4.2f * u, 14.5f * u, 3f * u, 16.5f * u, 3f * u)
            cubicTo(19f * u, 3f * u, 21.5f * u, 5f * u, 21.5f * u, 8f * u)
            cubicTo(21.5f * u, 11f * u, 20f * u, 14.5f * u, 12f * u, 20.5f * u)
            close()
          }
      drawPath(heart, color)
    }
    GameIcon.SUITCASE -> {
      drawRoundRect(color, p(3f, 7.5f), Size(18f * u, 13f * u), CornerRadius(2f * u), style = line)
      drawPath(path(9f to 7.5f, 9f to 4f, 15f to 4f, 15f to 7.5f), color, style = line)
      drawLine(color, p(8f, 7.5f), p(8f, 20.5f), strokeWidth = w * 0.8f)
      drawLine(color, p(16f, 7.5f), p(16f, 20.5f), strokeWidth = w * 0.8f)
    }
    GameIcon.LABEL -> {
      drawPath(path(3f to 5f, 13f to 5f, 21f to 12f, 13f to 19f, 3f to 19f, close = true), color, style = line)
      drawCircle(color, 1.7f * u, p(8f, 12f))
    }
    GameIcon.LIST -> {
      for (y in listOf(6f, 12f, 18f)) {
        drawCircle(color, 1.5f * u, p(4.5f, y))
        drawLine(color, p(8.5f, y), p(20.5f, y), strokeWidth = w, cap = StrokeCap.Round)
      }
    }
    GameIcon.NOTE -> {
      drawPath(path(5f to 3f, 15f to 3f, 19f to 7f, 19f to 21f, 5f to 21f, close = true), color, style = line)
      drawPath(path(15f to 3f, 15f to 7f, 19f to 7f), color, style = line)
      drawLine(color, p(8f, 12f), p(16f, 12f), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
      drawLine(color, p(8f, 16f), p(14f, 16f), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
    }
    GameIcon.TIMELINE -> {
      // A winding road with stops on it.
      drawPath(path(4f to 20f, 4f to 14f, 12f to 14f, 12f to 9f, 20f to 9f, 20f to 4f), color, style = line)
      drawCircle(color, 2.4f * u, p(4f, 20f))
      drawCircle(color, 2.4f * u, p(12f, 11.5f))
      drawCircle(color, 2.4f * u, p(20f, 4f))
    }
    GameIcon.CALENDAR -> {
      drawRoundRect(color, p(3f, 5f), Size(18f * u, 16f * u), CornerRadius(2f * u), style = line)
      drawLine(color, p(3f, 10f), p(21f, 10f), strokeWidth = w)
      drawLine(color, p(8f, 3f), p(8f, 7f), strokeWidth = w, cap = StrokeCap.Round)
      drawLine(color, p(16f, 3f), p(16f, 7f), strokeWidth = w, cap = StrokeCap.Round)
      drawCircle(color, 1.3f * u, p(8f, 14.5f))
      drawCircle(color, 1.3f * u, p(12f, 14.5f))
      drawCircle(color, 1.3f * u, p(16f, 14.5f))
    }
    GameIcon.BIKE -> {
      // A motorbike from the side: two wheels, the body between, bars up front.
      drawCircle(color, 3.6f * u, p(5.5f, 16.5f), style = line)
      drawCircle(color, 3.6f * u, p(18.5f, 16.5f), style = line)
      drawPath(path(5.5f to 16.5f, 9f to 11f, 15f to 11f, 18.5f to 16.5f), color, style = line)
      drawPath(path(15f to 11f, 16.5f to 6.5f, 19.5f to 6.5f), color, style = line)
      drawPath(path(8f to 11f, 9.5f to 8.5f, 13f to 8.5f), color, style = line)
    }
    GameIcon.WALK -> {
      drawCircle(color, 2.2f * u, p(13f, 3.8f))
      drawPath(path(12f to 7.5f, 10f to 14f, 7f to 21f), color, style = line)
      drawPath(path(10f to 14f, 14f to 16f, 15f to 21f), color, style = line)
      drawPath(path(7f to 11.5f, 11.5f to 8f, 16.5f to 11.5f), color, style = line)
    }
    GameIcon.OFFLINE -> {
      // A folded map with an arrow down onto it.
      drawPath(path(3f to 8f, 9f to 6f, 15f to 8f, 21f to 6f, 21f to 20f, 15f to 22f, 9f to 20f, 3f to 22f, close = true), color, style = line)
      drawLine(color, p(12f, 1.5f), p(12f, 14f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(8.5f to 10.5f, 12f to 14f, 15.5f to 10.5f), color, style = line)
    }
    GameIcon.DOWNLOAD -> {
      drawLine(color, p(12f, 3f), p(12f, 15f), strokeWidth = w, cap = StrokeCap.Round)
      drawPath(path(7f to 10.5f, 12f to 15.5f, 17f to 10.5f), color, style = line)
      drawPath(path(4f to 16f, 4f to 20.5f, 20f to 20.5f, 20f to 16f), color, style = line)
    }
    GameIcon.BELL -> {
      drawPath(path(4f to 17.5f, 6.5f to 14f, 6.5f to 10f, 9f to 5.5f, 12f to 4.5f, 15f to 5.5f, 17.5f to 10f, 17.5f to 14f, 20f to 17.5f, close = true), color, style = line)
      drawLine(color, p(10f, 20.5f), p(14f, 20.5f), strokeWidth = w, cap = StrokeCap.Round)
    }
    GameIcon.SHIELD -> {
      drawPath(path(12f to 2.5f, 20f to 5.5f, 20f to 11f, 12f to 21.5f, 4f to 11f, 4f to 5.5f, close = true), color, style = line)
      drawCircle(color, 2f * u, p(12f, 10f))
      drawLine(color, p(12f, 11f), p(12f, 15f), strokeWidth = w, cap = StrokeCap.Round)
    }
    GameIcon.INFO -> {
      drawCircle(color, 9.5f * u, p(12f, 12f), style = line)
      drawCircle(color, 1.4f * u, p(12f, 7.5f))
      drawLine(color, p(12f, 11f), p(12f, 17f), strokeWidth = w * 1.1f, cap = StrokeCap.Round)
    }
    GameIcon.STATS -> {
      drawLine(color, p(3f, 21f), p(21f, 21f), strokeWidth = w, cap = StrokeCap.Round)
      drawRect(color, p(4.5f, 13f), Size(4f * u, 8f * u))
      drawRect(color, p(10f, 8f), Size(4f * u, 13f * u))
      drawRect(color, p(15.5f, 3.5f), Size(4f * u, 17.5f * u))
    }
  }
}
