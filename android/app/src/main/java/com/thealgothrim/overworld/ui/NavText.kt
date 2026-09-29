package com.thealgothrim.overworld.ui

import com.stadiamaps.ferrostar.core.NavigationUiState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import uniffi.ferrostar.GeographicCoordinate

// Shared by the game skins (GTA V, RDR2): turn sentences and small geometry helpers.

/** Turns Ferrostar's maneuver data into a game-style help-text sentence. */
internal fun turnSentence(uiState: NavigationUiState, destination: String?): String {
  val content = uiState.visualInstruction?.primaryContent ?: return "Follow the route."
  // Named roads come through as the road name; unnamed ones as a sentence ("Bear left.").
  val road = content.text.trim().takeUnless { it.endsWith(".") }.orEmpty()
  val type = content.maneuverType?.name.orEmpty()
  val mod = content.maneuverModifier?.name.orEmpty()
  val side = when { "LEFT" in mod -> "left"; "RIGHT" in mod -> "right"; else -> "" }
  val dist = uiState.progress?.distanceToNextManeuver?.let(::formatDistance)
  if (type == "ARRIVE") {
    val where = destination?.takeIf { it.isNotBlank() } ?: road.ifBlank { "your destination" }
    return if (dist != null) "In $dist, arrive at $where." else "Arrive at $where."
  }
  val verb =
      when (type) {
        "DEPART" -> "head out"
        "MERGE" -> "merge"
        "ON_RAMP" -> "take the ramp"
        "OFF_RAMP" -> "take the exit"
        "FORK" -> "keep $side"
        "ROUNDABOUT", "ROTARY", "ROUNDABOUT_TURN" -> "at the roundabout, take the exit"
        "END_OF_ROAD" -> "turn $side"
        "CONTINUE", "NEW_NAME" -> if (mod.startsWith("SLIGHT")) "keep $side" else "continue"
        else ->
            when {
              mod == "U_TURN" -> "make a U-turn"
              mod.startsWith("SLIGHT") -> "bear $side"
              mod.startsWith("SHARP") -> "turn sharp $side"
              side.isNotEmpty() -> "turn $side"
              else -> "continue straight"
            }
      }.trim()
  val onto = if (road.isNotBlank()) " onto $road" else ""
  return if (dist != null) "In $dist, $verb$onto." else "${verb.replaceFirstChar { it.uppercase() }}$onto."
}



internal fun distanceMeters(a: GeographicCoordinate, b: GeographicCoordinate): Double {
  val r = 6_371_008.8
  val dLat = Math.toRadians(b.lat - a.lat)
  val dLng = Math.toRadians(b.lng - a.lng)
  val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
  return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

internal fun lengthMeters(points: List<GeographicCoordinate>): Double =
    points.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }

/** Screen-relative direction to [to], given the map's current bearing. */
internal fun relativeBearing(from: GeographicCoordinate?, to: GeographicCoordinate, mapBearing: Double): Float {
  if (from == null) return 0f
  val y = sin(Math.toRadians(to.lng - from.lng)) * cos(Math.toRadians(to.lat))
  val x =
      cos(Math.toRadians(from.lat)) * sin(Math.toRadians(to.lat)) -
          sin(Math.toRadians(from.lat)) * cos(Math.toRadians(to.lat)) * cos(Math.toRadians(to.lng - from.lng))
  val bearing = Math.toDegrees(atan2(y, x))
  return ((bearing - mapBearing + 360) % 360).toFloat()
}

/**
 * The arrow to show for a turn. "End of road" (a T-junction) draws the crossing road as a thin
 * hollow bar that reads as a glitch at card size; Google shows a plain turn there, so do we.
 */
fun uniffi.ferrostar.VisualInstructionContent.forDisplay(): uniffi.ferrostar.VisualInstructionContent =
    if (maneuverType == uniffi.ferrostar.ManeuverType.END_OF_ROAD) copy(maneuverType = uniffi.ferrostar.ManeuverType.TURN) else this
