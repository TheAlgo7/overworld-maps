package com.thealgothrim.overworld.routing

/**
 * How the trip is made, like Google Maps' mode tabs. Bike is a two-wheeler (motorbike or scooter),
 * as in Google's India app; Google's web directions have no two-wheeler mode, so bike routes come
 * from Valhalla's motorcycle profile. Android Auto always drives as a [CAR].
 */
enum class TravelMode(
    val label: String,
    /** Valhalla's costing for this mode. */
    val costing: String,
    /** Google's travel mode in its keyless directions call (`!20m5!1e{N}`), or null where it has none. */
    val google: Int?,
) {
  CAR("Car", "auto", 0),
  BIKE("Bike", "motorcycle", null),
  WALK("Walk", "pedestrian", 2);

  /** Car and bike: speed limits, cameras, traffic and lane-level things matter. */
  val vehicle: Boolean
    get() = this != WALK
}
