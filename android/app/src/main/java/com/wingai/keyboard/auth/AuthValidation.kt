package com.wingai.keyboard.auth

/** Account identity is separate from the existing device ID, quota and paid plan. */
internal object AuthValidation {
    fun email(value: String): String? {
        val email = value.trim()
        return if (email.length in 3..254 &&
            Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) null
        else "Enter a valid email address."
    }

    // Validate new passwords, never apply new-password rules to existing accounts.
    fun newPassword(value: String): String? = when {
        value.length < 8 -> "Use at least 8 characters for your password."
        value.length > 128 -> "Use no more than 128 characters."
        else -> null
    }

    fun name(value: String): String? = when {
        value.trim().isEmpty() -> "Enter your name."
        value.trim().length > 60 -> "Use no more than 60 characters for your name."
        else -> null
    }
}
