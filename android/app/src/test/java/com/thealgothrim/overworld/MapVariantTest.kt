package com.thealgothrim.overworld

import com.thealgothrim.overworld.theme.THEMES
import com.thealgothrim.overworld.theme.isRadar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which style file each world loads. Since GTA VI's launch (22 Nov 2026) its own palette is the
 * pause map (day, and "night"), and on a phone trip it turns into the minimap by time of day
 * ("radar", "radar-dusk", "radar-night"); GTA V and Red Dead have one radar.
 */
class MapVariantTest {
  private fun theme(id: String) = THEMES.first { it.id == id }

  @Test
  fun gtaViKeepsAMinimapForEachTimeOfDay() {
    val vi = theme("gta6")
    assertEquals("radar", vi.radarFor(null))
    assertEquals("radar-dusk", vi.radarFor("dusk"))
    assertEquals("radar-night", vi.radarFor("night"))
  }

  @Test
  fun gtaVAndRedDeadHaveOneRadar() {
    for (id in listOf("gta5", "rdr2")) {
      assertEquals("radar", theme(id).radarFor(null))
      assertEquals("radar", theme(id).radarFor("night"))
    }
  }

  @Test
  fun gtaViPauseMapHasDayDuskAndNight() {
    // The pause map has a golden hour of its own (derived), and a night; so does the car.
    val vi = theme("gta6")
    assertEquals("styles/gta6-dusk.json", vi.styleAsset(car = false, variant = "dusk"))
    assertEquals("styles/gta6-dusk-car.json", vi.styleAsset(car = true, variant = "dusk"))
    assertEquals("styles/gta6-night-car.json", vi.styleAsset(car = true, variant = "night"))
    assertEquals("styles/gta6-radar-dusk.json", vi.styleAsset(car = false, variant = "radar-dusk"))
    assertFalse(vi.isDark("dusk"))
    assertTrue(vi.isDark("night"))
  }

  @Test
  fun onlyGtaViOffersTimeOfDay() {
    assertTrue(theme("gta6").timeOfDay)
    assertFalse(theme("gta5").timeOfDay)
    assertFalse(theme("rdr2").timeOfDay)
  }

  @Test
  fun radarNames() {
    assertTrue(isRadar("radar"))
    assertTrue(isRadar("radar-night"))
    assertFalse(isRadar("night"))
    assertFalse(isRadar(null))
  }

  @Test
  fun seaDepthWhereTheSeaIsShaded() {
    assertTrue(theme("gta5").seaDepth)
    assertTrue(theme("gta6").seaDepth)
    assertFalse(theme("rdr2").seaDepth)
  }
}
