package by.toxic.phonecontacts

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri // <<< Убедитесь, что этот импорт есть >>>
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat

class ContactsGridWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d("WidgetProvider", "onUpdate called for widgets: ${appWidgetIds.joinToString()}")
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d("WidgetProvider", "onDeleted called for widgets: ${appWidgetIds.joinToString()}")
        // Удаляем настройки и связанные файлы (включая кастомные аватары)
        WidgetPrefsUtils.deleteWidgetConfig(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WidgetProvider", "onReceive triggered. Action: ${intent.action}")

        when (intent.action) {
            ACTION_CLICK_CONTACT -> {
                Log.d("WidgetProvider", "Action is ACTION_CLICK_CONTACT")
                // Извлекаем данные, переданные через fillInIntent из Factory
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
                val photoUriString = intent.getStringExtra(EXTRA_PHOTO_URI) // URI миниатюры контакта
                val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
                // Получаем ID виджета из Intent'а-шаблона, который мы создали в updateAppWidget
                val clickedWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

                Log.d("WidgetProvider", "Extracted PN: $phoneNumber, Name: $contactName, PhotoUri: $photoUriString, ContactID: $contactId from widget $clickedWidgetId")

                if (!phoneNumber.isNullOrEmpty() && clickedWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    // Загружаем настройку действия по клику для данного виджета
                    val clickAction = WidgetPrefsUtils.loadClickAction(context, clickedWidgetId)
                    Log.d("WidgetProvider", "Click action preference for widget $clickedWidgetId: $clickAction")
                    try {
                        when (clickAction) {
                            WidgetPrefsUtils.CLICK_ACTION_CALL -> {
                                // Действие: Немедленный звонок
                                Log.d("WidgetProvider", "Attempting direct call (ACTION_CALL)")
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                                        // <<< ИЗМЕНЕНИЕ: Используем Uri.fromParts для корректной обработки '#' >>>
                                        data = Uri.fromParts("tel", phoneNumber, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Важно для запуска из BroadcastReceiver
                                    }
                                    if (callIntent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(callIntent)
                                        Log.d("WidgetProvider", "ACTION_CALL started.")
                                    } else {
                                        handleNoActivityFound(context, "ACTION_CALL")
                                    }
                                } else {
                                    // Если разрешение отозвано, fallback на открытие звонилки
                                    Log.w("WidgetProvider", "CALL_PHONE permission missing. Falling back to ACTION_DIAL.")
                                    Toast.makeText(context, R.string.call_permission_revoked, Toast.LENGTH_LONG).show()
                                    startDialActivity(context, phoneNumber)
                                }
                            }
                            WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL -> {
                                // Действие: Показать диалог подтверждения
                                Log.d("WidgetProvider", "Starting CallConfirmActivity...")
                                val confirmIntent = Intent(context, CallConfirmActivity::class.java).apply {
                                    putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                                    putExtra(EXTRA_CONTACT_NAME, contactName)
                                    putExtra(EXTRA_PHOTO_URI, photoUriString) // Передаем URI миниатюры
                                    putExtra(EXTRA_CONTACT_ID, contactId) // Передаем ID контакта
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, clickedWidgetId) // Передаем ID виджета!
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                                if (confirmIntent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(confirmIntent)
                                    Log.d("WidgetProvider", "CallConfirmActivity started.")
                                } else {
                                    handleNoActivityFound(context, "CallConfirmActivity")
                                }
                            }
                            else -> { // WidgetPrefsUtils.CLICK_ACTION_DIAL или неизвестное значение
                                // Действие по умолчанию: Открыть номер в звонилке
                                Log.d("WidgetProvider", "Defaulting to dialer action (ACTION_DIAL)")
                                startDialActivity(context, phoneNumber)
                            }
                        }
                    } catch (e: SecurityException) {
                        // Обработка SecurityException (например, при попытке ACTION_CALL без разрешения)
                        Log.e("WidgetProvider", "SecurityException during click action: ${e.message}", e)
                        Toast.makeText(context, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show()
                        startDialActivity(context, phoneNumber) // Fallback на звонилку
                    } catch (e: Exception) {
                        // Обработка других ошибок
                        Log.e("WidgetProvider", "Error processing click: ${e.message}", e)
                        Toast.makeText(context, R.string.error_processing_click, Toast.LENGTH_SHORT).show() // Используем строку из ресурсов
                    }
                } else {
                    // Обработка случая, когда номер телефона пуст или ID виджета невалидный
                    handleMissingData(phoneNumber, clickedWidgetId, contactId)
                }
            }
            ACTION_REFRESH_WIDGET -> {
                // Обработка нажатия кнопки "Обновить"
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Log.d("WidgetProvider", "Refresh action received for widget $widgetId")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    // Уведомляем систему, что данные для GridView изменились (запустит onDataSetChanged в Factory)
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_grid_view)
                    Toast.makeText(context, R.string.widget_refreshing, Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("WidgetProvider", "Refresh action received with invalid widget ID.")
                }
            }
            else -> {
                // Передаем обработку другим действиям (например, APPWIDGET_UPDATE) родительскому классу
                super.onReceive(context, intent)
            }
        }
    }

    // Вспомогательная функция для запуска стандартной звонилки (ACTION_DIAL)
    private fun startDialActivity(context: Context, phoneNumber: String) {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                // <<< ИЗМЕНЕНИЕ: Используем Uri.fromParts для корректной обработки '#' >>>
                data = Uri.fromParts("tel", phoneNumber, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (dialIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(dialIntent)
                Log.d("WidgetProvider", "ACTION_DIAL started.")
            } else {
                handleNoActivityFound(context, "ACTION_DIAL")
            }
        } catch (e: Exception) {
            Log.e("WidgetProvider", "Error starting ACTION_DIAL: ${e.message}", e)
            Toast.makeText(context, R.string.error_opening_dialer, Toast.LENGTH_SHORT).show() // Используем строку из ресурсов
        }
    }

    // Вспомогательная функция для обработки отсутствия Activity
    private fun handleNoActivityFound(context: Context, action: String) {
        Log.w("WidgetProvider", "No activity found to handle $action")
        Toast.makeText(context, R.string.error_action_app_not_found, Toast.LENGTH_SHORT).show() // Используем строку из ресурсов
    }

    // Вспомогательная функция для логирования отсутствующих данных
    private fun handleMissingData(phoneNumber: String?, widgetId: Int, contactId: Long) {
        if (phoneNumber.isNullOrEmpty()) {
            Log.w("WidgetProvider", "ACTION_CLICK_CONTACT ignored: Missing phone number! (WidgetID: $widgetId, ContactID: $contactId)")
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.w("WidgetProvider", "ACTION_CLICK_CONTACT ignored: Invalid widget ID! (PhoneNumber: $phoneNumber, ContactID: $contactId)")
        }
        // Можно добавить Toast для пользователя, если необходимо
        // Toast.makeText(context, "Ошибка: Недостаточно данных для обработки нажатия", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val ACTION_CLICK_CONTACT = "by.toxic.phonecontacts.ACTION_CLICK_CONTACT"
        const val ACTION_REFRESH_WIDGET = "by.toxic.phonecontacts.ACTION_REFRESH_WIDGET"
        // Константы для передачи данных в Intent
        const val EXTRA_PHONE_NUMBER = "by.toxic.phonecontacts.EXTRA_PHONE_NUMBER"
        const val EXTRA_CONTACT_NAME = "by.toxic.phonecontacts.EXTRA_CONTACT_NAME"
        const val EXTRA_PHOTO_URI = "by.toxic.phonecontacts.EXTRA_PHOTO_URI" // URI миниатюры контакта
        const val EXTRA_CONTACT_ID = "by.toxic.phonecontacts.EXTRA_CONTACT_ID" // ID контакта

        // Статический метод для обновления конкретного виджета (вызывается из конфигурации и onUpdate)
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d("WidgetProvider", "[static] Updating widget $appWidgetId...")

            // Загружаем количество колонок из настроек
            val columnCount = WidgetPrefsUtils.loadColumnCount(context, appWidgetId)
            // Выбираем layout в зависимости от количества колонок
            val layoutId = when (columnCount) {
                3 -> R.layout.widget_layout_3_cols
                4 -> R.layout.widget_layout_4_cols
                5 -> R.layout.widget_layout_5_cols
                6 -> R.layout.widget_layout_6_cols
                else -> R.layout.widget_layout_default // По умолчанию 4 колонки
            }
            Log.d("WidgetProvider", "[static] Widget $appWidgetId - Using layout ID: $layoutId for $columnCount columns")

            // Создаем RemoteViews для виджета
            val views = RemoteViews(context.packageName, layoutId)

            // Настраиваем адаптер для GridView
            val serviceIntent = Intent(context, ContactsGridWidgetService::class.java).apply {
                // Передаем ID виджета в Service, чтобы Factory знал, для какого виджета работать
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Установка уникального data URI предотвращает кэширование Intent разными виджетами
                data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_grid_view, serviceIntent)

            // Устанавливаем View для пустого состояния GridView
            views.setEmptyView(R.id.widget_grid_view, R.id.widget_empty_view)

            // Создаем шаблон PendingIntent для обработки кликов по элементам GridView
            val clickIntentTemplate = Intent(context, ContactsGridWidgetProvider::class.java).apply {
                action = ACTION_CLICK_CONTACT
                // ВАЖНО: Добавляем ID виджета в шаблон. Этот ID будет добавлен к fillInIntent
                // для каждого элемента GridView и доступен в onReceive.
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("widget_click://$appWidgetId") // Уникальный data для интента шаблона
            }
            // Устанавливаем флаги для PendingIntent
            val flagsTemplate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // MUTABLE нужен для fillInIntent
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            // Создаем PendingIntent-шаблон
            val clickPendingIntentTemplate = PendingIntent.getBroadcast(
                context,
                appWidgetId, // Используем ID виджета как requestCode для уникальности
                clickIntentTemplate,
                flagsTemplate
            )
            // Устанавливаем шаблон для GridView
            views.setPendingIntentTemplate(R.id.widget_grid_view, clickPendingIntentTemplate)


            // Настройка видимости и обработчиков для кнопок "Звонилка" и "Обновить"
            val showDialer = WidgetPrefsUtils.loadShowDialerButton(context, appWidgetId)
            val showRefresh = WidgetPrefsUtils.loadShowRefreshButton(context, appWidgetId)
            Log.d("WidgetProvider", "[static] Widget $appWidgetId - ShowDialer=$showDialer, ShowRefresh=$showRefresh")

            // Кнопка "Звонилка"
            views.setViewVisibility(R.id.widget_button_dialer, if (showDialer) View.VISIBLE else View.GONE)
            if (showDialer) {
                val dialerIntent = Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                // Используем уникальный requestCode для этого PendingIntent
                val dialerRequestCode = -appWidgetId - 1 // Пример уникального кода
                val flagsDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val dialerPendingIntent = PendingIntent.getActivity(context, dialerRequestCode, dialerIntent, flagsDialer)
                views.setOnClickPendingIntent(R.id.widget_button_dialer, dialerPendingIntent)
                Log.d("WidgetProvider", "[static] Dialer button setup for widget $appWidgetId")
            }

            // Кнопка "Обновить"
            views.setViewVisibility(R.id.widget_button_refresh, if (showRefresh) View.VISIBLE else View.GONE)
            if (showRefresh) {
                val refreshIntent = Intent(context, ContactsGridWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("widget_refresh://$appWidgetId") // Уникальный data URI
                }
                // Используем другой уникальный requestCode
                val refreshRequestCode = appWidgetId + 1
                val flagsRefresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(context, refreshRequestCode, refreshIntent, flagsRefresh)
                views.setOnClickPendingIntent(R.id.widget_button_refresh, refreshPendingIntent)
                Log.d("WidgetProvider", "[static] Refresh button setup for widget $appWidgetId")
            }


            // Обновляем виджет с помощью AppWidgetManager
            try {
                // Сначала обновляем сам виджет
                appWidgetManager.updateAppWidget(appWidgetId, views)
                // Затем уведомляем об изменении данных в GridView (если это необходимо после updateAppWidget)
                // Это может быть избыточно, но иногда помогает обновить коллекцию
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_grid_view)
                Log.d("WidgetProvider", "[static] Widget $appWidgetId update commands sent.")
            } catch (e: Exception) {
                Log.e("WidgetProvider", "[static] Error updating widget $appWidgetId: ${e.message}", e)
            }
        }
    }
}