package com.thealgothrim.overworld.car

import android.content.Intent
import android.net.Uri
import com.stadiamaps.ferrostar.car.app.intent.NavigationDestination
import com.stadiamaps.ferrostar.car.app.intent.NavigationIntentParser

/**
 * Where a navigation request wants to go: "Hey Google, navigate to India Gate" in Android Auto, or
 * an address tapped in a message. They arrive as geo: links (geo:28.61,77.22, geo:0,0?q=India+Gate,
 * geo:0,0?q=28.61,77.22(India Gate)) or Google Maps' google.navigation:q=... links.
 *
 * Ferrostar's NavigationIntentParser reads the q= part with Uri.getQueryParameter, which throws on
 * every geo: link (they are "opaque" URIs, not scheme://host ones), so a navigation request closed
 * the app in the car. This reads the link itself and keeps Ferrostar's coordinate rules.
 */
internal fun Intent.navigationDestination(): NavigationDestination? {
  val uri = data ?: return null
  val ssp = uri.encodedSchemeSpecificPart ?: return null
  return when (uri.scheme) {
    // geo:lat,lng[;u=...][?q=...]
    "geo" -> {
      val q = params(ssp.substringAfter('?', ""))["q"]
      val labelled = q?.let(::labelledCoordinates)
      labelled
          ?: NavigationIntentParser.parseGeoSsp(
              coordString = Uri.decode(ssp.substringBefore('?').substringBefore(';')),
              query = q,
          )
    }
    // google.navigation:q=lat,lng or google.navigation:q=place+name[&mode=d]
    "google.navigation" -> params(ssp)["q"]?.let { labelledCoordinates(it) ?: NavigationIntentParser.parseGoogleNavigationSsp(it) }
    else -> null
  }
}

/** a=1&b=two+words → {a: 1, b: two words} */
private fun params(query: String): Map<String, String> =
    query
        .split('&')
        .mapNotNull { pair ->
          val (key, value) = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
          Uri.decode(key) to Uri.decode(value.replace('+', ' ')).trim()
        }
        .filter { it.second.isNotEmpty() }
        .toMap()

/** Google Maps' "lat,lng(Label)" (or plain "lat,lng") as a destination, else null. */
private fun labelledCoordinates(q: String): NavigationDestination? {
  val match = LABELLED.matchEntire(q) ?: return null
  val lat = match.groupValues[1].toDoubleOrNull() ?: return null
  val lng = match.groupValues[2].toDoubleOrNull() ?: return null
  if (lat !in -90.0..90.0 || lng !in -180.0..180.0 || (lat == 0.0 && lng == 0.0)) return null
  return NavigationDestination(lat, lng, match.groupValues[3].trim().ifEmpty { null })
}

private val LABELLED = Regex("""\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*""")
