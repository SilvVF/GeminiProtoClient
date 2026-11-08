package ios.silv.gembrowse

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import kotlin.math.roundToInt


actual fun ImageBitmap.encodeThumbnail(targetWidth: Int, quality: Int): ByteArray {
    val source = Image.makeFromBitmap(asSkiaBitmap())
    try {
        val width = minOf(targetWidth, source.width)
        val height = (source.height * width.toFloat() / source.width)
            .roundToInt()
            .coerceAtLeast(1)

        Surface.makeRasterN32Premul(width, height).use { surface ->
            surface.canvas.drawImageRect(
                source,
                Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), // good quality for big downscales
                null,
                true
            )
            val snapshot = surface.makeImageSnapshot()
            try {
                val data = snapshot.encodeToData(EncodedImageFormat.WEBP, quality)
                return checkNotNull(data) { "skia failed to encode thumbnail" }.bytes
            } finally {
                snapshot.close()
            }
        }
    } finally {
        source.close()
    }
}
