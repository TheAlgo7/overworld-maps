@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.thealgothrim.overworld.map

import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState

/**
 * Ferrostar's own switch that keeps its tracking camera still while an animation runs. Internal
 * in 0.57; reached here so the re-centre can glide on the live car instead of Ferrostar's fixed
 * 1 s flight to where the car was at the tap (then a jump to where it is). If a Ferrostar update
 * renames it, the build stops here.
 */
internal var NavigationMapState.trackingPaused: Boolean
  get() = suppressTrackingUpdates
  set(value) {
    suppressTrackingUpdates = value
  }
