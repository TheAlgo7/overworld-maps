package com.thealgothrim.overworld.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Screens a debug build's DebugDriveReceiver can open from adb, for checking them and taking
 * screenshots without tapping a phone someone is holding. Each is read once and cleared.
 */
object DebugUi {
  /** Open You on this tab ("none" closes it). */
  val you = MutableStateFlow<String?>(null)

  /** Open Settings on this tab (0 to 6), or close it with -1. */
  val settingsTab = MutableStateFlow<Int?>(null)

  /** Show this day ("today", "yesterday" or yyyy-mm-dd) on the map. */
  val day = MutableStateFlow<String?>(null)

  /** Open the offline download frame. */
  val pickArea = MutableStateFlow(false)

  /** A scripted pan to measure the phone map's frames: lat, lng, zoom, tilt, seconds (see FrameBenchEffect). */
  val bench = MutableStateFlow<DoubleArray?>(null)
}
