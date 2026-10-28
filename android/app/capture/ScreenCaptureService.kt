package com.wingai.keyboard.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.wingai.keyboard.ai.WingAiApiClient
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCaptureNotification()
        when (intent?.action) {
            ACTION_CAPTURE_FRAME -> {
                captureAndAnalyzeCurrentFrame()
                return START_STICKY
            }

            ACTION_STOP_PROJECTION -> {
                ScreenCaptureManager.stopSession()
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    ?: Activity.RESULT_CANCELED
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val captureNow = intent?.getBooleanExtra(EXTRA_CAPTURE_IMMEDIATELY, false) ?: false

                if (resultCode != Activity.RESULT_OK || resultData == null) {
                    if (!ScreenCaptureManager.hasActiveSession()) {
                        stopSelf()
                    }
                    return START_NOT_STICKY
                }

                ScreenCaptureManager.startSession(
                    context = applicationContext,
                    resultCode = resultCode,
                    permissionData = resultData,
                    onReady = {
                        if (captureNow) {
                            captureAndAnalyzeCurrentFrame()
                        }
                    },
                    onError = { message ->
                        InputConnectionBridge.updateState {
                            it.copy(isPanelOpen = true, isLoading = false, errorMessage = message)
                        }
                        stopSelf()
                    }
                )
                return START_STICKY
            }
        }
    }

    private fun captureAndAnalyzeCurrentFrame() {
        InputConnectionBridge.updateState {
            it.copy(isPanelOpen = true, isLoading = true, errorMessage = null)
        }
        ScreenCaptureManager.captureLatestFrame(
            onSuccess = { base64Image ->
                serviceScope.launch {
                    val settings = SettingsRepository(applicationContext).settingsFlow.first()
                    if (settings.inspectBeforeUpload) {
                        InputConnectionBridge.updateState {
                            it.copy(
                                isPanelOpen = true,
                                isLoading = false,
                                pendingImageBase64 = base64Image,
                                errorMessage = null
                            )
                        }
                    } else {
                        val repo = SettingsRepository(applicationContext)
                        val userId = repo.getOrCreateUserId()
                        val client = WingAiApiClient({ settings.backendUrl }, { userId })
                        val state = InputConnectionBridge.sessionState.value
                        val result = client.analyzeContext(
                            imageBase64 = base64Image,
                            tone = state.selectedTone,
                            customInstruction = state.customInstruction
                        )
                        result.fold(
                            onSuccess = { res ->
                                InputConnectionBridge.updateState {
                                    it.copy(
                                        isLoading = false,
                                        pendingImageBase64 = null,
                                        suggestions = res.replies,
                                        userProfile = res.user ?: it.userProfile,
                                        editingReplyText = res.replies.firstOrNull()?.text.orEmpty(),
                                        errorMessage = if (res.contextStatus == "insufficient") {
                                            res.message ?: "Conversation is not clear. Add more context."
                                        } else null
                                    )
                                }
                            },
                            onFailure = { err ->
                                InputConnectionBridge.updateState {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = err.message ?: "AI request failed."
                                    )
                                }
                            }
                        )
                    }
                }
            },
            onError = { message ->
                InputConnectionBridge.updateState {
                    it.copy(isPanelOpen = true, isLoading = false, errorMessage = message)
                }
            }
        )
    }

    private fun startCaptureNotification() {
        val channelId = "wingai_capture_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "WingAI Screen Assistant",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("WingAI Ready")
            .setContentText("Double-tap the floating emoji or tap Analyze Screen to read replies.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        ScreenCaptureManager.stopSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4102
        const val ACTION_INIT_PROJECTION = "com.wingai.keyboard.action.INIT_PROJECTION"
        const val ACTION_CAPTURE_FRAME = "com.wingai.keyboard.action.CAPTURE_FRAME"
        const val ACTION_STOP_PROJECTION = "com.wingai.keyboard.action.STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_CAPTURE_IMMEDIATELY = "extra_capture_immediately"
    }
}
