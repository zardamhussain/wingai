package com.wingai.keyboard.auth

import com.google.firebase.firestore.FieldValue

/**
 * wingaiDeviceId is the EXISTING SettingsRepository device user ID (wing_ + 10 hex digits),
 * never a Firebase UID, Android hardware identifier, newly minted login ID or entitlement.
 * The root profile records the most recently synced installation. It is client-reported
 * metadata, not proof of device ownership and must not authorize Cloudflare plans/quotas.
 */
internal fun accountProfileData(name: String, email: String, wingaiDeviceId: String, isNew: Boolean): Map<String, Any> {
    require(Regex("^wing_[0-9a-f]{10}$").matches(wingaiDeviceId)) { "Invalid WingAI device ID" }
    return mutableMapOf<String, Any>(
        "displayName" to name.take(60),
        "email" to email,
        "wingaiDeviceId" to wingaiDeviceId,
        "updatedAt" to FieldValue.serverTimestamp()
    ).apply {
        if (isNew) put("createdAt", FieldValue.serverTimestamp())
    }
}
