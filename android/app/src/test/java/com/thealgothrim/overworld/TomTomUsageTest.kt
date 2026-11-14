package com.thealgothrim.overworld

import com.thealgothrim.overworld.traffic.TomTomUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TomTomUsageTest {
  @Test
  fun sortsEachCallIntoItsKind() {
    val host = "api.tomtom.com"
    assertEquals("incidents", TomTomUsage.kind(host, "/traffic/services/5/incidentDetails"))
    assertEquals("route times", TomTomUsage.kind(host, "/routing/1/calculateRoute/28.6,77.2:28.5,77.1/json"))
    assertEquals("search along route", TomTomUsage.kind(host, "/search/2/searchAlongRoute/petrol%20station.json"))
    assertEquals("nearby", TomTomUsage.kind(host, "/search/2/nearbySearch/.json"))
    assertEquals("search", TomTomUsage.kind(host, "/search/2/search/india%20gate.json"))
    assertEquals("traffic tiles", TomTomUsage.kind(host, "/traffic/map/4/tile/flow/relative/14/1/2.pbf"))
  }

  @Test
  fun leavesOtherServersAlone() {
    assertNull(TomTomUsage.kind("photon.komoot.io", "/search/2/search/x.json"))
  }
}