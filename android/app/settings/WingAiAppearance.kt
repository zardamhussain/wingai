package com.wingai.keyboard.settings

/** Stable preference IDs. Named presets theme both Compose and native HeliBoard keycaps. */
enum class WingAiAppearance(val id: String, val title: String, val description: String) {
    KEYBOARD("keyboard", "Match keyboard", "Use your original keyboard appearance"),
    AURORA("aurora", "Aurora", "Soft violet, after dark"),
    DAYLIGHT("daylight", "Daylight", "Airy, bright and focused"),
    OCEAN("ocean", "Ocean", "Deep blue with a fresh mint accent"),
    ROSE("rose", "Rose", "Warm blush with a little personality");

    companion object {
        // Keep existing installations unchanged, and tolerate unknown future preference values.
        fun fromId(id: String?): WingAiAppearance =
            entries.firstOrNull { it.id == id } ?: KEYBOARD
    }
}
