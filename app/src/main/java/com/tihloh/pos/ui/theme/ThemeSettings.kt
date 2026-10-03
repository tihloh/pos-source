package com.tihloh.pos.ui.theme

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("pos_theme", Context.MODE_PRIVATE)

    fun load(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString("mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    }.getOrDefault(ThemeMode.SYSTEM)

    fun save(mode: ThemeMode) {
        prefs.edit().putString("mode", mode.name).apply()
    }
}
