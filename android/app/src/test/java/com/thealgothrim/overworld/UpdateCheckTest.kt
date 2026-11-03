package com.thealgothrim.overworld

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
  @Test
  fun comparesVersionsPartByPart() {
    assertTrue(UpdateCheck.newer("0.5.9", "0.5.8"))
    assertTrue(UpdateCheck.newer("0.6.0", "0.5.12"))
    assertTrue(UpdateCheck.newer("0.5.10", "0.5.9"))
    assertTrue(UpdateCheck.newer("1.0", "0.9.9"))
    assertFalse(UpdateCheck.newer("0.5.8", "0.5.8"))
    assertFalse(UpdateCheck.newer("0.5.7", "0.5.8"))
    assertFalse(UpdateCheck.newer("beta", "0.5.8"))
  }
}
