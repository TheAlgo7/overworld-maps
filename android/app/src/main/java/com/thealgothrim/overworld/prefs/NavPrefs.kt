package com.thealgothrim.overworld.prefs

import android.content.Context
import androidx.core.content.edit
import com.thealgothrim.overworld.routing.TravelMode
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Distance units, as Google Maps offers them: the phone's region decides on Automatic. */
enum class Units(val label: String) {
  AUTO("Auto"),
  KM("Km"),
  MILES("Miles");

  /** Whether distances read in miles (and speeds in mph). */
  val miles: Boolean
    get() =
        when (this) {
          KM -> false
          MILES -> true
          // The US, the UK, Liberia and Myanmar drive in miles; everyone else, India included, in km.
          AUTO -> Locale.getDefault().country.uppercase() in MILE_COUNTRIES
        }

  companion object {
    private val MILE_COUNTRIES = setOf("US", "GB", "LR", "MM")
  }
}

/** The car's fuel, like Google's Your vehicles: it picks what Nearby's fuel button looks for. */
enum class EngineType(val label: String) {
  PETROL("Petrol"),
  DIESEL("Diesel"),
  CNG("CNG"),
  HYBRID("Hybrid"),
  ELECTRIC("Electric"),
}

/** Google's three mute states: voice and beep, the camera beep only, or nothing. */
enum class VoiceLevel(val label: String) {
  MUTED("Muted"),
  ALERTS("Alerts only"),
  ON("On"),
}

/** How loud the turn voice and the camera beep play over music. */
enum class GuidanceVolume(val label: String, val gain: Float) {
  SOFTER("Softer", 0.45f),
  NORMAL("Normal", 0.8f),
  LOUDER("Louder", 1f),
}

/** How long Timeline keeps drives and stops, Google's choices plus forever. */
enum class TimelineKeep(val label: String, val months: Int?) {
  M3("3 months", 3),
  M18("18 months", 18),
  M36("36 months", 36),
  FOREVER("Forever", null),
}

/** How fast walking routes reckon you walk (Valhalla's walking speed, km/h). */
enum class WalkPace(val label: String, val kmh: Double) {
  RELAXED("Relaxed", 4.0),
  NORMAL("Normal", 5.1),
  BRISK("Brisk", 6.2),
}

/**
 * The settings Google Maps keeps under Navigation, Your vehicles, Location & privacy, Offline maps
 * and Notifications, for Overworld. Kept in the app's own preferences, like the theme.
 */
class NavPrefs(context: Context) {
  private val prefs = context.getSharedPreferences("overworld", Context.MODE_PRIVATE)

  private inline fun <reified E : Enum<E>> read(key: String, default: E): E =
      runCatching { enumValueOf<E>(prefs.getString(key, null) ?: "") }.getOrDefault(default)

  private fun flag(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))

  private fun save(key: String, on: Boolean, flow: MutableStateFlow<Boolean>) {
    flow.value = on
    prefs.edit { putBoolean(key, on) }
  }

  private fun <E : Enum<E>> save(key: String, value: E, flow: MutableStateFlow<E>) {
    flow.value = value
    prefs.edit { putString(key, value.name) }
  }

  // ------------------------------------------------------------ support (see SupportAsk)
  /** Real trips driven to their end, counted for the one time the app asks for support. */
  val finishedTrips: Int
    get() = prefs.getInt("finished_trips", 0)

  fun tripFinished() = prefs.edit { putInt("finished_trips", finishedTrips + 1) }

  /** The support card was answered (either way): it never comes up by itself again. */
  val supportAnswered: Boolean
    get() = prefs.getBoolean("support_answered", false)

  fun answerSupport() = prefs.edit { putBoolean("support_answered", true) }

  // ------------------------------------------------------------ display
  private val _units = MutableStateFlow(read("units", Units.AUTO))
  val units: StateFlow<Units> = _units.asStateFlow()
  fun setUnits(u: Units) = save("units", u, _units)

  // ------------------------------------------------------------ navigation
  private val _mode = MutableStateFlow(read("travel_mode", TravelMode.CAR))
  /** The phone's last travel mode, kept like Google keeps it. The car always drives as a car. */
  val mode: StateFlow<TravelMode> = _mode.asStateFlow()
  fun setMode(m: TravelMode) = save("travel_mode", m, _mode)

  private val _avoidTolls = flag("avoid_tolls", false)
  val avoidTolls: StateFlow<Boolean> = _avoidTolls.asStateFlow()
  fun setAvoidTolls(on: Boolean) = save("avoid_tolls", on, _avoidTolls)

  private val _avoidHighways = flag("avoid_highways", false)
  val avoidHighways: StateFlow<Boolean> = _avoidHighways.asStateFlow()
  fun setAvoidHighways(on: Boolean) = save("avoid_highways", on, _avoidHighways)

  private val _avoidFerries = flag("avoid_ferries", false)
  val avoidFerries: StateFlow<Boolean> = _avoidFerries.asStateFlow()
  fun setAvoidFerries(on: Boolean) = save("avoid_ferries", on, _avoidFerries)

  private val _fuelSaver = flag("fuel_efficient", false)
  /** Prefer the shorter of two routes about as quick (less fuel), like Google's fuel-efficient routes. */
  val fuelSaver: StateFlow<Boolean> = _fuelSaver.asStateFlow()
  fun setFuelSaver(on: Boolean) = save("fuel_efficient", on, _fuelSaver)

  private val _volume = MutableStateFlow(read("guidance_volume", GuidanceVolume.NORMAL))
  val volume: StateFlow<GuidanceVolume> = _volume.asStateFlow()
  fun setVolume(v: GuidanceVolume) = save("guidance_volume", v, _volume)

  private val _wantedFlash = flag("wanted_flash", true)
  /** GTA's wanted flash when a speed camera is passed over the limit (ui/WantedFlash). On by default. */
  val wantedFlash: StateFlow<Boolean> = _wantedFlash.asStateFlow()
  fun setWantedFlash(on: Boolean) = save("wanted_flash", on, _wantedFlash)

  private val _northUp = flag("north_up", false)
  /** The phone's map stays north up while driving instead of turning with the road. */
  val northUp: StateFlow<Boolean> = _northUp.asStateFlow()
  fun setNorthUp(on: Boolean) = save("north_up", on, _northUp)

  private val _showLimit = flag("show_speed_limit", true)
  val showLimit: StateFlow<Boolean> = _showLimit.asStateFlow()
  fun setShowLimit(on: Boolean) = save("show_speed_limit", on, _showLimit)

  private val _showSpeed = flag("show_speedometer", true)
  val showSpeed: StateFlow<Boolean> = _showSpeed.asStateFlow()
  fun setShowSpeed(on: Boolean) = save("show_speedometer", on, _showSpeed)

  private val _walkPace = MutableStateFlow(read("walk_pace", WalkPace.NORMAL))
  val walkPace: StateFlow<WalkPace> = _walkPace.asStateFlow()
  fun setWalkPace(p: WalkPace) = save("walk_pace", p, _walkPace)

  // ------------------------------------------------------------ vehicle
  private val _engine = MutableStateFlow(read("engine_type", EngineType.PETROL))
  val engine: StateFlow<EngineType> = _engine.asStateFlow()
  fun setEngine(e: EngineType) = save("engine_type", e, _engine)

  // ------------------------------------------------------------ privacy
  private val _timeline = flag("timeline_on", true)
  /** Timeline records drives and stops while Overworld runs. On for new installs (his call, 8 Nov). */
  val timeline: StateFlow<Boolean> = _timeline.asStateFlow()
  fun setTimeline(on: Boolean) = save("timeline_on", on, _timeline)

  private val _timelineKeep = MutableStateFlow(read("timeline_keep", TimelineKeep.FOREVER))
  val timelineKeep: StateFlow<TimelineKeep> = _timelineKeep.asStateFlow()
  fun setTimelineKeep(k: TimelineKeep) = save("timeline_keep", k, _timelineKeep)

  private val _timelineNoticed = flag("timeline_noticed", false)
  /** The one-time card saying Timeline is on and stays on the phone has been seen. */
  val timelineNoticed: StateFlow<Boolean> = _timelineNoticed.asStateFlow()
  fun setTimelineNoticed() = save("timeline_noticed", true, _timelineNoticed)

  private val _keepHistory = flag("keep_history", true)
  /** Searches, places looked at and directions are kept on the phone for Search and History. */
  val keepHistory: StateFlow<Boolean> = _keepHistory.asStateFlow()
  fun setKeepHistory(on: Boolean) = save("keep_history", on, _keepHistory)

  // ------------------------------------------------------------ offline
  private val _offlineWifi = flag("offline_wifi_only", true)
  val offlineWifiOnly: StateFlow<Boolean> = _offlineWifi.asStateFlow()
  fun setOfflineWifiOnly(on: Boolean) = save("offline_wifi_only", on, _offlineWifi)

  private val _offlineAuto = flag("offline_auto_update", true)
  /** Downloaded areas older than a month refresh by themselves (on Wi-Fi when that's set). */
  val offlineAutoUpdate: StateFlow<Boolean> = _offlineAuto.asStateFlow()
  fun setOfflineAutoUpdate(on: Boolean) = save("offline_auto_update", on, _offlineAuto)

  // ------------------------------------------------------------ notifications
  private val _notifyParking = flag("notify_parking", true)
  val notifyParking: StateFlow<Boolean> = _notifyParking.asStateFlow()
  fun setNotifyParking(on: Boolean) = save("notify_parking", on, _notifyParking)

  private val _notifyRecap = flag("notify_timeline_recap", true)
  /** "Your October in Overworld": the month's drives, sent once at the start of the next month. */
  val notifyRecap: StateFlow<Boolean> = _notifyRecap.asStateFlow()
  fun setNotifyRecap(on: Boolean) = save("notify_timeline_recap", on, _notifyRecap)

  private val _notifyOffline = flag("notify_offline", true)
  val notifyOffline: StateFlow<Boolean> = _notifyOffline.asStateFlow()
  fun setNotifyOffline(on: Boolean) = save("notify_offline", on, _notifyOffline)

  private val _notifyUpdates = flag("notify_updates", true)
  val notifyUpdates: StateFlow<Boolean> = _notifyUpdates.asStateFlow()
  fun setNotifyUpdates(on: Boolean) = save("notify_updates", on, _notifyUpdates)

  /** A plain value kept beside the settings (the last month whose recap went out, and such). */
  fun string(key: String): String? = prefs.getString(key, null)

  fun putString(key: String, value: String?) = prefs.edit { putString(key, value) }
}
