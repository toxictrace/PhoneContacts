package by.toxic.phonecontacts

import android.net.Uri
import android.provider.ContactsContract // Импорт для ContactsContract

data class ContactItem(
    val id: Long,         // ID контакта в системе
    val lookupKey: String, // Более стабильный ключ для поиска контакта
    val name: String?,     // Имя контакта
    val photoUri: String?, // URI **миниатюры** фото контакта (строка) из ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
    val numbers: List<String>, // Список номеров телефона
    var isSelected: Boolean = false // Для экрана конфигурации
) {
    // Стабильный URI для использования в виджете и сохранения в SharedPreferences
    // Использует LOOKUP_KEY для надежности
    val contentUri: String?
        get() = lookupKey?.let { Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, it).toString() }
}