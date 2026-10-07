package com.secureledger.integration.qr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlin.math.max
import kotlin.math.roundToInt

/** Decode only the selected URI, off the UI thread, with bounded bitmap allocation. */
object QrImageDecoder {
    private const val MAX_EDGE = 2048

    fun decode(resolver: ContentResolver, uri: Uri): String? {
        val bitmap = readBitmap(resolver, uri)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            // Transparent QR backgrounds must appear white, not black, to the decoder.
            for (index in pixels.indices) {
                val color = pixels[index]
                val alpha = color ushr 24
                if (alpha != 255) {
                    fun blend(shift: Int) = (((color ushr shift) and 255) * alpha + 255 * (255 - alpha)) / 255
                    pixels[index] = (255 shl 24) or (blend(16) shl 16) or (blend(8) shl 8) or blend(0)
                }
            }
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val reader = MultiFormatReader().apply {
                setHints(mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8"
                ))
            }
            for (candidate in listOf(source, source.invert())) {
                for (binarizer in listOf(HybridBinarizer(candidate), GlobalHistogramBinarizer(candidate))) {
                    try {
                        return reader.decodeWithState(BinaryBitmap(binarizer)).text
                    } catch (_: NotFoundException) {
                        // Try the alternate threshold / polarity before reporting no QR.
                    } finally {
                        reader.reset()
                    }
                }
            }
            return null
        } finally {
            bitmap.recycle()
        }
    }

    private fun readBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF orientation and downsizes before allocating pixels.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1.0, MAX_EDGE.toDouble() / max(info.size.width, info.size.height))
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).roundToInt()),
                    max(1, (info.size.height * scale).roundToInt())
                )
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "无法读取图片" }
        options.inSampleSize = 1
        while (max(options.outWidth, options.outHeight) / options.inSampleSize > MAX_EDGE) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return requireNotNull(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }) {
            "无法读取图片"
        }
    }
}
