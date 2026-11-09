package com.wingai.keyboard.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ScreenCaptureManager {

    @Volatile
    private var mediaProjection: MediaProjection? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    private var imageReader: ImageReader? = null

    private val _capturedBitmapFlow = MutableStateFlow<Bitmap?>(null)
    val capturedBitmapFlow: StateFlow<Bitmap?> = _capturedBitmapFlow.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasActiveSession(): Boolean = false

    fun setCapturedBitmap(bitmap: Bitmap?) {
        val old = _capturedBitmapFlow.value
        _capturedBitmapFlow.value = bitmap
        if (old != null && old !== bitmap && !old.isRecycled) {
            runCatching { old.recycle() }
        }
    }

    fun clearCapturedBitmap() {
        val old = _capturedBitmapFlow.value
        _capturedBitmapFlow.value = null
        if (old != null && !old.isRecycled) {
            runCatching { old.recycle() }
        }
    }

    /**
     * Captures ONE single full-screen frame after the permission dialog closes
     * and IMMEDIATELY terminates MediaProjection & VirtualDisplay so screen sharing
     * never stays running in the background.
     */
    fun captureSingleFrameAndStop(
        context: Context,
        resultCode: Int,
        permissionData: Intent,
        onCaptured: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        stopProjectionOnly()

        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager == null) {
            onError("Screen capture is not supported on this device.")
            return
        }

        val projection = runCatching {
            manager.getMediaProjection(resultCode, permissionData)
        }.getOrNull()

        if (projection == null) {
            onError("Screen capture permission was denied.")
            return
        }

        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels.coerceAtLeast(320)
        val screenHeight = metrics.heightPixels.coerceAtLeast(480)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        var latestBitmap: Bitmap? = null

        reader.setOnImageAvailableListener({ ir ->
            val img = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val bmp = imageToBitmap(img, screenWidth, screenHeight)
                if (bmp != null) {
                    latestBitmap?.recycle()
                    latestBitmap = bmp
                }
            } finally {
                runCatching { img.close() }
            }
        }, mainHandler)

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                stopProjectionOnly()
            }
        }
        projection.registerCallback(callback, mainHandler)

        val display = runCatching {
            projection.createVirtualDisplay(
                "WingAiOneShotCapture",
                screenWidth,
                screenHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )
        }.getOrElse {
            runCatching { reader.close() }
            runCatching { projection.stop() }
            onError("Screen content is protected and cannot be captured.")
            return
        }

        mediaProjection = projection
        imageReader = reader
        virtualDisplay = display

        // Wait 450ms for the permission dialog to disappear, grab one frame, and IMMEDIATELY stop projection.
        mainHandler.postDelayed({
            val freshImg = runCatching { reader.acquireLatestImage() }.getOrNull()
            try {
                if (freshImg != null) {
                    val bmp = imageToBitmap(freshImg, screenWidth, screenHeight)
                    if (bmp != null) {
                        latestBitmap?.recycle()
                        latestBitmap = bmp
                    }
                }
            } finally {
                runCatching { freshImg?.close() }
            }

            // Immediately stop MediaProjection so screen sharing is NEVER left active.
            stopProjectionOnly()

            val finalBitmap = latestBitmap
            if (finalBitmap == null || isBlankFrame(finalBitmap)) {
                finalBitmap?.recycle()
                onError("Screen content is protected (FLAG_SECURE) or could not be captured.")
            } else {
                setCapturedBitmap(finalBitmap)
                onCaptured(finalBitmap)
            }
        }, 450L)
    }

    fun cropBitmap(
        source: Bitmap,
        normLeft: Float,
        normTop: Float,
        normRight: Float,
        normBottom: Float
    ): Bitmap {
        val leftFrac = min(normLeft, normRight).coerceIn(0f, 0.95f)
        val rightFrac = max(normLeft, normRight).coerceIn(leftFrac + 0.05f, 1f)
        val topFrac = min(normTop, normBottom).coerceIn(0f, 0.95f)
        val bottomFrac = max(normTop, normBottom).coerceIn(topFrac + 0.05f, 1f)

        val x = (source.width * leftFrac).toInt().coerceIn(0, source.width - 1)
        val y = (source.height * topFrac).toInt().coerceIn(0, source.height - 1)
        val w = ((source.width * (rightFrac - leftFrac)).toInt()).coerceIn(1, source.width - x)
        val h = ((source.height * (bottomFrac - topFrac)).toInt()).coerceIn(1, source.height - y)

        return Bitmap.createBitmap(source, x, y, w, h)
    }

    fun cropAndEncodeBitmap(
        source: Bitmap,
        normLeft: Float,
        normTop: Float,
        normRight: Float,
        normBottom: Float
    ): String {
        val cropped = cropBitmap(source, normLeft, normTop, normRight, normBottom)
        // Downscale if largest dimension exceeds 1280px for fast, reliable upload and Gemini OCR
        val maxDim = max(cropped.width, cropped.height)
        val scaled = if (maxDim > 1280) {
            val scale = 1280f / maxDim.toFloat()
            val targetW = (cropped.width * scale).toInt().coerceAtLeast(1)
            val targetH = (cropped.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        } else {
            cropped
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (scaled !== cropped && scaled !== source) {
            scaled.recycle()
        }
        if (cropped !== source) {
            cropped.recycle()
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun stopProjectionOnly() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        val proj = mediaProjection
        mediaProjection = null
        runCatching { proj?.stop() }
    }

    fun stopSession() {
        stopProjectionOnly()
        clearCapturedBitmap()
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val rawBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        rawBitmap.copyPixelsFromBuffer(buffer)
        return if (rawBitmap.width == width) {
            rawBitmap
        } else {
            val trimmed = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
            rawBitmap.recycle()
            trimmed
        }
    }

    private fun isBlankFrame(bitmap: Bitmap): Boolean {
        val sampleX = listOf(bitmap.width / 4, bitmap.width / 2, (bitmap.width * 3) / 4)
        val sampleY = listOf(bitmap.height / 4, bitmap.height / 2, (bitmap.height * 3) / 4)
        var nonBlackPixels = 0
        for (x in sampleX) {
            for (y in sampleY) {
                val pixel = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                if (r > 4 || g > 4 || b > 4) {
                    nonBlackPixels++
                }
            }
        }
        return nonBlackPixels == 0
    }
}
