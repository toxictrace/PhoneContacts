package by.toxic.phonecontacts

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * Объект для управления настройками приложения.
 * Предоставляет доступ к сохраненным настройкам и их изменению.
 */
object AppSettings {
    private const val TAG = "AppSettings"
    private const val PREFS_NAME = "by.toxic.phonecontacts.AppSettings" // Имя файла для общих настроек

    // Ключи для настроек
    private const val KEY_MIUI_WARNING_SHOWN = "miui_warning_shown"
    private const val KEY_USE_MONET = "use_monet_theme" // Ключ для темы

    /**
     * Получает экземпляр SharedPreferences для работы с настройками
     * @throws IllegalArgumentException если context равен null
     */
    private fun getPrefs(context: Context): SharedPreferences {
        // Проверка на null, чтобы избежать потенциальных NullPointerException
        if (context == null) {
            Log.e(TAG, "Context не может быть null")
            throw IllegalArgumentException("Context не может быть null")
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Проверяет, было ли показано предупреждение для MIUI
     * @return true если предупреждение уже было показано
     */
    fun hasShownMiuiWarning(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MIUI_WARNING_SHOWN, false)
    }

    /**
     * Устанавливает флаг о том, что предупреждение для MIUI было показано
     * @param shown флаг, было ли показано предупреждение
     */
    fun setMiuiWarningShown(context: Context, shown: Boolean) {
        try {
            // Используем commit() вместо apply() для гарантии сохранения настройки
            getPrefs(context).edit().putBoolean(KEY_MIUI_WARNING_SHOWN, shown).commit()
            Log.d(TAG, "MIUI warning flag set to: $shown")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении настройки MIUI: ${e.message}")
        }
    }

    /**
     * Проверяет, включена ли динамическая тема Monet.
     * Возвращает true, если настройка включена И версия Android >= 12.
     * По умолчанию false.
     * @return статус включения темы Monet
     */
    fun isMonetEnabled(context: Context): Boolean {
        // Динамическая тема работает только на Android 12 (API 31) и выше
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        // Возвращаем сохраненное значение, по умолчанию false
        return getPrefs(context).getBoolean(KEY_USE_MONET, false)
    }

    /**
     * Сохраняет настройку использования темы Monet.
     * @param enabled флаг, включать ли тему Monet
     */
    fun setMonetEnabled(context: Context, enabled: Boolean) {
        try {
            // Сохраняем только если Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Используем commit() для важных настроек темы
                val result = getPrefs(context).edit().putBoolean(KEY_USE_MONET, enabled).commit()
                if (result) {
                    Log.d(TAG, "Настройка темы Monet сохранена: $enabled")
                } else {
                    Log.w(TAG, "Не удалось сохранить настройку темы Monet")
                }
            } else {
                Log.i(TAG, "Тема Monet не поддерживается на Android ниже 12 (API 31)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении настройки темы Monet: ${e.message}")
        }
    }

    /**
     * Получает ID ресурса темы в зависимости от настройки Monet.
     * @return идентификатор выбранной темы
     */
    fun getSelectedThemeResId(context: Context): Int {
        return if (isMonetEnabled(context)) {
            R.style.Theme_PhoneContacts_Dynamic
        } else {
            R.style.Theme_PhoneContacts_Static
        }
    }
}