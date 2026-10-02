package com.thealgothrim.overworld.search

import kotlin.math.floor
import kotlin.math.pow

// Places written as text, read without any search server: typed coordinates, Google's plus codes
// ("F5QR+3F New Delhi", shown on every Google Maps place page) and Google Maps links.

/** "28.4877, 77.1912" or "28.4877 77.1912" as (lat, lng), else null. */
fun coordinatesIn(text: String): Pair<Double, Double>? {
  val m = COORDINATES.matchEntire(text.trim()) ?: return null
  val lat = m.groupValues[1].toDoubleOrNull() ?: return null
  val lng = m.groupValues[2].toDoubleOrNull() ?: return null
  if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || (lat == 0.0 && lng == 0.0)) return null
  return lat to lng
}

private val COORDINATES = Regex("""(-?\d{1,2}(?:\.\d+)?)\s*[,\s]\s*(-?\d{1,3}(?:\.\d+)?)""")

/** A plus code at the start of [text] and what follows it ("F5QR+3F" and "New Delhi, Delhi"), else null. */
fun plusCodeIn(text: String): Pair<String, String>? {
  val m = PLUS_CODE.find(text.trim().uppercase()) ?: return null
  if (m.range.first != 0) return null
  val code = m.value
  val rest = text.trim().substring(m.range.last + 1).trim().trimStart(',').trim()
  return code to rest
}

private val PLUS_CODE = Regex("""[23456789CFGHJMPQRVWX]{2,8}\+[23456789CFGHJMPQRVWX]{0,3}""")

/**
 * The centre of a plus code. A short one (Google drops the first four characters when a town is
 * named) is placed near [refLat], [refLng], which must be within about 50 km of it.
 */
fun decodePlusCode(code: String, refLat: Double?, refLng: Double?): Pair<Double, Double>? {
  val c = code.uppercase()
  val plus = c.indexOf('+')
  if (plus < 2 || plus > 8 || plus % 2 != 0) return null
  if (plus == 8) return decodeFull(c)
  if (refLat == null || refLng == null) return null
  // Recover a short code from the reference point (the open-location-code "recover nearest").
  val padding = 8 - plus
  val resolution = 20.0.pow(2 - padding / 2)
  val half = resolution / 2
  val full = encode(refLat, refLng).substring(0, padding) + c
  val (lat0, lng0) = decodeFull(full) ?: return null
  var lat = lat0
  var lng = lng0
  if (refLat + half < lat && lat - resolution >= -90) lat -= resolution
  else if (refLat - half > lat && lat + resolution <= 90) lat += resolution
  if (refLng + half < lng) lng -= resolution else if (refLng - half > lng) lng += resolution
  return lat to lng
}

private const val ALPHABET = "23456789CFGHJMPQRVWX"
private val PAIR_RESOLUTIONS = doubleArrayOf(20.0, 1.0, 0.05, 0.0025, 0.000125)

private fun decodeFull(code: String): Pair<Double, Double>? {
  val digits = code.replace("+", "").trimEnd('0')
  if (digits.isEmpty() || digits.any { it !in ALPHABET }) return null
  var lat = -90.0
  var lng = -180.0
  var last = 0.0
  var i = 0
  while (i + 1 < digits.length && i / 2 < PAIR_RESOLUTIONS.size) {
    val res = PAIR_RESOLUTIONS[i / 2]
    lat += ALPHABET.indexOf(digits[i]) * res
    lng += ALPHABET.indexOf(digits[i + 1]) * res
    last = res
    i += 2
  }
  return (lat + last / 2) to (lng + last / 2)
}

/** The first eight characters of the full code for a point (all a short code can need). */
private fun encode(lat: Double, lng: Double): String {
  var y = (lat.coerceIn(-90.0, 89.999999) + 90)
  var x = (((lng + 180) % 360 + 360) % 360)
  val out = StringBuilder()
  for (res in PAIR_RESOLUTIONS.take(4)) {
    val dy = floor(y / res).toInt()
    val dx = floor(x / res).toInt()
    out.append(ALPHABET[dy]).append(ALPHABET[dx])
    y -= dy * res
    x -= dx * res
  }
  return out.toString()
}

/** The first web link in shared text (Google Maps shares "Name\nhttps://maps.app.goo.gl/..."). */
fun linkIn(text: String): String? = LINK.find(text)?.value?.trimEnd('.', ',', ')')

private val LINK = Regex("""https?://\S+""")

/**
 * Where a Google Maps link points, read from the link itself: the place's own pin
 * (!3d<lat>!4d<lng>), else a "q=" or "ll=" point, else the map's centre (@lat,lng).
 */
fun coordinatesInMapsLink(url: String): Pair<Double, Double>? {
  PIN.find(url)?.let { m -> return m.groupValues[1].toDouble() to m.groupValues[2].toDouble() }
  QUERY_POINT.find(url)?.let { m -> return m.groupValues[2].toDouble() to m.groupValues[3].toDouble() }
  CENTRE.find(url)?.let { m -> return m.groupValues[1].toDouble() to m.groupValues[2].toDouble() }
  return null
}

private val PIN = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
private val QUERY_POINT = Regex("""[?&](q|ll|query|destination|daddr|center)=(-?\d+\.\d+)(?:,|%2C)\s*(-?\d+\.\d+)""")
private val CENTRE = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")

/** The place name in a Google Maps link (/maps/place/Prarthana+Bhavan/...), else null. */
fun placeNameInMapsLink(url: String): String? =
    Regex("""/maps/place/([^/@?]+)""").find(url)?.groupValues?.get(1)?.let {
      java.net.URLDecoder.decode(it.replace("+", "%20"), "UTF-8").trim()
    }?.takeIf { it.isNotEmpty() && coordinatesIn(it) == null }
