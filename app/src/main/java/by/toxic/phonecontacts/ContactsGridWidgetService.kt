package by.toxic.phonecontacts

import android.content.Intent
import android.widget.RemoteViewsService

class ContactsGridWidgetService : RemoteViewsService() {

    /**
     * Этот метод вызывается системой, когда виджету требуется новый RemoteViewsFactory.
     * RemoteViewsFactory отвечает за создание и предоставление RemoteViews для каждого
     * элемента в коллекции (GridView в нашем случае).
     *
     * @param intent Intent, который был использован для запуска этого сервиса.
     *               Он содержит важные данные, такие как EXTRA_APPWIDGET_ID.
     * @return Экземпляр нашего RemoteViewsFactory.
     */
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        // Создаем и возвращаем наш Factory, передавая ему контекст приложения
        // и Intent, который содержит ID виджета (EXTRA_APPWIDGET_ID).
        // Factory будет использовать этот ID для загрузки настроек и данных
        // конкретного экземпляра виджета.
        return ContactsGridRemoteViewsFactory(this.applicationContext, intent)
    }
}