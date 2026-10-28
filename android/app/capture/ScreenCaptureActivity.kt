package com.wingai.keyboard.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.wingai.keyboard.data.InputConnectionBridge

class ScreenCaptureActivity : ComponentActivity() {

    private var captureImmediately: Boolean = true

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_INIT_PROJECTION
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                putExtra(ScreenCaptureService.EXTRA_CAPTURE_IMMEDIATELY, captureImmediately)
            }
            startForegroundService(serviceIntent)
        } else {
            InputConnectionBridge.updateState {
                it.copy(
                    isPanelOpen = true,
                    isLoading = false,
                    errorMessage = "Screen capture permission denied. Use Paste Context instead."
                )
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureImmediately = intent?.getBooleanExtra(
            ScreenCaptureService.EXTRA_CAPTURE_IMMEDIATELY,
            true
        ) ?: true

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager != null) {
            captureLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            InputConnectionBridge.updateState {
                it.copy(
                    isPanelOpen = true,
                    errorMessage = "Screen capture is unavailable on this device."
                )
            }
            finish()
        }
    }
}
