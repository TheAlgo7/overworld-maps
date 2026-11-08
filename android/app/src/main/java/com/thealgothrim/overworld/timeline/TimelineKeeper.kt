package com.thealgothrim.overworld.timeline

import android.util.Log
import com.thealgothrim.overworld.AppModule
import com.thealgothrim.overworld.notify.Notices
import com.thealgothrim.overworld.traffic.metres
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.ferrostar.GeographicCoordinate

/**
 * Runs the Timeline: fixes go to the [TimelineRecorder] on one background thread, kept stays get
 * their names (Home, Work, a saved place, the trip's destination, else the map's name for the
 * spot), old pieces go by the Privacy setting, and a recap of last month is offered once a month.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineKeeper(val store: TimelineStore) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
  private val _changed = MutableStateFlow(0L)
  /** Bumps when pieces are added or change, for screens showing them. */
  val changed: StateFlow<Long> = _changed.asStateFlow()

  private val recorder =
      TimelineRecorder(
          object : TimelineRecorder.Sink {
            override fun insert(s: Segment): Long = store.insert(s).also { bump() }

            override fun update(s: Segment) = store.update(s)

            override fun delete(id: Long) = store.delete(id).also { bump() }

            override fun last(): Segment? = store.last()

            override fun confirmed(s: Segment) = name(s)

            override fun setNames(id: Long, name: String?, area: String?, city: String?) = store.setNames(id, name, area, city).also { bump() }
          }
      )

  private val on: Boolean
    get() = AppModule.prefs.timeline.value

  private var lastBump = 0L

  private fun bump(force: Boolean = true) {
    val now = System.currentTimeMillis()
    if (force || now - lastBump > 30_000) {
      lastBump = now
      _changed.value = now
    }
  }

  fun fix(f: Fix, ctx: TimelineRecorder.Context) {
    if (!on) return
    scope.launch {
      runCatching { recorder.onFix(f, ctx) }.onFailure { Log.w(TAG, "timeline fix failed", it) }
      bump(force = false)
    }
  }

  fun arrived(name: String, at: GeographicCoordinate) {
    if (!on) return
    scope.launch { runCatching { recorder.onArrived(name, at, System.currentTimeMillis()) }.onFailure { Log.w(TAG, "timeline arrival failed", it) } }
  }

  /** Keep what's being recorded (the app is going to the background or recording stops). */
  fun flush() {
    scope.launch { runCatching { recorder.flush() } }
  }

  fun deleteDay(date: LocalDate) =
      scope.launch {
        store.deleteDay(date)
        bump()
      }

  fun deleteAll() =
      scope.launch {
        store.deleteAll()
        bump()
      }

  fun rename(id: Long, name: String) =
      scope.launch {
        recorder.renamed(id, name)
        store.rename(id, name)
        bump()
      }

  /** Once at start: auto-delete what's older than the Privacy setting keeps, and last month's recap. */
  fun startUp() =
      scope.launch {
        runCatching {
              AppModule.prefs.timelineKeep.value.months?.let { months ->
                val cutoff = LocalDate.now().minusMonths(months.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                store.deleteOlderThan(cutoff)
              }
              recap()
            }
            .onFailure { Log.w(TAG, "timeline start-up failed", it) }
      }

  /** "Your October in Overworld": sent once, early in the next month, when there's something to say. */
  private fun recap() {
    val prefs = AppModule.prefs
    if (!on || !prefs.notifyRecap.value) return
    val last = YearMonth.now().minusMonths(1)
    if (prefs.string("recap_month") == last.toString()) return
    val totals = store.totals(last)
    val metres = totals.sumOf { it.metres }
    prefs.putString("recap_month", last.toString())
    if (metres < 1_000) return
    val places = store.places(store.month(last)).size
    Notices.recap(AppModule.context, last, metres, totals.sumOf { it.count }, places)
  }

  private fun name(s: Segment) {
    scope.launch(Dispatchers.IO) {
      val saved = AppModule.saved.state.value
      val own =
          when {
            saved.home?.let { metres(it.coordinate, s.at) < OWN_PLACE_METRES } == true -> "Home"
            saved.work?.let { metres(it.coordinate, s.at) < OWN_PLACE_METRES } == true -> "Work"
            else -> saved.places.filter { metres(it.place.coordinate, s.at) < OWN_PLACE_METRES }.minByOrNull { metres(it.place.coordinate, s.at) }?.name
          }
      val found = runCatching { AppModule.search.reverseInfo(s.at) }.getOrNull()
      val name = own ?: found?.name
      scope.launch { recorder.named(s.id, name, found?.area, found?.city) }
    }
  }

  companion object {
    private const val TAG = "Timeline"
    /** A stop this close to a saved place (or Home, Work) is that place. */
    private const val OWN_PLACE_METRES = 120.0
  }
}
