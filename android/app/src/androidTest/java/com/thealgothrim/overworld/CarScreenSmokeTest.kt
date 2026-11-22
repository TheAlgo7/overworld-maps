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

  /** A failed check mustn't leave its trip running into the next test. */
  @org.junit.After
  fun endTrip() {
    main { AppModule.viewModel.stopNavigation(); AppModule.viewModel.setTestDrive(false) }
  }

  @Test
  fun carScreenShowsThemedTrip() {
    val appContext = instrumentation.targetContext
    AppModule.init(appContext)
    val store = AppModule.themeStore
    // Km chosen in Settings: on Auto the emulator's US region would mean miles.
    main { AppModule.prefs.setUnits(com.thealgothrim.overworld.prefs.Units.KM) }
    main {
      store.select("gta5")
      store.selectCar("gta5")
    }
    // Android Auto card mode first: the idle message and the turn card come from the host.
    main { store.setCarGameHud(false) }

    val screen = main {
      val carContext = TestCarContext.createCarContext(appContext)
      CarNavigationScreen(carContext).also {
        ScreenController(it).moveToState(Lifecycle.State.RESUMED)
      }
    }

    // Idle: "Set a waypoint" in the world's card, Settings (whose first row switches the theme), and pan on the map.
    val idle = main { screen.onGetTemplate() } as NavigationTemplate
    val message = idle.navigationInfo as MessageInfo
    assertEquals("Set a waypoint", message.title.toString())
    // The world's name is already on the map (0.6.3 car look), so the card only says how to pick.
    assertEquals("Search, or pick from Saved or Nearby", message.text.toString())
    // Nothing on the car screen sends the driver to the phone (Play's Android Auto review, rule VI-1).
    assertTrue("car idle text mentions the phone: ${message.text}", message.text?.toString()?.contains("phone") != true)
    val titles = idle.actionStrip!!.actions.map { it.title.toString() }
    assertEquals(listOf("Search", "Nearby", "Saved", "Settings"), titles)
    // Each with the world's own icon, and the map buttons too.
    assertTrue("top buttons without the world's icons", idle.actionStrip!!.actions.all { it.icon != null })
    assertTrue("map buttons without icons", idle.mapActionStrip!!.actions.filter { it.type != androidx.car.app.model.Action.TYPE_PAN }.all { it.icon != null })
    // Play builds keep Android Auto's own lists; test builds can switch Overworld's own on, which
    // open over the map...
    main { store.setCarThemedLists(true) }
    main { idle.actionStrip!!.actions[3].onClickDelegate!!.sendClick(NoopCallback) }
    assertEquals(com.thealgothrim.overworld.ui.CarPanel.Settings, com.thealgothrim.overworld.ui.CarPanels.open)
    main { idle.actionStrip!!.actions[3].onClickDelegate!!.sendClick(NoopCallback) }
    assertEquals(null, com.thealgothrim.overworld.ui.CarPanels.open)
    // ...and Android Auto's own list with the themed lists off (a separate screen where the head
    // unit's Car App API level is unknown, as in this test).
    main { store.setCarThemedLists(false) }
    main { idle.actionStrip!!.actions[3].onClickDelegate!!.sendClick(NoopCallback) }
    val settings = main { (screen.screenManager.top as com.thealgothrim.overworld.car.CarSettingsScreen).onGetTemplate() } as androidx.car.app.model.ListTemplate
    assertEquals(listOf("Map", "Driving"), settings.sectionedLists.map { it.header.toString() })
    val themeRow = settings.sectionedLists[0].itemList.items[0] as androidx.car.app.model.Row
    main { themeRow.onClickDelegate!!.sendClick(NoopCallback) }
    // The car's own theme: the phone keeps its.
    assertEquals("rdr2", store.carTheme.value.id)
    assertEquals("gta5", store.theme.value.id)

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
    // The world's own turn icon (CarArrows), not Android Auto's stock arrow.
    assertNotNull("no turn icon", routing.currentStep?.maneuver?.icon)
    assertEquals(androidx.core.graphics.drawable.IconCompat.TYPE_BITMAP, routing.currentStep!!.maneuver!!.icon!!.icon!!.type)
    assertNotNull("no ETA", driving.destinationTravelEstimate)
    val expected = store.carTheme.value.carCard.toArgb()
    assertEquals(CarColor.TYPE_CUSTOM, driving.backgroundColor!!.type)
    assertEquals(expected, driving.backgroundColor!!.color)
    assertEquals(4, driving.mapActionStrip!!.actions.size)
    assertEquals(listOf("End", "Nearby", "Settings"), driving.actionStrip!!.actions.map { it.title.toString() })
    // + Cam joins them when it's switched on.
    main { store.setCarCamButton(true) }
    val withCam = main { screen.onGetTemplate() } as NavigationTemplate
    assertEquals(listOf("End", "Nearby", "Settings", "+ Cam"), withCam.actionStrip!!.actions.map { it.title.toString() })
    main { store.setCarCamButton(false) }
    // Km in Settings means km on the dashboard, whatever the phone's language (the emulator is en-US).
    val unit = routing.currentDistance!!.displayUnit
    assertTrue("turn card not metric: $unit", unit == androidx.car.app.model.Distance.UNIT_METERS || unit == androidx.car.app.model.Distance.UNIT_KILOMETERS)
    android.util.Log.i(
        "CarScreenSmokeTest",
        "turn card: ${routing.currentStep?.cue} in ${routing.currentDistance}; ETA ${driving.destinationTravelEstimate?.remainingTimeSeconds}s",
    )

    // The old game HUD (test builds only): the host cards are not sent while driving.
    main { store.setCarGameHud(true) }
    val gameHud = main { screen.onGetTemplate() } as NavigationTemplate
    assertEquals(null, gameHud.navigationInfo)
    assertEquals(null, gameHud.destinationTravelEstimate)
    main { store.setCarGameHud(false) }

    AppModule.viewModel.stopNavigation()
    main {
      store.select("gta5")
      store.selectCar("gta5")
    }
  }

  /**
   * Opened in the car before location was allowed: the car screen says the next step is on the phone,
   * only when it's safe, and Allow works only parked (Play's Android Auto review, rule VI-1).
   */
  @Test
  fun locationScreenSendsToThePhoneOnlyWhenSafe() {
    val appContext = instrumentation.targetContext
    AppModule.init(appContext)
    val template = main {
      com.thealgothrim.overworld.car.CarLocationScreen(TestCarContext.createCarContext(appContext)).onGetTemplate()
    } as androidx.car.app.model.MessageTemplate
    assertTrue(template.message.toString().contains("Only look at your phone when it's safe"))
    assertEquals(listOf("Allow"), template.actions.map { it.title.toString() })
    assertTrue("Allow must work only while parked", template.actions[0].onClickDelegate!!.isParkedOnly)
  }

  /**
   * The route preview card Android Auto shows beside the map (API 7 heads): it must build within
   * Android Auto's rules for a pane (two actions, one primary in the world's colour) in every world.
   */
  @Test
  fun previewCardBuildsInEveryWorld() {
    val appContext = instrumentation.targetContext
    AppModule.init(appContext)
    val place = com.thealgothrim.overworld.search.Place("India Gate", "Monument", GeographicCoordinate(28.6129, 77.2295))
    val route = uniffi.ferrostar.Route(
        listOf(GeographicCoordinate(28.6315, 77.2167), GeographicCoordinate(28.6129, 77.2295)),
        uniffi.ferrostar.BoundingBox(GeographicCoordinate(28.6129, 77.2167), GeographicCoordinate(28.6315, 77.2295)),
        2600.0,
        emptyList(),
        emptyList(),
    )
    val preview = RoutePreview(place, route, durationSeconds = 600.0, distanceMeters = 2600.0, via = "Kartavya Path")
    com.thealgothrim.overworld.theme.THEMES.forEach { theme ->
      val template = main {
        com.thealgothrim.overworld.car.previewCardTemplate(TestCarContext.createCarContext(appContext), preview, RouteExtras(), theme, {}, {})
      } as androidx.car.app.navigation.model.MapWithContentTemplate
      val pane = (template.contentTemplate as androidx.car.app.model.PaneTemplate).pane
      assertEquals("India Gate", (template.contentTemplate as androidx.car.app.model.PaneTemplate).title.toString())
      assertEquals(listOf("Start", "Cancel"), pane.actions.map { it.title.toString() })
      assertEquals(theme.hudAccent.toArgb(), pane.actions[0].backgroundColor!!.color)
      assertTrue(pane.rows.first().title.toString().contains("10 min"))
    }
  }

  private object NoopCallback : androidx.car.app.OnDoneCallback
}
