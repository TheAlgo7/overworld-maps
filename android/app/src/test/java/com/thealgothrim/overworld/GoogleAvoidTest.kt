package com.thealgothrim.overworld

import com.thealgothrim.overworld.routing.GoogleMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google's avoid flags land where its own web client puts them, with every count kept right. */
class GoogleAvoidTest {
  private val template =
      "!1m4!3m2!3d{OLAT}!4d{OLNG}!6e2!1m4!3m2!3d{DLAT}!4d{DLNG}!6e2" +
          "!3m12!1m3!1d24960.741896132306!2d{CLNG}!3d{CLAT}!2m3!1f0.0!2f0.0!3f0.0" +
          "!3m2!1i1024!2i768!4f13.1!6m56!1m5!18b1!30b1!31m1!1b1!34e1!2m4!5m1!6e2!20e3!39b1!6m27!32i1" +
          "!49b1!63m0!66b1!85b1!114b1!149b1!206b1!209b1!212b1!216b1!222b1!223b1!232b1!234b1!235b1" +
          "!244b1!246b1!250b1!253b1!260b1!266b1!270b1!273b1!279b1!281b1!291m0!10b1!12b1!13b1!14b1" +
          "!16b1!17m1!3e1!20m5!1e{MODE}!2e3!5e2!6b1!14b1!46m1!1b0!96b1!99b1!15i10142!20m28!1m6!1m2"

  @Test
  fun `nothing to avoid leaves the request alone`() {
    assertEquals(template, GoogleMaps.withAvoid(template, GoogleMaps.Avoid()))
  }

  @Test
  fun `tolls and highways open the route options group`() {
    val out = GoogleMaps.withAvoid(template, GoogleMaps.Avoid(highways = true, tolls = true))
    assertTrue(out.contains("!6m58!1m5!18b1!30b1!31m1!1b1!34e1!2m6!1b1!2b1!5m1!6e2"))
  }

  @Test
  fun `ferries go before the outer group's tenth field`() {
    val out = GoogleMaps.withAvoid(template, GoogleMaps.Avoid(ferries = true))
    assertTrue(out.contains("!6m57!1m5"))
    assertTrue(out.contains("!291m0!7b1!10b1"))
  }
}
