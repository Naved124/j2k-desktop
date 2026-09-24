package dev.naved.j2kdesktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.naved.j2kdesktop.compat.AndroidCompat

enum class AppTheme(val label: String) {
    Dark("Default dark"),
    Black("Pure black (OLED)"),
    Tako("Tako"),
    TokyoNight("Tokyo Night"),
    YinYang("Yin & Yang"),
    FlatLime("Flat Lime"),
    MidnightDusk("Midnight Dusk"),
    ChocolateStrawberry("Chocolate Strawberry"),
    SapphireDusk("Sapphire Dusk"),
    Light("Light"),
}

/** App-wide settings (More tab), saved in prefs/app.json. Compose-observable. */
object AppSettings {
    private val prefs get() = AndroidCompat.sharedPreferences("app")

    var theme by mutableStateOf(
        runCatching { AppTheme.valueOf(prefs.getString("theme", AppTheme.Dark.name)!!) }.getOrDefault(AppTheme.Dark),
    )
        private set

    fun changeTheme(value: AppTheme) {
        theme = value
        prefs.edit().putString("theme", value.name).apply()
    }

    var updateOnStart: Boolean
        get() = prefs.getBoolean("update_on_start", true)
        set(value) = prefs.edit().putBoolean("update_on_start", value).apply()

    /** Reader mode for manga you haven't opened before ("Paged", "Vertical", "Webtoon"). */
    var defaultReadingMode: String
        get() = prefs.getString("default_reading_mode", "Paged") ?: "Paged"
        set(value) = prefs.edit().putString("default_reading_mode", value).apply()

    /** Open long-strip series (manhwa, webtoon genres) in webtoon mode automatically. */
    var autoWebtoon: Boolean
        get() = prefs.getBoolean("auto_webtoon", true)
        set(value) = prefs.edit().putBoolean("auto_webtoon", value).apply()

    var defaultRightToLeft: Boolean
        get() = prefs.getBoolean("default_rtl", true)
        set(value) = prefs.edit().putBoolean("default_rtl", value).apply()
}
