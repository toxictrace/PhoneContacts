package by.toxic.phonecontacts

import android.content.Intent
import android.util.Log // <<< Добавлен импорт >>>
import android.widget.RemoteViewsService

/**
 * Сервис, предоставляющий RemoteViewsFactory для GridView в виджете контактов.
 * Этот сервис запускается системой Android, когда виджету требуется обновить
 * содержимое его коллекции (GridView).
 */
class ContactsGridWidgetService : RemoteViewsService() {

    // TAG для логов
    private companion object {
        const val TAG = "ContactsGridWidgetSvc"
    }

    /**
     * Этот метод вызывается системой для получения экземпляра RemoteViewsFactory,
     * который будет отвечать за создание и предоставление данных для каждого
     * элемента в GridView виджета.
     *
     * @param intent Intent, который был использован для запуска этого сервиса.
     *               Он содержит важные данные, такие как EXTRA_APPWIDGET_ID,
     *               необходимые для Factory, чтобы знать, для какого виджета
     *               загружать данные и настройки.
     * @return Экземпляр нашего [ContactsGridRemoteViewsFactory].
     */
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        Log.d(TAG, "Запрос Factory для widget ID: $widgetId")
        // Создаем и возвращаем наш Factory, передавая ему контекст приложения
        // (через this.applicationContext) и Intent, который содержит ID виджета.
        // Factory будет использовать этот ID для загрузки настроек и данных
        // конкретного экземпляра виджета.
        return ContactsGridRemoteViewsFactory(this.applicationContext, intent)
    }
}