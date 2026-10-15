package com.thealgothrim.overworld

import com.thealgothrim.overworld.routing.GoogleMaps
import com.thealgothrim.overworld.routing.OverworldRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google's answers as saved on 15 Oct 2026 (src/test/resources/google), read the way the app reads them. */
class GoogleMapsTest {
  private fun fixture(name: String) = javaClass.classLoader!!.getResource("google/$name")!!.readText()

  @Test
  fun readsDrivingRoutesWithTrafficAndLines() {
    val routes = GoogleMaps.parse(fixture("directions-cp-chhatarpur.txt"))
    assertEquals(3, routes.size)
    val best = routes.first()
    assertEquals("Sri Aurobindo Marg", best.via)
    assertEquals(15313.0, best.metres, 1.0)
    assertTrue(best.trafficSeconds != null && best.trafficSeconds!! > 0)
    // The line starts at Connaught Place and ends near Chhatarpur.
    assertEquals(28.6315, best.line.first().lat, 0.001)
    assertEquals(28.5248, best.line.last().lat, 0.001)
    assertTrue(best.line.size > 500)
    assertTrue(best.jams.all { it.level in 1..3 && it.length > 0 })
  }

  @Test
  fun thinnedKeepsEndsAndSpacing() {
    val line = GoogleMaps.parse(fixture("directions-cp-chhatarpur.txt")).first().line
    val thin = OverworldRoutes.thinned(line)
    assertEquals(line.first(), thin.first())
    assertEquals(line.last(), thin.last())
    assertTrue(thin.size in 200..2_001)
  }

  @Test
  fun searchFindsPrarthanaBhavanInRajpurKhurd() {
    val places = GoogleMaps.parseSearch(fixture("search-prarthana.txt"))
    assertTrue(places.size >= 5)
    val first = places.first()
    assertEquals("Prarthana Bhavan", first.name)
    assertTrue(first.address!!.contains("Rajpur Khurd"))
    assertEquals(28.4876, first.at.lat, 0.001)
    assertEquals(77.1912, first.at.lng, 0.001)
  }
}
