package com.thealgothrim.overworld

import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thealgothrim.overworld.car.CarNearbyListScreen
import com.thealgothrim.overworld.car.CarNearbyScreen
import com.thealgothrim.overworld.car.CarSearchScreen
import com.thealgothrim.overworld.search.Nearby
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Search and Nearby on the car screen, in Google's car-app test host. Needs the network. */
@RunWith(AndroidJUnit4::class)
class CarSearchTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun <T> main(block: () -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST") return result as T
  }

  private fun waitFor(what: String, check: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 20_000
    while (!main(check) && System.currentTimeMillis() < deadline) Thread.sleep(250)
    assertTrue(what, main(check))
  }

  @Test
  fun searchOnTheCarScreenFindsPlacesAndPlusCodes() {
    val context = instrumentation.targetContext
    AppModule.init(context)
    val screen = main { CarSearchScreen(TestCarContext.createCarContext(context)).also { ScreenController(it).moveToState(Lifecycle.State.RESUMED) } }
    val search = main { screen.onGetTemplate() } as SearchTemplate
    main { search.searchCallbackDelegate.sendSearchSubmitted("India Gate", NoopCallback) }
    fun rows() = (screen.onGetTemplate() as SearchTemplate).let { t -> if (t.isLoading) emptyList() else t.itemList!!.items.map { (it as Row).title.toString() } }
    waitFor("no results for India Gate") { rows().isNotEmpty() }
    assertTrue("India Gate not first: ${main { rows() }}", main { rows() }.first().contains("India Gate"))

    // Google's plus code for Prarthana Bhavan (not in OpenStreetMap or TomTom) goes straight there.
    main { search.searchCallbackDelegate.sendSearchSubmitted("F5QR+3F New Delhi", NoopCallback) }
    waitFor("plus code not read") { rows() == listOf("F5QR+3F") }
  }

  @Test
  fun nearbyPetrolPumpsComeClosestFirst() {
    val context = instrumentation.targetContext
    AppModule.init(context)
    val grid = main { CarNearbyScreen(TestCarContext.createCarContext(context)).onGetTemplate() } as GridTemplate
    assertEquals(Nearby.entries.size, grid.singleList!!.items.size)

    val list = main { CarNearbyListScreen(TestCarContext.createCarContext(context), Nearby.FUEL).also { ScreenController(it).moveToState(Lifecycle.State.RESUMED) } }
    fun rows() = (list.onGetTemplate() as ListTemplate).let { t -> if (t.isLoading) emptyList() else t.singleList!!.items.map { it as Row } }
    waitFor("no petrol pumps") { rows().isNotEmpty() }
    val km = main { rows() }.map { it.texts.first().toString().substringBefore(" ").toDouble().let { d -> if (it.texts.first().toString().contains(" km")) d else d / 1000 } }
    assertEquals("not closest first: $km", km.sorted(), km)
    android.util.Log.i("CarSearchTest", "petrol: " + main { rows() }.joinToString { it.title.toString() + " " + it.texts.first() })
  }

  private object NoopCallback : androidx.car.app.OnDoneCallback
}
