package com.thealgothrim.overworld

import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.timeline.Fix
import com.thealgothrim.overworld.timeline.Segment
import com.thealgothrim.overworld.timeline.TimelineRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recorder against a list instead of the database: drives, stops, red lights, a night parked. */
class TimelineRecorderTest {
  private class ListSink : TimelineRecorder.Sink {
    val rows = linkedMapOf<Long, Segment>()
    private var next = 1L

    override fun insert(s: Segment): Long = next++.also { rows[it] = s.copy(id = it) }

    override fun update(s: Segment) {
      if (s.id in rows) rows[s.id] = s
    }

    override fun delete(id: Long) {
      rows.remove(id)
    }

    override fun last(): Segment? = rows.values.maxByOrNull { it.start }

    val segments: List<Segment>
      get() = rows.values.sortedBy { it.start }
  }

  private val start = 1_700_000_000_000L
  private val lat0 = 28.6315
  private val lng0 = 77.2167
  /** About 111 m of latitude per 0.001°. */
  private fun north(metres: Double) = metres / 111_320.0

  /** Fixes every [step] seconds for [seconds], standing at metres-north [at]. */
  private fun stand(r: TimelineRecorder, from: Long, seconds: Int, at: Double, step: Int = 10): Long {
    var t = from
    repeat(seconds / step) {
      r.onFix(Fix(lat0 + north(at), lng0, 0.2, 8.0, t))
      t += step * 1000L
    }
    return t
  }

  /** Driving north at [speed] m/s from metres-north [from] for [seconds]; returns (time, metres north). */
  private fun drive(r: TimelineRecorder, t0: Long, from: Double, seconds: Int, speed: Double = 10.0): Pair<Long, Double> {
    var t = t0
    var at = from
    repeat(seconds) {
      at += speed
      t += 1000L
      r.onFix(Fix(lat0 + north(at), lng0, speed, 6.0, t))
    }
    return t to at
  }

  @Test
  fun `a stay, a drive and a stay`() {
    val sink = ListSink()
    val r = TimelineRecorder(sink)
    var t = stand(r, start, 600, 0.0)
    val (t2, at) = drive(r, t, 0.0, 300)
    t = stand(r, t2, 600, at)
    r.flush()
    val s = sink.segments
    assertEquals(listOf(false, true, false), s.map { it.move })
    val move = s[1]
    assertTrue("drove ${move.metres} m", move.metres in 2_700.0..3_300.0)
    assertEquals(TravelMode.CAR, move.mode)
    assertTrue(move.path != null)
    assertTrue("top ${move.topKmh}", move.topKmh in 30.0..40.0)
  }

  @Test
  fun `a red light doesn't end the drive`() {
    val sink = ListSink()
    val r = TimelineRecorder(sink)
    var t = stand(r, start, 600, 0.0)
    var (t2, at) = drive(r, t, 0.0, 120)
    // Two minutes at a light.
    t = stand(r, t2, 120, at, step = 2)
    val more = drive(r, t, at, 120)
    t = stand(r, more.first, 600, more.second)
    r.flush()
    assertEquals(1, sink.segments.count { it.move })
  }

  @Test
  fun `a slow stretch with no trip was a walk`() {
    val sink = ListSink()
    val r = TimelineRecorder(sink)
    var t = stand(r, start, 600, 0.0)
    val (t2, at) = drive(r, t, 0.0, 900, speed = 1.4)
    stand(r, t2, 600, at)
    r.flush()
    assertEquals(TravelMode.WALK, sink.segments.single { it.move }.mode)
  }

  @Test
  fun `the app away for the night at the same spot is a stay`() {
    val sink = ListSink()
    val r = TimelineRecorder(sink)
    var t = stand(r, start, 600, 0.0)
    // Ten hours later, same place.
    t += 10 * 3_600_000L
    stand(r, t, 120, 0.0)
    r.flush()
    val stays = sink.segments.filter { !it.move }
    assertEquals(1, stays.size)
    assertTrue("stay of ${stays.single().millis / 60_000} min", stays.single().millis >= 10 * 3_600_000L)
  }

  @Test
  fun `arriving on a trip names the stop`() {
    val sink = ListSink()
    val r = TimelineRecorder(sink)
    var t = stand(r, start, 600, 0.0)
    val ctx = TimelineRecorder.Context(tripMode = TravelMode.BIKE, tripTo = "Lodhi Garden")
    var at = 0.0
    repeat(200) {
      at += 9.0
      t += 1000L
      r.onFix(Fix(lat0 + north(at), lng0, 9.0, 6.0, t), ctx)
    }
    r.onArrived("Lodhi Garden", uniffi.ferrostar.GeographicCoordinate(lat0 + north(at), lng0), t)
    stand(r, t + 1000, 600, at)
    r.flush()
    val s = sink.segments
    val move = s.single { it.move }
    assertEquals(TravelMode.BIKE, move.mode)
    assertTrue(move.trip)
    assertEquals("Lodhi Garden", s.last().name)
  }
}
