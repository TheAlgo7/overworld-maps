package com.thealgothrim.overworld

import com.thealgothrim.overworld.search.Candidate
import com.thealgothrim.overworld.search.Place
import com.thealgothrim.overworld.search.PlaceKind
import com.thealgothrim.overworld.search.fold
import com.thealgothrim.overworld.search.matchScore
import com.thealgothrim.overworld.search.rank
import com.thealgothrim.overworld.traffic.metres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate

/**
 * Search ranking, with the answers TomTom and Photon really gave on 2026-10-03 for searches made
 * from Gurgaon (the drive where "Chattarpur" couldn't be found).
 */
class RankingTest {
  private val gurgaon = GeographicCoordinate(28.4595, 77.0266)

  private fun c(name: String, detail: String, lat: Double, lng: Double, kind: PlaceKind, tomtom: Boolean, rank: Int) =
      GeographicCoordinate(lat, lng).let { at ->
        Candidate(Place(name, detail, at), kind, tomtom, rank, metres(gurgaon, at))
      }

  @Test
  fun spellingsOfOnePlaceFoldTogether() {
    assertEquals(fold("Chhatarpur"), fold("Chattarpur"))
    assertEquals(fold("Chhatarpur"), fold("Chhattarpur"))
    assertEquals(fold("Prarthana Bhawan"), fold("Prarthana Bhavan"))
    assertTrue(fold("Chhatarpur") != fold("Chhapra"))
  }

  @Test
  fun matchesFromExactToLoose() {
    assertEquals(100, matchScore("chattarpur", "Chhatarpur"))
    assertEquals(80, matchScore("chattarpur", "Chattarpur Metro Station"))
    assertEquals(60, matchScore("chatt met", "Chattarpur Metro Station"))
    assertEquals(10, matchScore("chattarpur", "Golden Tulip"))
  }

  @Test
  fun typingAnAreaFindsTheAreaFirst() {
    // "Chattarpur" from Gurgaon: TomTom lists shops and the metro station; the area itself only
    // came from OpenStreetMap, seventh, so the car (six rows) never showed it.
    val tomtom =
        listOf(
            c("Chattarpur Metro Station", "New Delhi", 28.5067, 77.1747, PlaceKind.PLACE, true, 0),
            c("Chattarpur Road", "Chhatarpur Village, New Delhi", 28.5005, 77.1790, PlaceKind.STREET, true, 1),
            c("Chattarpur Metro Parking", "New Delhi", 28.5070, 77.1750, PlaceKind.PLACE, true, 2),
            c("Golden Tulip Chattarpur", "New Delhi", 28.4990, 77.1810, PlaceKind.PLACE, true, 3),
            c("Oodles Chattarpur", "New Delhi", 28.4995, 77.1800, PlaceKind.PLACE, true, 4),
            c("Chattarpur Village Main Market", "New Delhi", 28.5000, 77.1795, PlaceKind.PLACE, true, 5),
        )
    val osm =
        listOf(
            c("Chattarpur", "South Delhi, Delhi", 28.5048, 77.1742, PlaceKind.AREA, false, 0),
            c("Chattarpur Farms", "South Delhi, Delhi", 28.4870, 77.1690, PlaceKind.AREA, false, 1),
            c("Chattarpur Main Road", "South Delhi, Delhi", 28.5000, 77.1780, PlaceKind.STREET, false, 2),
        )
    val ranked = rank("Chattarpur", tomtom + osm)
    assertEquals("Chattarpur", ranked.first().name)
    // Still the places with the name after it, within the six rows a car shows.
    assertTrue(ranked.take(6).any { it.name == "Chattarpur Metro Station" })
  }

  @Test
  fun theNearAreaBeatsTheSameNameFarAway() {
    val ranked =
        rank(
            "Chhatarpur",
            listOf(
                c("Chhatarpur", "Govindgarh Tehsil, Rajasthan", 27.2400, 75.7200, PlaceKind.AREA, false, 0),
                c("Chhatarpur Temple", "New Delhi", 28.4996, 77.1798, PlaceKind.PLACE, true, 1),
                c("Chhatarpur", "New Delhi, Delhi", 28.5068, 77.1749, PlaceKind.AREA, true, 2),
            ),
        )
    assertEquals("New Delhi, Delhi", ranked.first().detail)
  }

  @Test
  fun townsOfTheSameNameFarAwayComeAfterLocalPlaces() {
    val ranked =
        rank(
            "Chhatarpur",
            listOf(
                c("Chhatarpur", "New Delhi, Delhi", 28.5068, 77.1749, PlaceKind.AREA, true, 0),
                c("Chhatarpur", "Rajasthan", 27.6000, 76.5000, PlaceKind.AREA, false, 1),
                c("Chhatarpur", "Madhya Pradesh", 24.9180, 79.5880, PlaceKind.AREA, false, 2),
                c("Chhatarpur Temple", "New Delhi", 28.4996, 77.1798, PlaceKind.PLACE, true, 1),
                c("Chhatarpur Metro Station", "New Delhi", 28.5067, 77.1747, PlaceKind.PLACE, true, 3),
            ),
        )
    assertEquals(listOf("New Delhi, Delhi", "New Delhi", "New Delhi"), ranked.take(3).map { it.detail })
  }

  @Test
  fun aFarCityStillBeatsLocalShopsNamedAfterIt() {
    val ranked =
        rank(
            "Jaipur",
            listOf(
                c("Jaipur Golden Hospital", "Rohini, Delhi", 28.7180, 77.1150, PlaceKind.PLACE, true, 0),
                c("Jaipur Chowk", "Sector 31, Gurugram", 28.4500, 77.0500, PlaceKind.PLACE, true, 1),
                c("Jaipur", "Rajasthan", 26.9124, 75.7873, PlaceKind.AREA, false, 0),
            ),
        )
    assertEquals("Rajasthan", ranked.first().detail)
  }

  @Test
  fun oneAreaUnderTwoSpellingsShowsOnce() {
    val ranked =
        rank(
            "Chhatarpur",
            listOf(
                c("Chhatarpur", "New Delhi, Delhi", 28.5068, 77.1749, PlaceKind.AREA, true, 0),
                c("Chattarpur", "South Delhi, Delhi", 28.5048, 77.1742, PlaceKind.AREA, false, 0),
                c("Chhatarpur Temple", "New Delhi", 28.4996, 77.1798, PlaceKind.PLACE, true, 1),
            ),
        )
    assertEquals(2, ranked.size)
  }

  @Test
  fun halfTypedWordsStillRankTheirMatchesFirst() {
    val ranked =
        rank(
            "chatt",
            listOf(
                c("Chatori Gali", "Sector 29, Gurugram", 28.4680, 77.0650, PlaceKind.PLACE, true, 0),
                c("Chattarpur", "South Delhi, Delhi", 28.5048, 77.1742, PlaceKind.AREA, false, 0),
                c("Hotel Chhattisgarh Bhawan", "New Delhi", 28.6000, 77.2000, PlaceKind.PLACE, true, 1),
            ),
        )
    assertEquals("Chattarpur", ranked.first().name)
  }

  @Test
  fun placesAbroadOnlyFillTheEnd() {
    val ranked =
        rank(
            "Prarthana Bhavan",
            listOf(
                Candidate(Place("Prarthana Bhavan", "Toronto, Canada", GeographicCoordinate(43.65, -79.38)), PlaceKind.PLACE, false, 0, 11_000_000.0, abroad = true),
                c("Prarthana Bhawan", "Chhatarpur, New Delhi", 28.5010, 77.1760, PlaceKind.PLACE, true, 3),
            ),
        )
    assertEquals("Chhatarpur, New Delhi", ranked.first().detail)
  }
}
