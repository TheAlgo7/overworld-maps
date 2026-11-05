package com.thealgothrim.overworld.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.thealgothrim.overworld.AppModule
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Shown over the map when the car app opens before location was ever allowed (a fresh install
 * opened in the car first). Android Auto puts the permission dialog on the phone, so this says so,
 * with the line Android for Cars asks of any app that sends the driver to the phone (rule VI-1), and
 * Allow works only while parked. It closes itself once location is allowed, here or on the phone.
 */
class CarLocationScreen(carContext: CarContext) : Screen(carContext) {
  init {
    AppModule.viewModel.locationAllowed.filter { it }.onEach { finish() }.launchIn(lifecycleScope)
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          // Allowed in the phone's settings meanwhile: noticed when this comes back.
          override fun onResume(owner: LifecycleOwner) = AppModule.viewModel.refreshLocationPermission()
        }
    )
  }

  override fun onGetTemplate(): Template =
      MessageTemplate.Builder(CAR_LOCATION_MESSAGE)
          .setTitle("Allow location")
          .setHeaderAction(Action.BACK)
          .addAction(
              Action.Builder()
                  .setTitle("Allow")
                  .setFlags(Action.FLAG_PRIMARY)
                  .setOnClickListener(
                      ParkedOnlyOnClickListener.create {
                        carContext.requestPermissions(LOCATION_PERMISSIONS) { _, _ -> AppModule.viewModel.refreshLocationPermission() }
                        CarToast.makeText(carContext, "Allow location on your phone. Only look at it when it's safe.", CarToast.LENGTH_LONG).show()
                      }
                  )
                  .build()
          )
          .build()
}

internal const val CAR_LOCATION_MESSAGE =
    "Overworld needs your location to show where you are and guide you. Tap Allow while parked, then allow it on your phone. " +
        "Only look at your phone when it's safe to do so."

private val LOCATION_PERMISSIONS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
