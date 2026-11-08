package ios.silv.gembrowse

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import androidx.core.graphics.scale

actual fun ImageBitmap.encodeThumbnail(targetWidth: Int, quality: Int): ByteArray {
    val source = asAndroidBitmap() // owned by the ImageBitmap: never recycle this one

    // Captures (e.g. GraphicsLayer.toImageBitmap) can be HARDWARE bitmaps, which
    // can't be read or scaled on a software canvas. Copy to a software bitmap first.
    val software = if (source.config == Bitmap.Config.HARDWARE) {
        checkNotNull(source.copy(Bitmap.Config.ARGB_8888, false)) { "could not copy hardware bitmap" }
    } else source

    val scaled = if (software.width > targetWidth) {
        val height = (software.height * targetWidth.toFloat() / software.width)
            .roundToInt()
            .coerceAtLeast(1)
        software.scale(targetWidth, height)
    } else software

    try {
        val format = if (Build.VERSION.SDK_INT >= 30) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.JPEG
        }
        val out = ByteArrayOutputStream()
        check(scaled.compress(format, quality, out)) { "bitmap compress failed" }
        return out.toByteArray()
    } finally {
        if (scaled !== software) scaled.recycle()
        if (software !== source) software.recycle()
    }
}

