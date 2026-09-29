package com.thealgothrim.overworld

import androidx.car.app.model.CarColor
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thealgothrim.overworld.car.CarNavigationScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.ferrostar.GeographicCoordinate

/**
 * Runs the Android Auto screen inside Google's car-app test host on a device or emulator:
 * idle template, theme switching from the car, then a real simulated trip to India Gate.
 */
@RunWith(AndroidJUnit4::class)
class CarScreenSmokeTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun <T> main(block: () -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST") return result as T
  }

  @Test
  fun carScreenShowsThemedTrip() {
    val appContext = instrumentation.targetContext
    AppModule.init(appContext)
    val store = AppModule.themeStore
    main { store.select("gta5") }
    // Android Auto card mode first: the idle message and the turn card come from the host.
    main { store.setCarGameHud(false) }

    val screen = main {
      val carContext = TestCarContext.createCarContext(appContext)
      CarNavigationScreen(carContext).also {
        ScreenController(it).moveToState(Lifecycle.State.RESUMED)
      }
    }

    // Idle: themed message, a theme switch, and pan on the map.
    val idle = main { screen.onGetTemplate() } as NavigationTemplate
    val message = idle.navigationInfo as MessageInfo
    assertEquals("GTA V", message.title.toString())
    val themeAction = idle.actionStrip!!.actions[1]
    main { themeAction.onClickDelegate!!.sendClick(NoopCallback) }
    assertEquals("rdr2", store.theme.value.id)

    // A real trip: Valhalla route, simulated driving, started with the phone app open.
    appContext.startActivity(
        android.content.Intent(appContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    Thread.sleep(4000)
    AppModule.viewModel.setTestDrive(true)
    // From Connaught Place, wherever the device thinks it is (an emulator says California).
    AppModule.viewModel.startNavigation(GeographicCoordinate(28.6129, 77.2295), "India Gate", from = GeographicCoordinate(28.6315, 77.2167))
    val deadline = System.currentTimeMillis() + 30_000
    while (!AppModule.viewModel.navigationUiState.value.isNavigating() && System.currentTimeMillis() < deadline) {
      Thread.sleep(250)
    }
    assertTrue("trip did not start", AppModule.viewModel.navigationUiState.value.isNavigating())
    Thread.sleep(4000)

    val driving = main { screen.onGetTemplate() } as NavigationTemplate
    val routing = driving.navigationInfo as RoutingInfo
    assertNotNull("no current step on the turn card", routing.currentStep)
    assertNotNull("no ETA", driving.destinationTravelEstimate)
    val expected = store.theme.value.carCard.toArgb()
    assertEquals(CarColor.TYPE_CUSTOM, driving.backgroundColor!!.type)
    assertEquals(expected, driving.backgroundColor!!.color)
    assertEquals(4, driving.mapActionStrip!!.actions.size)
    // Metric on the dashboard whatever the phone's language (the emulator is en-US).
    val unit = routing.currentDistance!!.displayUnit
    assertTrue("turn card not metric: $unit", unit == androidx.car.app.model.Distance.UNIT_METERS || unit == androidx.car.app.model.Distance.UNIT_KILOMETERS)
    android.util.Log.i(
        "CarScreenSmokeTest",
        "turn card: ${routing.currentStep?.cue} in ${routing.currentDistance}; ETA ${driving.destinationTravelEstimate?.remainingTimeSeconds}s",
    )

    // Game HUD mode: the host cards are not sent while driving (the HUD is drawn on the map).
    main { store.setCarGameHud(true) }
    val gameHud = main { screen.onGetTemplate() } as NavigationTemplate
    assertEquals(null, gameHud.navigationInfo)
    assertEquals(null, gameHud.destinationTravelEstimate)

    AppModule.viewModel.stopNavigation()
    main { store.select("gta5") }
  }

  private object NoopCallback : androidx.car.app.OnDoneCallback
}
