package com.thealgothrim.overworld

import com.thealgothrim.overworld.theme.MapTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The car screen's palette (MapTime.carVariant). Play's Android Auto review (4 Nov 2026, rule MR-1):
 * by default a map must be light when the car says day and dark when it says night.
 */
class CarDayNightTest {
  @Test
  fun autoFollowsTheCarNotTheSun() {
    assertEquals("night", MapTime.AUTO.carVariant(carDark = true, bySun = null))
    assertEquals("night", MapTime.AUTO.carVariant(carDark = true, bySun = "dusk"))
    // The car says day: the day map, even with the sun down (the car's sensor wins) or at dusk,
    // whose mid-grey map is neither light nor dark.
    assertNull(MapTime.AUTO.carVariant(carDark = false, bySun = "night"))
    assertNull(MapTime.AUTO.carVariant(carDark = false, bySun = "dusk"))
    assertNull(MapTime.AUTO.carVariant(carDark = false, bySun = null))
  }

  @Test
  fun noCarYetFollowsTheSun() {
    assertEquals("dusk", MapTime.AUTO.carVariant(carDark = null, bySun = "dusk"))
    assertEquals("night", MapTime.AUTO.carVariant(carDark = null, bySun = "night"))
    assertNull(MapTime.AUTO.carVariant(carDark = null, bySun = null))
  }

  @Test
  fun aHeldTimeStaysHeldWhateverTheCarSays() {
    for (dark in listOf(true, false, null)) {
      assertNull(MapTime.DAY.carVariant(dark, "night"))
      assertEquals("dusk", MapTime.DUSK.carVariant(dark, null))
      assertEquals("night", MapTime.NIGHT.carVariant(dark, null))
    }
  }

  @Test
  fun thePhoneStillFollowsTheSun() {
    assertEquals("dusk", MapTime.AUTO.variant("dusk"))
    assertNull(MapTime.AUTO.variant(null))
  }
}
