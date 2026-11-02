package com.wingai.keyboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType

@Immutable
data class WingAiPalette(
    val isLight: Boolean,
    val bgCanvas: Color,
    val surface0: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val borderAccent: Color,
    val accentPrimary: Color,
    val onAccentPrimary: Color,
    val accentPrimaryContainer: Color,
    val accentCyan: Color,
    val accentEmerald: Color,
    val accentEmeraldContainer: Color,
    val accentAmber: Color,
    val accentRose: Color,
    val accentRoseContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

/**
 * Exact Microsoft SwiftKey Fluent Adaptive Dark Palette (`assets/themes/7c610bde.../style.json` & `colors.xml`):
 * - `gray_950` (`#141414`): Main keyboard / panel background
 * - `gray_700` (`#404040`): `toolbar_panel_card_bg_dark`
 * - `gray_800` (`#292929`): Primary key background
 * - `sk_brand_20` (`#9DC3FF`): SwiftKey dark-mode primary accent (`@color/sk_primary`)
 * - `sk_brand_50` (`#3961ED`): SwiftKey light-mode primary accent
 */
private val DefaultDarkPalette = WingAiPalette(
    isLight = false,
    bgCanvas = Color(0xFF141414),
    surface0 = Color(0xFF141414),
    surface1 = Color(0xFF404040),
    surface2 = Color(0xFF292929),
    surface3 = Color(0xFF4A4A4A),
    borderSubtle = Color(0xFF000000),
    borderStrong = Color(0xFF4A4A4A),
    borderAccent = Color(0xFF9DC3FF),
    accentPrimary = Color(0xFF9DC3FF),
    onAccentPrimary = Color(0xFF000000),
    accentPrimaryContainer = Color(0xFF1A2D6E),
    accentCyan = Color(0xFF18C1ED),
    accentEmerald = Color(0xFF52B471),
    accentEmeraldContainer = Color(0xFF084606),
    accentAmber = Color(0xFFFFB657),
    accentRose = Color(0xFFE63237),
    accentRoseContainer = Color(0xFF6C0000),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB7B7B7),
    textMuted = Color(0xFF939393)
)

val LocalWingAiPalette = staticCompositionLocalOf { DefaultDarkPalette }

object WingAiTokens {
    val BgCanvas: Color @Composable get() = LocalWingAiPalette.current.bgCanvas
    val Surface0: Color @Composable get() = LocalWingAiPalette.current.surface0
    val Surface1: Color @Composable get() = LocalWingAiPalette.current.surface1
    val Surface2: Color @Composable get() = LocalWingAiPalette.current.surface2
    val Surface3: Color @Composable get() = LocalWingAiPalette.current.surface3

    val BorderSubtle: Color @Composable get() = LocalWingAiPalette.current.borderSubtle
    val BorderStrong: Color @Composable get() = LocalWingAiPalette.current.borderStrong
    val BorderAccent: Color @Composable get() = LocalWingAiPalette.current.borderAccent
    val BorderCyan: Color @Composable get() = LocalWingAiPalette.current.accentCyan

    val AccentPrimary: Color @Composable get() = LocalWingAiPalette.current.accentPrimary
    val OnAccentPrimary: Color @Composable get() = LocalWingAiPalette.current.onAccentPrimary
    val AccentPrimaryContainer: Color @Composable get() = LocalWingAiPalette.current.accentPrimaryContainer
    val AccentCyan: Color @Composable get() = LocalWingAiPalette.current.accentCyan
    val AccentEmerald: Color @Composable get() = LocalWingAiPalette.current.accentEmerald
    val AccentEmeraldContainer: Color @Composable get() = LocalWingAiPalette.current.accentEmeraldContainer
    val AccentAmber: Color @Composable get() = LocalWingAiPalette.current.accentAmber
    val AccentRose: Color @Composable get() = LocalWingAiPalette.current.accentRose
    val AccentRoseContainer: Color @Composable get() = LocalWingAiPalette.current.accentRoseContainer

    val TextPrimary: Color @Composable get() = LocalWingAiPalette.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalWingAiPalette.current.textSecondary
    val TextMuted: Color @Composable get() = LocalWingAiPalette.current.textMuted

    // SwiftKey Fluent geometry: 6dp keys, 12dp cards (`toolbar_panel_standard_corner_radius`), 16dp pill labels
    val RadiusSm = RoundedCornerShape(6.dp)
    val RadiusMd = RoundedCornerShape(12.dp)
    val RadiusLg = RoundedCornerShape(16.dp)
    val RadiusPill = RoundedCornerShape(16.dp)

    // SwiftKey Tone Gradient (`$ic_tone_selected__0.xml`: #18C1ED -> #3495EA -> #2161EF)
    val SwiftKeyToneBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF18C1ED), Color(0xFF3495EA), Color(0xFF2161EF))
    )

    // WingAI Brand Gradient (#38BDF8 sky blue -> #6366F1 indigo -> #A855F7 purple)
    val WingAiBrandBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF38BDF8),
            Color(0xFF6366F1),
            Color(0xFFA855F7)
        )
    )

    // SwiftKey Copilot Ribbon Gradient (`$ic_copilot_selected`: #00AEFF -> #8C48FF -> #F2598A -> #FFB152)
    val SwiftKeyCopilotBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF38BDF8),
            Color(0xFF6366F1),
            Color(0xFFA855F7)
        )
    )

    @Composable
    fun toneColor(tone: String): Color {
        val isLight = LocalWingAiPalette.current.isLight
        return if (isLight) Color(0xFF3961ED) else Color(0xFF9DC3FF)
    }

    /**
     * SwiftKey `toolbar_panel_card_label_background`:
     * Semi-transparent pill (`alpha = 0.16f`) inside the tone card.
     */
    @Composable
    fun cardPillBackground(): Color {
        val isLight = LocalWingAiPalette.current.isLight
        return if (isLight) Color(0xFFF1F1F1) else Color(0x29FFFFFF)
    }
}

/**
 * Exact vector paths extracted from Microsoft SwiftKey v9.13.18.4 (`res/drawable/ic_*.xml`).
 */
object SwiftKeyIcons {
    val ReplaceUnselected: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwiftKeyReplaceUnselected",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 21.25f)
                curveTo(20f, 21.664f, 19.664f, 22f, 19.25f, 22f)
                horizontalLineTo(12.75f)
                curveTo(10.679f, 22f, 9f, 20.321f, 9f, 18.25f)
                verticalLineTo(4.586f)
                lineTo(5.28f, 8.305f)
                curveTo(5.014f, 8.571f, 4.597f, 8.596f, 4.304f, 8.378f)
                lineTo(4.22f, 8.305f)
                curveTo(3.953f, 8.039f, 3.929f, 7.622f, 4.147f, 7.329f)
                lineTo(4.22f, 7.245f)
                lineToRelative(5f, -5f)
                curveToRelative(0.266f, -0.267f, 0.683f, -0.291f, 0.976f, -0.073f)
                lineToRelative(0.084f, 0.073f)
                lineToRelative(5f, 5f)
                curveToRelative(0.293f, 0.292f, 0.293f, 0.767f, 0f, 1.06f)
                curveToRelative(-0.266f, 0.266f, -0.683f, 0.291f, -0.976f, 0.073f)
                lineTo(14.22f, 8.305f)
                lineTo(10.5f, 4.586f)
                verticalLineTo(18.25f)
                curveToRelative(0f, 1.191f, 0.925f, 2.166f, 2.096f, 2.245f)
                lineTo(12.75f, 20.5f)
                horizontalLineToRelative(6.5f)
                curveToRelative(0.414f, 0f, 0.75f, 0.336f, 0.75f, 0.75f)
                close()
            }
        }.build()
    }

    val CopyUnselected: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwiftKeyCopyUnselected",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(5.503f, 4.627f)
                lineTo(5.5f, 6.75f)
                verticalLineToRelative(10.504f)
                curveToRelative(0f, 1.795f, 1.455f, 3.25f, 3.25f, 3.25f)
                horizontalLineToRelative(8.616f)
                curveToRelative(-0.309f, 0.874f, -1.142f, 1.5f, -2.122f, 1.5f)
                horizontalLineTo(8.75f)
                curveToRelative(-2.623f, 0f, -4.75f, -2.126f, -4.75f, -4.75f)
                verticalLineTo(6.75f)
                curveToRelative(0f, -0.981f, 0.627f, -1.815f, 1.503f, -2.123f)
                close()
                moveTo(17.75f, 2f)
                curveTo(18.993f, 2f, 20f, 3.007f, 20f, 4.25f)
                verticalLineToRelative(13f)
                curveToRelative(0f, 1.243f, -1.007f, 2.25f, -2.25f, 2.25f)
                horizontalLineToRelative(-9f)
                curveToRelative(-1.243f, 0f, -2.25f, -1.007f, -2.25f, -2.25f)
                verticalLineToRelative(-13f)
                curveTo(6.5f, 3.007f, 7.507f, 2f, 8.75f, 2f)
                horizontalLineToRelative(9f)
                close()
                moveTo(17.75f, 3.5f)
                horizontalLineToRelative(-9f)
                curveTo(8.336f, 3.5f, 8f, 3.836f, 8f, 4.25f)
                verticalLineToRelative(13f)
                curveTo(8f, 17.664f, 8.336f, 18f, 8.75f, 18f)
                horizontalLineToRelative(9f)
                curveToRelative(0.414f, 0f, 0.75f, -0.336f, 0.75f, -0.75f)
                verticalLineToRelative(-13f)
                curveToRelative(0f, -0.414f, -0.336f, -0.75f, -0.75f, -0.75f)
                close()
            }
        }.build()
    }

    val ChevronLeftSmall: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwiftKeyChevronLeftSmall",
            defaultWidth = 18.dp,
            defaultHeight = 18.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.53f, 4.22f)
                curveToRelative(0.293f, 0.293f, 0.293f, 0.767f, 0f, 1.06f)
                lineTo(8.811f, 12f)
                lineToRelative(6.719f, 6.72f)
                curveToRelative(0.293f, 0.293f, 0.293f, 0.767f, 0f, 1.06f)
                curveToRelative(-0.293f, 0.293f, -0.767f, 0.293f, -1.06f, 0f)
                lineToRelative(-7.25f, -7.25f)
                curveToRelative(-0.293f, -0.293f, -0.293f, -0.767f, 0f, -1.06f)
                lineToRelative(7.25f, -7.25f)
                curveToRelative(0.293f, -0.293f, 0.767f, -0.293f, 1.06f, 0f)
                close()
            }
        }.build()
    }

    val ArrowUndo: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwiftKeyArrowUndo",
            defaultWidth = 14.dp,
            defaultHeight = 14.dp,
            viewportWidth = 14f,
            viewportHeight = 14f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3.001f, 2.5f)
                curveToRelative(0f, -0.276f, 0.224f, -0.5f, 0.5f, -0.5f)
                reflectiveCurveToRelative(0.5f, 0.224f, 0.5f, 0.5f)
                verticalLineToRelative(3.843f)
                lineToRelative(3.171f, -3.171f)
                curveToRelative(1.562f, -1.563f, 4.095f, -1.563f, 5.657f, 0f)
                curveToRelative(1.562f, 1.562f, 1.562f, 4.094f, 0f, 5.656f)
                lineToRelative(-5.025f, 5.026f)
                curveToRelative(-0.196f, 0.195f, -0.512f, 0.195f, -0.707f, 0f)
                curveToRelative(-0.196f, -0.196f, -0.196f, -0.512f, 0f, -0.708f)
                lineToRelative(5.025f, -5.025f)
                curveToRelative(1.171f, -1.171f, 1.171f, -3.07f, 0f, -4.242f)
                curveToRelative(-1.172f, -1.172f, -3.071f, -1.172f, -4.243f, 0f)
                lineTo(4.758f, 7f)
                horizontalLineTo(8.5f)
                curveTo(8.776f, 7f, 9f, 7.224f, 9f, 7.5f)
                reflectiveCurveTo(8.777f, 8f, 8.501f, 8f)
                horizontalLineToRelative(-4.9f)
                curveToRelative(-0.331f, 0f, -0.6f, -0.269f, -0.6f, -0.6f)
                verticalLineTo(2.5f)
                close()
            }
        }.build()
    }

    val TonePersonVoice: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwiftKeyToneIcon",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(14.754f, 15f)
                curveToRelative(1.242f, 0f, 2.249f, 1.007f, 2.249f, 2.249f)
                verticalLineToRelative(0.918f)
                curveToRelative(0f, 0.574f, -0.179f, 1.133f, -0.512f, 1.599f)
                curveTo(14.945f, 21.93f, 12.42f, 23.001f, 9f, 23.001f)
                curveToRelative(-3.421f, 0f, -5.944f, -1.072f, -7.486f, -3.236f)
                curveToRelative(-0.332f, -0.466f, -0.51f, -1.024f, -0.51f, -1.596f)
                verticalLineToRelative(-0.92f)
                curveTo(1.004f, 16.007f, 2.011f, 15f, 3.253f, 15f)
                horizontalLineToRelative(11.501f)
                close()
                moveTo(19.054f, 1.404f)
                curveToRelative(0.36f, -0.206f, 0.818f, -0.081f, 1.023f, 0.279f)
                curveTo(21.168f, 3.591f, 21.75f, 5.754f, 21.75f, 8f)
                curveToRelative(0f, 2.254f, -0.586f, 4.424f, -1.684f, 6.337f)
                curveToRelative(-0.206f, 0.359f, -0.664f, 0.483f, -1.023f, 0.277f)
                curveToRelative(-0.36f, -0.206f, -0.484f, -0.665f, -0.278f, -1.024f)
                curveToRelative(0.968f, -1.687f, 1.485f, -3.6f, 1.485f, -5.59f)
                curveToRelative(0f, -1.983f, -0.513f, -3.89f, -1.475f, -5.573f)
                curveToRelative(-0.206f, -0.36f, -0.081f, -0.818f, 0.279f, -1.023f)
                close()
                moveTo(9f, 3.005f)
                curveToRelative(2.762f, 0f, 5f, 2.238f, 5f, 5f)
                curveToRelative(0f, 2.761f, -2.238f, 5f, -5f, 5f)
                curveToRelative(-2.761f, 0f, -5f, -2.239f, -5f, -5f)
                curveToRelative(0f, -2.762f, 2.239f, -5f, 5f, -5f)
                close()
                moveTo(15.589f, 3.4f)
                curveToRelative(0.36f, -0.205f, 0.818f, -0.079f, 1.022f, 0.281f)
                curveTo(17.354f, 4.987f, 17.75f, 6.465f, 17.75f, 8f)
                curveToRelative(0f, 1.538f, -0.398f, 3.02f, -1.144f, 4.328f)
                curveToRelative(-0.205f, 0.36f, -0.663f, 0.486f, -1.023f, 0.28f)
                curveToRelative(-0.36f, -0.205f, -0.485f, -0.663f, -0.28f, -1.023f)
                curveTo(15.921f, 10.502f, 16.25f, 9.276f, 16.25f, 8f)
                curveToRelative(0f, -1.273f, -0.328f, -2.496f, -0.943f, -3.578f)
                curveToRelative(-0.204f, -0.36f, -0.078f, -0.818f, 0.282f, -1.022f)
                close()
            }
        }.build()
    }

    val EditorPen: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwiftKeyEditorPen",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(14.692f, 0.407f)
                curveToRelative(-0.738f, -0.491f, -1.735f, -0.285f, -2.217f, 0.459f)
                lineTo(2.818f, 15.782f)
                curveToRelative(-0.058f, 0.09f, -0.1f, 0.19f, -0.122f, 0.294f)
                lineToRelative(-1.367f, 6.403f)
                curveToRelative(-0.07f, 0.326f, 0.051f, 0.665f, 0.314f, 0.872f)
                curveToRelative(0.262f, 0.207f, 0.62f, 0.248f, 0.922f, 0.104f)
                lineTo(8.56f, 20.61f)
                curveToRelative(0.15f, -0.073f, 0.278f, -0.185f, 0.367f, -0.326f)
                lineToRelative(0.895f, -1.423f)
                horizontalLineToRelative(7.867f)
                curveToRelative(0.646f, 0f, 1.17f, -0.524f, 1.17f, -1.17f)
                reflectiveCurveToRelative(-0.524f, -1.17f, -1.17f, -1.17f)
                horizontalLineToRelative(-6.395f)
                lineToRelative(1.472f, -2.34f)
                horizontalLineToRelative(7.263f)
                curveToRelative(0.646f, 0f, 1.17f, -0.524f, 1.17f, -1.17f)
                reflectiveCurveToRelative(-0.524f, -1.17f, -1.17f, -1.17f)
                horizontalLineToRelative(-5.793f)
                lineToRelative(1.472f, -2.34f)
                horizontalLineToRelative(6.66f)
                curveToRelative(0.646f, 0f, 1.17f, -0.524f, 1.17f, -1.17f)
                reflectiveCurveToRelative(-0.524f, -1.17f, -1.17f, -1.17f)
                horizontalLineToRelative(-5.189f)
                lineToRelative(1.436f, -2.281f)
                curveToRelative(0.457f, -0.729f, 0.252f, -1.69f, -0.464f, -2.168f)
                lineToRelative(-3.46f, -2.304f)
                close()
            }
        }.build()
    }

    val SparkleAi: ImageVector by lazy {
        ImageVector.Builder(
            name = "SwiftKeySparkleAi",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 20f,
            viewportHeight = 20f
        ).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(7.398f, 12.809f)
                curveToRelative(0.176f, 0.124f, 0.387f, 0.19f, 0.602f, 0.19f)
                curveToRelative(0.216f, 0f, 0.427f, -0.067f, 0.602f, -0.193f)
                curveToRelative(0.178f, -0.131f, 0.313f, -0.311f, 0.387f, -0.518f)
                lineToRelative(0.447f, -1.373f)
                curveToRelative(0.115f, -0.344f, 0.308f, -0.657f, 0.564f, -0.914f)
                curveToRelative(0.257f, -0.257f, 0.569f, -0.45f, 0.913f, -0.565f)
                lineToRelative(1.391f, -0.451f)
                curveToRelative(0.152f, -0.054f, 0.29f, -0.143f, 0.401f, -0.259f)
                curveToRelative(0.112f, -0.117f, 0.196f, -0.257f, 0.244f, -0.411f)
                curveToRelative(0.049f, -0.154f, 0.061f, -0.317f, 0.036f, -0.477f)
                curveToRelative(-0.025f, -0.159f, -0.087f, -0.31f, -0.181f, -0.442f)
                curveToRelative(-0.134f, -0.186f, -0.325f, -0.323f, -0.544f, -0.391f)
                lineToRelative(-1.375f, -0.447f)
                curveToRelative(-0.344f, -0.114f, -0.657f, -0.307f, -0.914f, -0.564f)
                curveTo(9.714f, 5.738f, 9.52f, 5.425f, 9.405f, 5.081f)
                lineTo(8.953f, 3.693f)
                curveToRelative(-0.072f, -0.202f, -0.205f, -0.377f, -0.382f, -0.5f)
                curveToRelative(-0.132f, -0.094f, -0.285f, -0.155f, -0.446f, -0.18f)
                curveToRelative(-0.16f, -0.024f, -0.324f, -0.01f, -0.479f, 0.04f)
                curveToRelative(-0.154f, 0.05f, -0.295f, 0.135f, -0.41f, 0.25f)
                curveTo(7.12f, 3.417f, 7.033f, 3.556f, 6.98f, 3.71f)
                lineToRelative(-0.457f, 1.4f)
                curveTo(6.409f, 5.445f, 6.22f, 5.749f, 5.97f, 6f)
                curveTo(5.721f, 6.251f, 5.418f, 6.442f, 5.083f, 6.559f)
                lineTo(3.692f, 7.007f)
                curveToRelative(-0.151f, 0.054f, -0.288f, 0.143f, -0.4f, 0.259f)
                curveTo(3.18f, 7.383f, 3.097f, 7.523f, 3.048f, 7.677f)
                curveTo(3f, 7.831f, 2.988f, 7.994f, 3.013f, 8.153f)
                curveToRelative(0.025f, 0.159f, 0.086f, 0.31f, 0.179f, 0.442f)
                curveToRelative(0.128f, 0.179f, 0.309f, 0.314f, 0.517f, 0.386f)
                lineToRelative(1.374f, 0.445f)
                curveToRelative(0.441f, 0.146f, 0.827f, 0.421f, 1.11f, 0.788f)
                curveToRelative(0.163f, 0.211f, 0.288f, 0.447f, 0.371f, 0.7f)
                lineToRelative(0.452f, 1.391f)
                curveToRelative(0.072f, 0.203f, 0.206f, 0.379f, 0.382f, 0.504f)
                close()
                moveTo(13.535f, 16.851f)
                curveToRelative(0.136f, 0.096f, 0.299f, 0.148f, 0.465f, 0.148f)
                curveToRelative(0.165f, 0f, 0.326f, -0.051f, 0.461f, -0.146f)
                curveToRelative(0.14f, -0.099f, 0.245f, -0.238f, 0.3f, -0.4f)
                lineToRelative(0.248f, -0.762f)
                curveToRelative(0.054f, -0.158f, 0.142f, -0.302f, 0.26f, -0.421f)
                curveToRelative(0.118f, -0.119f, 0.262f, -0.207f, 0.42f, -0.259f)
                lineToRelative(0.772f, -0.252f)
                curveToRelative(0.158f, -0.054f, 0.295f, -0.157f, 0.391f, -0.293f)
                curveToRelative(0.074f, -0.103f, 0.122f, -0.222f, 0.14f, -0.347f)
                curveToRelative(0.019f, -0.125f, 0.007f, -0.253f, -0.033f, -0.373f)
                curveToRelative(-0.041f, -0.119f, -0.109f, -0.228f, -0.199f, -0.316f)
                curveToRelative(-0.091f, -0.089f, -0.201f, -0.154f, -0.322f, -0.192f)
                lineToRelative(-0.764f, -0.249f)
                curveToRelative(-0.158f, -0.053f, -0.302f, -0.141f, -0.42f, -0.259f)
                curveToRelative(-0.118f, -0.117f, -0.207f, -0.261f, -0.26f, -0.419f)
                lineToRelative(-0.252f, -0.773f)
                curveToRelative(-0.053f, -0.158f, -0.156f, -0.294f, -0.293f, -0.39f)
                curveToRelative(-0.102f, -0.073f, -0.22f, -0.121f, -0.343f, -0.14f)
                curveToRelative(-0.124f, -0.019f, -0.251f, -0.009f, -0.37f, 0.03f)
                curveToRelative(-0.12f, 0.039f, -0.228f, 0.106f, -0.317f, 0.194f)
                curveToRelative(-0.089f, 0.089f, -0.155f, 0.197f, -0.195f, 0.316f)
                lineToRelative(-0.247f, 0.762f)
                curveToRelative(-0.052f, 0.158f, -0.14f, 0.301f, -0.256f, 0.42f)
                curveToRelative(-0.114f, 0.117f, -0.254f, 0.205f, -0.409f, 0.259f)
                lineToRelative(-0.773f, 0.252f)
                curveToRelative(-0.159f, 0.054f, -0.297f, 0.157f, -0.394f, 0.293f)
                curveToRelative(-0.098f, 0.137f, -0.15f, 0.301f, -0.149f, 0.469f)
                curveToRelative(0.001f, 0.168f, 0.055f, 0.331f, 0.154f, 0.467f)
                reflectiveCurveToRelative(0.238f, 0.237f, 0.397f, 0.289f)
                lineToRelative(0.763f, 0.247f)
                curveToRelative(0.159f, 0.053f, 0.304f, 0.143f, 0.422f, 0.261f)
                curveToRelative(0.119f, 0.118f, 0.207f, 0.263f, 0.258f, 0.422f)
                lineToRelative(0.253f, 0.774f)
                curveToRelative(0.055f, 0.156f, 0.157f, 0.292f, 0.292f, 0.388f)
                close()
            }
        }.build()
    }

    val WingAiLogo: ImageVector by lazy {
        ImageVector.Builder(
            name = "WingAiLogo",
            defaultWidth = 20.dp,
            defaultHeight = 20.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Top sweeping aerodynamic feather
            path(fill = SolidColor(Color.White)) {
                moveTo(3.0f, 13.5f)
                curveTo(3.5f, 7.2f, 9.2f, 3.2f, 17.5f, 3.0f)
                curveTo(14.6f, 5.4f, 12.4f, 8.0f, 11.6f, 10.8f)
                curveTo(8.0f, 10.8f, 5.1f, 11.8f, 3.0f, 13.5f)
                close()
            }
            // Middle feather
            path(fill = SolidColor(Color.White)) {
                moveTo(4.5f, 16.5f)
                curveTo(5.6f, 11.6f, 10.2f, 8.4f, 16.8f, 8.0f)
                curveTo(14.3f, 10.5f, 12.5f, 12.6f, 11.4f, 14.8f)
                curveTo(8.5f, 14.8f, 6.3f, 15.5f, 4.5f, 16.5f)
                close()
            }
            // Bottom feather
            path(fill = SolidColor(Color.White)) {
                moveTo(6.5f, 19.2f)
                curveTo(8.0f, 15.2f, 11.6f, 12.7f, 16.2f, 12.3f)
                curveTo(14.0f, 14.5f, 12.6f, 16.3f, 11.2f, 18.1f)
                curveTo(9.4f, 18.1f, 7.6f, 18.5f, 6.5f, 19.2f)
                close()
            }
            // Upper-right 4-point AI star
            path(fill = SolidColor(Color.White)) {
                moveTo(19.2f, 2.2f)
                lineTo(20.1f, 5.0f)
                lineTo(22.9f, 5.9f)
                lineTo(20.1f, 6.8f)
                lineTo(19.2f, 9.6f)
                lineTo(18.3f, 6.8f)
                lineTo(15.5f, 5.9f)
                lineTo(18.3f, 5.0f)
                close()
            }
        }.build()
    }

    val CopilotRibbon: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M4.694 17.244c0.556 0.018 0.81 0.21 0.986 0.443 0.237 0.315 0.376 0.763 0.562 1.396l0.01 0.036c0.165 0.56 0.378 1.283 0.825 1.85C7.584 21.615 8.333 22 9.404 22h7.228c1.49 0 2.587-0.964 3.375-2.13 0.793-1.174 1.381-2.7 1.837-4.16l0.002-0.005c0.52-1.67 1.181-3.788 1.157-5.5-0.013-0.868-0.2-1.753-0.786-2.425-0.605-0.693-1.512-1.023-2.662-1.023h-0.242c-0.556-0.019-0.81-0.211-0.985-0.444-0.238-0.315-0.376-0.763-0.563-1.395l-0.01-0.036C17.59 4.32 17.377 3.597 16.93 3.03 16.424 2.385 15.674 2 14.604 2h-7.23C5.886 2 4.79 2.965 4 4.131 3.207 5.304 2.62 6.83 2.163 8.291L2.162 8.294c-0.521 1.67-1.182 3.788-1.157 5.5 0.012 0.868 0.2 1.753 0.786 2.425 0.604 0.694 1.511 1.024 2.662 1.024h0.241zM3.595 8.737C4.04 7.315 4.575 5.959 5.243 4.97 5.915 3.976 6.617 3.5 7.375 3.5h4.63c-0.193 0.344-0.355 0.725-0.5 1.118-0.198 0.535-0.383 1.138-0.572 1.758l-0.042 0.138c-0.75 2.457-1.689 5.623-2.294 7.673-0.265 0.898-1.074 1.522-2.004 1.556h-2.14c-0.866 0-1.297-0.24-1.532-0.509-0.253-0.29-0.407-0.76-0.417-1.46-0.02-1.424 0.547-3.293 1.091-5.037zm15.17 10.291c-0.673 0.995-1.375 1.471-2.133 1.471h-4.629c0.192-0.344 0.354-0.724 0.5-1.117 0.198-0.536 0.382-1.14 0.57-1.759l0.043-0.138c0.75-2.457 1.69-5.622 2.294-7.672 0.265-0.899 1.074-1.523 2.004-1.556H19.4l0.047-0.001h0.108c0.865 0 1.296 0.24 1.531 0.51 0.254 0.29 0.407 0.76 0.417 1.46 0.02 1.423-0.547 3.292-1.091 5.036-0.444 1.422-0.98 2.778-1.647 3.766zm-8.3-3.285h-0.99c0.246-0.336 0.437-0.716 0.56-1.132 0.266-0.902 0.597-2.02 0.947-3.197L11.468 9.8c0.275-0.917 1.118-1.544 2.075-1.544h0.988c-0.246 0.335-0.437 0.716-0.56 1.131-0.266 0.903-0.597 2.02-0.947 3.197L12.54 14.2c-0.275 0.916-1.119 1.543-2.075 1.543zm3.078-8.986c-0.425 0-0.837 0.073-1.221 0.209l0.042-0.139c0.193-0.633 0.365-1.196 0.548-1.688 0.195-0.528 0.384-0.923 0.588-1.202 0.047-0.064 0.178-0.18 0.406-0.282 0.218-0.097 0.468-0.155 0.698-0.155 0.657 0 0.953 0.21 1.148 0.457 0.245 0.312 0.39 0.758 0.574 1.383l0.024 0.08c0.119 0.403 0.261 0.89 0.495 1.338h-3.301zm-3.079 10.486c0.425 0 0.837-0.074 1.222-0.21l-0.043 0.139c-0.193 0.634-0.365 1.196-0.547 1.689-0.196 0.527-0.385 0.922-0.589 1.2-0.047 0.065-0.178 0.181-0.405 0.283C9.883 20.442 9.633 20.5 9.403 20.5c-0.658 0-0.954-0.21-1.149-0.457-0.245-0.312-0.39-0.758-0.574-1.383l-0.023-0.078c-0.118-0.403-0.261-0.89-0.494-1.338h3.3z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyCopilotRibbon", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val ToolbarChevronUp: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M4.22 15.53c0.293 0.293 0.767 0.293 1.06 0L12 8.811l6.72 6.719c0.293 0.293 0.767 0.293 1.06 0 0.293-0.293 0.293-0.767 0-1.06l-7.25-7.25c-0.293-0.293-0.767-0.293-1.06 0l-7.25 7.25c-0.293 0.293-0.293 0.767 0 1.06z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyToolbarChevronUp", 12.dp, 12.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val ToolbarChevronDown: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M4.22 8.47c0.293-0.293 0.767-0.293 1.06 0L12 15.189l6.72-6.719c0.293-0.293 0.767-0.293 1.06 0 0.293 0.293 0.293 0.767 0 1.06l-7.25 7.25c-0.293 0.293-0.767 0.293-1.06 0L4.22 9.53c-0.293-0.293-0.293-0.767 0-1.06z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyToolbarChevronDown", 12.dp, 12.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val ClipboardBoard: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M13.75 2c1.191 0 2.166 0.925 2.245 2.096L16 4.25c0-0.085-0.005-0.168-0.014-0.251L17.75 4C18.993 4 20 5.007 20 6.25v13.5c0 1.243-1.007 2.25-2.25 2.25H6.25C5.007 22 4 20.993 4 19.75V6.25C4 5.007 5.007 4 6.25 4l1.764-0.001C8.007 4.058 8.003 4.117 8.001 4.176L8 4.25C8 3.007 9.007 2 10.25 2h3.5zm0 4.5h-3.5c-0.794 0-1.492-0.411-1.892-1.033L8.379 5.5H6.25C5.836 5.5 5.5 5.836 5.5 6.25v13.5c0 0.414 0.336 0.75 0.75 0.75h11.5c0.414 0 0.75-0.336 0.75-0.75V6.25c0-0.414-0.336-0.75-0.75-0.75h-2.129l0.021-0.033C15.242 6.089 14.544 6.5 13.75 6.5zm0-3h-3.5C9.836 3.5 9.5 3.836 9.5 4.25S9.836 5 10.25 5h3.5c0.414 0 0.75-0.336 0.75-0.75S14.164 3.5 13.75 3.5zM8 14.5h4c0.414 0 0.75-0.336 0.75-0.75S12.414 13 12 13H8c-0.414 0-0.75 0.336-0.75 0.75S7.586 14.5 8 14.5zm0-4h8c0.414 0 0.75-0.336 0.75-0.75S16.414 9 16 9H8C7.586 9 7.25 9.336 7.25 9.75S7.586 10.5 8 10.5zm0 8h6c0.414 0 0.75-0.336 0.75-0.75S14.414 17 14 17H8c-0.414 0-0.75 0.336-0.75 0.75S7.586 18.5 8 18.5z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyClipboardBoard", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val EmojiSmiley: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M12 1.999c5.524 0 10.002 4.478 10.002 10.002 0 5.523-4.478 10.001-10.002 10.001-5.524 0-10.001-4.478-10.001-10.001C1.999 6.477 6.476 1.999 12 1.999zm0 1.5c-4.695 0-8.501 3.806-8.501 8.502 0 4.695 3.806 8.501 8.501 8.501 4.695 0 8.502-3.806 8.502-8.501 0-4.696-3.807-8.502-8.502-8.502zM8.462 14.784c0.849 1.078 2.14 1.718 3.538 1.718 1.396 0 2.686-0.639 3.535-1.714 0.257-0.325 0.728-0.38 1.053-0.124 0.325 0.257 0.381 0.729 0.124 1.054-1.131 1.431-2.852 2.284-4.712 2.284-1.863 0-3.586-0.855-4.716-2.29-0.257-0.325-0.201-0.797 0.125-1.053 0.325-0.256 0.796-0.201 1.053 0.125zm0.539-6.033c0.69 0 1.249 0.56 1.249 1.249 0 0.691-0.559 1.25-1.249 1.25-0.69 0-1.25-0.559-1.25-1.25 0-0.689 0.56-1.249 1.25-1.249zm5.999 0c0.691 0 1.25 0.56 1.25 1.249 0 0.691-0.559 1.25-1.25 1.25-0.689 0-1.249-0.559-1.249-1.25 0-0.689 0.56-1.249 1.249-1.249z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyEmojiSmiley", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val GifBadge: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M18.75 3.5C20.545 3.5 22 4.956 22 6.75v10.503c0 1.795-1.455 3.25-3.25 3.25H5.25c-1.795 0-3.25-1.455-3.25-3.25V6.75C2 4.956 3.455 3.5 5.25 3.5h13.5zm0 1.5H5.25C4.284 5 3.5 5.784 3.5 6.75v10.503c0 0.967 0.784 1.75 1.75 1.75h13.5c0.966 0 1.75-0.783 1.75-1.75V6.75C20.5 5.784 19.716 5 18.75 5zM8.015 8.872c0.596 0 1.019 0.081 1.502 0.313 0.311 0.15 0.442 0.523 0.293 0.834-0.15 0.311-0.523 0.443-0.834 0.293-0.3-0.144-0.54-0.19-0.961-0.19-0.867 0-1.504 0.796-1.504 1.872 0 1.077 0.638 1.876 1.504 1.876 0.428 0 0.791-0.18 0.98-0.5l0.006-0.016V12.62l-0.376 0.001c-0.314 0-0.573-0.231-0.618-0.532L8 11.996c0-0.314 0.231-0.573 0.533-0.618l0.092-0.007h1.002c0.314 0 0.573 0.231 0.618 0.533l0.007 0.092-0.002 1.548-0.006 0.055-0.021 0.09-0.02 0.055c-0.377 0.89-1.241 1.376-2.188 1.376-1.626 0-2.754-1.412-2.754-3.126 0-1.712 1.127-3.122 2.754-3.122zm4.614 0.122c0.314 0 0.574 0.231 0.618 0.533l0.007 0.092v4.762c0 0.345-0.28 0.625-0.625 0.625-0.314 0-0.574-0.231-0.618-0.533l-0.007-0.092V9.619c0-0.345 0.28-0.625 0.625-0.625zm2.996 0l1.997 0.007c0.345 0.001 0.624 0.282 0.623 0.627-0.001 0.314-0.233 0.573-0.535 0.616l-0.092 0.007-1.371-0.006V12h1.123c0.314 0 0.574 0.232 0.618 0.533l0.007 0.092c0 0.314-0.231 0.574-0.533 0.619L17.37 13.25h-1.123l0.001 1.115c0 0.314-0.232 0.574-0.533 0.618l-0.092 0.007c-0.314 0-0.574-0.231-0.619-0.533l-0.006-0.092V9.617C14.999 9.303 15.231 9.044 15.532 9l0.093-0.006z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyGifBadge", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val VoiceMic: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M18.25 11c0.38 0 0.694 0.282 0.743 0.648L19 11.75v0.5c0 3.559-2.755 6.475-6.249 6.732L12.75 21.25c0 0.414-0.336 0.75-0.75 0.75-0.38 0-0.693-0.282-0.743-0.648L11.25 21.25v-2.268c-3.417-0.25-6.127-3.044-6.246-6.496L5 12.25v-0.5C5 11.336 5.336 11 5.75 11c0.38 0 0.693 0.282 0.743 0.648L6.5 11.75v0.5c0 2.827 2.234 5.132 5.034 5.246L11.75 17.5h0.5c2.827 0 5.132-2.234 5.246-5.034L17.5 12.25v-0.5c0-0.414 0.336-0.75 0.75-0.75zM12 2c2.209 0 4 1.791 4 4v6c0 2.209-1.791 4-4 4s-4-1.791-4-4V6c0-2.209 1.791-4 4-4zm0 1.5c-1.381 0-2.5 1.119-2.5 2.5v6c0 1.381 1.119 2.5 2.5 2.5s2.5-1.119 2.5-2.5V6c0-1.381-1.119-2.5-2.5-2.5z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyVoiceMic", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val SettingsGear: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M12.012 2.25c0.734 0.008 1.465 0.093 2.182 0.253 0.313 0.07 0.547 0.331 0.582 0.649l0.17 1.527c0.077 0.701 0.669 1.232 1.375 1.233 0.189 0 0.377-0.04 0.552-0.117l1.401-0.615c0.291-0.128 0.632-0.059 0.849 0.174 1.012 1.081 1.766 2.377 2.205 3.792 0.095 0.305-0.014 0.636-0.271 0.826l-1.242 0.915c-0.354 0.26-0.563 0.673-0.563 1.113 0 0.439 0.209 0.852 0.564 1.113l1.243 0.915c0.257 0.19 0.366 0.521 0.271 0.826-0.439 1.414-1.192 2.711-2.204 3.792-0.217 0.232-0.557 0.302-0.848 0.175l-1.407-0.616c-0.402-0.177-0.864-0.151-1.244 0.069s-0.634 0.607-0.682 1.044l-0.169 1.526c-0.035 0.315-0.264 0.574-0.572 0.648-1.448 0.344-2.957 0.344-4.406 0-0.308-0.074-0.537-0.333-0.572-0.648L9.058 19.32c-0.05-0.436-0.303-0.822-0.683-1.041-0.38-0.219-0.842-0.245-1.243-0.07l-1.406 0.617c-0.292 0.127-0.632 0.057-0.849-0.175-1.012-1.082-1.766-2.38-2.204-3.796-0.094-0.305 0.015-0.636 0.272-0.826l1.243-0.916C4.543 12.853 4.752 12.44 4.752 12c0-0.439-0.209-0.852-0.564-1.113L2.945 9.973c-0.257-0.19-0.366-0.521-0.271-0.826 0.438-1.415 1.192-2.711 2.204-3.792 0.218-0.233 0.558-0.302 0.849-0.174l1.401 0.615c0.403 0.177 0.866 0.15 1.248-0.073 0.38-0.221 0.634-0.609 0.682-1.045l0.17-1.526c0.036-0.319 0.27-0.579 0.583-0.649 0.717-0.16 1.449-0.244 2.201-0.253zm0.001 1.5c-0.454 0.005-0.907 0.044-1.355 0.117l-0.109 0.977c-0.102 0.91-0.629 1.717-1.418 2.175-0.795 0.464-1.763 0.52-2.606 0.15L5.627 6.775C5.055 7.469 4.599 8.251 4.279 9.092l0.798 0.587C5.815 10.222 6.252 11.084 6.252 12c0 0.917-0.437 1.779-1.175 2.321L4.278 14.91c0.321 0.842 0.776 1.626 1.348 2.322l0.905-0.397c0.838-0.366 1.8-0.312 2.593 0.144 0.792 0.457 1.321 1.263 1.424 2.174l0.109 0.983c0.89 0.152 1.799 0.152 2.688 0l0.109-0.983c0.101-0.911 0.629-1.719 1.422-2.178 0.794-0.458 1.757-0.512 2.597-0.145l0.905 0.397c0.571-0.695 1.026-1.477 1.347-2.318l-0.798-0.588c-0.739-0.543-1.175-1.405-1.175-2.321 0-0.917 0.436-1.779 1.174-2.322l0.797-0.587c-0.321-0.84-0.776-1.623-1.348-2.317l-0.896 0.393c-0.366 0.162-0.761 0.245-1.16 0.245-1.47-0.002-2.703-1.108-2.864-2.568l-0.109-0.977c-0.445-0.073-0.893-0.112-1.333-0.117zM12 8.25c2.071 0 3.75 1.679 3.75 3.75 0 2.071-1.679 3.75-3.75 3.75-2.071 0-3.75-1.679-3.75-3.75 0-2.071 1.679-3.75 3.75-3.75zm0 1.5c-1.243 0-2.25 1.007-2.25 2.25s1.007 2.25 2.25 2.25 2.25-1.007 2.25-2.25S13.243 9.75 12 9.75z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeySettingsGear", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val MoreVerticalDots: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M12 7.75c-0.967 0-1.75-0.784-1.75-1.75S11.033 4.25 12 4.25 13.75 5.034 13.75 6 12.967 7.75 12 7.75zm0 6c-0.967 0-1.75-0.783-1.75-1.75s0.783-1.75 1.75-1.75 1.75 0.783 1.75 1.75-0.783 1.75-1.75 1.75zM10.25 18c0 0.966 0.783 1.75 1.75 1.75s1.75-0.784 1.75-1.75-0.783-1.75-1.75-1.75-1.75 0.784-1.75 1.75z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyMoreVerticalDots", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val MoreHorizontalDots: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M6 10.25c-0.966 0-1.75 0.783-1.75 1.75s0.784 1.75 1.75 1.75 1.75-0.783 1.75-1.75-0.784-1.75-1.75-1.75zm6 0c-0.967 0-1.75 0.783-1.75 1.75s0.783 1.75 1.75 1.75 1.75-0.783 1.75-1.75-0.783-1.75-1.75-1.75zm6 0c-0.966 0-1.75 0.783-1.75 1.75s0.784 1.75 1.75 1.75 1.75-0.783 1.75-1.75-0.784-1.75-1.75-1.75z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyMoreHorizontalDots", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val StickerSparkle: ImageVector by lazy {
        val p1 = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M16.088 6.412c-0.072-0.093-0.15-0.182-0.234-0.266-0.312-0.313-0.693-0.549-1.113-0.689l-1.378-0.448c-0.106-0.037-0.198-0.106-0.263-0.198C13.035 4.719 13 4.609 13 4.497c0-0.113 0.035-0.223 0.1-0.315 0.065-0.091 0.157-0.161 0.263-0.198l0.711-0.231-0.004-0.001 0.671-0.218c0.244-0.083 0.474-0.199 0.686-0.344 0.146-0.101 0.284-0.217 0.41-0.345 0.299-0.303 0.525-0.669 0.663-1.071l0.011-0.034 0.448-1.377c0.038-0.106 0.107-0.198 0.199-0.263C17.25 0.035 17.36 0 17.472 0c0.113 0 0.223 0.035 0.315 0.1s0.161 0.157 0.199 0.263l0.447 1.377c0.14 0.418 0.375 0.799 0.687 1.11 0.312 0.312 0.693 0.547 1.112 0.686l1.377 0.448 0.028 0.007c0.106 0.037 0.198 0.106 0.263 0.198 0.065 0.092 0.1 0.202 0.1 0.314 0 0.113-0.035 0.223-0.1 0.315-0.065 0.091-0.157 0.161-0.263 0.198l-1.378 0.448c-0.175 0.058-0.343 0.132-0.502 0.222l0.004 0.005c-0.224 0.126-0.431 0.283-0.614 0.466-0.178 0.178-0.332 0.38-0.456 0.597l-0.003-0.003c-0.092 0.162-0.168 0.332-0.227 0.509l-0.448 1.377L18 8.671c-0.039 0.092-0.104 0.171-0.186 0.229C17.722 8.965 17.613 9 17.5 9c-0.113 0-0.222-0.035-0.314-0.1s-0.162-0.157-0.199-0.263L16.539 7.26c-0.101-0.307-0.254-0.593-0.451-0.848zm7.695 3.801l-0.766-0.248c-0.232-0.078-0.444-0.208-0.617-0.381-0.174-0.174-0.304-0.385-0.381-0.618L21.77 8.202c-0.021-0.059-0.06-0.11-0.111-0.146C21.608 8.019 21.547 8 21.485 8c-0.063 0-0.124 0.019-0.175 0.056-0.051 0.036-0.09 0.087-0.111 0.146l-0.248 0.764c-0.076 0.231-0.204 0.442-0.375 0.615-0.103 0.104-0.219 0.193-0.346 0.265l-0.002-0.001-0.002 0.001v0.002c-0.082 0.046-0.169 0.086-0.259 0.117l-0.765 0.248c-0.059 0.021-0.11 0.06-0.146 0.111C19.019 10.375 19 10.436 19 10.498c0 0.063 0.019 0.124 0.056 0.175 0.036 0.051 0.087 0.089 0.146 0.11l0.765 0.249c0.233 0.077 0.445 0.208 0.619 0.382 0.078 0.079 0.148 0.166 0.209 0.26 0.071 0.111 0.128 0.231 0.17 0.357l0.249 0.765c0.007 0.02 0.015 0.039 0.026 0.056 0.022 0.037 0.051 0.068 0.085 0.092C21.376 12.981 21.437 13 21.5 13c0.063 0 0.124-0.019 0.175-0.056 0.051-0.036 0.089-0.087 0.11-0.146l0.249-0.765c0.077-0.232 0.208-0.443 0.381-0.617 0.174-0.173 0.385-0.303 0.618-0.381l0.765-0.248c0.059-0.021 0.11-0.06 0.146-0.111C23.981 10.625 24 10.564 24 10.502c0-0.063-0.019-0.124-0.056-0.175-0.036-0.051-0.087-0.089-0.146-0.11l-0.015-0.004zM9.25 10.25c0 0.69-0.56 1.25-1.25 1.25s-1.25-0.56-1.25-1.25S7.31 9 8 9s1.25 0.56 1.25 1.25zm6 0c0 0.69-0.56 1.25-1.25 1.25s-1.25-0.56-1.25-1.25S13.31 9 14 9s1.25 0.56 1.25 1.25z"
        ).toNodes()
        val p2 = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M5.3 5.5c-0.994 0-1.8 0.806-1.8 1.8v11.4c0 0.994 0.806 1.8 1.8 1.8h11.4c0.994 0 1.8-0.806 1.8-1.8V12c1 0 1.5 0.5 1.5 1.5v5.2c0 1.822-1.478 3.3-3.3 3.3H5.3C3.477 22 2 20.522 2 18.7V7.3C2 5.477 3.477 4 5.3 4H12c-0.5 0.5 0 1.5 0 1.5H5.3z"
        ).toNodes()
        val p3 = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M11.002 17.507h-0.001c-1.348 0-2.581-0.382-3.677-1.14-0.341-0.235-0.427-0.703-0.191-1.044 0.146-0.21 0.381-0.324 0.62-0.323 0.146 0 0.294 0.043 0.424 0.133 0.844 0.584 1.78 0.874 2.824 0.874h0.001c1.044 0 1.979-0.29 2.824-0.874 0.13-0.09 0.278-0.133 0.424-0.133 0.239-0.001 0.474 0.113 0.62 0.323 0.235 0.341 0.149 0.809-0.191 1.044-1.097 0.758-2.329 1.14-3.677 1.14z"
        ).toNodes()
        ImageVector.Builder("SwiftKeyStickerSparkle", 20.dp, 20.dp, 24f, 24f)
            .addPath(p1, fill = SolidColor(Color.White))
            .addPath(p2, fill = SolidColor(Color.White))
            .addPath(p3, fill = SolidColor(Color.White))
            .build()
    }

    val TranslatorIcon: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M3.385 5.996C3.958 5.68 5.721 4.878 7.961 5.529L7.962 5.53c1.232 0.361 1.906 1.11 2.237 1.939 0.3 0.754 0.3 1.537 0.299 1.991v5.789c0 0.415-0.335 0.75-0.75 0.75-0.414 0-0.75-0.335-0.75-0.75v-0.3c-0.124 0.074-0.256 0.148-0.395 0.222-0.952 0.506-2.327 1.019-3.757 0.761-1.584-0.284-2.576-1.594-2.8-2.951-0.229-1.381 0.316-2.937 1.902-3.697 1.44-0.69 2.99-0.628 4.122-0.427 0.342 0.06 0.653 0.135 0.923 0.21-0.013-0.351-0.055-0.71-0.188-1.042-0.166-0.417-0.491-0.829-1.264-1.056C5.846 6.476 4.515 7.086 4.11 7.31c-0.363 0.2-0.819 0.068-1.019-0.295-0.2-0.362-0.069-0.819 0.294-1.019zm4.423 4.338c-0.986-0.176-2.175-0.194-3.212 0.303-0.891 0.427-1.208 1.269-1.07 2.099 0.141 0.854 0.741 1.568 1.586 1.72 0.938 0.169 1.947-0.163 2.788-0.61 0.409-0.217 0.753-0.447 0.993-0.624l0.105-0.078v-2.506c-0.292-0.098-0.707-0.218-1.19-0.304zm8.677-3.296c0.393 0.13 0.606 0.555 0.476 0.948-0.09 0.269-0.185 0.61-0.271 0.939 0.948-0.088 1.87-0.225 2.638-0.406 0.403-0.094 0.807 0.156 0.901 0.559 0.095 0.403-0.155 0.807-0.558 0.902-0.973 0.228-2.152 0.391-3.336 0.481-0.11 0.546-0.194 1.044-0.257 1.5 0.587-0.167 1.147-0.242 1.672-0.238C17.764 11.321 18.094 11 18.499 11c0.415 0 0.75 0.335 0.75 0.75 0 0.074 0 0.148-0.002 0.221 1.204 0.414 2.091 1.308 2.504 2.424 0.364 0.984 0.34 2.107-0.154 3.127-0.496 1.025-1.44 1.894-2.827 2.427-0.386 0.149-0.82-0.044-0.969-0.43-0.149-0.387 0.044-0.821 0.431-0.97 1.083-0.416 1.708-1.045 2.015-1.68 0.31-0.641 0.325-1.339 0.097-1.953-0.209-0.564-0.64-1.087-1.278-1.399-0.37 1.738-1.262 3.111-2.285 4.046 0.057 0.133 0.114 0.273 0.17 0.424 0.145 0.388-0.052 0.82-0.44 0.965-0.388 0.145-0.82-0.052-0.965-0.44l-0.018-0.047c-0.414 0.226-0.834 0.388-1.24 0.475-0.721 0.154-1.557 0.098-2.129-0.486-0.866-0.886-0.819-2.277-0.205-3.443 0.477-0.908 1.323-1.77 2.538-2.412 0.059-0.615 0.157-1.298 0.3-2.064-0.654 0.013-1.281 0.001-1.844-0.037-0.413-0.029-0.725-0.387-0.697-0.8 0.028-0.413 0.386-0.725 0.799-0.697 0.622 0.043 1.331 0.05 2.067 0.025 0.116-0.473 0.272-1.067 0.42-1.513 0.13-0.393 0.555-0.606 0.948-0.475zm-2.058 7.372c-0.546 0.413-0.92 0.869-1.146 1.299-0.444 0.844-0.289 1.451-0.05 1.696 0.055 0.056 0.257 0.172 0.742 0.068 0.345-0.074 0.672-0.203 0.977-0.374-0.15-0.398-0.292-0.854-0.386-1.372-0.071-0.392-0.12-0.826-0.137-1.317zm1.755 1.66c0.713-0.826 1.204-1.881 1.413-2.848-0.403 0.013-0.847 0.091-1.328 0.249-0.117 0.039-0.23 0.079-0.339 0.121-0.028 0.774 0.023 1.377 0.112 1.866 0.041 0.228 0.089 0.428 0.142 0.612z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeyTranslatorIcon", 20.dp, 20.dp, 24f, 24f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }

    val SearchMagnifier: ImageVector by lazy {
        androidx.compose.ui.graphics.vector.PathParser()
            .parsePathString(
                "M8.5 3C11.538 3 14 5.462 14 8.5c0 1.339-0.478 2.566-1.273 3.52l4.127 4.126c0.195 0.196 0.195 0.512 0 0.708-0.174 0.173-0.443 0.192-0.638 0.057l-0.07-0.057-4.126-4.127C11.066 13.522 9.839 14 8.5 14 5.462 14 3 11.538 3 8.5S5.462 3 8.5 3zm0 1C6.015 4 4 6.015 4 8.5S6.015 13 8.5 13 13 10.985 13 8.5 10.985 4 8.5 4z"
            )
            .toNodes()
            .let { nodes ->
                ImageVector.Builder("SwiftKeySearchMagnifier", 20.dp, 20.dp, 20f, 20f)
                    .addPath(nodes, fill = SolidColor(Color.White))
                    .build()
            }
    }
}

@Composable
private fun rememberKeyboardMatchedPalette(): WingAiPalette {
    val context = LocalContext.current
    return remember(context.resources.configuration.uiMode) {
        runCatching {
            val kbColors = KeyboardTheme.getColorsForCurrentTheme(context)
            val mainBg = Color(kbColors.get(ColorType.MAIN_BACKGROUND)).copy(alpha = 1f)
            val keyBg = Color(kbColors.get(ColorType.KEY_BACKGROUND)).copy(alpha = 1f)
            val isLight = mainBg.luminance() > 0.5f

            // SwiftKey Fluent Adaptive exact colors:
            // Light: bg=#F1F1F1 (gray_50), card=#FFFFFF (white), stroke=#C8C8C8 (gray_200), sk_primary=#3961ED (sk_brand_50)
            // Dark: bg=#141414 (gray_950), card=#404040 (gray_700), stroke=#000000 (black), sk_primary=#9DC3FF (sk_brand_20)
            val accent = if (isLight) Color(0xFF3961ED) else Color(0xFF9DC3FF)
            val cardSurface = if (isLight) Color(0xFFFFFFFF) else Color(0xFF404040)
            val secondarySurface = if (isLight) Color(0xFFE3E3E3) else keyBg
            val cardStroke = if (isLight) Color(0xFFC8C8C8) else Color(0xFF000000)
            val strongBorder = if (isLight) Color(0xFFB7B7B7) else Color(0xFF4A4A4A)
            val primaryText = if (isLight) Color(0xFF000000) else Color(0xFFFFFFFF)
            val secondaryText = if (isLight) Color(0xFF4A4A4A) else Color(0xFFB7B7B7)
            val mutedText = if (isLight) Color(0xFF777777) else Color(0xFF939393)

            WingAiPalette(
                isLight = isLight,
                bgCanvas = mainBg,
                surface0 = mainBg,
                surface1 = cardSurface,
                surface2 = secondarySurface,
                surface3 = strongBorder,
                borderSubtle = cardStroke,
                borderStrong = strongBorder,
                borderAccent = accent,
                accentPrimary = accent,
                onAccentPrimary = if (isLight) Color.White else Color.Black,
                accentPrimaryContainer = if (isLight) Color(0xFFEF4FE) else Color(0xFF1A2D6E),
                accentCyan = Color(0xFF18C1ED),
                accentEmerald = if (isLight) Color(0xFF1D873B) else Color(0xFF52B471),
                accentEmeraldContainer = if (isLight) Color(0xFFD4EDD4) else Color(0xFF084606),
                accentAmber = Color(0xFFFFB657),
                accentRose = Color(0xFFE63237),
                accentRoseContainer = if (isLight) Color(0xFFFFB5B5) else Color(0xFF6C0000),
                textPrimary = primaryText,
                textSecondary = secondaryText,
                textMuted = mutedText
            )
        }.getOrDefault(DefaultDarkPalette)
    }
}

@Composable
fun WingAiTheme(content: @Composable () -> Unit) {
    val palette = rememberKeyboardMatchedPalette()
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accentPrimary,
            onPrimary = palette.onAccentPrimary,
            primaryContainer = palette.accentPrimaryContainer,
            secondary = palette.accentCyan,
            background = palette.bgCanvas,
            surface = palette.surface1,
            surfaceVariant = palette.surface2,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            error = palette.accentRose
        )
    } else {
        darkColorScheme(
            primary = palette.accentPrimary,
            onPrimary = palette.onAccentPrimary,
            primaryContainer = palette.accentPrimaryContainer,
            secondary = palette.accentCyan,
            background = palette.bgCanvas,
            surface = palette.surface1,
            surfaceVariant = palette.surface2,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            error = palette.accentRose
        )
    }

    CompositionLocalProvider(LocalWingAiPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}

/**
 * Renders a SwiftKey vector icon with optional gradient brush or solid color tint.
 */
@Composable
fun SwiftKeyVectorIcon(
    imageVector: ImageVector,
    modifier: Modifier = Modifier.size(18.dp),
    tint: Color = WingAiTokens.TextPrimary,
    gradientBrush: Brush? = null
) {
    val painter = rememberVectorPainter(imageVector)
    if (gradientBrush != null) {
        Canvas(
            modifier = modifier.graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
        ) {
            with(painter) {
                draw(size = size, alpha = 1f, colorFilter = null)
            }
            drawRect(
                brush = gradientBrush,
                blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn
            )
        }
    } else {
        Canvas(modifier = modifier) {
            with(painter) {
                draw(
                    size = size,
                    alpha = 1f,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint)
                )
            }
        }
    }
}

@Composable
fun CompactPillButton(
    text: String,
    backgroundColor: Color = WingAiTokens.Surface1,
    textColor: Color = WingAiTokens.TextPrimary,
    borderColor: Color = WingAiTokens.BorderSubtle,
    height: Dp = 28.dp,
    fontSize: TextUnit = 12.sp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "keyCapScale"
    )
    val bg by animateColorAsState(
        targetValue = if (enabled) backgroundColor else WingAiTokens.Surface2,
        animationSpec = tween(120),
        label = "keyCapBg"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .height(height)
            .clip(WingAiTokens.RadiusSm)
            .background(bg)
            .border(0.5.dp, borderColor, WingAiTokens.RadiusSm)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * SwiftKey-style Bottom Bar Tab (`style/ToolbarPanelTab` in `toolbar_panel_bottom_bar.xml`):
 * Flat text tab (`14sp`), `2dp` bottom indicator bar in `@color/sk_primary` (`#3961ED` light / `#9DC3FF` dark).
 */
@Composable
fun WingAiToneChip(
    label: String,
    selected: Boolean,
    accentColor: Color = WingAiTokens.AccentPrimary,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bgColor by animateColorAsState(
        targetValue = if (selected) accentColor.copy(alpha = 0.22f) else WingAiTokens.Surface2,
        animationSpec = tween(140),
        label = "tabBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else WingAiTokens.BorderStrong,
        animationSpec = tween(140),
        label = "tabBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) accentColor else WingAiTokens.TextPrimary,
        animationSpec = tween(140),
        label = "tabText"
    )

    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun WingAiSurfaceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = WingAiTokens.Surface1,
    borderColor: Color = WingAiTokens.BorderSubtle,
    padding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = modifier
        .clip(WingAiTokens.RadiusMd)
        .background(containerColor)
        .border(0.5.dp, borderColor, WingAiTokens.RadiusMd)
        .let { base ->
            if (onClick != null) {
                base.clickable(role = Role.Button, onClick = onClick)
            } else {
                base
            }
        }
        .padding(padding)

    Column(
        modifier = cardModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun WingAiSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = WingAiTokens.RadiusSm
) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    val baseColor = WingAiTokens.Surface2
    val highlightColor = WingAiTokens.BorderStrong.copy(alpha = 0.65f)
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 260f, 80f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}
