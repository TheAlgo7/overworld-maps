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

/** The turn card: maneuver, road and distance, in the chosen units. India drives on the left. */
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

fun TripProgress.toMetricTravelEstimate(timeColor: CarColor = CarColor.GREEN): TravelEstimate {
  val arrival = System.currentTimeMillis() + (durationRemaining * 1000).toLong()
  return TravelEstimate.Builder(
          distanceRemaining.toMetricCarDistance(),
          DateTimeWithZone.create(arrival, TimeZone.getDefault()),
      )
      .setRemainingTimeSeconds(durationRemaining.toLong())
      .setRemainingTimeColor(timeColor)
      .build()
}
