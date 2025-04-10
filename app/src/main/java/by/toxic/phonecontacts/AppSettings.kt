package by.toxic.phonecontacts

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private const val PREFS_NAME = "by.toxic.phonecontacts.AppSettings" // Имя файла для общих настроек
    private const val KEY_MIUI_WARNING_SHOWN = "miui_warning_shown"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Проверяем, было ли уже показано предупреждение
    fun hasShownMiuiWarning(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MIUI_WARNING_SHOWN, false)
    }

    // Устанавливаем флаг, что предупреждение было показано
    fun setMiuiWarningShown(context: Context, shown: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_MIUI_WARNING_SHOWN, shown).apply()
    }
}