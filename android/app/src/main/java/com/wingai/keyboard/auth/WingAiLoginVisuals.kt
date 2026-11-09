package com.wingai.keyboard.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// User reference: turquoise illustrated upper section + edge-to-edge white rounded sheet.
// This fixed light palette is scoped ONLY to login/signup/reset, not keyboard or account UI.
private val LoginColors = lightColorScheme(
    primary = Color(0xFF007A82), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F6F6), onPrimaryContainer = Color(0xFF064E54),
    secondary = Color(0xFF007A82), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F6F3), onSecondaryContainer = Color(0xFF13544D),
    background = Color.White, onBackground = Color(0xFF16282C),
    surface = Color.White, onSurface = Color(0xFF16282C),
    surfaceContainer = Color.White, surfaceContainerHighest = Color(0xFFF1F3F4),
    surfaceVariant = Color(0xFFF1F3F4), onSurfaceVariant = Color(0xFF626F73),
    outline = Color(0xFFBAC5C7), outlineVariant = Color(0xFFE8EEEE),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFFEDEA), onErrorContainer = Color(0xFF7A1510)
)

@Composable
internal fun WingAiLoginPage(mode: AuthMode, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    MaterialTheme(colorScheme = LoginColors) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color(0xFF15A2AD))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val minimumHeight = maxHeight
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .background(Color.White).heightIn(min = minimumHeight)
            ) {
                LoginHero(mode, onClose)
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    // Natural content height is essential: a weighted sheet constrains
                    // long forms to the viewport and clips controls at large font scales.
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF15A2AD))
                        .testTag("auth-white-sheet")
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 12.dp)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginHero(mode: AuthMode, onClose: () -> Unit) {
    // Text measures naturally; the artwork has its own area and cannot overlap large type.
    Column(
        Modifier.fillMaxWidth().background(Brush.linearGradient(
            // Slightly deepen the reference teal so the large white heading clears 3:1.
            listOf(Color(0xFF15A2AD), Color(0xFF0E929F))))
            .testTag("auth-turquoise-header")
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WingAI", color = Color(0xFF073D43), fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp, letterSpacing = 0.3.sp)
            TextButton(onClick = onClose) {
                Text("Close", color = Color(0xFF073D43), fontSize = 12.sp)
            }
        }
        Text(
            when (mode) {
                AuthMode.SIGN_IN -> "Log in for better\nconversations."
                AuthMode.REGISTER -> "Create your account.\nFind your words."
                AuthMode.RESET -> "A fresh start,\none step away."
            },
            color = Color.White, fontSize = 24.sp, lineHeight = 30.sp,
            fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp)
        )
        WingAiConversationArtwork(Modifier.fillMaxWidth().height(116.dp).clipToBounds())
    }
}

/** Original WingAI chat/keyboard illustration, inspired by the reference's product artwork.
 * Not an interactive screenshot, fake third-party brand, or copied Asana illustration.
 */
@Composable
private fun WingAiConversationArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.width / 390f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun box(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
            drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
        }
        fun line(color: Color, x: Float, y: Float, w: Float, h: Float = 4f) = box(color, x, y, w, h, h / 2)
        val mint = Color(0xFFBCF1ED)
        val deep = Color(0xFF074D56)
        // Faint rings and stars make a lively hero while keeping the UI itself quiet.
        drawCircle(Color.White.copy(alpha = 0.12f), 44f * u, p(350f, 7f), style = Stroke(1.4f * u))
        drawCircle(Color.White.copy(alpha = 0.09f), 35f * u, p(40f, 78f), style = Stroke(1.4f * u))
        for ((x, y) in listOf(37f to 28f, 182f to 35f, 335f to 77f)) {
            val star = Path().apply {
                moveTo(x * u, (y - 6f) * u); lineTo((x + 2f) * u, (y - 2f) * u)
                lineTo((x + 6f) * u, y * u); lineTo((x + 2f) * u, (y + 2f) * u)
                lineTo(x * u, (y + 6f) * u); lineTo((x - 2f) * u, (y + 2f) * u)
                lineTo((x - 6f) * u, y * u); lineTo((x - 2f) * u, (y - 2f) * u); close()
            }
            drawPath(star, mint.copy(alpha = 0.7f))
        }
        // Conversation cards to the left, visually connected to the phone keyboard.
        box(deep.copy(alpha = 0.12f), 68f, 30f, 116f, 46f, 12f)
        box(mint, 62f, 24f, 116f, 46f, 12f)
        drawCircle(Color(0xFF64C9C8), 9f * u, p(80f, 41f))
        line(deep.copy(alpha = 0.5f), 97f, 36f, 60f)
        line(deep.copy(alpha = 0.25f), 97f, 47f, 44f)
        box(Color.White.copy(alpha = 0.94f), 99f, 80f, 98f, 35f, 11f)
        line(Color(0xFF3CA8AB), 113f, 91f, 63f)
        line(Color(0xFFB1DAD9), 113f, 100f, 42f, 3f)
        // Angled phone rising from the lower panel, with an actual keyboard motif.
        rotate(8f, pivot = p(274f, 83f)) {
            box(deep.copy(alpha = 0.16f), 226f, 15f, 110f, 160f, 19f)
            box(Color(0xFF103A42), 220f, 9f, 110f, 160f, 19f)
            box(Color(0xFFF2FAF9), 224f, 13f, 102f, 152f, 15f)
            box(Color(0xFF103A42), 253f, 13f, 44f, 8f, 4f)
            box(Color(0xFFD4EBE9), 233f, 35f, 61f, 20f, 6f)
            box(Color(0xFF29B7BD), 251f, 62f, 65f, 23f, 7f)
            line(Color.White.copy(alpha = 0.8f), 260f, 70f, 43f, 3f)
            line(Color.White.copy(alpha = 0.6f), 260f, 77f, 30f, 2f)
            box(Color(0xFFDCECEB), 230f, 94f, 90f, 51f, 6f)
            for (row in 0..2) for (key in 0..7) {
                box(Color.White, 234f + key * 10.3f + (row % 2) * 2f,
                    99f + row * 12f, 7.5f, 8f, 2f)
            }
            box(Color.White, 251f, 135f, 48f, 5f, 2f)
        }
    }
}
