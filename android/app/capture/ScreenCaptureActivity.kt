package com.wingai.keyboard.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingai.keyboard.ai.WingAiApiClient
import com.wingai.keyboard.data.AiHubMode
import com.wingai.keyboard.data.InputConnectionBridge
import com.wingai.keyboard.settings.SettingsRepository
import com.wingai.keyboard.ui.WingAiTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private enum class DragMode {
    NEW_BOX,
    MOVE_BOX,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

class ScreenCaptureActivity : ComponentActivity() {

    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var keyboardRestoreTriggered = false

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_INIT_PROJECTION
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(serviceIntent)
            finish()
        } else {
            InputConnectionBridge.updateState {
                it.copy(
                    isPanelOpen = true,
                    isLoading = false,
                    errorMessage = "Selection canceled."
                )
            }
            closeAndRestoreKeyboard()
        }
    }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = runCatching {
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bmp != null) {
                ScreenCaptureManager.setCapturedBitmap(bmp)
            }
        } else if (ScreenCaptureManager.capturedBitmapFlow.value == null) {
            closeAndRestoreKeyboard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            WingAiTheme {
                val bitmap by ScreenCaptureManager.capturedBitmapFlow.collectAsState()
                BackHandler {
                    ScreenCaptureManager.clearCapturedBitmap()
                    closeAndRestoreKeyboard()
                }
                if (bitmap != null && !bitmap!!.isRecycled) {
                    SelectAndSearchCropOverlay(
                        bitmap = bitmap!!,
                        onPickFromGallery = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCancel = {
                            ScreenCaptureManager.clearCapturedBitmap()
                            closeAndRestoreKeyboard()
                        },
                        onConfirmSelection = { left, top, right, bottom ->
                            val croppedBitmap = ScreenCaptureManager.cropBitmap(
                                bitmap!!,
                                left,
                                top,
                                right,
                                bottom
                            )
                            ScreenCaptureManager.clearCapturedBitmap()
                            analyzeSelectedCropBitmap(croppedBitmap)
                            closeAndRestoreKeyboard()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        keyboardRestoreTriggered = false
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            triggerKeyboardRestoreOnce()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            ScreenCaptureManager.clearCapturedBitmap()
            triggerKeyboardRestoreOnce()
        }
        super.onDestroy()
    }

    private fun closeAndRestoreKeyboard() {
        triggerKeyboardRestoreOnce()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun triggerKeyboardRestoreOnce() {
        if (!keyboardRestoreTriggered) {
            keyboardRestoreTriggered = true
            InputConnectionBridge.restoreKeyboardAfterSelection()
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_CROP -> Unit
            ACTION_PICK_IMAGE -> {
                pickMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            else -> {
                if (ScreenCaptureManager.capturedBitmapFlow.value == null) {
                    requestOneShotSnapshot()
                }
            }
        }
    }

    private fun requestOneShotSnapshot() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (manager != null) {
            captureLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private fun analyzeSelectedCropBitmap(croppedBitmap: Bitmap) {
        InputConnectionBridge.updateState {
            it.copy(
                isPanelOpen = true,
                hubMode = AiHubMode.REPLY,
                isLoading = true,
                selectedImageBase64 = null,
                pendingImageBase64 = null,
                errorMessage = null
            )
        }
        val appContext = applicationContext
        analysisScope.launch {
            val ocrResult = try {
                ChatOcrExtractor.extractStructuredChat(croppedBitmap)
            } finally {
                if (!croppedBitmap.isRecycled) {
                    runCatching { croppedBitmap.recycle() }
                }
            }

            val structured = ocrResult.getOrElse { err ->
                InputConnectionBridge.updateState {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Could not extract text from selected region."
                    )
                }
                return@launch
            }

            InputConnectionBridge.updateState {
                it.copy(
                    structuredContext = structured,
                    pastedContext = structured.formattedTranscript
                )
            }

            val repo = SettingsRepository(appContext)
            val userId = repo.getOrCreateUserId()
            val settings = repo.settingsFlow.first()
            val client = WingAiApiClient({ settings.backendUrl }, { userId })
            val state = InputConnectionBridge.sessionState.value
            val result = client.analyzeContext(
                conversationText = structured.formattedTranscript,
                tone = state.selectedTone,
                customInstruction = state.customInstruction,
                structuredContext = structured
            )
            result.fold(
                onSuccess = { res ->
                    InputConnectionBridge.updateState {
                        it.copy(
                            isLoading = false,
                            suggestions = res.replies,
                            userProfile = res.user ?: it.userProfile,
                            editingReplyText = res.replies.firstOrNull()?.text.orEmpty(),
                            errorMessage = if (res.contextStatus == "insufficient") {
                                res.message ?: "Conversation is not clear. Select a clearer chat section."
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

    companion object {
        const val ACTION_SHOW_CROP = "com.wingai.keyboard.action.SHOW_CROP"
        const val ACTION_PICK_IMAGE = "com.wingai.keyboard.action.PICK_IMAGE"
    }
}

@Composable
fun SelectAndSearchCropOverlay(
    bitmap: Bitmap,
    onPickFromGallery: (() -> Unit)? = null,
    onCancel: () -> Unit,
    onConfirmSelection: (Float, Float, Float, Float) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Since the keyboard is hidden before taking the snapshot, default selection covers up to 80% height
    var normLeft by remember { mutableFloatStateOf(0.05f) }
    var normTop by remember { mutableFloatStateOf(0.12f) }
    var normRight by remember { mutableFloatStateOf(0.95f) }
    var normBottom by remember { mutableFloatStateOf(0.80f) }

    var dragMode by remember { mutableStateOf(DragMode.NEW_BOX) }
    var dragStartX by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (boxSize.width > 0 && boxSize.height > 0) {
                            val nx = (offset.x / boxSize.width).coerceIn(0f, 1f)
                            val ny = (offset.y / boxSize.height).coerceIn(0f, 1f)
                            val hitTol = 0.06f
                            dragMode = when {
                                abs(nx - normLeft) < hitTol && abs(ny - normTop) < hitTol -> DragMode.TOP_LEFT
                                abs(nx - normRight) < hitTol && abs(ny - normTop) < hitTol -> DragMode.TOP_RIGHT
                                abs(nx - normLeft) < hitTol && abs(ny - normBottom) < hitTol -> DragMode.BOTTOM_LEFT
                                abs(nx - normRight) < hitTol && abs(ny - normBottom) < hitTol -> DragMode.BOTTOM_RIGHT
                                nx in normLeft..normRight && ny in normTop..normBottom -> DragMode.MOVE_BOX
                                else -> DragMode.NEW_BOX
                            }
                            dragStartX = nx
                            dragStartY = ny
                            if (dragMode == DragMode.NEW_BOX) {
                                normLeft = nx
                                normRight = nx
                                normTop = ny
                                normBottom = ny
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (boxSize.width > 0 && boxSize.height > 0) {
                            val nx = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                            val ny = (change.position.y / boxSize.height).coerceIn(0f, 1f)
                            val dx = dragAmount.x / boxSize.width
                            val dy = dragAmount.y / boxSize.height
                            when (dragMode) {
                                DragMode.NEW_BOX -> {
                                    normLeft = min(dragStartX, nx)
                                    normRight = max(dragStartX, nx)
                                    normTop = min(dragStartY, ny)
                                    normBottom = max(dragStartY, ny)
                                }
                                DragMode.MOVE_BOX -> {
                                    val w = normRight - normLeft
                                    val h = normBottom - normTop
                                    val nextL = (normLeft + dx).coerceIn(0f, 1f - w)
                                    val nextT = (normTop + dy).coerceIn(0f, 1f - h)
                                    normLeft = nextL
                                    normRight = nextL + w
                                    normTop = nextT
                                    normBottom = nextT + h
                                }
                                DragMode.TOP_LEFT -> {
                                    normLeft = nx.coerceAtMost(normRight - 0.08f)
                                    normTop = ny.coerceAtMost(normBottom - 0.06f)
                                }
                                DragMode.TOP_RIGHT -> {
                                    normRight = nx.coerceAtLeast(normLeft + 0.08f)
                                    normTop = ny.coerceAtMost(normBottom - 0.06f)
                                }
                                DragMode.BOTTOM_LEFT -> {
                                    normLeft = nx.coerceAtMost(normRight - 0.08f)
                                    normBottom = ny.coerceAtLeast(normTop + 0.06f)
                                }
                                DragMode.BOTTOM_RIGHT -> {
                                    normRight = nx.coerceAtLeast(normLeft + 0.08f)
                                    normBottom = ny.coerceAtLeast(normTop + 0.06f)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (normRight - normLeft < 0.08f) {
                            normRight = (normLeft + 0.15f).coerceAtMost(1f)
                        }
                        if (normBottom - normTop < 0.06f) {
                            normBottom = (normTop + 0.12f).coerceAtMost(1f)
                        }
                    }
                )
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Screen Snapshot",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // Google Lens / Circle-to-Search Style Scrim + Cutout + Rounded Corner Brackets
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(color = Color(0x990A0C10))

            val leftPx = size.width * normLeft
            val topPx = size.height * normTop
            val rightPx = size.width * normRight
            val bottomPx = size.height * normBottom
            val widthPx = rightPx - leftPx
            val heightPx = bottomPx - topPx
            val cornerR = min(56f, min(widthPx, heightPx) * 0.22f)
            val armLen = min(76f, min(widthPx, heightPx) * 0.32f)

            // Clear the selected cutout
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(leftPx, topPx),
                size = Size(widthPx, heightPx),
                cornerRadius = CornerRadius(cornerR, cornerR),
                blendMode = BlendMode.Clear
            )

            // Subtle translucent full perimeter hairline
            drawRoundRect(
                color = Color(0x40FFFFFF),
                topLeft = Offset(leftPx, topPx),
                size = Size(widthPx, heightPx),
                cornerRadius = CornerRadius(cornerR, cornerR),
                style = Stroke(width = 2.5f)
            )

            // 4 Google-Lens style rounded corner brackets (no dots)
            val bracketPath = Path().apply {
                // Top-Left bracket
                moveTo(leftPx, topPx + armLen)
                lineTo(leftPx, topPx + cornerR)
                quadraticTo(leftPx, topPx, leftPx + cornerR, topPx)
                lineTo(leftPx + armLen, topPx)

                // Top-Right bracket
                moveTo(rightPx - armLen, topPx)
                lineTo(rightPx - cornerR, topPx)
                quadraticTo(rightPx, topPx, rightPx, topPx + cornerR)
                lineTo(rightPx, topPx + armLen)

                // Bottom-Left bracket
                moveTo(leftPx, bottomPx - armLen)
                lineTo(leftPx, bottomPx - cornerR)
                quadraticTo(leftPx, bottomPx, leftPx + cornerR, bottomPx)
                lineTo(leftPx + armLen, bottomPx)

                // Bottom-Right bracket
                moveTo(rightPx - armLen, bottomPx)
                lineTo(rightPx - cornerR, bottomPx)
                quadraticTo(rightPx, bottomPx, rightPx, bottomPx - cornerR)
                lineTo(rightPx, bottomPx - armLen)
            }

            drawPath(
                path = bracketPath,
                color = Color.White,
                style = Stroke(
                    width = 11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Top Centered Minimal Instruction Pill (no cross button)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xE616181D))
                .border(1.dp, Color(0xFF2E323D), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A6CF7))
            )
            Text(
                text = "Drag corners to select messages",
                color = Color(0xFFF3F4F6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Sleek Minimal Floating Pill Dock at Bottom (Cancel + Analyze only)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 32.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xF216181D))
                .border(1.dp, Color(0xFF2E323D), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverlayDockPill(
                text = "Cancel",
                backgroundColor = Color(0xFF242730),
                textColor = Color(0xFFD1D5DB),
                onClick = onCancel
            )
            OverlayDockPill(
                text = "Analyze",
                backgroundColor = Color(0xFF4A6CF7),
                textColor = Color.White,
                isPrimary = true,
                onClick = { onConfirmSelection(normLeft, normTop, normRight, normBottom) }
            )
        }
    }
}

@Composable
private fun OverlayDockPill(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = if (isPrimary) 18.dp else 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.5.sp,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
