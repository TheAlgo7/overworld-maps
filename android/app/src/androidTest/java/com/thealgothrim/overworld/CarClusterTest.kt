package com.thealgothrim.overworld

import android.content.Intent
import androidx.car.app.SessionInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thealgothrim.overworld.car.CarClusterScreen
import com.thealgothrim.overworld.car.OverworldCarAppService
import com.thealgothrim.overworld.car.OverworldCarSession
import com.thealgothrim.overworld.car.OverworldClusterSession
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The instrument cluster: Android Auto asks for a session per display, and the cluster's must be
 * the map-only one, whose screen comes up on its own (the cluster can open first, or alone).
 */
@RunWith(AndroidJUnit4::class)
class CarClusterTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun <T> main(block: () -> T): T {
    var result: T? = null
    instrumentation.runOnMainSync { result = block() }
    @Suppress("UNCHECKED_CAST") return result as T
  }

  @Test
  fun clusterDisplayGetsTheMapOnlySession() {
    // Android Auto makes sessions on the main thread (a session observes its own lifecycle).
    val service = OverworldCarAppService()
    assertTrue(main { service.onCreateSession(SessionInfo(SessionInfo.DISPLAY_TYPE_CLUSTER, "cluster")) } is OverworldClusterSession)
    assertTrue(main { service.onCreateSession(SessionInfo(SessionInfo.DISPLAY_TYPE_MAIN, "main")) } is OverworldCarSession)
  }

  @Test
  fun clusterScreenIsAMapWithNoCards() {
    val appContext = instrumentation.targetContext
    val carContext = main { TestCarContext.createCarContext(appContext) }
    main {
      SessionController(OverworldClusterSession(), carContext, Intent()).moveToState(Lifecycle.State.RESUMED)
    }
    val screen = main { carContext.getCarService(TestScreenManager::class.java).top }
    assertTrue("cluster screen is ${screen::class.simpleName}", screen is CarClusterScreen)
    val template = main { screen.onGetTemplate() } as NavigationTemplate
    // The car's cluster prints the turn and the time itself, from the trip the main screen sends.
    assertNull(template.navigationInfo)
    assertNull(template.destinationTravelEstimate)
  }
}
