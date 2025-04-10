package by.toxic.phonecontacts

import android.net.Uri

data class ContactItem(
    val id: Long,         // ID контакта в системе
    val lookupKey: String, // Более стабильный ключ для поиска контакта
    val name: String?,     // Имя контакта
    val photoUri: String?, // URI фото контакта (строка)
    val numbers: List<String>, // Список номеров телефона
    var isSelected: Boolean = false // Для экрана конфигурации
) {
    // Стабильный URI для использования в виджете
    val contentUri: String?
        get() = lookupKey?.let { Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI, it).toString() }
}