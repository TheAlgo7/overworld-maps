package com.thealgothrim.overworld.search

import java.text.Normalizer
import kotlin.math.log10

/** What a search result is: an area (Chhatarpur), a road, a place (a temple, a shop) or an address. */
enum class PlaceKind { AREA, STREET, PLACE, ADDRESS }

/** A result from one source, with what ranking needs. [rank] is its position in that source's list. */
data class Candidate(
    val place: Place,
    val kind: PlaceKind,
    val tomtom: Boolean,
    val rank: Int,
    /** Metres from the user, when known. */
    val metres: Double? = null,
    val abroad: Boolean = false,
)

/**
 * A name folded the way Indian names vary when written in English, so that Chhatarpur, Chattarpur
 * and Chhattarpur, or Bhawan and Bhavan, compare equal: no accents, no h, w as v, ee as i, oo as u,
 * and no doubled letters.
 */
fun fold(text: String): String {
  val plain = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
  val words = plain.replace(Regex("[^a-z0-9]+"), " ").trim()
  val spelled =
      words.replace("ee", "i").replace("oo", "u").replace("ph", "f").replace("ck", "k")
          .replace('w', 'v').replace('q', 'k').replace("h", "")
  val out = StringBuilder()
  for (c in spelled) if (out.isEmpty() || out.last() != c || c == ' ' || c.isDigit()) out.append(c)
  return out.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { if (it.length > 2 && it.endsWith('y')) it.dropLast(1) + "i" else it }
}

/**
 * How well [name] answers [query], 0 to 100: the same name, a name starting with it, every word
 * starting a word of the name (the last word may be half typed), every word somewhere in it, or
 * only the source's own fuzzy match.
 */
fun matchScore(query: String, name: String, detail: String = ""): Int {
  val q = fold(query)
  val n = fold(name)
  if (q.isEmpty()) return 0
  val qWords = q.split(' ')
  val nWords = n.split(' ')
  return when {
    n == q -> 100
    n.startsWith(q) -> 80
    qWords.all { w -> nWords.any { it.startsWith(w) } } -> 60
    qWords.all { it in n } -> 40
    qWords.all { it in "$n ${fold(detail)}" } -> 30
    else -> 10
  }
}

/**
 * The results of all sources in one list, best first, the same place once.
 *
 * The score is the name match, plus a lift for areas when their name is what was typed (typing
 * "Chhatarpur" means the area, not the first shop with Chhatarpur in its name), less a little for
 * distance (a slow log, so an exact match in another city still beats a loose one nearby) and for
 * each step down its source's own list. Places abroad only fill the end.
 */
fun rank(query: String, candidates: List<Candidate>, limit: Int = 10): List<Place> {
  fun local(c: Candidate) = (c.metres ?: 0.0) < LOCAL_METRES
  // Typed "Chhatarpur" with Delhi's Chhatarpur nearby: the towns of that name in Rajasthan and
  // Madhya Pradesh come after the temple and the metro station, not before them. With no such
  // name nearby ("Jaipur" from Delhi), the far city still comes first.
  val namedNearby = candidates.any { local(it) && matchScore(query, it.place.name) == 100 }
  fun score(c: Candidate): Double {
    val match = matchScore(query, c.place.name, c.place.detail)
    var s = match.toDouble()
    if (match >= 80) {
      s += when (c.kind) {
        PlaceKind.AREA -> if (local(c)) 25.0 else 10.0
        PlaceKind.STREET -> 5.0
        else -> 0.0
      }
    }
    c.metres?.let { s -= 12 * log10(1 + it / 3000.0) }
    if (namedNearby && !local(c)) s -= 30.0
    s -= c.rank * 0.8
    if (c.tomtom && c.kind == PlaceKind.PLACE) s += 2.0
    if (c.abroad) s -= 40.0
    return s
  }
  val out = mutableListOf<Candidate>()
  for (c in candidates.sortedByDescending(::score)) {
    val same =
        out.any { o ->
          val apart = com.thealgothrim.overworld.traffic.metres(o.place.coordinate, c.place.coordinate)
          val near = if (o.kind == PlaceKind.AREA && c.kind == PlaceKind.AREA) 2_500.0 else 150.0
          apart < near && fold(o.place.name) == fold(c.place.name)
        }
    if (!same) out += c
    if (out.size == limit) break
  }
  return out.map { it.place }
}

/** Within this, a place counts as in the same city region (Gurgaon to Chhatarpur is 16 km). */
private const val LOCAL_METRES = 60_000.0
