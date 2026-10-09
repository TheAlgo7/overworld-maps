package com.thealgothrim.overworld.theme

import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sun, for themes whose map changes with the time of day (GTA VI's minimap is lit by the game's
 * sun: pale by day, dim and warm at golden hour, violet at night).
 */
object Daylight {
  /**
   * The sun's height above the horizon in degrees at [lat], [lng] at [time]: the NOAA low-precision
   * formulas, good to well under a degree, which is plenty to tell day from dusk.
   */
  fun sunAltitude(lat: Double, lng: Double, time: Instant): Double {
    val days = time.toEpochMilli() / 86_400_000.0 + 2_440_587.5 - 2_451_545.0
    val meanLongitude = 280.460 + 0.9856474 * days
    val anomaly = Math.toRadians(357.528 + 0.9856003 * days)
    val eclipticLongitude = Math.toRadians(meanLongitude + 1.915 * sin(anomaly) + 0.020 * sin(2 * anomaly))
    val obliquity = Math.toRadians(23.439 - 0.0000004 * days)
    val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))
    val declination = asin(sin(obliquity) * sin(eclipticLongitude))
    val siderealDegrees = (280.46061837 + 360.98564736629 * days + lng) % 360
    val hourAngle = Math.toRadians(siderealDegrees) - rightAscension
    val latitude = Math.toRadians(lat)
    return Math.toDegrees(asin(sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(hourAngle)))
  }

  /**
   * The palette for a sun at [altitude] degrees: day (null) above 6 degrees, "dusk" from there to
   * 4 degrees below the horizon (golden hour into twilight, about 45 minutes at Delhi), then "night".
   */
  fun phase(altitude: Double): String? =
      when {
        altitude > 6.0 -> null
        altitude > -4.0 -> "dusk"
        else -> "night"
      }
}
