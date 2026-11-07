package com.thealgothrim.overworld.routing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sign
import uniffi.ferrostar.GeographicCoordinate

/** What the route does at a flyover or underpass where the driver has a choice, in words for the turn card. */
enum class CueKind(val text: String, val title: String) {
  TAKE_FLYOVER("Take the flyover", "Take Flyover"),
  SKIP_FLYOVER("Don't take the flyover", "Avoid Flyover"),
  TAKE_UNDERPASS("Take the underpass", "Take Underpass"),
  SKIP_UNDERPASS("Don't take the underpass", "Avoid Underpass");

  val takes: Boolean
    get() = this == TAKE_FLYOVER || this == TAKE_UNDERPASS

  val flyover: Boolean
    get() = this == TAKE_FLYOVER || this == SKIP_FLYOVER
}

/**
 * A choice at a flyover or underpass. At [at] metres along the route it and the other road split
 * (a route's turn there when there is one, so the card shows both); the flyover or underpass itself
 * starts at [structureAt]. [otherSide] is where the other road goes: -1 left of the route, +1 right.
 */
data class LevelCue(val kind: CueKind, val at: Double, val structureAt: Double, val otherSide: Int)

/**
 * A road beside the route that the car could be on instead (the flyover the route doesn't take, or
 * the service road under the one it does), between [from] and [to] metres along the route. Its
 * own flyover or underpass, if it has one, starts at [structureAt].
 */
class ParallelRoad(val level: Level, val from: Double, val to: Double, val structureAt: Double, val lines: List<List<GeographicCoordinate>>)

/**
 * Where a route goes over or under the city and what runs beside it, worked out from the map's own
 * tiles (see VectorTiles): the turn card says "Take the flyover" or "Don't take the flyover" at the
 * split, like Google Maps does in India, and the GPS check (LevelCheck) knows which road beside the
 * route the car might really be on. On 7 Nov the route left NH-48 for the service road 700 m before
 * the Shankar Chowk flyover with only "Bear left"; the car stayed on NH-48 and went up, and the arrow
 * stayed down on the service road, 10 to 30 m away, until Ambience.
 */
class RoadLevels(
    /** The route this is for. */
    val route: List<GeographicCoordinate>,
    /** The route's length as measured here; along = length - Ferrostar's distance remaining. */
    val length: Double,
    /** Metres along the route this covers (a long trip is looked at ahead of the car). */
    val from: Double,
    val to: Double,
    val cues: List<LevelCue>,
    val parallels: List<ParallelRoad>,
    /** Stretches of the route on a flyover or in an underpass. */
    val structures: List<Pair<ClosedFloatingPointRange<Double>, Level>>,
) {
  fun levelAt(along: Double): Level = structures.firstOrNull { along in it.first }?.second ?: Level.SURFACE

  private val cum: DoubleArray by lazy {
    DoubleArray(route.size).also { c ->
      for (i in 1 until route.size) {
        val k = 111_320.0 * cos(Math.toRadians((route[i].lat + route[i - 1].lat) / 2))
        c[i] = c[i - 1] + hypot((route[i].lng - route[i - 1].lng) * k, (route[i].lat - route[i - 1].lat) * 111_320.0)
      }
    }
  }

  /** The point [along] metres into the route. */
  fun pointAt(along: Double): GeographicCoordinate {
    if (route.size < 2) return route.first()
    val a = along.coerceIn(0.0, cum.last())
    var lo = 0
    var hi = cum.size - 2
    while (lo < hi) {
      val mid = (lo + hi) / 2
      if (a <= cum[mid + 1]) hi = mid else lo = mid + 1
    }
    val t = ((a - cum[lo]) / (cum[lo + 1] - cum[lo]).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
    return GeographicCoordinate(route[lo].lat + (route[lo + 1].lat - route[lo].lat) * t, route[lo].lng + (route[lo + 1].lng - route[lo].lng) * t)
  }

  /** The route's compass heading at [along], over 8 m either side so a kink doesn't swing it. */
  fun headingAt(along: Double): Double {
    val a = pointAt(along - 8)
    val b = pointAt(along + 8)
    return LevelAnalysis.bearing((b.lng - a.lng) * 111_320.0 * cos(Math.toRadians(a.lat)), (b.lat - a.lat) * 111_320.0)
  }

  /** Where the route's own flyover or underpass at [along] started, or null on the ground. */
  fun structureStartAt(along: Double): Double? = structures.firstOrNull { along in it.first }?.first?.start

  /** Whether this was worked out for [geometry] (Ferrostar hands over copies, not the same list). */
  fun matches(geometry: List<GeographicCoordinate>): Boolean =
      geometry.size == route.size && geometry.isNotEmpty() && geometry.first() == route.first() && geometry.last() == route.last() &&
          geometry[geometry.size / 2] == route[route.size / 2]

  companion object {
    /**
     * Works out [route]'s flyovers and underpasses between [from] and [to] metres along it from the
     * [roads] around it. [maneuvers]: where the route's turns are, metres along it; a split close to
     * one is put on it.
     */
    fun analyse(route: List<GeographicCoordinate>, roads: List<TileRoad>, maneuvers: List<Double>, from: Double = 0.0, to: Double = Double.MAX_VALUE): RoadLevels =
        LevelAnalysis(route, roads).run(maneuvers, from, to)
  }
}

/** The trip's levels, for the off-route check, which runs on Ferrostar's own thread (set by the view model). */
object CurrentLevels {
  @Volatile var value: RoadLevels? = null
}

/** The arithmetic of [RoadLevels.analyse], on a flat metre grid around the route's start. */
internal class LevelAnalysis(private val route: List<GeographicCoordinate>, private val roads: List<TileRoad>) {
  private val lat0 = route.firstOrNull()?.lat ?: 0.0
  private val lng0 = route.firstOrNull()?.lng ?: 0.0
  private val kx = 111_320.0 * cos(Math.toRadians(lat0))
  private val ky = 111_320.0

  private fun x(p: GeographicCoordinate) = (p.lng - lng0) * kx
  private fun y(p: GeographicCoordinate) = (p.lat - lat0) * ky

  private val rx = DoubleArray(route.size) { x(route[it]) }
  private val ry = DoubleArray(route.size) { y(route[it]) }
  // Measured segment by segment at each one's own latitude, like Ferrostar measures the distance
  // left: one grid scale for a whole 30 km route put a cue tens of metres off by the far end.
  private val cum =
      DoubleArray(route.size).also { c ->
        for (i in 1 until route.size) {
          val k = 111_320.0 * cos(Math.toRadians((route[i].lat + route[i - 1].lat) / 2))
          c[i] = c[i - 1] + hypot((route[i].lng - route[i - 1].lng) * k, (route[i].lat - route[i - 1].lat) * ky)
        }
      }
  val length = cum.lastOrNull() ?: 0.0

  private class Seg(val road: Int, val ax: Double, val ay: Double, val bx: Double, val by: Double, val heading: Double)

  private val segs = ArrayList<Seg>()
  private val grid = HashMap<Long, MutableList<Int>>()

  init {
    roads.forEachIndexed { r, road ->
      for (i in 0 until road.line.lastIndex) {
        val ax = x(road.line[i])
        val ay = y(road.line[i])
        val bx = x(road.line[i + 1])
        val by = y(road.line[i + 1])
        if (hypot(bx - ax, by - ay) < 0.3) continue
        val index = segs.size
        segs += Seg(r, ax, ay, bx, by, bearing(bx - ax, by - ay))
        val c0x = floor(minOf(ax, bx) / CELL).toLong()
        val c1x = floor(maxOf(ax, bx) / CELL).toLong()
        val c0y = floor(minOf(ay, by) / CELL).toLong()
        val c1y = floor(maxOf(ay, by) / CELL).toLong()
        for (cx in c0x..c1x) for (cy in c0y..c1y) grid.getOrPut(key(cx, cy)) { ArrayList() } += index
      }
    }
  }

  /** A road near a point on the route: [offset] metres to its right (negative: left), [turn] degrees off the route's way. */
  private class Near(val road: Int, val offset: Double, val turn: Double, val level: Level)

  private class Sample(val along: Double, val level: Level?, val beside: List<Near>)

  fun run(maneuvers: List<Double>, from: Double, to: Double): RoadLevels {
    val start = from.coerceIn(0.0, length)
    val end = to.coerceIn(start, length)
    if (route.size < 2 || end - start < STEP) return RoadLevels(route, length, start, end, emptyList(), emptyList(), emptyList())
    val samples = (0..((end - start) / STEP).toInt()).map { sample(start + it * STEP) }

    val structures = structureRuns(samples)
    val cues = ArrayList<LevelCue>()
    val parallels = ArrayList<ParallelRoad>()

    // The route goes up or down: a choice only where a road stays on the ground beside it.
    for ((span, level) in structures) {
      val first = samples.indexOfFirst { it.along >= span.start }.takeIf { it >= 0 } ?: continue
      val checkTo = minOf(span.endInclusive, span.start + ALTERNATIVE_CHECK_M)
      val window = samples.filter { it.along >= span.start && it.along <= checkTo }
      val ground = window.mapNotNull { s -> s.beside.filter { it.level == Level.SURFACE && abs(it.turn) <= PARALLEL_DEG }.minByOrNull { abs(it.offset) } }
      if (window.isEmpty() || ground.size < window.size * ALTERNATIVE_SHARE) continue
      val other = ground.first()
      val fork = forkBefore(samples, first, other.offset) ?: continue
      val roadsBeside = window.flatMap { s -> s.beside.filter { it.level == Level.SURFACE && abs(it.turn) <= PARALLEL_DEG }.map { it.road } }.toSet()
      val kind = if (level == Level.BRIDGE) CueKind.TAKE_FLYOVER else CueKind.TAKE_UNDERPASS
      cues += LevelCue(kind, onTurn(samples[fork.first].along, maneuvers), span.start, sign(other.offset).toInt())
      parallels += ParallelRoad(Level.SURFACE, samples[fork.first].along, span.endInclusive, Double.NaN, lines(fork.second + roadsBeside))
    }

    // A flyover or underpass beside a route that stays on the ground.
    for (level in listOf(Level.BRIDGE, Level.TUNNEL)) {
      for (run in besideRuns(samples, level)) {
        val first = run.first()
        val offset = samples[first].beside.filter { it.level == level && abs(it.turn) <= PARALLEL_DEG }.minByOrNull { abs(it.offset) }?.offset ?: continue
        val fork = forkBefore(samples, first, offset) ?: continue
        val roadsBeside = run.flatMap { i -> samples[i].beside.filter { it.level == level && abs(it.turn) <= PARALLEL_DEG }.map { it.road } }.toSet()
        val kind = if (level == Level.BRIDGE) CueKind.SKIP_FLYOVER else CueKind.SKIP_UNDERPASS
        val structureAt = samples[first].along
        cues += LevelCue(kind, onTurn(samples[fork.first].along, maneuvers), structureAt, sign(offset).toInt())
        parallels += ParallelRoad(level, samples[fork.first].along, samples[run.last()].along, structureAt, lines(fork.second + roadsBeside))
      }
    }

    // One card per split: the nearest structure wins where two are found at the same place.
    val kept = ArrayList<LevelCue>()
    for (c in cues.sortedWith(compareBy({ it.at }, { it.structureAt }))) {
      if (kept.none { abs(it.at - c.at) < SAME_SPLIT_M }) kept += c
    }
    return RoadLevels(route, length, start, end, kept, parallels, structures)
  }

  /** What the analysis sees every [STEP] from [from] to [to] metres along, for tests and debugging. */
  internal fun describe(from: Double, to: Double): List<String> =
      (0..((to - from) / STEP).toInt()).map { k ->
        val s = sample(from + k * STEP)
        "${s.along.toInt()} m: on ${s.level ?: "?"}; beside " +
            s.beside.sortedBy { abs(it.offset) }.joinToString { "${roads[it.road].cls}/${it.level} ${"%.1f".format(it.offset)} m ${"%.0f".format(it.turn)}°" }
      }

  /** The route at [along]: what it's on, and the roads beside it that run its way. */
  private fun sample(along: Double): Sample {
    val i = segmentAt(along)
    val t = ((along - cum[i]) / (cum[i + 1] - cum[i]).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
    val px = rx[i] + (rx[i + 1] - rx[i]) * t
    val py = ry[i] + (ry[i + 1] - ry[i]) * t
    val heading = headingAt(along)
    val ux = kotlin.math.sin(Math.toRadians(heading))
    val uy = kotlin.math.cos(Math.toRadians(heading))
    // Right of the way the route runs.
    val nx = uy
    val ny = -ux
    var carrier: Pair<Double, Level>? = null
    val best = HashMap<Int, Near>()
    val cx = floor(px / CELL).toLong()
    val cy = floor(py / CELL).toLong()
    val seen = HashSet<Int>()
    for (dx in -1L..1L) for (dy in -1L..1L) {
      val cell = grid[key(cx + dx, cy + dy)] ?: continue
      for (index in cell) {
        if (!seen.add(index)) continue
        val s = segs[index]
        val ex = s.bx - s.ax
        val ey = s.by - s.ay
        val len2 = ex * ex + ey * ey
        val raw = ((px - s.ax) * ex + (py - s.ay) * ey) / len2
        val u = raw.coerceIn(0.0, 1.0)
        val qx = s.ax + ex * u - px
        val qy = s.ay + ey * u - py
        val d = hypot(qx, qy)
        if (d > BESIDE_MAX_M) continue
        val turn = angle(s.heading, heading)
        val road = roads[s.road]
        val forward = turn <= TRACK_DEG
        val backward = turn >= 180 - TRACK_DEG
        if (d <= CARRIER_M && (forward || backward)) {
          if (carrier == null || d < carrier.first) carrier = d to road.level
          continue
        }
        // Beside means alongside: a road that starts or ends just ahead is measured to its end
        // point, a diagonal, and the flyover's deck seemed to jump out to 35 m where it began 26 m
        // off (Shankar Chowk), which lost it when followed back to its split.
        val segLength = kotlin.math.sqrt(len2)
        if (raw * segLength < -END_SLACK_M || raw * segLength > segLength + END_SLACK_M) continue
        // Only roads a car can drive the route's way: the other carriageway doesn't count.
        val drivable = when (road.oneway) { 1 -> forward; -1 -> backward; else -> forward || backward }
        if (!drivable) continue
        val offset = if (qx * nx + qy * ny >= 0) d else -d
        val sameWay = if (forward) turn else 180 - turn
        val known = best[s.road]
        if (known == null || abs(offset) < abs(known.offset)) best[s.road] = Near(s.road, offset, sameWay, road.level)
      }
    }
    return Sample(along, carrier?.second, best.values.toList())
  }

  /** The route's own flyovers and underpasses: runs of samples on one, short gaps bridged. */
  private fun structureRuns(samples: List<Sample>): List<Pair<ClosedFloatingPointRange<Double>, Level>> {
    val out = ArrayList<Pair<ClosedFloatingPointRange<Double>, Level>>()
    for (level in listOf(Level.BRIDGE, Level.TUNNEL)) {
      var runStart = -1
      var lastOn = -1
      fun close() {
        if (runStart >= 0 && samples[lastOn].along - samples[runStart].along >= MIN_STRUCTURE_M) out += (samples[runStart].along..samples[lastOn].along) to level
        runStart = -1
      }
      samples.forEachIndexed { i, s ->
        if (s.level == level) {
          if (runStart >= 0 && i - lastOn > GAP_SAMPLES) close()
          if (runStart < 0) runStart = i
          lastOn = i
        }
      }
      close()
    }
    return out.sortedBy { it.first.start }
  }

  /** Runs of samples (indices) where the route is on the ground and a [level] road runs beside it. */
  private fun besideRuns(samples: List<Sample>, level: Level): List<List<Int>> {
    val out = ArrayList<List<Int>>()
    var run = ArrayList<Int>()
    var lastOn = -1
    fun close() {
      if (run.isNotEmpty() && samples[run.last()].along - samples[run.first()].along >= MIN_BESIDE_M) out += run
      run = ArrayList()
    }
    samples.forEachIndexed { i, s ->
      val on = s.level != Level.BRIDGE && s.level != Level.TUNNEL && s.beside.any { it.level == level && abs(it.turn) <= PARALLEL_DEG }
      if (on) {
        if (run.isNotEmpty() && i - lastOn > GAP_SAMPLES) close()
        run += i
        lastOn = i
      }
    }
    close()
    return out
  }

  /**
   * Where the road beside the route at sample [from], [offset] metres to its side, splits from the
   * route: followed back sample by sample (it moves at most a few metres sideways per step, a ramp
   * leaving at up to 40°) until it meets the route. Null when it never does within [MAX_BACK_M]: then
   * it ran beside the route all along, and there's no choice to call out here. With the sample of
   * the split, the roads followed on the way.
   */
  private fun forkBefore(samples: List<Sample>, from: Int, offset: Double): Pair<Int, Set<Int>>? {
    var tracked = offset
    val followed = HashSet<Int>()
    var misses = 0
    var i = from
    while (i > 0 && samples[from].along - samples[i - 1].along <= MAX_BACK_M) {
      i--
      val next = samples[i].beside.filter { abs(it.turn) <= TRACK_DEG }.minByOrNull { abs(it.offset - tracked) }
      if (next != null && abs(next.offset - tracked) <= TRACK_JUMP_M) {
        tracked = next.offset
        followed += next.road
        misses = 0
        if (abs(tracked) <= MERGE_M) return i to followed
      } else {
        // Lost close to the route: it became the road the route is on, the split is here.
        if (abs(tracked) <= MERGE_NEAR_M) return i to followed
        if (++misses > MAX_MISSES) return null
      }
    }
    return null
  }

  /** [along], or the route's turn within [ON_TURN_M] of it. */
  private fun onTurn(along: Double, maneuvers: List<Double>): Double =
      maneuvers.filter { abs(it - along) <= ON_TURN_M }.minByOrNull { abs(it - along) } ?: along

  private fun lines(roadIndices: Set<Int>): List<List<GeographicCoordinate>> = roadIndices.map { roads[it].line }

  private fun segmentAt(along: Double): Int {
    var lo = 0
    var hi = cum.size - 2
    while (lo < hi) {
      val mid = (lo + hi) / 2
      if (along <= cum[mid + 1]) hi = mid else lo = mid + 1
    }
    return lo.coerceAtLeast(0)
  }

  /** The route's direction around [along], over [HEADING_SPAN_M] either side so a kink doesn't swing it. */
  private fun headingAt(along: Double): Double {
    val a = (along - HEADING_SPAN_M).coerceAtLeast(0.0)
    val b = (along + HEADING_SPAN_M).coerceAtMost(length)
    val (ax, ay) = pointAt(a)
    val (bx, by) = pointAt(b)
    return bearing(bx - ax, by - ay)
  }

  private fun pointAt(along: Double): Pair<Double, Double> {
    val i = segmentAt(along)
    val t = ((along - cum[i]) / (cum[i + 1] - cum[i]).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
    return (rx[i] + (rx[i + 1] - rx[i]) * t) to (ry[i] + (ry[i + 1] - ry[i]) * t)
  }

  companion object {
    const val STEP = 5.0
    /** A road this close that runs the route's way is the one the route is on. */
    const val CARRIER_M = 3.5
    const val BESIDE_MAX_M = 45.0
    /** Running beside the route: within this many degrees of its way. */
    const val PARALLEL_DEG = 25.0
    /** Followed back to a split: a ramp can leave at up to this angle. */
    const val TRACK_DEG = 40.0
    const val TRACK_JUMP_M = 4.5
    const val MERGE_M = 4.0
    const val MERGE_NEAR_M = 8.0
    const val MAX_MISSES = 3
    const val MAX_BACK_M = 1_200.0
    const val GAP_SAMPLES = 4
    const val MIN_STRUCTURE_M = 40.0
    const val MIN_BESIDE_M = 60.0
    /** The ground road beside the route's own flyover is looked for over its first stretch. */
    const val ALTERNATIVE_CHECK_M = 200.0
    const val ALTERNATIVE_SHARE = 0.4
    const val ON_TURN_M = 70.0
    const val SAME_SPLIT_M = 50.0
    const val HEADING_SPAN_M = 8.0
    /** How far past a road piece's end a point may sit and still be beside it. */
    const val END_SLACK_M = 2.0
    private const val CELL = 50.0

    private fun key(x: Long, y: Long) = (x shl 32) xor (y and 0xffffffffL)

    /** Compass degrees of a step east [dx], north [dy]. */
    fun bearing(dx: Double, dy: Double) = (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0

    /** The angle between two compass headings, 0 to 180. */
    fun angle(a: Double, b: Double) = abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)
  }
}
