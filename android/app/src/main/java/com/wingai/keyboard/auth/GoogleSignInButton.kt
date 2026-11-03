package com.wingai.keyboard.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R

/** Neutral Google-branded pill, matching the reference while preserving the official mark. */
@Composable
internal fun GoogleSignInButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp), border = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF2F2F2),
            contentColor = Color(0xFF1F1F1F), disabledContainerColor = Color(0xFFF2F2F2),
            disabledContentColor = Color(0xFF747775))
    ) {
        Image(painterResource(R.drawable.ic_google_sign_in), contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Continue with Google", fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
