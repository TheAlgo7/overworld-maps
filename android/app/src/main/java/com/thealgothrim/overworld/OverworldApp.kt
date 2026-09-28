package com.thealgothrim.overworld

import android.app.Application

class OverworldApp : Application() {
  override fun onCreate() {
    super.onCreate()
    AppModule.init(this)
  }
}
