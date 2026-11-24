package com.thealgothrim.overworld

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.VisualInstruction
import uniffi.ferrostar.VisualInstructionContent

class ShownInstructionTest {
  private fun visual(text: String, trigger: Double) =
      VisualInstruction(VisualInstructionContent(text, ManeuverType.TURN, ManeuverModifier.RIGHT, null, null, emptyList()), null, null, trigger)

  private val first = visual("Turn right", 89.0)
  private val last = visual("Turn right now", 15.0)
  private val step =
      RouteStep(
          listOf(GeographicCoordinate(28.4997, 77.1837), GeographicCoordinate(28.4993, 77.1845)),
          89.0, 70.0, null, emptyList(), "Turn right.", listOf(first, last), emptyList(), null, emptyList(), null, null,
      )

  @Test
  fun ferrostarsInstructionWinsWhenItHasOne() {
    assertSame(last, shownInstruction(last, step))
  }

  @Test
  fun offTheRouteTheStepsOwnTurnStillShows() {
    // 24 Nov walk: off the route Ferrostar gave no instruction, and the card went blank.
    assertSame(first, shownInstruction(null, step))
  }

  @Test
  fun nothingWithNoStep() {
    assertNull(shownInstruction(null, null))
  }
}
