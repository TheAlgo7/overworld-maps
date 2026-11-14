package com.thealgothrim.overworld

import com.thealgothrim.overworld.prefs.SupportAsk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportAskTest {
  @Test
  fun asksOnlyAfterEnoughFinishedTrips() {
    assertFalse(SupportAsk.due(SupportAsk.TRIPS - 1, answered = false, carConnected = false, onTrip = false))
    assertTrue(SupportAsk.due(SupportAsk.TRIPS, answered = false, carConnected = false, onTrip = false))
  }

  @Test
  fun neverAsksAgainOnceAnswered() {
    assertFalse(SupportAsk.due(500, answered = true, carConnected = false, onTrip = false))
  }

  @Test
  fun neverAsksWithACarConnectedOrOnATrip() {
    assertFalse(SupportAsk.due(50, answered = false, carConnected = true, onTrip = false))
    assertFalse(SupportAsk.due(50, answered = false, carConnected = false, onTrip = true))
  }
}