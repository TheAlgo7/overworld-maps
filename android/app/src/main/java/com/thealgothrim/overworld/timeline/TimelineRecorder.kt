package com.thealgothrim.overworld.timeline

import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.traffic.encodePolyline6
import com.thealgothrim.overworld.traffic.metres
import uniffi.ferrostar.GeographicCoordinate

/** A GPS fix as the Timeline takes it: where, how fast (m/s), how sure (m), when (ms). */
data class Fix(val lat: Double, val lng: Double, val speed: Double?, val accuracy: Double, val time: Long) {
  val at: GeographicCoordinate
    get() = GeographicCoordinate(lat, lng)
}

/**
 * Turns fixes into Timeline pieces while Overworld runs: a stay while the phone stays within
 * [STAY_RADIUS] for [MIN_STAY] or more (a red light or a jam is shorter), a move between stays with
 * its line, distance and mode. When the app was away for a while and comes back at the same spot,
 * the time away counts as a stay there (parked overnight with the app closed).
 *
 * Not thread safe: fed from one coroutine. [sink] keeps the pieces (the database, or a list in tests).
 */
class TimelineRecorder(private val sink: Sink) {
  interface Sink {
    fun insert(s: Segment): Long

    fun update(s: Segment)

    fun delete(id: Long)

    fun last(): Segment?

    /** A stay has lasted long enough to keep: give it a name (asynchronously is fine, through [named]). */
    fun confirmed(s: Segment) {}

    /** Fill in a kept piece's names; a name already there stays. */
    fun setNames(id: Long, name: String?, area: String?, city: String?) {}
  }

  /** You renamed piece [id]: the piece being recorded takes the new name so a later save keeps it. */
  fun renamed(id: Long, name: String) {
    val cur = current
    if (cur != null && cur.id == id) current = cur.copy(name = name)
  }

  /** Names found for piece [id] (see [Sink.confirmed]); the piece being recorded keeps them too. */
  fun named(id: Long, name: String?, area: String?, city: String?) {
    val cur = current
    if (cur != null && cur.id == id) {
      current = cur.copy(name = cur.name ?: name, area = area ?: cur.area, city = city ?: cur.city).also { sink.update(it) }
    } else sink.setNames(id, name, area, city)
  }

  /** What else is known about a fix: the trip being driven, if any, and the vehicle otherwise. */
  data class Context(
      /** The trip's mode while a trip runs. */
      val tripMode: TravelMode? = null,
      /** Where the trip goes, for the move's name. */
      val tripTo: String? = null,
      /** The mode to file a moving stretch under when there's no trip (car with Android Auto on). */
      val vehicle: TravelMode = TravelMode.CAR,
  )

  private var current: Segment? = null
  private var lastFix: Fix? = null
  /** First fix of what may be a stop, while moving. */
  private var stillSince: Fix? = null
  /** Last fix that was still inside the stay, while it looks like the phone is leaving. */
  private var lastNear: Fix? = null
  private var leaving = 0
  private val path = mutableListOf<GeographicCoordinate>()
  private var tripMode: TravelMode? = null
  private var tripTo: String? = null
  private var savedAt = 0L

  fun onFix(f: Fix, ctx: Context = Context()) {
    if (f.accuracy > MAX_ACCURACY) return
    ctx.tripMode?.let { tripMode = it }
    ctx.tripTo?.let { tripTo = it }
    val prev = lastFix
    if (prev != null && f.time <= prev.time) return
    if (prev != null && f.time - prev.time > GAP_MS) {
      // The app was away: whatever was going on ended at the last fix.
      close(prev)
      if (metres(prev.at, f.at) < STAY_RADIUS) startStay(prev.at, prev.time, f.time) else current = null
    }
    if (current == null) begin(f)
    val cur = current ?: return
    if (cur.move) moving(cur, f, ctx) else staying(cur, f, ctx)
    lastFix = f
  }

  /** The trip reached [name] at [at]: the move ends and a stay there begins, under that name. */
  fun onArrived(name: String, at: GeographicCoordinate, time: Long) {
    val cur = current
    if (cur != null && cur.move) {
      finishMove(cur, at, time, name)
    }
    startStay(at, time, time, name)
    tripMode = null
    tripTo = null
  }

  /** Overworld is closing or recording is turned off: keep what there is. */
  fun flush() {
    lastFix?.let { close(it, keepOpen = true) }
  }

  // ------------------------------------------------------------ states

  private fun begin(f: Fix) {
    val last = sink.last()
    when {
      // Still at the place the last stay was: carry on with it.
      last != null && !last.move && metres(last.at, f.at) < STAY_RADIUS && f.time - last.end < RESUME_MS -> current = last.copy(end = f.time)
      // The last move ended here, and the phone stayed put since.
      last != null && last.move && last.to != null && metres(last.to, f.at) < STAY_RADIUS && f.time - last.end < RESUME_MS ->
          startStay(last.to, last.end, f.time)
      // Somewhere new: a stay until it moves (dropped if it moves within MIN_STAY).
      else -> startStay(f.at, f.time, f.time)
    }
  }

  private fun startStay(at: GeographicCoordinate, from: Long, to: Long, name: String? = null) {
    val last = sink.last()
    // The same place as the stay just before (a short hop that came back): carry that one on.
    if (last != null && !last.move && metres(last.at, at) < STAY_RADIUS && from - last.end < RESUME_MS) {
      current = last.copy(end = maxOf(last.end, to), name = name ?: last.name).also { sink.update(it) }
    } else {
      val s = Segment(move = false, start = from, end = to, at = at, name = name)
      // Kept from the start only when it's already long enough (a gap at the same spot); otherwise
      // saved once it has lasted MIN_STAY.
      current = if (to - from >= MIN_STAY) s.copy(id = sink.insert(s)).also { sink.confirmed(it) } else s
    }
    stillSince = null
    leaving = 0
    lastNear = null
  }

  private fun staying(cur: Segment, f: Fix, ctx: Context) {
    val far = metres(cur.at, f.at) > LEAVE_RADIUS
    if (!far) {
      leaving = 0
      lastNear = f
      val next = cur.copy(end = f.time)
      if (next.id == 0L && next.millis >= MIN_STAY) {
        current = next.copy(id = sink.insert(next)).also { sink.confirmed(it) }
      } else {
        current = next
        if (next.id > 0 && f.time - savedAt > SAVE_EVERY_MS) save(next)
      }
      return
    }
    leaving++
    if (leaving < 2 && (f.speed ?: 0.0) < MOVING_SPEED) return
    // Left: the stay ends at the last fix inside it, and a move starts from there.
    val endAt = lastNear?.time ?: cur.end
    val stay = cur.copy(end = maxOf(cur.start, endAt))
    val moveStart: Long
    val moveFrom: GeographicCoordinate
    if (stay.id == 0L) {
      // Never lasted long enough to be a stay: the move began where it did.
      moveStart = stay.start
      moveFrom = stay.at
    } else {
      save(stay)
      moveStart = stay.end
      moveFrom = stay.at
    }
    path.clear()
    path += moveFrom
    val move = Segment(move = true, mode = ctx.tripMode ?: ctx.vehicle, start = moveStart, end = f.time, at = moveFrom, to = f.at, trip = ctx.tripMode != null, name = ctx.tripTo)
    current = move.copy(id = sink.insert(move))
    leaving = 0
    stillSince = null
    moving(current!!, f, ctx)
  }

  private fun moving(cur: Segment, f: Fix, ctx: Context) {
    val prev = lastFix
    var next = cur.copy(end = f.time, to = f.at)
    if (prev != null) {
      val step = metres(prev.at, f.at)
      // A step longer than 250 km/h is a jump in GPS, not road.
      val seconds = (f.time - prev.time) / 1000.0
      if (seconds > 0 && step / seconds < 70.0) next = next.copy(metres = next.metres + step)
      val kmh = (f.speed ?: (if (seconds > 0) step / seconds else 0.0)) * 3.6
      if (kmh > next.topKmh && kmh < 250) next = next.copy(topKmh = kmh)
    }
    if (path.isEmpty() || metres(path.last(), f.at) >= PATH_SPACING) path += f.at
    if (ctx.tripMode != null) next = next.copy(mode = ctx.tripMode, trip = true, name = ctx.tripTo ?: next.name)
    current = next
    // A stop: still (or barely moving) within STAY_RADIUS for MIN_STAY.
    val still = (f.speed ?: Double.MAX_VALUE) < STILL_SPEED || (prev != null && f.speed == null && metres(prev.at, f.at) < 8)
    val since = stillSince
    if (still) {
      if (since == null || metres(since.at, f.at) > STAY_RADIUS) stillSince = f
      else if (f.time - since.time >= MIN_STAY) {
        finishMove(next, since.at, since.time, null)
        startStay(since.at, since.time, f.time)
        return
      }
    } else if (since != null && metres(since.at, f.at) > STAY_RADIUS) {
      stillSince = null
    }
    if (f.time - savedAt > SAVE_EVERY_MS) save(withPath(next))
  }

  private fun finishMove(cur: Segment, at: GeographicCoordinate, time: Long, name: String?) {
    // Trim the line back to where it stopped.
    while (path.size > 1 && metres(path.last(), at) > STAY_RADIUS && path.any { metres(it, at) <= STAY_RADIUS }) path.removeAt(path.lastIndex)
    if (path.isEmpty() || metres(path.last(), at) > 1) path += at
    val done = withPath(cur.copy(end = maxOf(cur.start, time), to = at, name = name ?: cur.name, mode = modeFor(cur)))
    if (done.metres < MIN_MOVE_METRES && !done.trip) {
      if (done.id > 0) sink.delete(done.id)
    } else save(done)
    path.clear()
    stillSince = null
    current = null
  }

  /** A stretch with no trip that never went faster than a brisk walk was on foot. */
  private fun modeFor(s: Segment): TravelMode {
    if (s.trip && s.mode != null) return s.mode
    val hours = s.millis / 3_600_000.0
    val average = if (hours > 0) s.metres / 1000 / hours else 0.0
    return if (s.topKmh < WALK_TOP_KMH && average < WALK_AVERAGE_KMH) TravelMode.WALK else s.mode ?: TravelMode.CAR
  }

  private fun close(at: Fix, keepOpen: Boolean = false) {
    val cur = current ?: return
    if (cur.move) {
      if (keepOpen) save(withPath(cur.copy(mode = modeFor(cur)))) else finishMove(cur, at.at, at.time, null)
    } else if (cur.id > 0) {
      save(cur.copy(end = maxOf(cur.end, at.time)))
      if (!keepOpen) current = null
    } else if (!keepOpen) current = null
  }

  private fun withPath(s: Segment): Segment = if (path.size >= 2) s.copy(path = encodePolyline6(path)) else s

  private fun save(s: Segment) {
    if (s.id > 0) sink.update(s)
    savedAt = s.end
    if (current?.id == s.id) current = s
  }

  companion object {
    /** Fixes vaguer than this are skipped. */
    const val MAX_ACCURACY = 80.0
    /** A stop is within this of where it began. */
    const val STAY_RADIUS = 120.0
    /** Further than this from a stay's spot is leaving it (GPS wanders more indoors). */
    const val LEAVE_RADIUS = 180.0
    /** A stay lasts at least this long; a red light or a jam doesn't. */
    const val MIN_STAY = 4 * 60_000L
    /** No fix for this long: the app was away. */
    const val GAP_MS = 10 * 60_000L
    /** A stay can be carried on when the app starts again at the same place within this long. */
    const val RESUME_MS = 36 * 3_600_000L
    /** About 4 km/h: under this, standing (or parked). */
    const val STILL_SPEED = 1.1
    /** About 5 km/h: over this, moving off. */
    const val MOVING_SPEED = 1.5
    /** A move shorter than this between stops is GPS wander, unless it was a trip. */
    const val MIN_MOVE_METRES = 250.0
    const val PATH_SPACING = 20.0
    const val SAVE_EVERY_MS = 60_000L
    const val WALK_TOP_KMH = 13.0
    const val WALK_AVERAGE_KMH = 7.5
  }
}
