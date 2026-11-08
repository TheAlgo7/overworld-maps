package com.thealgothrim.overworld

import com.thealgothrim.overworld.routing.TravelMode
import com.thealgothrim.overworld.search.ListKind
import com.thealgothrim.overworld.search.ParkingSpot
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.SavedList
import com.thealgothrim.overworld.search.SavedPlaces
import com.thealgothrim.overworld.search.SavedPlaces.Ops
import com.thealgothrim.overworld.search.SavedState
import com.thealgothrim.overworld.search.SavedTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

class SavedPlacesTest {
  private val gate = Place("India Gate", "Kartavya Path", GeographicCoordinate(28.6129, 77.2295))
  private val pin = Place("Dropped pin", "28.62110, 77.20900", GeographicCoordinate(28.6211, 77.2090))
  private val near = Place("India Gate lawns", "", GeographicCoordinate(28.6130, 77.2296))

  @Test
  fun `a place goes into a list and out again, and is unsaved when in none`() {
    var s = Ops.inList(SavedState(), gate, SavedPlaces.FAVOURITES, true)
    assertTrue(s.isStarred(gate))
    // Within 30 m is the same spot.
    assertTrue(s.isStarred(near))
    s = Ops.inList(s, gate, SavedPlaces.WANT_TO_GO, true)
    assertEquals(setOf(SavedPlaces.FAVOURITES, SavedPlaces.WANT_TO_GO), s.savedFor(gate)!!.lists)
    s = Ops.inList(s, gate, SavedPlaces.FAVOURITES, false)
    s = Ops.inList(s, gate, SavedPlaces.WANT_TO_GO, false)
    assertFalse(s.isStarred(gate))
  }

  @Test
  fun `a dropped pin given a name is labelled and keeps its own name underneath`() {
    val s = Ops.label(SavedState(), pin, "  Bua's home ")
    val saved = s.savedFor(pin)!!
    assertEquals("Bua's home", saved.name)
    assertEquals("Bua's home", s.starred.single().name)
    // "Dropped pin" isn't worth showing under it; the coordinates are.
    assertEquals("28.62110, 77.20900", s.starred.single().detail)
    assertEquals(1, s.labelled.size)
    // Taking the name off a place in no list unsaves it.
    assertFalse(Ops.label(s, pin, "").isStarred(pin))
  }

  @Test
  fun `a label stays when the place leaves its last list`() {
    var s = Ops.inList(SavedState(), gate, SavedPlaces.FAVOURITES, true)
    s = Ops.label(s, gate, "The gate")
    s = Ops.inList(s, gate, SavedPlaces.FAVOURITES, false)
    assertEquals("The gate", s.savedFor(gate)?.name)
  }

  @Test
  fun `a note alone saves the place`() {
    val s = Ops.note(SavedState(), gate, "Parking behind the canopy")
    assertEquals("Parking behind the canopy", s.savedFor(gate)?.note)
    assertTrue(SavedPlaces.STARRED in s.savedFor(gate)!!.lists)
  }

  @Test
  fun `search finds a place by its own name with Indian spellings folded`() {
    val s = Ops.label(SavedState(), pin, "Sunita Bua's Home")
    assertEquals(1, s.matching("bua").size)
    assertEquals(1, s.matching("SUNITA bua").size)
    // Spelled with an h, as people do (Bhua): folded the same.
    assertEquals(1, s.matching("bhua").size)
    assertTrue(s.matching("office").isEmpty())
  }

  @Test
  fun `everything survives being stored and read back`() {
    val food = SavedList("list-1", "Food", ListKind.CUSTOM, "🍜", 5)
    var s = SavedState(home = gate, lists = SavedPlaces.DEFAULT_LISTS + food)
    s = Ops.inList(s, pin, food.id, true)
    s = Ops.label(s, pin, "Chole stall")
    s = Ops.note(s, pin, "Closes at 4")
    s = s.copy(trips = listOf(SavedTrip("trip-1", gate, TravelMode.BIKE, savedAt = 9)), parking = ParkingSpot(gate.coordinate, 7, near = "Kartavya Path", until = 99), recents = listOf(near))
    val back = SavedPlaces.Json.read(org.json.JSONObject(SavedPlaces.Json.write(s).toString()))
    assertEquals(s.home, back.home)
    assertEquals(s.lists, back.lists)
    assertEquals(s.places, back.places)
    assertEquals(s.trips, back.trips)
    assertEquals(s.parking, back.parking)
    assertEquals(s.recents, back.recents)
    assertNull(back.work)
  }

  @Test
  fun `a list deleted takes only places that had nothing else`() {
    val food = SavedList("list-1", "Food", ListKind.CUSTOM)
    var s = SavedState(lists = SavedPlaces.DEFAULT_LISTS + food)
    s = Ops.inList(s, gate, food.id, true)
    s = Ops.inList(s, gate, SavedPlaces.FAVOURITES, true)
    s = Ops.inList(s, pin, food.id, true)
    s = Ops.tidy(s.copy(lists = s.lists - food, places = s.places.map { it.copy(lists = it.lists - food.id) }))
    assertNotNull(s.savedFor(gate))
    assertNull(s.savedFor(pin))
  }
}
