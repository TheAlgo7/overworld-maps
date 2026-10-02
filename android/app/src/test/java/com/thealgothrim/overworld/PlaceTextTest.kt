package com.thealgothrim.overworld

import com.thealgothrim.overworld.search.coordinatesIn
import com.thealgothrim.overworld.search.coordinatesInMapsLink
import com.thealgothrim.overworld.search.decodePlusCode
import com.thealgothrim.overworld.search.linkIn
import com.thealgothrim.overworld.search.placeNameInMapsLink
import com.thealgothrim.overworld.search.plusCodeIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Places written as text: plus codes from Google's place pages, typed coordinates, Maps links. */
class PlaceTextTest {
  private fun near(expected: Pair<Double, Double>, actual: Pair<Double, Double>?) {
    assertNotNull(actual)
    assertEquals(expected.first, actual!!.first, 0.0002)
    assertEquals(expected.second, actual.second, 0.0002)
  }

  @Test
  fun shortPlusCodeFromGoogleIsPlacedNearHere() {
    // Prarthana Bhavan, Rajpur Khurd Extension: "F5QR+3F New Delhi, Delhi" on its Google page.
    assertEquals("F5QR+3F" to "New Delhi, Delhi", plusCodeIn("F5QR+3F New Delhi, Delhi"))
    near(28.48769 to 77.19119, decodePlusCode("F5QR+3F", 28.5068, 77.1749))
    // From across Delhi the same short code lands on the same spot.
    near(28.48769 to 77.19119, decodePlusCode("F5QR+3F", 28.6315, 77.2167))
  }

  @Test
  fun fullPlusCodeNeedsNoReference() {
    near(28.48769 to 77.19119, decodePlusCode("7JWVF5QR+3F", null, null))
    assertNull(decodePlusCode("F5QR+3F", null, null))
    assertNull(plusCodeIn("Prarthana Bhavan"))
  }

  @Test
  fun typedCoordinates() {
    near(28.4877 to 77.1912, coordinatesIn("28.4877, 77.1912"))
    near(28.4877 to 77.1912, coordinatesIn(" 28.4877 77.1912 "))
    assertNull(coordinatesIn("Sector 18"))
    assertNull(coordinatesIn("0, 0"))
  }

  @Test
  fun googleMapsLinks() {
    val text = "Prarthana Bhavan\nhttps://maps.app.goo.gl/AbC123xyz?g_st=ac"
    assertEquals("https://maps.app.goo.gl/AbC123xyz?g_st=ac", linkIn(text))
    val place =
        "https://www.google.com/maps/place/Prarthana+Bhavan/@28.4877,77.1886,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d28.4876941!4d77.1911887!16s?entry=tts"
    near(28.4876941 to 77.1911887, coordinatesInMapsLink(place))
    assertEquals("Prarthana Bhavan", placeNameInMapsLink(place))
    near(28.6129 to 77.2295, coordinatesInMapsLink("https://maps.google.com/?q=28.6129,77.2295"))
    near(28.6 to 77.2, coordinatesInMapsLink("https://www.google.com/maps/@28.6,77.2,15z"))
    assertNull(coordinatesInMapsLink("https://maps.google.com/?cid=123"))
  }
}
