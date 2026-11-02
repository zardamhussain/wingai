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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.common.ColorType
import com.wingai.keyboard.settings.SettingsRepository
import com.wingai.keyboard.settings.WingAiAppearance
import com.wingai.keyboard.settings.WingAiSettings

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

private val DefaultDarkPalette = WingAiPalette(
    isLight = false,
    bgCanvas = Color(0xFF16181D),
    surface0 = Color(0xFF16181D),
    surface1 = Color(0xFF242730),
    surface2 = Color(0xFF1C1F27),
    surface3 = Color(0xFF2E323D),
    borderSubtle = Color(0xFF2E323D),
    borderStrong = Color(0xFF3B404E),
    borderAccent = Color(0xFF4A6CF7),
    accentPrimary = Color(0xFF4A6CF7),
    onAccentPrimary = Color(0xFFFFFFFF),
    accentPrimaryContainer = Color(0xFF222B4A),
    accentCyan = Color(0xFF4A6CF7),
    accentEmerald = Color(0xFF10B981),
    accentEmeraldContainer = Color(0xFF142E28),
    accentAmber = Color(0xFFF59E0B),
    accentRose = Color(0xFFE11D48),
    accentRoseContainer = Color(0xFF351821),
    textPrimary = Color(0xFFF3F4F6),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF6B7280)
)

val LocalWingAiPalette = staticCompositionLocalOf { DefaultDarkPalette }
val LocalWingAiAppearance = staticCompositionLocalOf { WingAiAppearance.KEYBOARD }

/**
 * Dynamic tokens synced with the active keyboard theme (Light, Dark, Dynamic Material You, etc.)
 * so the keyboard keys, top suggestion strip, and AI reply cards share the exact same minimal aesthetic.
 */
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

    // Softer surfaces; preserve the fixed IME height rather than expanding the keyboard.
    val RadiusSm = RoundedCornerShape(10.dp)
    val RadiusMd = RoundedCornerShape(18.dp)
    val RadiusLg = RoundedCornerShape(24.dp)
    val RadiusPill = RoundedCornerShape(50)

    @Composable
    fun toneColor(tone: String): Color {
        val isLight = LocalWingAiPalette.current.isLight
        return when (tone.lowercase()) {
            "casual" -> if (isLight) Color(0xFF2563EB) else Color(0xFF60A5FA)
            "friendly" -> if (isLight) Color(0xFF059669) else Color(0xFF34D399)
            "professional" -> if (isLight) Color(0xFF4F46E5) else Color(0xFF818CF8)
            "funny" -> if (isLight) Color(0xFFD97706) else Color(0xFFFBBF24)
            "flirty" -> if (isLight) Color(0xFFDB2777) else Color(0xFFF472B6)
            "short" -> if (isLight) Color(0xFF0D9488) else Color(0xFF2DD4BF)
            "detailed" -> if (isLight) Color(0xFF7C3AED) else Color(0xFFA78BFA)
            else -> LocalWingAiPalette.current.accentPrimary
        }
    }
}

@Composable
private fun rememberKeyboardMatchedPalette(): WingAiPalette {
    val context = LocalContext.current
    return remember(context.resources.configuration.uiMode) {
        runCatching {
            val kbColors = KeyboardTheme.getHeliBoardColors(context)
            val mainBg = Color(kbColors.get(ColorType.MAIN_BACKGROUND)).copy(alpha = 1f)
            val keyBg = Color(kbColors.get(ColorType.KEY_BACKGROUND)).copy(alpha = 1f)
            val funcBg = Color(kbColors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND)).copy(alpha = 1f)
            val keyText = Color(kbColors.get(ColorType.KEY_TEXT)).copy(alpha = 1f)
            val isLight = mainBg.luminance() > 0.5f

            val accent = if (isLight) Color(0xFF3B5BDB) else Color(0xFF4A6CF7)
            val subtleBorder = if (isLight) {
                Color(0xFFD0D5DD)
            } else {
                Color(0xFF2E323D)
            }
            val strongBorder = if (isLight) {
                Color(0xFFB8C0CC)
            } else {
                Color(0xFF3E4452)
            }
            val secondaryText = if (isLight) {
                Color(0xFF525866)
            } else {
                Color(0xFF9CA3AF)
            }
            val mutedText = if (isLight) {
                Color(0xFF7A8291)
            } else {
                Color(0xFF6B7280)
            }

            WingAiPalette(
                isLight = isLight,
                bgCanvas = mainBg,
                surface0 = mainBg,
                surface1 = keyBg,
                surface2 = funcBg,
                surface3 = subtleBorder,
                borderSubtle = subtleBorder,
                borderStrong = strongBorder,
                borderAccent = accent,
                accentPrimary = accent,
                onAccentPrimary = Color.White,
                accentPrimaryContainer = if (isLight) Color(0xFFE0E7FF) else Color(0xFF222B4A),
                accentCyan = accent,
                accentEmerald = if (isLight) Color(0xFF059669) else Color(0xFF10B981),
                accentEmeraldContainer = if (isLight) Color(0xFFD1FAE5) else Color(0xFF142E28),
                accentAmber = if (isLight) Color(0xFFD97706) else Color(0xFFF59E0B),
                accentRose = if (isLight) Color(0xFFE11D48) else Color(0xFFF43F5E),
                accentRoseContainer = if (isLight) Color(0xFFFFE4E6) else Color(0xFF351821),
                textPrimary = keyText,
                textSecondary = secondaryText,
                textMuted = mutedText
            )
        }.getOrDefault(DefaultDarkPalette)
    }
}

@Composable
fun WingAiTheme(
    appearance: WingAiAppearance? = null,
    content: @Composable () -> Unit
) {
    // Explicit themes keep Android Studio previews deterministic and avoid DataStore/JNI there.
    val inspection = LocalInspectionMode.current
    val resolvedAppearance = if (appearance != null) {
        appearance
    } else if (inspection) {
        WingAiAppearance.AURORA
    } else {
        val context = LocalContext.current.applicationContext
        val repository = remember(context) { SettingsRepository(context) }
        val settings by repository.settingsFlow.collectAsState(initial = WingAiSettings())
        settings.appearance
    }
    val palette = wingAiPresetPalette(resolvedAppearance)
        ?: if (inspection) DefaultDarkPalette else rememberKeyboardMatchedPalette()
    val baseScheme = if (palette.isLight) lightColorScheme() else darkColorScheme()
    // Include M3 container roles used by the existing settings app bar, not only custom cards.
    val scheme = baseScheme.copy(
        primary = palette.accentPrimary,
        onPrimary = palette.onAccentPrimary,
        primaryContainer = palette.accentPrimaryContainer,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.accentCyan,
        background = palette.bgCanvas,
        surface = palette.surface1,
        surfaceVariant = palette.surface2,
        surfaceContainerLowest = palette.bgCanvas,
        surfaceContainerLow = palette.surface0,
        surfaceContainer = palette.surface1,
        surfaceContainerHigh = palette.surface2,
        surfaceContainerHighest = palette.surface3,
        surfaceTint = palette.accentPrimary,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.borderStrong,
        outlineVariant = palette.borderSubtle,
        error = palette.accentRose,
        errorContainer = palette.accentRoseContainer,
        onErrorContainer = palette.textPrimary
    )

    CompositionLocalProvider(
        LocalWingAiPalette provides palette,
        LocalWingAiAppearance provides resolvedAppearance
    ) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}

/**
 * Minimal rounded key-cap button matching the keyboard's key geometry and surface colors.
 */
@Composable
fun CompactPillButton(
    text: String,
    backgroundColor: Color = WingAiTokens.Surface1,
    textColor: Color = WingAiTokens.TextPrimary,
    borderColor: Color = WingAiTokens.BorderSubtle,
    height: Dp = 36.dp,
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
            .border(1.dp, borderColor, WingAiTokens.RadiusSm)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 11.dp),
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
 * Minimal tone selector key-cap chip.
 */
@Composable
fun WingAiToneChip(
    label: String,
    selected: Boolean,
    accentColor: Color = WingAiTokens.AccentPrimary,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chipScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) WingAiTokens.AccentPrimaryContainer else WingAiTokens.Surface1,
        animationSpec = tween(150),
        label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else WingAiTokens.BorderSubtle,
        animationSpec = tween(150),
        label = "chipBorder"
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(36.dp)
            .clip(WingAiTokens.RadiusPill)
            .background(bgColor)
            .border(1.dp, borderColor, WingAiTokens.RadiusPill)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
        Text(
            text = label,
            color = if (selected) WingAiTokens.TextPrimary else WingAiTokens.TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * Minimal surface card matching the keyboard's key-cap elevation and rounded geometry.
 */
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
        .border(1.dp, borderColor, WingAiTokens.RadiusMd)
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

/**
 * Animated shimmer skeleton placeholder box that adapts to the active keyboard theme.
 */
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
