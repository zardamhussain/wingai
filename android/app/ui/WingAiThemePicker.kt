package com.wingai.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wingai.keyboard.settings.WingAiAppearance

@Composable
fun WingAiThemePicker(selected: WingAiAppearance, onSelect: (WingAiAppearance) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Make it yours", color = WingAiTokens.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "One theme for keyboard keys, buttons, AI replies and your account. Reopen the keyboard after choosing. Match keyboard restores your original appearance.",
            color = WingAiTokens.TextSecondary, fontSize = 13.sp, lineHeight = 19.sp
        )
        ThemeOption(WingAiAppearance.KEYBOARD, selected, Modifier.fillMaxWidth(), onSelect)
        WingAiAppearance.entries.filter { it != WingAiAppearance.KEYBOARD }.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { appearance ->
                    ThemeOption(appearance, selected, Modifier.weight(1f), onSelect)
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(
    appearance: WingAiAppearance,
    selected: WingAiAppearance,
    modifier: Modifier,
    onSelect: (WingAiAppearance) -> Unit
) {
    val palette = wingAiPresetPalette(appearance) ?: LocalWingAiPalette.current
    val active = appearance == selected
    Column(
        modifier = modifier
            .heightIn(min = 80.dp)
            .clip(WingAiTokens.RadiusMd)
            .background(palette.surface1)
            .border(if (active) 2.dp else 1.dp, if (active) palette.accentPrimary else palette.borderSubtle, WingAiTokens.RadiusMd)
            .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(appearance) })
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(palette.accentPrimary))
            Text(
                appearance.title, color = palette.textPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            if (active) Text("\u2713", color = palette.accentPrimary, fontSize = 14.sp)
        }
        Text(appearance.description, color = palette.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
    }
}
