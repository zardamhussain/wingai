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
import java.io.ByteArrayOutputStream

object ScreenCaptureManager {

    @Volatile
    private var mediaProjection: MediaProjection? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    private var imageReader: ImageReader? = null

    @Volatile
    private var latestEncodedFrame: String? = null

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasActiveSession(): Boolean =
        mediaProjection != null && virtualDisplay != null && imageReader != null

    fun startSession(
        context: Context,
        resultCode: Int,
        permissionData: Intent,
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        stopSession()

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
        screenWidth = metrics.widthPixels.coerceAtLeast(320)
        screenHeight = metrics.heightPixels.coerceAtLeast(480)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ ir ->
            val img = runCatching { ir.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val encoded = encodeCroppedConversationFrame(img, screenWidth, screenHeight)
                if (encoded != null) {
                    latestEncodedFrame = encoded
                }
            } finally {
                runCatching { img.close() }
            }
        }, mainHandler)

        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                stopSession()
            }
        }
        projection.registerCallback(callback, mainHandler)

        val display = runCatching {
            projection.createVirtualDisplay(
                "WingAiScreenSession",
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
        // Wait 550ms after permission dialog closes so the underlying chat app is fully visible.
        mainHandler.postDelayed({ onReady() }, 550L)
    }

    fun captureLatestFrame(
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val reader = imageReader
        if (!hasActiveSession() || reader == null) {
            onError("Screen permission is not active. Tap Read again to grant permission.")
            return
        }

        mainHandler.postDelayed({
            val freshImage = runCatching { reader.acquireLatestImage() }.getOrNull()
            try {
                if (freshImage != null) {
                    val encoded = encodeCroppedConversationFrame(freshImage, screenWidth, screenHeight)
                    if (encoded != null) {
                        latestEncodedFrame = encoded
                    }
                }
            } finally {
                runCatching { freshImage?.close() }
            }

            val frame = latestEncodedFrame
            if (frame == null) {
                onError("Screen content is protected (FLAG_SECURE) or not ready yet.")
            } else {
                onSuccess(frame)
            }
        }, 150L)
    }

    fun stopSession() {
        latestEncodedFrame = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        val proj = mediaProjection
        mediaProjection = null
        runCatching { proj?.stop() }
    }

    private fun encodeCroppedConversationFrame(image: Image, width: Int, height: Int): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val fullBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        fullBitmap.copyPixelsFromBuffer(buffer)

        // Trim top status bar (6%) and bottom keyboard area (36%) so Gemini sees only the chat conversation.
        val topCrop = (height * 0.06f).toInt().coerceAtLeast(0)
        val bottomCrop = (height * 0.36f).toInt().coerceAtLeast(0)
        val cropHeight = (height - topCrop - bottomCrop).coerceAtLeast(height / 3)

        val cropped = Bitmap.createBitmap(fullBitmap, 0, topCrop, width, cropHeight)
        fullBitmap.recycle()

        if (isBlankFrame(cropped)) {
            cropped.recycle()
            return null
        }

        val out = ByteArrayOutputStream()
        cropped.compress(Bitmap.CompressFormat.JPEG, 82, out)
        cropped.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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
