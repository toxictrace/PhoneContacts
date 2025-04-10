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

    override fun onUpdate( context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray ) {
        Log.d("WidgetProvider", "onUpdate called for widgets: ${appWidgetIds.joinToString()}")
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted( context: Context, appWidgetIds: IntArray ) {
        Log.d("WidgetProvider", "onDeleted called for widgets: ${appWidgetIds.joinToString()}")
        WidgetPrefsUtils.deleteWidgetConfig(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WidgetProvider", "onReceive triggered. Action: ${intent.action}")

        when (intent.action) {
            ACTION_CLICK_CONTACT -> {
                Log.d("WidgetProvider", "Action is ACTION_CLICK_CONTACT")
                val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
                val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
                val photoUriString = intent.getStringExtra(EXTRA_PHOTO_URI)
                val contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1L)
                val clickedWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                Log.d("WidgetProvider", "Extracted PN: $phoneNumber, Name: $contactName, PhotoUri: $photoUriString, ContactID: $contactId from widget $clickedWidgetId")

                if (!phoneNumber.isNullOrEmpty() && clickedWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val clickAction = WidgetPrefsUtils.loadClickAction(context, clickedWidgetId)
                    Log.d("WidgetProvider", "Click action preference for widget $clickedWidgetId: $clickAction")
                    try {
                        when (clickAction) {
                            WidgetPrefsUtils.CLICK_ACTION_CALL -> {
                                Log.d("WidgetProvider", "Attempting direct call (ACTION_CALL)")
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                                    val callIntent = Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:$phoneNumber"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    if (callIntent.resolveActivity(context.packageManager) != null) { context.startActivity(callIntent); Log.d("WidgetProvider", "ACTION_CALL started.") }
                                    else { handleNoActivityFound(context, "ACTION_CALL") }
                                } else { Log.w("WidgetProvider", "CALL_PHONE permission missing. Falling back to ACTION_DIAL."); Toast.makeText(context, R.string.call_permission_revoked, Toast.LENGTH_LONG).show(); startDialActivity(context, phoneNumber) }
                            }
                            WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL -> {
                                Log.d("WidgetProvider", "Starting CallConfirmActivity...")
                                val confirmIntent = Intent(context, CallConfirmActivity::class.java).apply { putExtra(EXTRA_PHONE_NUMBER, phoneNumber); putExtra(EXTRA_CONTACT_NAME, contactName); putExtra(EXTRA_PHOTO_URI, photoUriString); putExtra(EXTRA_CONTACT_ID, contactId); flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                                if (confirmIntent.resolveActivity(context.packageManager) != null) { context.startActivity(confirmIntent); Log.d("WidgetProvider", "CallConfirmActivity started.") }
                                else { handleNoActivityFound(context, "CallConfirmActivity") }
                            }
                            else -> { Log.d("WidgetProvider", "Defaulting to dialer action (ACTION_DIAL)"); startDialActivity(context, phoneNumber) }
                        }
                    } catch (e: SecurityException) { Log.e("WidgetProvider", "SecurityException: ${e.message}", e); Toast.makeText(context, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show(); startDialActivity(context, phoneNumber) }
                    catch (e: Exception) { Log.e("WidgetProvider", "Error processing click: ${e.message}", e); Toast.makeText(context, "Ошибка обработки нажатия", Toast.LENGTH_SHORT).show() }
                } else {
                    handleMissingData(phoneNumber, clickedWidgetId, contactId)
                }
            }
            // <<<--- РАСКОММЕНТИРОВАНА И ДОПОЛНЕНА ОБРАБОТКА REFRESH ---<<<
            ACTION_REFRESH_WIDGET -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Log.d("WidgetProvider", "Refresh action received for widget $widgetId")
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    // Просто уведомляем GridView об изменении данных
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_grid_view)
                    Toast.makeText(context, R.string.widget_refreshing, Toast.LENGTH_SHORT).show()
                } else {
                    Log.w("WidgetProvider", "Refresh action received with invalid widget ID")
                }
            }
            else -> {
                super.onReceive(context, intent)
            }
        }
    }

    private fun startDialActivity(context: Context, phoneNumber: String) { try { val dI=Intent(Intent.ACTION_DIAL).apply{data=Uri.parse("tel:$phoneNumber");addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}; if(dI.resolveActivity(context.packageManager)!=null){context.startActivity(dI);Log.d("WidgetProvider","ACTION_DIAL started.")}else{handleNoActivityFound(context,"ACTION_DIAL")} } catch(e:Exception){Log.e("WidgetProvider","Error ACTION_DIAL: ${e.message}",e);Toast.makeText(context,"Не удалось открыть звонилку", Toast.LENGTH_SHORT).show()} }
    private fun handleNoActivityFound(context: Context, action: String) { Log.w("WidgetProvider", "No activity for $action"); Toast.makeText(context, "Приложение не найдено", Toast.LENGTH_SHORT).show() }
    private fun handleMissingData(phoneNumber: String?, widgetId: Int, contactId: Long) { if(phoneNumber.isNullOrEmpty())Log.w("WidgetProvider","Missing PN!(W:$widgetId,C:$contactId)"); if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID)Log.w("WidgetProvider","Invalid widget ID!(C:$contactId)") }

    companion object {
        const val ACTION_CLICK_CONTACT = "by.toxic.phonecontacts.ACTION_CLICK_CONTACT"
        const val ACTION_REFRESH_WIDGET = "by.toxic.phonecontacts.ACTION_REFRESH_WIDGET"
        const val EXTRA_PHONE_NUMBER = "by.toxic.phonecontacts.EXTRA_PHONE_NUMBER"
        const val EXTRA_CONTACT_NAME = "by.toxic.phonecontacts.EXTRA_CONTACT_NAME"
        const val EXTRA_PHOTO_URI = "by.toxic.phonecontacts.EXTRA_PHOTO_URI"
        const val EXTRA_CONTACT_ID = "by.toxic.phonecontacts.EXTRA_CONTACT_ID"

        internal fun updateAppWidget( context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int ) {
            Log.d("WidgetProvider", "[static] Updating widget $appWidgetId (Stage 3: Button Logic)") // Обновили лог
            val columnCount = WidgetPrefsUtils.loadColumnCount(context, appWidgetId)
            val layoutId = when (columnCount) { 3 -> R.layout.widget_layout_3_cols; 4 -> R.layout.widget_layout_4_cols; 5 -> R.layout.widget_layout_5_cols; 6 -> R.layout.widget_layout_6_cols; else -> R.layout.widget_layout_default }
            Log.d("WidgetProvider", "[static] Widget $appWidgetId - using layout ID: $layoutId")

            val views = RemoteViews(context.packageName, layoutId)
            val serviceIntent = Intent(context, ContactsGridWidgetService::class.java).apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId); data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME)) }
            views.setRemoteAdapter(R.id.widget_grid_view, serviceIntent)
            views.setEmptyView(R.id.widget_grid_view, R.id.widget_empty_view)

            val clickIntentTemplate = Intent(context, ContactsGridWidgetProvider::class.java).apply { action = ACTION_CLICK_CONTACT; putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId); data = Uri.parse("widget_click://$appWidgetId") }
            val flagsTemplate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE } else { PendingIntent.FLAG_UPDATE_CURRENT }
            val clickPendingIntentTemplate = PendingIntent.getBroadcast(context, appWidgetId, clickIntentTemplate, flagsTemplate)
            views.setPendingIntentTemplate(R.id.widget_grid_view, clickPendingIntentTemplate)

            // <<<--- РАСКОММЕНТИРОВАНО и ДОПОЛНЕНО ---<<<
            val showDialer = WidgetPrefsUtils.loadShowDialerButton(context, appWidgetId)
            val showRefresh = WidgetPrefsUtils.loadShowRefreshButton(context, appWidgetId)
            Log.d("WidgetProvider", "[static] Widget $appWidgetId - ShowDialer=$showDialer, ShowRefresh=$showRefresh")

            // Управляем видимостью кнопки "Звонилка"
            views.setViewVisibility(R.id.widget_button_dialer, if (showDialer) View.VISIBLE else View.GONE)
            if (showDialer) {
                // Intent для открытия журнала звонков
                val dialerIntent = Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI)
                // Добавляем флаг, т.к. запускаем Activity извне (из виджета)
                dialerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // PendingIntent для запуска Activity
                val flagsDialer = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                // Используем уникальный requestCode (отрицательный ID виджета), чтобы PendingIntent'ы не пересекались
                val dialerPendingIntent = PendingIntent.getActivity(context, -appWidgetId - 1, dialerIntent, flagsDialer)
                // Устанавливаем обработчик клика
                views.setOnClickPendingIntent(R.id.widget_button_dialer, dialerPendingIntent)
                Log.d("WidgetProvider", "[static] Dialer button setup for widget $appWidgetId")
            }

            // Управляем видимостью кнопки "Обновить"
            views.setViewVisibility(R.id.widget_button_refresh, if (showRefresh) View.VISIBLE else View.GONE)
            if (showRefresh) {
                // Intent для отправки broadcast нашему же провайдеру
                val refreshIntent = Intent(context, ContactsGridWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET // Наше кастомное действие
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) // Передаем ID виджета
                    // Уникальный data URI, чтобы этот PendingIntent отличался от других
                    data = Uri.parse("widget_refresh://$appWidgetId")
                }
                val flagsRefresh = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                // Используем уникальный requestCode (ID виджета + 1)
                val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId + 1, refreshIntent, flagsRefresh)
                // Устанавливаем обработчик клика
                views.setOnClickPendingIntent(R.id.widget_button_refresh, refreshPendingIntent)
                Log.d("WidgetProvider", "[static] Refresh button setup for widget $appWidgetId")
            }
            // <<<------------------------------------->>>

            try {
                appWidgetManager.updateAppWidget(appWidgetId, views)
                // Уведомление адаптера все еще нужно, если данные могли измениться с момента последнего обновления
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_grid_view)
                // Второй update можно убрать, т.к. первый уже применит все изменения RemoteViews
                // appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d("WidgetProvider", "[static] Widget $appWidgetId update commands sent (final).")
            } catch (e: Exception) { Log.e("WidgetProvider", "[static] Error updating widget $appWidgetId: ${e.message}", e) }
        }
    }
}