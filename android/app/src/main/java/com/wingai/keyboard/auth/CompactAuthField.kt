package com.wingai.keyboard.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class AuthFieldIcon { PERSON, EMAIL, LOCK, EYE, EYE_OFF }

/**
 * Reference: slim, softly filled pill fields with inline labels and small leading icons.
 * Use a 48 dp minimum, not a fixed height: preserve touch targets and large-font expansion.
 * BasicTextField avoids clipping Material TextField's built-in 56 dp/floating-label layout.
 */
@Composable
internal fun CompactAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: AuthFieldIcon,
    autofillType: ContentType,
    enabled: Boolean,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    passwordVisible: Boolean? = null,
    onTogglePassword: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.6f)
            ),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth()
                .testTag("auth-field:$label")
                .clip(shape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.55f))
                .then(if (error != null) Modifier.border(1.dp, colors.error, shape) else Modifier)
                .semantics {
                    contentDescription = label
                    contentType = autofillType
                },
            decorationBox = { innerTextField ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .padding(start = 14.dp, end = if (passwordVisible == null) 14.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuthFieldGlyph(icon, Modifier.size(18.dp))
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (value.isEmpty()) {
                            Text(label, color = colors.onSurfaceVariant, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        innerTextField()
                    }
                    if (passwordVisible != null) {
                        IconButton(onClick = onTogglePassword, enabled = enabled,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            }) {
                            AuthFieldGlyph(if (passwordVisible) AuthFieldIcon.EYE_OFF else AuthFieldIcon.EYE,
                                Modifier.size(18.dp))
                        }
                    }
                }
            }
        )
        error?.let {
            Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 14.dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

/** Small original line icons; no extra icon library or changes to the existing keyboard art. */
@Composable
private fun AuthFieldGlyph(icon: AuthFieldIcon, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 1.5f * unit)
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        when (icon) {
            AuthFieldIcon.EMAIL -> {
                drawRoundRect(tint, point(3f, 5f), Size(18f * unit, 14f * unit),
                    CornerRadius(3f * unit), style = stroke)
                drawLine(tint, point(4f, 7f), point(12f, 13f), stroke.width)
                drawLine(tint, point(12f, 13f), point(20f, 7f), stroke.width)
            }
            AuthFieldIcon.PERSON -> {
                drawCircle(tint, 3.5f * unit, point(12f, 7f), style = stroke)
                drawArc(tint, 180f, 180f, false, point(5f, 13f), Size(14f * unit, 12f * unit), style = stroke)
            }
            AuthFieldIcon.LOCK -> {
                drawArc(tint, 180f, 180f, false, point(7f, 3f), Size(10f * unit, 14f * unit), style = stroke)
                drawRoundRect(tint, point(5f, 10f), Size(14f * unit, 11f * unit),
                    CornerRadius(3f * unit), style = stroke)
                drawLine(tint, point(12f, 14f), point(12f, 17f), stroke.width)
            }
            AuthFieldIcon.EYE, AuthFieldIcon.EYE_OFF -> {
                drawOval(tint, point(2f, 6f), Size(20f * unit, 12f * unit), style = stroke)
                drawCircle(tint, 3f * unit, point(12f, 12f), style = stroke)
                if (icon == AuthFieldIcon.EYE_OFF) drawLine(tint, point(3f, 3f), point(21f, 21f), stroke.width)
            }
        }
    }
}
