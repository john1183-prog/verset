package com.johndev.verset.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("verset_prefs", Context.MODE_PRIVATE)

    var fontScale: Float
        get() = sp.getFloat("font_scale", 1.0f)
        set(value) = sp.edit().putFloat("font_scale", value).apply()

    var darkMode: Boolean
        get() = sp.getBoolean("dark_mode", false)
        set(value) = sp.edit().putBoolean("dark_mode", value).apply()

    var followSystemTheme: Boolean
        get() = sp.getBoolean("follow_system_theme", true)
        set(value) = sp.edit().putBoolean("follow_system_theme", value).apply()

    var lastBookIndex: Int
        get() = sp.getInt("last_book", 0)
        set(value) = sp.edit().putInt("last_book", value).apply()

    var lastSyncTimeMillis: Long
        get() = sp.getLong("last_sync_time", 0L)
        set(value) = sp.edit().putLong("last_sync_time", value).apply()

    var onboardingComplete: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(value) = sp.edit().putBoolean("onboarding_done", value).apply()

    /**
     * The Firebase Web Client ID entered by the app developer in the sync setup screen.
     * Empty until configured — the app checks for this before allowing sign-in.
     */
    var webClientId: String
        get() = sp.getString("web_client_id", "") ?: ""
        set(value) = sp.edit().putString("web_client_id", value).apply()

    var lastChapter: Int
        get() = sp.getInt("last_chapter", 1)
        set(value) = sp.edit().putInt("last_chapter", value).apply()

    // Only set to true after the FULL KJV import transaction commits successfully.
    // Used instead of a DB count() check so a killed/interrupted first-launch
    // import can never be mistaken for "already loaded".
    var bibleLoaded: Boolean
        get() = sp.getBoolean("bible_loaded", false)
        set(value) = sp.edit().putBoolean("bible_loaded", value).apply()
}
