package by.toxic.phonecontacts

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

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
     * Получает экземпляр SharedPreferences для работы с настройками.
     * Используйте applicationContext, чтобы избежать утечек памяти.
     * @param context Контекст приложения.
     * @return Экземпляр SharedPreferences.
     * @throws IllegalArgumentException если context равен null (хотя с applicationContext это маловероятно).
     */
    private fun getPrefs(context: Context): SharedPreferences {
        // Используем applicationContext для предотвращения утечек памяти Activity/Service
        val appContext = context.applicationContext
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Проверяет, было ли показано предупреждение для MIUI.
     * @param context Контекст.
     * @return true если предупреждение уже было показано.
     */
    fun hasShownMiuiWarning(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_MIUI_WARNING_SHOWN, false)
    }

    /**
     * Устанавливает флаг о том, что предупреждение для MIUI было показано.
     * @param context Контекст.
     * @param shown флаг, было ли показано предупреждение.
     */
    fun setMiuiWarningShown(context: Context, shown: Boolean) {
        try {
            // <<< ИЗМЕНЕНИЕ: Используем apply() для асинхронной записи >>>
            getPrefs(context).edit().putBoolean(KEY_MIUI_WARNING_SHOWN, shown).apply()
            Log.d(TAG, "MIUI warning flag set to: $shown")
        } catch (e: Exception) {
            // Ловим конкретные исключения, если это возможно, но SecurityException здесь маловероятен
            Log.e(TAG, "Ошибка при сохранении настройки MIUI: ${e.message}")
        }
    }

    /**
     * Проверяет, включена ли динамическая тема Monet.
     * Возвращает true, если настройка включена И версия Android >= 12.
     * По умолчанию false.
     * @param context Контекст.
     * @return статус включения темы Monet.
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
     * @param context Контекст.
     * @param enabled флаг, включать ли тему Monet.
     */
    @RequiresApi(Build.VERSION_CODES.S) // Явно указываем, что метод для Android 12+
    fun setMonetEnabled(context: Context, enabled: Boolean) {
        try {
            // Сохраняем только если Android 12+ (проверка уже есть в isMonetEnabled, но здесь тоже не помешает)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // <<< ИЗМЕНЕНИЕ: Используем apply() для асинхронной записи >>>
                // Настройка темы не настолько критична, чтобы блокировать поток с commit()
                getPrefs(context).edit().putBoolean(KEY_USE_MONET, enabled).apply()
                Log.d(TAG, "Настройка темы Monet сохранена (асинхронно): $enabled")
            } else {
                Log.i(TAG, "Тема Monet не поддерживается на Android ниже 12 (API 31)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при сохранении настройки темы Monet: ${e.message}")
        }
    }

    /**
     * Получает ID ресурса темы в зависимости от настройки Monet.
     * @param context Контекст.
     * @return идентификатор выбранной темы.
     */
    fun getSelectedThemeResId(context: Context): Int {
        return if (isMonetEnabled(context)) {
            R.style.Theme_PhoneContacts_Dynamic
        } else {
            R.style.Theme_PhoneContacts_Static
        }
    }
}