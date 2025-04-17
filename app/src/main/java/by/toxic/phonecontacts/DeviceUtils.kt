// app/src/main/java/by/toxic/phonecontacts/DeviceUtils.kt
package by.toxic.phonecontacts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

object DeviceUtils {

    /**
     * Проверяет, является ли производитель устройства Xiaomi.
     * Это упрощенная проверка, можно добавить и другие бренды (Oppo, Vivo и т.д.),
     * если у них есть похожие проблемы с разрешениями.
     */
    fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        // Можно добавить || Build.BRAND.equals("POCO", ignoreCase = true)
        // Но обычно MANUFACTURER достаточно.
    }

    /**
     * Показывает диалог, информирующий пользователя о необходимости
     * дополнительных разрешений на устройствах Xiaomi/MIUI и предлагающий
     * перейти в настройки приложения.
     *
     * @param context Контекст Activity.
     * @param onSettingsOpened Колбэк, вызываемый после нажатия "Перейти в настройки".
     * @param onAcknowledged Колбэк, вызываемый после нажатия "Понятно / Позже".
     */
    fun showMiuiPermissionsDialog(
        context: Context,
        onSettingsOpened: () -> Unit,
        onAcknowledged: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.miui_dialog_title) // Используем ресурсы строк
            .setMessage(
                "На устройствах Xiaomi (MIUI) для корректной работы виджета " +
                        "(особенно для совершения звонков по клику и отображения поверх других окон) может потребоваться " +
                        "вручную включить разрешения в разделе 'Другие разрешения':\n\n" +
                        "• Отображение всплывающих окон\n" +
                        "• Ярлыки рабочего стола\n"
                // Сообщение немного упрощено для ясности
            )
            .setPositiveButton(R.string.miui_dialog_settings_button) { dialog, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                    onSettingsOpened() // Уведомляем, что настройки открыты
                } catch (e: Exception) {
                    // На случай, если Intent не сработает
                    android.widget.Toast.makeText(
                        context,
                        R.string.miui_dialog_cant_open_settings, // Используем ресурс
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.miui_dialog_acknowledge_button) { dialog, _ ->
                onAcknowledged() // Уведомляем, что пользователь ознакомлен
                dialog.dismiss()
            }
            .setCancelable(false) // Нельзя закрыть кнопкой "Назад"
            .show()
    }
}