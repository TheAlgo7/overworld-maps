package com.thealgothrim.overworld

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: a two-finger upward drag (the map's tilt gesture, or a pinch with mode=pinch) sent to whichever app is named in
 * the "pkg" argument, the same for every app, so their tilt can be compared from screenshots. adb's
 * own input can only drag one finger. Run by hand:
 * am instrument -w -e class com.thealgothrim.overworld.TiltGestureProbe -e pkg <package> ...
 */
@RunWith(AndroidJUnit4::class)
class TiltGestureProbe {
  @Test
  fun tilt() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val args = InstrumentationRegistry.getArguments()
    val pkg = args.getString("pkg") ?: return
    val context = instrumentation.context
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
      it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(it)
    }
    Thread.sleep(args.getString("wait")?.toLong() ?: 4_000)
    val automation = instrumentation.uiAutomation
    // "pinch": the fingers close in from 150/930 to 400/680 instead of moving up together.
    val pinch = args.getString("mode") == "pinch"
    val fromY = args.getString("from")?.toFloat() ?: 1_500f
    val toY = args.getString("to")?.toFloat() ?: 1_000f
    val millis = args.getString("ms")?.toLong() ?: 500
    val down = SystemClock.uptimeMillis()
    fun event(action: Int, y: Float, pointers: Int, at: Long): MotionEvent {
      val props = Array(pointers) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
      val f = if (pinch) ((y - fromY) / (toY - fromY)).coerceIn(0f, 1f) else 0f
      val xs = if (pinch) floatArrayOf(150f + 250f * f, 930f - 250f * f) else floatArrayOf(380f, 700f)
      val yy = if (pinch) 1_200f else y
      val coords = Array(pointers) { i -> MotionEvent.PointerCoords().apply { x = xs[i]; this.y = yy; pressure = 1f; size = 0.1f } }
      return MotionEvent.obtain(down, at, action, pointers, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }
    automation.injectInputEvent(event(MotionEvent.ACTION_DOWN, fromY, 1, down), true)
    automation.injectInputEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), fromY, 2, down + 5), true)
    val steps = (millis / 8).toInt()
    for (k in 1..steps) {
      val at = down + 5 + k * 8
      SystemClock.sleep(8)
      automation.injectInputEvent(event(MotionEvent.ACTION_MOVE, fromY + (toY - fromY) * k / steps, 2, at), true)
    }
    val end = down + 10 + steps * 8L
    automation.injectInputEvent(event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), toY, 2, end), true)
    automation.injectInputEvent(event(MotionEvent.ACTION_UP, toY, 1, end + 5), true)
    Thread.sleep(1_000)
  }
}
