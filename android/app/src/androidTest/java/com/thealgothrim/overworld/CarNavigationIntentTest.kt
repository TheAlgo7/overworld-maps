package com.thealgothrim.overworld

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thealgothrim.overworld.car.OverworldCarSession
import com.thealgothrim.overworld.car.navigationDestination
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Hey Google, navigate to ..." in Android Auto: the request arrives as a geo: intent, either when
 * the app opens or while it is already running, and by name or by coordinates. Test drives, so the
 * trips are simulated. Needs the network.
 */
@RunWith(AndroidJUnit4::class)
class CarNavigationIntentTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun <T> main(block: () -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST") return result as T
  }

  private fun navigate(uri: String) = Intent(CarContext.ACTION_NAVIGATE, Uri.parse(uri))

  private fun awaitDestination(name: String) {
    val vm = AppModule.viewModel
    val deadline = System.currentTimeMillis() + 30_000
    while (System.currentTimeMillis() < deadline) {
      val state = vm.navigationUiState.value
      if (state.isNavigating() && state.destination == name) return
      Thread.sleep(250)
    }
    val state = vm.navigationUiState.value
    assertEquals("navigating to", name, if (state.isNavigating()) state.destination else "(not navigating)")
  }

  /** Ferrostar's parser threw on every one of the geo: forms. */
  @Test
  fun readsTheWaysNavigationRequestsArrive() {
    fun read(uri: String) = navigate(uri).navigationDestination()
    fun check(uri: String, lat: Double?, lng: Double?, name: String?) {
      val d = read(uri)
      assertEquals(uri, Triple(lat, lng, name), d?.let { Triple(it.latitude, it.longitude, it.query) })
    }
    check("geo:28.6129,77.2295", 28.6129, 77.2295, null)
    check("geo:28.6129,77.2295;u=35", 28.6129, 77.2295, null)
    check("geo:0,0?q=India%20Gate", null, null, "India Gate")
    check("geo:0,0?q=India+Gate", null, null, "India Gate")
    check("geo:28.6129,77.2295?q=India%20Gate", 28.6129, 77.2295, "India Gate")
    check("geo:0,0?q=28.6129,77.2295(India%20Gate)", 28.6129, 77.2295, "India Gate")
    check("google.navigation:q=28.6129,77.2295", 28.6129, 77.2295, null)
    check("google.navigation:q=India+Gate&mode=d", null, null, "India Gate")
    assertEquals(null, read("geo:0,0"))
    assertEquals(null, read("https://example.com"))
    assertEquals(null, Intent(CarContext.ACTION_NAVIGATE).navigationDestination())
  }

  @Test
  fun navigationRequestsFromTheCarStartTrips() {
    val appContext = instrumentation.targetContext
    AppModule.init(appContext)
    val vm = AppModule.viewModel
    main { vm.stopNavigation() }
    vm.setTestDrive(true)

    // Opened by a request with only a place name, the way the Assistant sends it.
    val session = main {
      OverworldCarSession().also {
        SessionController(it, TestCarContext.createCarContext(appContext), navigate("geo:0,0?q=India%20Gate"))
            .moveToState(Lifecycle.State.CREATED)
      }
    }
    awaitDestination("India Gate")

    // A new request while the app is open replaces the trip (it used to be ignored).
    main { session.onNewIntent(navigate("geo:28.6003,77.2270?q=Khan%20Market")) }
    awaitDestination("Khan Market")

    main { vm.stopNavigation() }
    vm.setTestDrive(false)
  }
}
