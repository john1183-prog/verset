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

    var lastChapter: Int
        get() = sp.getInt("last_chapter", 1)
        set(value) = sp.edit().putInt("last_chapter", value).apply()
}
