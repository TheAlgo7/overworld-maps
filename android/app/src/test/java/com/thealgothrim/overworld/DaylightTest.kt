package com.thealgothrim.overworld

import com.thealgothrim.overworld.theme.Daylight
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Delhi on 9 October 2026: sunrise about 6:19 am, sunset about 5:57 pm IST. */
class DaylightTest {
  private val ist = ZoneId.of("Asia/Kolkata")

  private fun altitude(hour: Int, minute: Int) =
      Daylight.sunAltitude(28.6139, 77.2090, ZonedDateTime.of(2026, 10, 9, hour, minute, 0, 0, ist).toInstant())

  @Test
  fun noonIsHighAndMidnightIsLow() {
    val noon = altitude(12, 5)
    // 90 - latitude + declination (about -6.3 degrees on 9 October) is about 55 degrees.
    assertEquals(55.1, noon, 1.0)
    assertTrue("midnight ${altitude(0, 5)}", altitude(0, 5) < -50)
  }

  @Test
  fun sunriseAndSunsetCrossTheHorizon() {
    // The geometric horizon, a few minutes off the published times (refraction lifts the sun).
    assertTrue("6:10 am ${altitude(6, 10)}", altitude(6, 10) < 0)
    assertTrue("6:30 am ${altitude(6, 30)}", altitude(6, 30) > 0)
    assertTrue("5:45 pm ${altitude(17, 45)}", altitude(17, 45) > 0)
    assertTrue("6:05 pm ${altitude(18, 5)}", altitude(18, 5) < 0)
  }

  @Test
  fun phasesFollowTheSun() {
    assertNull(Daylight.phase(altitude(15, 0)))
    assertEquals("dusk", Daylight.phase(altitude(17, 45)))
    assertEquals("night", Daylight.phase(altitude(19, 0)))
    assertEquals("dusk", Daylight.phase(altitude(6, 20)))
  }
}
