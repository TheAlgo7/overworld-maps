package com.thealgothrim.overworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.timeline.AwayTrip
import com.thealgothrim.overworld.timeline.CityVisits
import com.thealgothrim.overworld.timeline.ModeTotal
import com.thealgothrim.overworld.timeline.PlaceVisits
import com.thealgothrim.overworld.timeline.Segment
import com.thealgothrim.overworld.ui.skin.GameIcon
import com.thealgothrim.overworld.ui.skin.GameIconView
import com.thealgothrim.overworld.ui.skin.SkinDivider
import com.thealgothrim.overworld.ui.skin.SkinPanel
import com.thealgothrim.overworld.ui.skin.SkinPrimaryButton
import com.thealgothrim.overworld.ui.skin.SkinRow
import com.thealgothrim.overworld.ui.skin.SkinSecondaryButton
import com.thealgothrim.overworld.ui.skin.SkinSpec
import com.thealgothrim.overworld.ui.skin.SkinText
import com.thealgothrim.overworld.ui.skin.icon
import com.thealgothrim.overworld.ui.skin.sheetFloat
import com.thealgothrim.overworld.ui.skin.skinPanel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Timeline's views, like Google's tabs (Day, Insights, Places, Cities, Trips). */
private enum class TimelineView(val label: String) {
  DAY("Day"),
  MONTH("Month"),
  PLACES("Places"),
  CITIES("Cities"),
  TRIPS("Trips"),
}

private val zone: ZoneId
  get() = ZoneId.systemDefault()

private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "9:10 am", the app's 12-hour clock. */
fun clockAt(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(CLOCK).lowercase()

/** "Today", "Yesterday", "Sat 7 Nov", or "7 Nov 2025" in another year. */
fun dayTitle(date: LocalDate): String {
  val today = LocalDate.now()
  return when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "d MMM yyyy", Locale.ENGLISH))
  }
}

private fun monthTitle(m: YearMonth) = "${m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${m.year}"

/** Hours and minutes for a stay or a total: "45 min", "9 h 20 min". */
private fun span(ms: Long) = formatDuration(ms / 1000.0)

private fun modeWord(mode: TravelMode?) =
    when (mode) {
      TravelMode.WALK -> "Walked"
      TravelMode.BIKE -> "Rode"
      else -> "Drove"
    }

/**
 * Timeline: where you went while Overworld was running, kept on this phone. Day by day with the
 * day on the map, a month's totals per mode, the places you stop at most, cities, and trips away.
 */
@Composable
internal fun TimelineTab(spec: SkinSpec, onChoose: (Place) -> Unit, onShowDay: (LocalDate) -> Unit) {
  val on by AppModule.prefs.timeline.collectAsState()
  val noticed by AppModule.prefs.timelineNoticed.collectAsState()
  val changed by AppModule.timeline.changed.collectAsState()
  var view by rememberSaveable { mutableStateOf(TimelineView.DAY) }

  if (!on) {
    SkinPanel(spec, Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp)) {
        SkinText(spec.title("Timeline is off"), spec.title, spec.titleSize(20.sp, 22.sp), spec.fg, spacing = spec.titleSpacing)
        Gap(6)
        SkinText("Turn it on to keep your drives and the places you stop at, on this phone only. Nothing is uploaded.", spec.body, 15.sp, spec.sub, maxLines = 4)
        Gap(12)
        SkinPrimaryButton(spec, "Turn on Timeline", { AppModule.prefs.setTimeline(true) }, Modifier.fillMaxWidth(), GameIcon.TIMELINE)
      }
    }
    Gap(12)
  } else if (!noticed) {
    SkinPanel(spec, Modifier.fillMaxWidth()) {
      Column(Modifier.padding(16.dp)) {
        SkinText(spec.title("Timeline is on"), spec.title, spec.titleSize(20.sp, 22.sp), spec.fg, spacing = spec.titleSpacing)
        Gap(6)
        SkinText(
            "Overworld keeps your drives and the places you stop at while it's open, on the phone or the car screen. It stays on this phone and is never uploaded. Turn it off or delete it in Settings > Privacy.",
            spec.body, 15.sp, spec.sub, maxLines = 6,
        )
        Gap(12)
        SkinPrimaryButton(spec, "Got it", { AppModule.prefs.setTimelineNoticed() }, Modifier.fillMaxWidth())
      }
    }
    Gap(12)
  }

  ChoiceRow(spec, TimelineView.entries, view, { it.label }, { view = it })
  Gap(12)
  when (view) {
    TimelineView.DAY -> DayView(spec, changed, onChoose, onShowDay)
    TimelineView.MONTH -> MonthView(spec, changed, onChoose)
    TimelineView.PLACES -> PlacesView(spec, changed, onChoose)
    TimelineView.CITIES -> CitiesView(spec, changed)
    TimelineView.TRIPS -> TripsView(spec, changed)
  }
}

@Composable
private fun DayView(spec: SkinSpec, changed: Long, onChoose: (Place) -> Unit, onShowDay: (LocalDate) -> Unit) {
  var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
  var calendar by remember { mutableStateOf(false) }
  var deleting by remember(date) { mutableStateOf(false) }
  val segments by produceState(emptyList<Segment>(), date, changed) { value = withContext(Dispatchers.IO) { AppModule.timeline.store.day(date) } }
  Stepper(spec, dayTitle(date), onPrev = { date = date.minusDays(1) }, onNext = { if (date < LocalDate.now()) date = date.plusDays(1) }, onTitle = { calendar = !calendar })
  if (calendar) {
    Gap(8)
    MonthGrid(spec, YearMonth.from(date), date, changed) {
      date = it
      calendar = false
    }
  }
  Gap(10)
  val moves = segments.filter { it.move }
  if (segments.isNotEmpty()) {
    val total = moves.sumOf { it.metres }
    SkinText(
        listOfNotNull(count(moves.size, "trip"), formatDistance(total).takeIf { total > 0 }, count(segments.count { !it.move }, "stop")).joinToString("  ·  "),
        spec.body, 15.sp, spec.sub, Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
  }
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    if (segments.isEmpty()) {
      SkinText("Nothing kept on ${dayTitle(date).lowercase().replaceFirstChar { it.uppercase() }}. Timeline keeps what happens while Overworld is open, on the phone or the car screen.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp), maxLines = 4)
    }
    segments.forEachIndexed { i, s ->
      if (i > 0) SkinDivider(spec)
      if (s.move) {
        SkinRow(
            spec,
            "${modeWord(s.mode)} ${formatDistance(s.metres)}" + (s.name?.let { " to $it" } ?: ""),
            { onShowDay(date) },
            subtitle = "${clockAt(s.start)} – ${clockAt(s.end)}  ·  ${span(s.millis)}" + (if (s.topKmh > 1 && s.mode != TravelMode.WALK) "  ·  top ${displaySpeed(s.topKmh.toInt())} ${speedUnit(false)}" else ""),
            icon = (s.mode ?: TravelMode.CAR).icon,
        )
      } else {
        val name = s.name ?: "A stop"
        SkinRow(
            spec,
            name,
            { onChoose(Place(name, s.area ?: "", s.at)) },
            subtitle = "${clockAt(s.start)} – ${clockAt(s.end)}  ·  ${span(s.millis)}" + (s.area?.takeIf { it != name }?.let { "  ·  $it" } ?: ""),
            icon = GameIcon.PIN,
        )
      }
    }
  }
  if (segments.isNotEmpty()) {
    Gap(10)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      SkinSecondaryButton(
          spec,
          if (deleting) "Tap again" else "Delete day",
          {
            if (deleting) {
              AppModule.timeline.deleteDay(date)
              deleting = false
            } else deleting = true
          },
          icon = GameIcon.DELETE,
      )
      SkinPrimaryButton(spec, "Show on map", { onShowDay(date) }, Modifier.weight(1f), GameIcon.LAYERS)
    }
  }
}

/** ‹ title › with the title tappable (the day opens the calendar). */
@Composable
private fun Stepper(spec: SkinSpec, title: String, onPrev: () -> Unit, onNext: () -> Unit, onTitle: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().skinPanel(spec, elevated = false).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(44.dp).clickable(onClick = onPrev), contentAlignment = Alignment.Center) { GameIconView(GameIcon.BACK, spec.fg, Modifier.size(22.dp)) }
    Row(Modifier.weight(1f).then(if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
      if (onTitle != null) {
        GameIconView(GameIcon.CALENDAR, spec.sub, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
      }
      SkinText(spec.title(title), spec.title, spec.titleSize(if (spec.vi) 20.sp else 18.sp, 20.sp), spec.fg, spacing = spec.titleSpacing)
    }
    Box(Modifier.size(44.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
      // The back arrow turned to point forward.
      GameIconView(GameIcon.BACK, spec.fg, Modifier.size(22.dp).graphicsLayer { scaleX = -1f })
    }
  }
}

/** A month's calendar: days with anything kept get a dot; the chosen day is lit. */
@Composable
private fun MonthGrid(spec: SkinSpec, start: YearMonth, chosen: LocalDate, changed: Long, onPick: (LocalDate) -> Unit) {
  var month by remember { mutableStateOf(start) }
  val days by produceState(emptySet<Int>(), month, changed) { value = withContext(Dispatchers.IO) { AppModule.timeline.store.daysWithData(month) } }
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    Column(Modifier.padding(10.dp)) {
      Stepper(spec, monthTitle(month), onPrev = { month = month.minusMonths(1) }, onNext = { if (month < YearMonth.now()) month = month.plusMonths(1) })
      Gap(6)
      Row(Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
          Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { SkinText(d, spec.body, 13.sp, spec.sub) }
        }
      }
      val first = month.atDay(1)
      val lead = first.dayOfWeek.value - 1
      val cells = lead + month.lengthOfMonth()
      for (week in 0 until (cells + 6) / 7) {
        Row(Modifier.fillMaxWidth()) {
          for (col in 0 until 7) {
            val day = week * 7 + col - lead + 1
            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
              if (day in 1..month.lengthOfMonth()) {
                val date = month.atDay(day)
                val lit = date == chosen
                val future = date > LocalDate.now()
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(if (lit) spec.accent else spec.fg.copy(alpha = 0f)).then(if (!future) Modifier.clickable { onPick(date) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                  SkinText(day.toString(), spec.body, 15.sp, if (lit) spec.onAccent else if (future) spec.sub.copy(alpha = 0.4f) else spec.fg)
                  if (day in days && !lit) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(5.dp).background(spec.accent, CircleShape))
                }
              }
            }
          }
        }
      }
    }
  }
}

/** A month: how far and how long by car, bike and on foot, and where you stopped most. */
@Composable
private fun MonthView(spec: SkinSpec, changed: Long, onChoose: (Place) -> Unit) {
  var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
  val totals by produceState(emptyList<ModeTotal>(), month, changed) { value = withContext(Dispatchers.IO) { AppModule.timeline.store.totals(month) } }
  val places by produceState(emptyList<PlaceVisits>(), month, changed) { value = withContext(Dispatchers.IO) { AppModule.timeline.store.let { it.places(it.month(month)) } } }
  Stepper(spec, monthTitle(month), onPrev = { month = month.minusMonths(1) }, onNext = { if (month < YearMonth.now()) month = month.plusMonths(1) })
  SkinSectionTitle(spec, "Travel")
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    if (totals.isEmpty()) SkinText("No trips kept in ${monthTitle(month)}.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp))
    totals.forEachIndexed { i, t ->
      if (i > 0) SkinDivider(spec)
      SkinRow(
          spec,
          when (t.mode) { TravelMode.CAR -> "Driving"; TravelMode.BIKE -> "Riding"; TravelMode.WALK -> "Walking" },
          {},
          subtitle = "${count(t.count, "trip")}  ·  ${span(t.millis)}",
          icon = t.mode.icon,
          trailing = formatDistance(t.metres),
      )
    }
  }
  SkinSectionTitle(spec, "Visits")
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    if (places.isEmpty()) SkinText("No stops kept in ${monthTitle(month)}.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp))
    places.take(8).forEachIndexed { i, p ->
      if (i > 0) SkinDivider(spec)
      SkinRow(spec, p.name, { onChoose(Place(p.name, p.area ?: "", p.at)) }, subtitle = "${count(p.visits, "visit")}  ·  ${span(p.millis)}", icon = GameIcon.PIN)
    }
  }
}

@Composable
private fun PlacesView(spec: SkinSpec, changed: Long, onChoose: (Place) -> Unit) {
  val places by produceState(emptyList<PlaceVisits>(), changed) { value = withContext(Dispatchers.IO) { AppModule.timeline.store.places() } }
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    if (places.isEmpty()) SkinText("Places you stop at show up here, most visited first.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp))
    places.take(60).forEachIndexed { i, p ->
      if (i > 0) SkinDivider(spec)
      SkinRow(spec, p.name, { onChoose(Place(p.name, p.area ?: "", p.at)) }, subtitle = "${count(p.visits, "visit")}  ·  ${span(p.millis)}  ·  last ${agoText(p.last)}", icon = GameIcon.PIN)
    }
  }
}

@Composable
private fun CitiesView(spec: SkinSpec, changed: Long) {
  val cities by produceState(emptyList<CityVisits>(), changed) { value = withContext(Dispatchers.IO) { AppModule.timeline.store.cities() } }
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    if (cities.isEmpty()) SkinText("Cities you've been to show up here.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp))
    cities.forEachIndexed { i, c ->
      if (i > 0) SkinDivider(spec)
      SkinRow(spec, c.city, {}, subtitle = "${count(c.days, "day")}  ·  last ${agoText(c.last)}", icon = GameIcon.PIN)
    }
  }
}

@Composable
private fun TripsView(spec: SkinSpec, changed: Long) {
  val home = AppModule.saved.state.collectAsState().value.home?.coordinate
  val trips by produceState(emptyList<AwayTrip>(), changed, home) { value = withContext(Dispatchers.IO) { AppModule.timeline.store.awayTrips(home) } }
  if (home == null) SkinHelp(spec, "Set Home (Save on your home's place) so trips away from it are found from there.")
  SkinPanel(spec, Modifier.fillMaxWidth()) {
    if (trips.isEmpty()) SkinText("Days more than 40 km from home show up here as trips.", spec.body, 15.sp, spec.sub, Modifier.padding(16.dp))
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    trips.forEachIndexed { i, t ->
      if (i > 0) SkinDivider(spec)
      val dates = if (t.from == t.to) t.from.format(fmt) else "${t.from.format(fmt)} – ${t.to.format(fmt)}"
      SkinRow(spec, t.title, {}, subtitle = dates + (if (t.metres > 0) "  ·  ${formatDistance(t.metres)}" else ""), icon = GameIcon.SUITCASE)
    }
  }
}

/**
 * A Timeline day shown on the map: the date with ‹ ›, the day's trips and distance, and back to
 * the Timeline. The lines and stops themselves are drawn by the map (TimelineDayLayer).
 */
@Composable
fun BoxScope.TimelineDayCard(spec: SkinSpec, date: LocalDate, segments: List<Segment>, onDate: (LocalDate) -> Unit, onClose: () -> Unit) {
  SkinPanel(spec, Modifier.align(Alignment.BottomCenter).sheetFloat(spec).fillMaxWidth()) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
      Stepper(spec, dayTitle(date), onPrev = { onDate(date.minusDays(1)) }, onNext = { if (date < LocalDate.now()) onDate(date.plusDays(1)) })
      Gap(8)
      val moves = segments.filter { it.move }
      SkinText(
          if (segments.isEmpty()) "Nothing kept on this day."
          else listOf(count(moves.size, "trip"), formatDistance(moves.sumOf { it.metres }), count(segments.count { !it.move }, "stop")).joinToString("  ·  "),
          spec.body, 16.sp, spec.fg, Modifier.padding(start = 4.dp),
      )
      Gap(10)
      SkinSecondaryButton(spec, "Back to Timeline", onClose, Modifier.fillMaxWidth(), GameIcon.TIMELINE)
    }
  }
}

