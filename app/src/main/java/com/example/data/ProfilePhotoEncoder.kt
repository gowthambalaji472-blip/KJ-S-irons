package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ProfilePhotoEncoder {
    private const val TARGET_SIZE_PX = 192
    private const val MAX_BASE64_CHARS = 76_000

    suspend fun encodeUriToBase64(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        var sampledBitmap: Bitmap? = null
        var orientedBitmap: Bitmap? = null
        var croppedBitmap: Bitmap? = null
        var scaledBitmap: Bitmap? = null
        try {
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to open selected image."))

            val sampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight,
                TARGET_SIZE_PX * 2,
                TARGET_SIZE_PX * 2,
            )

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode selected image."))

            val rotationDegrees = resolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            orientedBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(
                    sampledBitmap,
                    0,
                    0,
                    sampledBitmap.width,
                    sampledBitmap.height,
                    matrix,
                    true,
                )
            } else {
                sampledBitmap
            }

            val side = min(orientedBitmap.width, orientedBitmap.height).coerceAtLeast(1)
            val cropX = ((orientedBitmap.width - side) / 2).coerceAtLeast(0)
            val cropY = ((orientedBitmap.height - side) / 2).coerceAtLeast(0)
            croppedBitmap = Bitmap.createBitmap(orientedBitmap, cropX, cropY, side, side)

            scaledBitmap = if (side != TARGET_SIZE_PX) {
                Bitmap.createScaledBitmap(croppedBitmap, TARGET_SIZE_PX, TARGET_SIZE_PX, true)
            } else {
                croppedBitmap
            }

            val qualities = intArrayOf(78, 65, 50, 35)
            for (quality in qualities) {
                val out = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= MAX_BASE64_CHARS) {
                    return@withContext Result.success(encoded)
                }
            }
            Result.failure(IllegalArgumentException("Image could not be compressed under size limit."))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (scaledBitmap != null && scaledBitmap !== croppedBitmap) scaledBitmap.recycle()
            if (croppedBitmap != null && croppedBitmap !== orientedBitmap) croppedBitmap.recycle()
            if (orientedBitmap != null && orientedBitmap !== sampledBitmap) orientedBitmap.recycle()
            sampledBitmap?.recycle()
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
