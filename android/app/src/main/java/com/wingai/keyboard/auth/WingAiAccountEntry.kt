package com.wingai.keyboard.auth

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.wingai.keyboard.ui.WingAiSurfaceCard
import com.wingai.keyboard.ui.WingAiTokens

/** A single additive entry point; the original account/plan cards remain untouched. */
@Composable
fun WingAiAccountEntry() {
    val context = LocalContext.current
    val auth = remember { runCatching { WingAiFirebase.auth(context) }.getOrNull() }
    var email by remember { mutableStateOf(auth?.currentUser?.email) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { email = it.currentUser?.email }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }
    WingAiSurfaceCard(modifier = Modifier.fillMaxWidth(), borderColor = WingAiTokens.BorderAccent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (email == null) "Your WingAI account" else "You're signed in",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WingAiTokens.TextPrimary)
            Text(email ?: "Sign in or create an account. Your keyboard and device plan stay unchanged.",
                color = WingAiTokens.TextSecondary, fontSize = 13.sp)
            Button(onClick = { context.startActivity(Intent(context, WingAiAuthActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (email == null) "Sign in / Create account" else "Manage login account")
            }
        }
    }
}
