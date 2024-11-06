package app.develop.camera.util

import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.math.max

@RequiresApi(Build.VERSION_CODES.P)
class ImageResizer(val targetWidth: Int, val targetHeight: Int) : ImageDecoder.OnHeaderDecodedListener {
    override fun onHeaderDecoded(decoder: ImageDecoder, info: ImageDecoder.ImageInfo, source: ImageDecoder.Source) {
        val size = info.size
        val w = size.width.toDouble()
        val h = size.height.toDouble()

        val ratio = max(w / targetWidth, h / targetHeight)
        decoder.setTargetSize((w / ratio).toInt(), (h / ratio).toInt())
    }
}
