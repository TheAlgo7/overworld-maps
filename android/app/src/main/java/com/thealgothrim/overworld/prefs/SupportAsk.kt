package com.thealgothrim.overworld.prefs

/**
 * Support Overworld: the app is free, and people who drive with it can put something toward what
 * it costs to run (live traffic, a future iPhone version) on the website. Nothing in the app is
 * sold, unlocked or changed by it, which is what lets it go through the website and not Google
 * Play's billing (Play's Payments policy: a contribution that gives nothing digital in return).
 * Keep it that way: no badge, theme or mark for people who pay.
 *
 * It lives in Settings > About, and the app asks once by itself, on the phone only: after
 * [TRIPS] finished trips, when the app is next opened with no car connected and no trip running.
 * Either answer ends it for good.
 */
object SupportAsk {
  const val URL = "https://overworld.thealgothrim.com/fund"

  /** Finished real trips (not test drives, not ones ended early) before the app asks. */
  const val TRIPS = 10

  /** Whether to ask now, at the app's start. */
  fun due(finishedTrips: Int, answered: Boolean, carConnected: Boolean, onTrip: Boolean): Boolean =
      !answered && !carConnected && !onTrip && finishedTrips >= TRIPS
}
