package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/** 아바타 이미지 압축: EXIF 회전 자동 보정 + 최대 200x200, JPEG 품질 60 */
internal fun compressAvatar(context: Context, uri: Uri): ByteArray? {
    return try {
        var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setTargetSampleSize(2)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            var sampleSize = 1
            while (opts.outWidth / sampleSize > 400 || opts.outHeight / sampleSize > 400) sampleSize *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                if (rotated !== bmp) bmp.recycle()
                bmp = rotated
            }
            bmp
        }

        val maxSide = 200
        val scale = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height, 1f)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        if (bitmap.config == Bitmap.Config.HARDWARE) {
            val sw = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            bitmap.recycle()
            bitmap = sw
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
        bitmap.recycle()
        out.toByteArray()
    } catch (e: Exception) {
        Log.e("dogakdogak", "compressAvatar failed", e)
        null
    }
}

internal fun Modifier.simpleScrollbar(
    state: ScrollState,
    color: Color,
    width: Dp = 3.dp
): Modifier = this.drawWithContent {
    drawContent()
    val scrollValue = state.value.toFloat()
    val maxScroll = state.maxValue.toFloat()
    if (maxScroll > 0f) {
        val viewportH = size.height
        val totalH = viewportH + maxScroll
        val thumbH = (viewportH / totalH) * viewportH
        val thumbY = (scrollValue / maxScroll) * (viewportH - thumbH)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - width.toPx(), thumbY),
            size = Size(width.toPx(), thumbH),
            cornerRadius = CornerRadius(width.toPx() / 2)
        )
    }
}
