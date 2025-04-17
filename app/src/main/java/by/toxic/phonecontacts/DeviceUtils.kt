// app/src/main/java/by/toxic/phonecontacts/DeviceUtils.kt
package by.toxic.phonecontacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log // <<< Добавлен импорт >>>
import android.widget.Toast // <<< Добавлен импорт >>>
import androidx.appcompat.app.AlertDialog

object DeviceUtils {

    private const val TAG = "DeviceUtils" // TAG для логов

    /**
     * Проверяет, является ли производитель устройства Xiaomi.
     * Сравнение без учета регистра для большей надежности.
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER
        val isXiaomi = "Xiaomi".equals(manufacturer, ignoreCase = true)
        if (isXiaomi) {
            Log.d(TAG, "Обнаружено устройство Xiaomi (Производитель: $manufacturer)")
        }
        return isXiaomi
        // Можно добавить другие бренды при необходимости:
        // || "POCO".equals(Build.BRAND, ignoreCase = true)
    }

    /**
     * Показывает диалог, информирующий пользователя о необходимости
     * дополнительных разрешений на устройствах Xiaomi/MIUI и предлагающий
     * перейти в настройки приложения.
     *
     * @param context Контекст Activity для отображения диалога.
     * @param onSettingsOpened Колбэк, вызываемый после нажатия "Перейти в настройки".
     * @param onAcknowledged Колбэк, вызываемый после нажатия "Понятно / Позже".
     */
    fun showMiuiPermissionsDialog(
        context: Context,
        onSettingsOpened: () -> Unit,
        onAcknowledged: () -> Unit
    ) {
        Log.d(TAG, "Показ диалога о дополнительных разрешениях MIUI.")
        AlertDialog.Builder(context)
            .setTitle(R.string.miui_dialog_title) // Заголовок из ресурсов
            .setMessage(R.string.miui_dialog_message) // Основное сообщение из ресурсов
            .setPositiveButton(R.string.miui_dialog_settings_button) { dialog, _ ->
                Log.d(TAG, "Нажата кнопка 'Перейти в настройки' в диалоге MIUI.")
                try {
                    // Создаем Intent для открытия системных настроек конкретного приложения
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        // Добавляем флаг, чтобы настройки открылись в новой задаче
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    // Проверяем, есть ли Activity для обработки Intent'а
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                        onSettingsOpened() // Вызываем колбэк после успешного запуска
                    } else {
                        Log.e(TAG, "Не найдено Activity для обработки ACTION_APPLICATION_DETAILS_SETTINGS.")
                        Toast.makeText(context, R.string.miui_dialog_cant_open_settings, Toast.LENGTH_LONG).show()
                        // В этом случае колбэк onSettingsOpened не вызываем, но onAcknowledged все равно сработает при закрытии
                        onAcknowledged() // Считаем, что пользователь увидел сообщение, хоть и не перешел
                    }
                } catch (e: Exception) {
                    // На случай других ошибок при запуске Intent'а
                    Log.e(TAG, "Ошибка при попытке открыть настройки приложения: ${e.message}", e)
                    Toast.makeText(context, R.string.miui_dialog_cant_open_settings, Toast.LENGTH_LONG).show()
                    onAcknowledged() // Считаем, что пользователь увидел сообщение
                }
                dialog.dismiss() // Закрываем диалог
            }
            .setNegativeButton(R.string.miui_dialog_acknowledge_button) { dialog, _ ->
                Log.d(TAG, "Нажата кнопка 'Понятно / Позже' в диалоге MIUI.")
                onAcknowledged() // Вызываем колбэк подтверждения
                dialog.dismiss() // Закрываем диалог
            }
            .setCancelable(false) // Нельзя закрыть кнопкой "Назад" или тапом вне диалога
            .show()
    }
}