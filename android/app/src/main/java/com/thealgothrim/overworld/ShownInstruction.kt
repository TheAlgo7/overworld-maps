package com.thealgothrim.overworld

import com.stadiamaps.ferrostar.core.NavigationUiState
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.VisualInstruction

/**
 * The turn to show: Ferrostar's, or when it gives none, the step's own. Ferrostar hands back no
 * instruction once the user is off the route, or further from the turn than the step's first
 * banner reaches (just after a reroute). The turn card then showed only a distance over an empty
 * arrow square for the rest of a walk through a lane OpenStreetMap doesn't have (24 Nov), and
 * Android Auto's card disappeared.
 */
fun shownInstruction(given: VisualInstruction?, step: RouteStep?): VisualInstruction? = given ?: step?.visualInstructions?.firstOrNull()

/** [shownInstruction] for the phone and car screens' state. */
val NavigationUiState.shownInstruction: VisualInstruction?
  get() = shownInstruction(visualInstruction, remainingSteps?.firstOrNull())
