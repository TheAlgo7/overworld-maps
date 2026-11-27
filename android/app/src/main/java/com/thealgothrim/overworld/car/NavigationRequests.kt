package com.thealgothrim.overworld.car

import android.content.Intent
import com.stadiamaps.ferrostar.car.app.intent.CarAppMapIntent
import com.stadiamaps.ferrostar.car.app.intent.CarAppMapIntentParser

/** Where a navigation request wants to go: a point, a name to search for, or both. */
data class NavigationDestination(val latitude: Double?, val longitude: Double?, val query: String?)

/**
 * Where a navigation request wants to go: "Hey Google, navigate to India Gate" in Android Auto, or
 * an address tapped in a message. They arrive as geo: links (geo:28.61,77.22, geo:0,0?q=India+Gate,
 * geo:0,0?q=28.61,77.22(India Gate)) or Google Maps' google.navigation:q=... links.
 *
 * Ferrostar's old NavigationIntentParser threw on every geo: link (Uri.getQueryParameter on an
 * opaque URI), so this used to read the links itself. Since Ferrostar 0.59 its CarAppMapIntentParser
 * reads them safely, covering the same forms and more (labelled pins, avoid and mode).
 */
internal fun Intent.navigationDestination(): NavigationDestination? =
    when (val parsed = CarAppMapIntentParser.parse(this)) {
      is CarAppMapIntent.Navigate ->
          when (val d = parsed.destination) {
            is CarAppMapIntent.Navigate.Destination.Coordinate -> NavigationDestination(d.location.lat, d.location.lng, d.label)
            is CarAppMapIntent.Navigate.Destination.Query -> NavigationDestination(null, null, d.query)
          }
      is CarAppMapIntent.Pin -> NavigationDestination(parsed.location.lat, parsed.location.lng, parsed.label)
      // A search near a point (geo:lat,lng?q=Name opened on the phone): the point, named.
      is CarAppMapIntent.Search -> NavigationDestination(parsed.near?.lat, parsed.near?.lng, parsed.query)
      is CarAppMapIntent.DisplayMap -> NavigationDestination(parsed.center.lat, parsed.center.lng, null)
      else -> null
    }
