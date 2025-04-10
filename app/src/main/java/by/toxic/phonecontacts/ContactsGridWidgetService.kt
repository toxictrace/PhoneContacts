package by.toxic.phonecontacts

import android.content.Intent
import android.widget.RemoteViewsService

class ContactsGridWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        // Передаем Intent в Factory, он содержит appWidgetId
        return ContactsGridRemoteViewsFactory(this.applicationContext, intent)
    }
}