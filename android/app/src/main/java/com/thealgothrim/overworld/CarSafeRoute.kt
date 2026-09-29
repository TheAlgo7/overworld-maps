package com.thealgothrim.overworld

import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.VisualInstructionContent

/**
 * A route Android Auto can show without crashing.
 *
 * Ferrostar's car bridge sends roundabout, rotary and roundabout-turn steps to Android Auto as
 * "enter and exit the roundabout", and exit-roundabout steps as "exit the roundabout", passing
 * along the step's exit number either way. Android Auto's Maneuver.Builder throws, taking the whole
 * app down, when an enter-and-exit maneuver has no exit number, and also when any other maneuver
 * has one. Valhalla routinely produces both: a route that starts on the roundabout itself (a trip
 * started there, or a reroute after a missed exit, which in Delhi happens all the time) has
 * roundabout steps without a count, and its exit steps carry one.
 *
 * So an exit number stays only on steps that are all enter-and-exit. Enter-and-exit steps without a
 * number become "leave the roundabout", which is what they mean when the count is unknown. Our own
 * HUD draws the same arrow for both (see ui/TurnArrow.kt) and never shows the number.
 */
internal fun Route.safeForCar(): Route {
  val safe = steps.map { it.safeForCar() }
  return if (safe == steps) this else copy(steps = safe)
}

private fun RouteStep.safeForCar(): RouteStep {
  val types = visualInstructions.map { it.primaryContent.maneuverType }
  val numbered = roundaboutExitNumber != null && types.isNotEmpty() && types.all { it in ENTER_AND_EXIT }
  if (numbered) return this
  if (roundaboutExitNumber == null && types.none { it in ENTER_AND_EXIT }) return this
  return copy(
      roundaboutExitNumber = null,
      visualInstructions =
          visualInstructions.map {
            it.copy(
                primaryContent = it.primaryContent.asExit(),
                secondaryContent = it.secondaryContent?.asExit(),
                subContent = it.subContent?.asExit(),
            )
          },
  )
}

/** The Ferrostar types its car bridge turns into Android Auto's enter-and-exit roundabout. */
private val ENTER_AND_EXIT = setOf(ManeuverType.ROUNDABOUT, ManeuverType.ROTARY, ManeuverType.ROUNDABOUT_TURN)

private fun VisualInstructionContent.asExit(): VisualInstructionContent =
    if (maneuverType in ENTER_AND_EXIT) copy(maneuverType = ManeuverType.EXIT_ROUNDABOUT) else this
