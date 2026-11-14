package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.theme.Skin
import com.thealgothrim.overworld.ui.gta.Gta
import com.thealgothrim.overworld.ui.gta.GtaMenuSubheader
import com.thealgothrim.overworld.ui.rdr.Rdr
import com.thealgothrim.overworld.ui.rdr.RdrDivider
import com.thealgothrim.overworld.ui.rdr.RdrText
import com.thealgothrim.overworld.ui.rdr.roughBox
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.vi.Vi

/**
 * Choices side by side, the one in use lit in the world's accent: one tap, no tick boxes (his rule
 * for every setting). [label] names each; [icon] adds a picture above the word.
 */
@Composable
fun <T> ChoiceRow(spec: SkinSpec, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit, icon: ((T) -> GameIcon?)? = null) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEach { o -> ChoiceChip(spec, label(o), o == selected, { onPick(o) }, Modifier.weight(1f), icon?.invoke(o)) }
  }
}

/** One choice: lit in the accent when [on] (a chosen option, or a switch that's on). */
@Composable
fun ChoiceChip(spec: SkinSpec, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GameIcon? = null) {
  val shape = RoundedCornerShape(if (spec.vi) 10.dp else 0.dp)
  val ink = if (on) spec.onAccent else spec.fg
  Column(
      modifier
          .heightIn(min = if (icon != null) 58.dp else 42.dp)
          .clip(shape)
          .background(if (on) spec.accent else spec.fg.copy(alpha = 0.1f))
          .then(if (spec.rdr && on) Modifier.border(1.5.dp, Rdr.White.copy(alpha = 0.8f)) else Modifier)
          .clickable(onClick = onClick)
          .padding(horizontal = 6.dp, vertical = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    icon?.let {
      GameIconView(it, ink, Modifier.size(20.dp))
      Spacer(Modifier.height(3.dp))
    }
    // Shrinks to fit rather than cutting the word: Red Dead's wide capitals cut "Petrol" and
    // "18 months" to "PETR…" in a row of five.
    val size = spec.titleSize(if (spec.vi) 15.sp else 14.sp, 15.sp)
    androidx.compose.foundation.text.BasicText(
        spec.title(label),
        // One unbroken line: wrapping let a word too wide for the chip break onto a hidden second
        // line, so Red Dead's lit "PETROL" lost its L at 384 dp instead of stepping down a size.
        softWrap = false,
        maxLines = 1,
        style = androidx.compose.ui.text.TextStyle(fontFamily = spec.title, color = ink, letterSpacing = spec.titleSpacing, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = size, stepSize = 0.5.sp),
    )
  }
}

/**
 * The game menu's tabs across the top: GTA V's white tab with black letters on a row of dark ones,
 * GTA VI's pink pill among glass ones, Red Dead's rough frame round the open one. With [scroll],
 * the row slides sideways like GTA V's pause menu and keeps the open tab in view.
 */
@Composable
fun GameTabs(spec: SkinSpec, labels: List<String>, open: Int, onOpen: (Int) -> Unit, scroll: Boolean = false) {
  if (!scroll) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (spec.gta) 2.dp else 6.dp)) {
      labels.forEachIndexed { i, label -> GameTab(spec, label, i == open, { onOpen(i) }, Modifier.weight(1f)) }
    }
    return
  }
  val state = rememberLazyListState()
  LaunchedEffect(open) { runCatching { state.animateScrollToItem((open - 1).coerceAtLeast(0)) } }
  LazyRow(Modifier.fillMaxWidth(), state = state, horizontalArrangement = Arrangement.spacedBy(if (spec.gta) 2.dp else 6.dp)) {
    itemsIndexed(labels) { i, label -> GameTab(spec, label, i == open, { onOpen(i) }, Modifier.widthIn(min = 92.dp)) }
  }
}

@Composable
private fun GameTab(spec: SkinSpec, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
  val tab = modifier.height(40.dp)
  when {
    spec.gta ->
        Box(tab.background(if (on) Gta.RowSelected else Gta.Panel).clickable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
          Text(label.uppercase(), color = if (on) Color.Black else Gta.White, fontFamily = spec.title, fontSize = 14.sp, maxLines = 1)
        }
    spec.vi -> {
      val shape = RoundedCornerShape(10.dp)
      Box(
          tab.clip(shape).background(if (on) Vi.Pink else Vi.Glass).border(1.dp, if (on) Vi.Pink else Vi.Hairline, shape).clickable(onClick = onClick).padding(horizontal = 12.dp),
          contentAlignment = Alignment.Center,
      ) {
        Text(label.uppercase(), color = if (on) Vi.Ink else Vi.White, fontFamily = Vi.condensed, fontSize = 16.sp, letterSpacing = 0.8.sp, maxLines = 1)
      }
    }
    else ->
        Box(tab.then(if (on) Modifier.roughBox() else Modifier).clickable(onClick = onClick).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
          RdrText(label.uppercase(), 16.sp, color = if (on) Color.White else Rdr.Grey, spacing = 1.sp)
        }
  }
}

/** A group's title: GTA V's black subheader bar, GTA VI's grey capitals, Red Dead's engraved rule. */
@Composable
fun SkinSectionTitle(spec: SkinSpec, title: String) {
  when (spec.skin) {
    Skin.GTA -> Box(Modifier.padding(top = 10.dp)) { GtaMenuSubheader(title) }
    Skin.GTA6 ->
        Text(
            title.uppercase(),
            color = Vi.Sub,
            fontFamily = Vi.condensed,
            fontSize = 15.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
        )
    Skin.RDR ->
        Column(Modifier.padding(top = 14.dp)) {
          RdrText(title.uppercase(), 16.sp, color = Rdr.Grey, spacing = 2.sp)
          RdrDivider()
        }
  }
}

/** A text box in the world's look: for a place's own name, a note, a list's name. */
@Composable
fun SkinField(spec: SkinSpec, value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
  val shape = RoundedCornerShape(if (spec.vi) 10.dp else 0.dp)
  Box(
      modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .clip(shape)
          .background(spec.fg.copy(alpha = 0.08f))
          .border(1.dp, spec.fg.copy(alpha = 0.25f), shape)
          .padding(horizontal = 12.dp, vertical = 12.dp),
      contentAlignment = Alignment.CenterStart,
  ) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 4,
        textStyle = TextStyle(color = spec.fg, fontFamily = spec.body, fontSize = 17.sp),
        cursorBrush = SolidColor(spec.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
          if (value.isEmpty()) SkinText(hint, spec.body, 17.sp, spec.sub)
          inner()
        },
    )
  }
}

/** A short line of help under a group, in the sub colour. */
@Composable
fun SkinHelp(spec: SkinSpec, text: String, modifier: Modifier = Modifier) {
  SkinText(text, spec.body, 14.sp, spec.sub, modifier.padding(horizontal = 4.dp, vertical = 6.dp), maxLines = 6)
}

/** A row with a small label on the left and its chips filling the rest, for one setting. */
@Composable
fun <T> LabeledChoices(spec: SkinSpec, title: String, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
  Column(Modifier.padding(vertical = 6.dp)) {
    SkinText(spec.title(title), spec.title, spec.titleSize(14.sp, 16.sp), spec.sub, Modifier.padding(start = 2.dp, bottom = 6.dp), spacing = spec.titleSpacing)
    ChoiceRow(spec, options, selected, label, onPick)
  }
}

/** Small "N places" style counts. */
fun count(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

/** A gap, for spacing groups in a page. */
@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

/** A fixed-width gap. */
@Composable
fun HGap(w: Int = 10) = Spacer(Modifier.width(w.dp))

/**
 * A row of the world's icons to pick one for a list, the picked one lit in the accent. Slides
 * sideways when they don't all fit.
 */
@Composable
fun IconPicker(spec: SkinSpec, icons: List<GameIcon>, selected: GameIcon, onPick: (GameIcon) -> Unit) {
  val shape = RoundedCornerShape(if (spec.vi) 10.dp else 0.dp)
  Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    icons.forEach { icon ->
      val on = icon == selected
      Box(
          Modifier.size(46.dp).clip(shape).background(if (on) spec.accent else spec.fg.copy(alpha = 0.1f))
              .then(if (spec.rdr && on) Modifier.border(1.5.dp, Rdr.White.copy(alpha = 0.8f)) else Modifier)
              .clickable { onPick(icon) },
          contentAlignment = Alignment.Center,
      ) { GameIconView(icon, if (on) spec.onAccent else spec.fg, Modifier.size(24.dp)) }
    }
  }
}

/**
 * "Overworld is free. Support it": one quiet line at the foot of Settings and You, so the way to
 * support the app can be found without hunting (see prefs/SupportAsk). It opens the website's page
 * in the browser; nothing is sold or unlocked in the app.
 */
@Composable
fun SupportLine(spec: SkinSpec) {
  val context = androidx.compose.ui.platform.LocalContext.current
  Row(
      Modifier.fillMaxWidth()
          .padding(top = 22.dp, bottom = 6.dp)
          .clickable {
            runCatching {
              context.startActivity(
                  android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.thealgothrim.overworld.prefs.SupportAsk.URL))
                      .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
              )
            }
          }
          .padding(vertical = 10.dp),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
  ) {
    com.thealgothrim.overworld.ui.skin.GameIconView(com.thealgothrim.overworld.ui.skin.GameIcon.HEART, spec.accent, Modifier.size(22.dp))
    Spacer(Modifier.width(12.dp))
    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
      com.thealgothrim.overworld.ui.skin.SkinText("Overworld is free, with no ads.", spec.body, 15.sp, spec.fg)
      com.thealgothrim.overworld.ui.skin.SkinText("Support it and help keep it that way", spec.body, 13.sp, spec.accent)
    }
    com.thealgothrim.overworld.ui.skin.GameIconView(com.thealgothrim.overworld.ui.skin.GameIcon.NAVIGATE, spec.sub, Modifier.size(16.dp))
  }
}
