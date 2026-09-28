package com.thealgothrim.overworld.car

import android.icu.util.MeasureUnit
import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.TravelEstimate
import com.stadiamaps.ferrostar.car.app.template.models.toCarStep
import com.stadiamaps.ferrostar.core.extensions.currentStep
import com.stadiamaps.ferrostar.core.extensions.progress
import com.stadiamaps.ferrostar.core.extensions.visualInstruction
import com.stadiamaps.ferrostar.ui.formatters.DistanceMeasurementSystem
import com.stadiamaps.ferrostar.ui.formatters.LocalizedDistanceFormatter
import java.util.TimeZone
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.TripProgress
import uniffi.ferrostar.TripState

// Ferrostar's car models pick units from the phone's locale, so an English (UK) or US phone would
// show yards or feet on the dashboard. India drives in metres and kilometres, always.

private val metric =
    LocalizedDistanceFormatter(distanceMeasurementSystemOverride = DistanceMeasurementSystem.SI)

fun Double.toMetricCarDistance(): Distance {
  val unit =
      if (metric.recommendedUnit(this) == MeasureUnit.KILOMETER) Distance.UNIT_KILOMETERS
      else Distance.UNIT_METERS
  return Distance.create(metric.roundedDistanceForUnit(this), unit)
}

/** The turn card: maneuver, road and distance, in metric. India drives on the left. */
fun metricRoutingInfo(context: CarContext, tripState: TripState): RoutingInfo? {
  val instruction = tripState.visualInstruction() ?: return null
  val progress = tripState.progress() ?: return null
  val step = tripState.currentStep() ?: return null
  val side = step.drivingSide ?: DrivingSide.LEFT
  return RoutingInfo.Builder()
      .setCurrentStep(
          instruction.toCarStep(context, side, step.roundaboutExitNumber?.toInt()),
          progress.distanceToNextManeuver.toMetricCarDistance(),
      )
      .build()
}

fun TripProgress.toMetricTravelEstimate(): TravelEstimate {
  val arrival = System.currentTimeMillis() + (durationRemaining * 1000).toLong()
  return TravelEstimate.Builder(
          distanceRemaining.toMetricCarDistance(),
          DateTimeWithZone.create(arrival, TimeZone.getDefault()),
      )
      .setRemainingTimeSeconds(durationRemaining.toLong())
      .setRemainingTimeColor(CarColor.GREEN)
      .build()
}
