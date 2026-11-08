package com.thealgothrim.overworld

import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.SavedPlaces
import com.thealgothrim.overworld.timeline.Segment
import com.thealgothrim.overworld.traffic.encodePolyline6
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uniffi.ferrostar.GeographicCoordinate

/**
 * Debug builds only: demo saved places, lists, history, a parked car and two days of Timeline,
 * all at public Delhi landmarks, for checking the 0.6 screens on a test phone (DEBUG_DEMO).
 * Clearing takes out only what this added.
 */
object DebugDemo {
  private const val DEMO_AREA = "Demo"

  private val cp = Place("Connaught Place", "Inner Circle, New Delhi", GeographicCoordinate(28.6315, 77.2167))
  private val indiaGate = Place("India Gate", "Kartavya Path, New Delhi", GeographicCoordinate(28.6129, 77.2295))
  private val lodhi = Place("Lodhi Garden", "Lodhi Road, New Delhi", GeographicCoordinate(28.5931, 77.2197))
  private val humayun = Place("Humayun's Tomb", "Mathura Road, Nizamuddin", GeographicCoordinate(28.5933, 77.2507))
  private val karims = Place("Karim's", "Jama Masjid, Old Delhi", GeographicCoordinate(28.6497, 77.2335))
  private val pin = Place("Dropped pin", "28.62110, 77.20900", GeographicCoordinate(28.6211, 77.2090))

  fun run(clear: Boolean) {
    val saved = AppModule.saved
    val store = AppModule.timeline.store
    val demo = listOf(cp, indiaGate, lodhi, humayun, karims, pin)
    if (clear) {
      demo.forEach { saved.remove(it) }
      saved.state.value.lists.filter { it.name == "Food" && !it.builtIn }.forEach { saved.deleteList(it.id) }
      saved.state.value.trips.filter { it.to.name == indiaGate.name }.forEach { saved.removeTrip(it.id) }
      AppModule.viewModel.clearParking()
      store.writableDatabase.delete("seg", "area = ?", arrayOf(DEMO_AREA))
      // The demo's searches and places in history.
      AppModule.history.entries.value.filter { e -> e.text == "chai near me" || demo.any { d -> e.place?.name == d.name } }.forEach { AppModule.history.remove(it) }
      return
    }
    saved.setInList(lodhi, SavedPlaces.FAVOURITES, true)
    saved.setInList(humayun, SavedPlaces.WANT_TO_GO, true)
    saved.setNote(humayun, "Go early, before the tour buses.")
    val food = saved.state.value.lists.firstOrNull { it.name == "Food" }?.id ?: saved.createList("Food", "FOOD")
    saved.setInList(karims, food, true)
    saved.setInList(cp, food, true)
    saved.setLabel(pin, "Demo spot by Gole Market")
    saved.saveTrip(indiaGate, TravelMode.CAR)
    saved.park(GeographicCoordinate(28.6135, 77.2290), "Kartavya Path")
    AppModule.viewModel.setParkingTime(60)
    AppModule.history.searched("chai near me")
    AppModule.history.viewed(lodhi)
    AppModule.history.directions(indiaGate)

    val zone = ZoneId.systemDefault()
    fun at(day: LocalDate, h: Int, m: Int) = day.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    fun line(vararg p: GeographicCoordinate) = encodePolyline6(p.toList())
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val c = GeographicCoordinate(28.6230, 77.2230)
    listOf(
            Segment(move = false, start = at(today, 8, 0), end = at(today, 10, 0), at = cp.coordinate, name = cp.name, area = DEMO_AREA, city = "New Delhi"),
            Segment(move = true, mode = TravelMode.CAR, start = at(today, 10, 0), end = at(today, 10, 22), at = cp.coordinate, to = indiaGate.coordinate, metres = 3_200.0, name = indiaGate.name, area = DEMO_AREA, path = line(cp.coordinate, c, indiaGate.coordinate), trip = true, topKmh = 46.0),
            Segment(move = false, start = at(today, 10, 22), end = at(today, 11, 5), at = indiaGate.coordinate, name = indiaGate.name, area = DEMO_AREA, city = "New Delhi"),
            Segment(move = true, mode = TravelMode.CAR, start = at(today, 11, 5), end = at(today, 11, 24), at = indiaGate.coordinate, to = lodhi.coordinate, metres = 3_600.0, area = DEMO_AREA, path = line(indiaGate.coordinate, GeographicCoordinate(28.6040, 77.2240), lodhi.coordinate), topKmh = 41.0),
            Segment(move = false, start = at(today, 11, 24), end = at(today, 12, 40), at = lodhi.coordinate, name = lodhi.name, area = DEMO_AREA, city = "New Delhi"),
            Segment(move = true, mode = TravelMode.BIKE, start = at(yesterday, 17, 10), end = at(yesterday, 17, 40), at = lodhi.coordinate, to = humayun.coordinate, metres = 4_100.0, area = DEMO_AREA, path = line(lodhi.coordinate, GeographicCoordinate(28.5900, 77.2350), humayun.coordinate), topKmh = 38.0),
            Segment(move = false, start = at(yesterday, 17, 40), end = at(yesterday, 19, 0), at = humayun.coordinate, name = humayun.name, area = DEMO_AREA, city = "New Delhi"),
            Segment(move = true, mode = TravelMode.WALK, start = at(yesterday, 19, 0), end = at(yesterday, 19, 25), at = humayun.coordinate, to = GeographicCoordinate(28.5890, 77.2430), metres = 1_300.0, area = DEMO_AREA, path = line(humayun.coordinate, GeographicCoordinate(28.5890, 77.2430)), topKmh = 6.0),
        )
        .forEach { store.insert(it) }
  }
}
