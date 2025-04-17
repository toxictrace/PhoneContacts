package by.toxic.phonecontacts

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat

class ContactsGridWidgetProvider : AppWidgetProvider() {

    // TAG для логов
    // <<< ИЗМЕНЕНИЕ: Убираем private >>>
    companion object {
        const val TAG = "WidgetProvider"
        // Действия для Intent'ов
        const val ACTION_CLICK_CONTACT = "by.toxic.phonecontacts.ACTION_CLICK_CONTACT"
        const val ACTION_REFRESH_WIDGET = "by.toxic.phonecontacts.ACTION_REFRESH_WIDGET"
        // Ключи для передачи данных в Intent
        const val EXTRA_PHONE_NUMBER = "by.toxic.phonecontacts.EXTRA_PHONE_NUMBER"
        const val EXTRA_CONTACT_NAME = "by.toxic.phonecontacts.EXTRA_CONTACT_NAME"
        const val EXTRA_PHOTO_URI = "by.toxic.phonecontacts.EXTRA_PHOTO_URI" // URI миниатюры
        const val EXTRA_CONTACT_ID = "by.toxic.phonecontacts.EXTRA_CONTACT_ID" // ID контакта

        /**
         * Обновляет внешний вид и данные конкретного экземпляра виджета.
         * Вызывается из onUpdate, а также из WidgetConfigureActivity после сохранения настроек.
         * <<< ИЗМЕНЕНИЕ: Делаем public или internal, если нужен доступ из других модулей >>>
         * internal - доступен внутри модуля app
         */
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Log.d(TAG, "[static] Обновление widget $appWidgetId...")
            // Используем applicationContext для PendingIntent'ов
            val appContext = context.applicationContext

            // 1. Загружаем количество колонок и выбираем соответствующий макет
            val columnCount = WidgetPrefsUtils.loadColumnCount(appContext, appWidgetId)
            val layoutId = when (columnCount) {
                3 -> R.layout.widget_layout_3_cols
                4 -> R.layout.widget_layout_4_cols
                5 -> R.layout.widget_layout_5_cols
                6 -> R.layout.widget_layout_6_cols
                else -> { // По умолчанию или если значение некорректно
                    Log.w(TAG, "[static] Неверное количество колонок ($columnCount) для widget $appWidgetId. Используется макет по умолчанию.")
                    R.layout.widget_layout_default
                }
            }
            Log.d(TAG, "[static] Widget $appWidgetId - Используется макет ID: $layoutId для $columnCount колонок")

            // 2. Создаем RemoteViews для виджета
            val views = RemoteViews(appContext.packageName, layoutId)

            // 3. Настраиваем адаптер для GridView через RemoteViewsService
            val serviceIntent = Intent(appContext, ContactsGridWidgetService::class.java).apply {
                // Передаем ID виджета в Service, чтобы Factory знал, для какого виджета работать
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Установка уникального data URI предотвращает кэширование Intent'а системой
                // для разных виджетов, использующих один и тот же Service.
                data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_grid_view, serviceIntent)

            // 4. Устанавливаем View для пустого состояния GridView
            views.setEmptyView(R.id.widget_grid_view, R.id.widget_empty_view) // ID из макетов widget_layout_*.xml

            // 5. Создаем шаблон PendingIntent для обработки кликов по элементам GridView
            val clickIntentTemplate = Intent(appContext, ContactsGridWidgetProvider::class.java).apply {
                action = ACTION_CLICK_CONTACT
                // ВАЖНО: Добавляем ID виджета в шаблон. Этот ID будет добавлен к fillInIntent
                // для каждого элемента GridView и доступен в onReceive провайдера.
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                // Уникальный data для интента-шаблона, чтобы система различала шаблоны для разных виджетов
                data = Uri.parse("widget_click://$appWidgetId/${System.currentTimeMillis()}") // Добавляем время для уникальности
            }
            // Определяем флаги для PendingIntent в зависимости от версии Android
            val flagsTemplate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // MUTABLE нужен, так как система будет добавлять fillInIntent
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            // Создаем PendingIntent-шаблон (тип Broadcast)
            val clickPendingIntentTemplate = PendingIntent.getBroadcast(
                appContext,
                appWidgetId, // Используем ID виджета как requestCode для уникальности PendingIntent'а
                clickIntentTemplate,
                flagsTemplate
            )
            // Устанавливаем шаблон для GridView
            views.setPendingIntentTemplate(R.id.widget_grid_view, clickPendingIntentTemplate)


            // 6. Настройка видимости и обработчиков для кнопок "Звонилка" и "Обновить"
            val showDialer = WidgetPrefsUtils.loadShowDialerButton(appContext, appWidgetId)
            val showRefresh = WidgetPrefsUtils.loadShowRefreshButton(appContext, appWidgetId)
            Log.d(TAG, "[static] Widget $appWidgetId - ShowDialer=$showDialer, ShowRefresh=$showRefresh")

            // Кнопка "Звонилка" (открывает журнал вызовов)
            views.setViewVisibility(R.id.widget_button_dialer, if (showDialer) View.VISIBLE else View.GONE)
            if (showDialer) {
                val dialerIntent = Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Флаг для запуска из виджета
                }
                // Уникальный requestCode для этого PendingIntent (отрицательный, чтобы не пересекаться с requestCode для clickIntentTemplate)
                val dialerRequestCode = appWidgetId * -1 - 1
                val flagsDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val dialerPendingIntent = PendingIntent.getActivity(appContext, dialerRequestCode, dialerIntent, flagsDialer)
                views.setOnClickPendingIntent(R.id.widget_button_dialer, dialerPendingIntent)
                Log.d(TAG, "[static] Кнопка звонилки настроена для widget $appWidgetId")
            }

            // Кнопка "Обновить"
            views.setViewVisibility(R.id.widget_button_refresh, if (showRefresh) View.VISIBLE else View.GONE)
            if (showRefresh) {
                val refreshIntent = Intent(appContext, ContactsGridWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("widget_refresh://$appWidgetId/${System.currentTimeMillis()}") // Уникальный data URI
                }
                // Другой уникальный requestCode (положительный, но отличный от appWidgetId)
                val refreshRequestCode = appWidgetId + 1000 // Простое смещение для уникальности
                val flagsRefresh = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Может быть IMMUTABLE, т.к. данные уже в Intent'е
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(appContext, refreshRequestCode, refreshIntent, flagsRefresh)
                views.setOnClickPendingIntent(R.id.widget_button_refresh, refreshPendingIntent)
                Log.d(TAG, "[static] Кнопка обновления настроена для widget $appWidgetId")
            }


            // 7. Обновляем виджет с помощью AppWidgetManager
            try {
                // Сначала обновляем сам виджет (макет, кнопки)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                // Затем уведомляем об изменении данных в GridView (это запустит onDataSetChanged в Factory)
                // Это может быть избыточно сразу после updateAppWidget, но гарантирует обновление коллекции.
                // Система может объединить эти вызовы.
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_grid_view)
                Log.d(TAG, "[static] Команды updateAppWidget и notifyAppWidgetViewDataChanged отправлены для widget $appWidgetId.")
            } catch (e: Exception) {
                Log.e(TAG, "[static] Ошибка при обновлении widget $appWidgetId: ${e.message}", e)
                // Здесь можно попробовать восстановить предыдущее состояние или показать ошибку
            }
        }
    } // <<< Закрывающая скобка для companion object

    // --- Lifecycle Methods ---

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate вызван для виджетов: ${appWidgetIds.joinToString()}")
        // Обновляем каждый виджет, для которого пришло событие
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted вызван для виджетов: ${appWidgetIds.joinToString()}")
        // Удаляем настройки и связанные файлы (включая кастомные аватары) для удаленных виджетов
        WidgetPrefsUtils.deleteWidgetConfig(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    // --- Event Handling ---

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: "null" // Безопасное получение действия
        Log.d(TAG, "onReceive получено действие: $action")

        // Используем when для обработки известных действий
        when (action) {
            ACTION_CLICK_CONTACT -> handleClickAction(context, intent)
            ACTION_REFRESH_WIDGET -> handleRefreshAction(context, intent)
            // Для всех остальных действий (например, стандартных AppWidgetManager.ACTION_*)
            // вызываем реализацию родительского класса
            else -> {
                Log.v(TAG, "Действие '$action' передано в super.onReceive")
                try {
                    super.onReceive(context, intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Исключение в super.onReceive для действия '$action'", e)
                }
            }
        }
    }


    // Обрабатывает клик по контакту в виджете
    private fun handleClickAction(context: Context, intent: Intent) {
        Log.d(TAG, "Обработка действия: ACTION_CLICK_CONTACT")

        // Извлекаем данные, переданные через fillInIntent из Factory
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        val photoUriString = intent.getStringExtra(EXTRA_PHOTO_URI)
        val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
        // Получаем ID виджета из Intent'а-шаблона
        val clickedWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        Log.d(TAG, "Извлечены данные: PN=$phoneNumber, Name=$contactName, PhotoUri=$photoUriString, ContactID=$contactId, WidgetID=$clickedWidgetId")

        // Проверяем наличие номера и ID виджета
        if (phoneNumber.isNullOrEmpty() || clickedWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            handleMissingData(context, phoneNumber, clickedWidgetId, contactId)
            return // Прерываем обработку, если данных не хватает
        }

        // Загружаем настройку действия по клику для данного виджета
        val clickAction = WidgetPrefsUtils.loadClickAction(context, clickedWidgetId)
        Log.d(TAG, "Действие по клику для widget $clickedWidgetId: $clickAction")

        try {
            when (clickAction) {
                WidgetPrefsUtils.CLICK_ACTION_CALL -> performDirectCall(context, phoneNumber)
                WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL -> startCallConfirmActivity(context, clickedWidgetId, phoneNumber, contactName, photoUriString, contactId)
                // WidgetPrefsUtils.CLICK_ACTION_DIAL или любое неизвестное значение
                else -> startDialActivity(context, phoneNumber)
            }
        } catch (e: SecurityException) {
            // Обработка SecurityException (например, при попытке ACTION_CALL без разрешения)
            Log.e(TAG, "SecurityException при выполнении действия по клику: ${e.message}", e)
            Toast.makeText(context, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show()
            startDialActivity(context, phoneNumber) // Fallback на звонилку
        } catch (e: Exception) {
            // Обработка других ошибок
            Log.e(TAG, "Ошибка обработки клика: ${e.message}", e)
            Toast.makeText(context, R.string.error_processing_click, Toast.LENGTH_SHORT).show()
        }
    }

    // Обрабатывает клик по кнопке "Обновить"
    private fun handleRefreshAction(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.d(TAG, "Получено действие ACTION_REFRESH_WIDGET для widget $widgetId")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            // Уведомляем систему, что данные для GridView изменились
            // Это запустит onDataSetChanged в ContactsGridRemoteViewsFactory
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_grid_view)
            Toast.makeText(context, R.string.widget_refreshing, Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Получено действие ACTION_REFRESH_WIDGET с невалидным widget ID.")
        }
    }


    // --- Вспомогательные функции для действий ---

    // Выполняет прямой звонок (ACTION_CALL)
    private fun performDirectCall(context: Context, phoneNumber: String) {
        Log.d(TAG, "Попытка прямого звонка (ACTION_CALL) на номер $phoneNumber")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.fromParts("tel", phoneNumber, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Важно для запуска из BroadcastReceiver
            }
            if (callIntent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(callIntent)
                    Log.d(TAG, "ACTION_CALL запущен.")
                } catch (se: SecurityException) {
                    Log.e(TAG, "SecurityException при запуске ACTION_CALL (неожиданно, т.к. разрешение проверено): ${se.message}")
                    Toast.makeText(context, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show()
                    startDialActivity(context, phoneNumber)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception при запуске ACTION_CALL: ${e.message}")
                    Toast.makeText(context, R.string.error_call_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                handleNoActivityFound(context, "ACTION_CALL")
            }
        } else {
            // Если разрешение отозвано после настройки виджета
            Log.w(TAG, "Отсутствует разрешение CALL_PHONE. Переход к ACTION_DIAL.")
            Toast.makeText(context, R.string.call_permission_revoked, Toast.LENGTH_LONG).show()
            startDialActivity(context, phoneNumber) // Fallback на звонилку
        }
    }

    // Запускает Activity подтверждения звонка
    private fun startCallConfirmActivity(context: Context, widgetId: Int, phoneNumber: String?, contactName: String?, photoUriString: String?, contactId: Long) {
        Log.d(TAG, "Запуск CallConfirmActivity...")
        val confirmIntent = Intent(context, CallConfirmActivity::class.java).apply {
            putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(EXTRA_CONTACT_NAME, contactName)
            putExtra(EXTRA_PHOTO_URI, photoUriString)
            putExtra(EXTRA_CONTACT_ID, contactId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId) // Передаем ID виджета!
            // Флаги для запуска новой задачи и очистки предыдущей (если она была того же типа)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        if (confirmIntent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(confirmIntent)
                Log.d(TAG, "CallConfirmActivity запущена.")
            } catch (e: Exception) {
                Log.e(TAG, "Exception при запуске CallConfirmActivity: ${e.message}")
                Toast.makeText(context, R.string.error_opening_confirmation, Toast.LENGTH_SHORT).show() // <<< НУЖНА НОВАЯ СТРОКА
            }
        } else {
            handleNoActivityFound(context, "CallConfirmActivity")
        }
    }

    // Запускает стандартную звонилку (ACTION_DIAL)
    private fun startDialActivity(context: Context, phoneNumber: String?) {
        if (phoneNumber.isNullOrEmpty()) {
            Log.w(TAG, "Невозможно открыть звонилку, номер пуст.")
            Toast.makeText(context, R.string.error_phone_number_missing, Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "Открытие звонилки (ACTION_DIAL) с номером $phoneNumber")
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.fromParts("tel", phoneNumber, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (dialIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(dialIntent)
                Log.d(TAG, "ACTION_DIAL запущен.")
            } else {
                handleNoActivityFound(context, "ACTION_DIAL")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска ACTION_DIAL: ${e.message}", e)
            Toast.makeText(context, R.string.error_opening_dialer, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Обработка ошибок и отсутствия данных ---

    // Обрабатывает ситуацию, когда не найдено Activity для Intent'а
    private fun handleNoActivityFound(context: Context, actionDescription: String) {
        Log.w(TAG, "Не найдено Activity для обработки действия '$actionDescription'")
        Toast.makeText(context, R.string.error_action_app_not_found, Toast.LENGTH_SHORT).show()
    }

    // Обрабатывает ситуацию, когда не хватает данных для клика
    private fun handleMissingData(context: Context, phoneNumber: String?, widgetId: Int, contactId: Long) {
        var errorMessage = context.getString(R.string.error_processing_click) + ": " // <<< Используем ресурс
        if (phoneNumber.isNullOrEmpty()) {
            errorMessage += context.getString(R.string.error_phone_number_missing) + " " // <<< Используем ресурс
            Log.w(TAG, "ACTION_CLICK_CONTACT проигнорирован: Отсутствует номер! (WidgetID: $widgetId, ContactID: $contactId)")
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            errorMessage += context.getString(R.string.error_widget_id_missing) + " " // <<< Используем ресурс
            Log.w(TAG, "ACTION_CLICK_CONTACT проигнорирован: Неверный ID виджета! (PhoneNumber: $phoneNumber, ContactID: $contactId)")
        }
        Toast.makeText(context, errorMessage.trim(), Toast.LENGTH_LONG).show() // Показываем более детальную ошибку
    }

} // Конец класса ContactsGridWidgetProvider