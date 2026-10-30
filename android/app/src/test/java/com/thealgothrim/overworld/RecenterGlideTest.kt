package com.thealgothrim.overworld

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.thealgothrim.overworld.map.between
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position

class RecenterGlideTest {
  private val a = CameraPosition(target = Position(77.0, 28.0), zoom = 12.0, tilt = 0.0, bearing = 350.0, padding = PaddingValues(0.dp))
  private val b = CameraPosition(target = Position(77.2, 28.2), zoom = 16.0, tilt = 45.0, bearing = 10.0, padding = PaddingValues(top = 200.dp))

  @Test
  fun endsExactlyOnTheCar() {
    val end = between(a, b, 1f)
    assertEquals(77.2, end.target.longitude, 1e-9)
    assertEquals(16.0, end.zoom, 1e-9)
    assertEquals(45.0, end.tilt, 1e-9)
    assertEquals(10.0, end.bearing, 1e-6)
    assertEquals(200f, end.padding.calculateTopPadding().value, 1e-3f)
  }

  @Test
  fun turnsTheShortWayRound() {
    // 350° to 10° is 20° through north, not 340° the long way.
    assertEquals(0.0, between(a, b, 0.5f).bearing, 1e-6)
  }
}
