package com.thealgothrim.overworld.car

import android.icu.util.MeasureUnit
import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import com.thealgothrim.overworld.CueAhead
import com.thealgothrim.overworld.theme.Skin
import com.stadiamaps.ferrostar.car.app.template.models.toCarStep
import com.stadiamaps.ferrostar.core.extensions.currentStep
import com.stadiamaps.ferrostar.core.extensions.progress
import com.stadiamaps.ferrostar.core.extensions.visualInstruction
import com.stadiamaps.ferrostar.ui.formatters.DistanceFormatter
import com.stadiamaps.ferrostar.ui.formatters.DistanceMeasurementSystem
import com.stadiamaps.ferrostar.ui.formatters.LocalizedDistanceFormatter
import com.thealgothrim.overworld.ui.milesNow
import java.util.TimeZone
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.TripProgress
import uniffi.ferrostar.TripState

// Ferrostar's car models pick units from the phone's locale, so an English (UK) or US phone showed
// yards or feet on the dashboard. The car follows Settings > Display > Distance units instead:
// metres and kilometres unless miles are chosen (or the phone's region drives in miles).

private val metric = LocalizedDistanceFormatter(distanceMeasurementSystemOverride = DistanceMeasurementSystem.SI)
private val imperial = LocalizedDistanceFormatter(distanceMeasurementSystemOverride = DistanceMeasurementSystem.IMPERIAL)

/** Ferrostar's formatter in the chosen units, asked afresh each time (the trip notification keeps one). */
object ChosenUnitsFormatter : DistanceFormatter {
  private val now: DistanceFormatter
    get() = if (milesNow) imperial else metric

  override fun recommendedUnit(distanceInMeters: Double): MeasureUnit = now.recommendedUnit(distanceInMeters)

  override fun roundedDistanceForUnit(distanceInMeters: Double): Double = now.roundedDistanceForUnit(distanceInMeters)

  override fun format(distanceInMeters: Double): String = now.format(distanceInMeters)
}

fun Double.toMetricCarDistance(): Distance {
  val unit =
      when (ChosenUnitsFormatter.recommendedUnit(this)) {
        MeasureUnit.KILOMETER -> Distance.UNIT_KILOMETERS
        MeasureUnit.MILE -> Distance.UNIT_MILES
        MeasureUnit.FOOT -> Distance.UNIT_FEET
        MeasureUnit.YARD -> Distance.UNIT_YARDS
        else -> Distance.UNIT_METERS
      }
  return Distance.create(ChosenUnitsFormatter.roundedDistanceForUnit(this), unit)
}

/**
 * Android Auto's turn card: the move, the road and how far, in the chosen units (India drives on
 * the left), with the world's own turn icons ([skin], see CarArrows). Like the old game HUD's card:
 * "Then" and the turn after when it comes within [THEN_M] of this one; a flyover or underpass
 * choice ([cue]) said in the card, as its own line before the turn when it comes first; while
 * [rerouting], Android Auto's own loading card.
 */
fun metricRoutingInfo(
    context: CarContext,
    tripState: TripState,
    skin: Skin? = null,
    rerouting: Boolean = false,
    cue: CueAhead? = null,
): RoutingInfo? {
  if (rerouting) return RoutingInfo.Builder().setLoading(true).build()
  val instruction = com.thealgothrim.overworld.shownInstruction(tripState.visualInstruction(), tripState.currentStep()) ?: return null
  val progress = tripState.progress() ?: return null
  val step = tripState.currentStep() ?: return null
  val side = step.drivingSide ?: DrivingSide.LEFT
  // At the turn itself the choice leads the turn's words: "Take the flyover, Aurobindo Marg".
  val atTurn = cue?.takeIf { it.withTurn }?.cue?.kind?.text
  val turn =
      instruction.toCarStep(context, side, step.roundaboutExitNumber?.toInt())
          .themed(skin?.let { CarArrows.turn(context, instruction.primaryContent, side, it) }, atTurn)
  val first = cue?.takeIf { !it.withTurn && it.distance < progress.distanceToNextManeuver }
  val builder = RoutingInfo.Builder()
  if (first != null) {
    // The choice comes before the turn: it's the card, and the turn is "Then".
    builder.setCurrentStep(levelStep(first, skin), first.distance.toMetricCarDistance())
    builder.setNextStep(turn)
  } else {
    builder.setCurrentStep(turn, progress.distanceToNextManeuver.toMetricCarDistance())
    thenStep(context, tripState, skin)?.let { builder.setNextStep(it) }
  }
  return builder.build()
}

/** The turn after the next, when it follows within [THEN_M] (the old HUD's "Then" row). */
private fun thenStep(context: CarContext, tripState: TripState, skin: Skin?): Step? {
  val next = (tripState as? TripState.Navigating)?.remainingSteps?.getOrNull(1) ?: return null
  if (next.distance >= THEN_M) return null
  val visual = next.visualInstructions.firstOrNull() ?: return null
  val side = next.drivingSide ?: DrivingSide.LEFT
  return runCatching {
        visual.toCarStep(context, side, next.roundaboutExitNumber?.toInt())
            .themed(skin?.let { CarArrows.turn(context, visual.primaryContent, side, it) }, null)
      }
      .getOrNull()
}

/**
 * A flyover or underpass choice ahead of the next turn, as a step of its own: keep to the side
 * away from the other road, with the arch (or dip) picture in the world's tile.
 */
private fun levelStep(cue: CueAhead, skin: Skin?): Step {
  val type =
      when (cue.cue.otherSide) {
        -1 -> Maneuver.TYPE_KEEP_RIGHT // the other road goes off to the left
        1 -> Maneuver.TYPE_KEEP_LEFT
        else -> Maneuver.TYPE_STRAIGHT
      }
  val maneuver = Maneuver.Builder(type).apply { skin?.let { setIcon(CarArrows.level(cue.cue.kind, it)) } }.build()
  return Step.Builder(cue.cue.kind.text).setManeuver(maneuver).build()
}

/**
 * This step with [icon] for its maneuver and [lead] before its words; everything else as Ferrostar
 * built it. Any trouble rebuilding it (Android Auto's rules for roundabout numbers) keeps
 * Ferrostar's step as it was.
 */
private fun Step.themed(icon: CarIcon?, lead: String?): Step {
  if (icon == null && lead == null) return this
  return runCatching {
        val words = listOfNotNull(lead, cue?.toCharSequence()?.toString()?.takeIf { it.isNotBlank() }).joinToString(", ")
        val builder = if (words.isEmpty()) Step.Builder() else Step.Builder(words)
        maneuver?.let { old ->
          builder.setManeuver(
              Maneuver.Builder(old.type)
                  .apply {
                    if (old.roundaboutExitNumber > 0) setRoundaboutExitNumber(old.roundaboutExitNumber)
                    if (old.roundaboutExitAngle > 0) setRoundaboutExitAngle(old.roundaboutExitAngle)
                    (icon ?: old.icon)?.let { setIcon(it) }
                  }
                  .build()
          )
        }
        road?.toCharSequence()?.let { builder.setRoad(it) }
        lanes.forEach { builder.addLane(it) }
        lanesImage?.let { builder.setLanesImage(it) }
        builder.build()
      }
      .getOrDefault(this)
}

/** "Then" shows the turn after the next when the two are closer than this, like the old HUD. */
private const val THEN_M = 500.0

/**
 * The arrival time's colour: like the old HUD's time, green when the roads are clear (Red Dead its
 * objective yellow), amber with some traffic, red with heavy; plain with no live traffic to go on.
 * Android Auto takes only its standard colours here (a custom one throws).
 */
fun etaColor(skin: Skin, eta: com.thealgothrim.overworld.traffic.TrafficEta?): CarColor =
    when (eta?.level) {
      null -> CarColor.DEFAULT
      0 -> if (skin == Skin.RDR) CarColor.YELLOW else CarColor.GREEN
      1 -> if (skin == Skin.RDR) CarColor.RED else CarColor.YELLOW
      else -> CarColor.RED
    }

/** [seconds]: the time to go to show (live traffic's, see remainingSeconds); else the route's own. */
fun TripProgress.toMetricTravelEstimate(timeColor: CarColor = CarColor.GREEN, seconds: Double? = null): TravelEstimate {
  val left = seconds ?: durationRemaining
  val arrival = System.currentTimeMillis() + (left * 1000).toLong()
  return TravelEstimate.Builder(
          distanceRemaining.toMetricCarDistance(),
          DateTimeWithZone.create(arrival, TimeZone.getDefault()),
      )
      .setRemainingTimeSeconds(left.toLong())
      .setRemainingTimeColor(timeColor)
      .build()
}
