package com.thealgothrim.overworld

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.display.VirtualDisplayConfig
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * Debug builds only. Measures a virtual display the way car-app-compose builds the car's (1920x720
 * at 200 dpi): a view that redraws every frame, counting the frames it draws and the frames that
 * reach the display's consumer, as Android Auto would receive them. [requested] 0 is
 * car-app-compose's default (no refresh rate asked for).
 */
object VirtualDisplayProbe {
  fun run(context: Context, requested: Float, seconds: Long = 3, onResult: (String) -> Unit) {
    val main = Handler(Looper.getMainLooper())
    val reader = ImageReader.newInstance(1920, 720, PixelFormat.RGBA_8888, 3)
    var produced = 0
    reader.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close(); produced++ }, main)
    val dm = context.getSystemService(DisplayManager::class.java)
    val display: VirtualDisplay =
        // Asking for a refresh rate needs Android 14; before that the probe measures the default twice.
        if (requested > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
          dm.createVirtualDisplay(
              VirtualDisplayConfig.Builder("ow-probe", 1920, 720, 200).setSurface(reader.surface).setRequestedRefreshRate(requested).build(),
              null,
              null,
          )!!
        } else {
          dm.createVirtualDisplay("ow-probe", 1920, 720, 200, reader.surface, 0)
        }
    var drawn = 0
    val view =
        object : View(context) {
          override fun onDraw(canvas: Canvas) {
            canvas.drawColor(if (drawn % 2 == 0) Color.DKGRAY else Color.GRAY)
            drawn++
            postInvalidateOnAnimation()
          }
        }
    val presentation = Presentation(context, display.display).apply { setContentView(view) }
    presentation.show()
    main.postDelayed({
      val start = drawn to produced
      main.postDelayed({
        val result =
            "%.0f fps drawn, %.0f fps delivered".format((drawn - start.first) / seconds.toFloat(), (produced - start.second) / seconds.toFloat())
        presentation.dismiss()
        display.release()
        reader.close()
        onResult(result)
      }, seconds * 1000)
    }, 500)
  }
}
