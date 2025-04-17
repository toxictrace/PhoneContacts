package by.toxic.phonecontacts

import android.net.Uri
import android.provider.ContactsContract // Импорт для ContactsContract

/**
 * Data class для представления контакта в приложении.
 *
 * @property id ID контакта из ContactsContract.Contacts._ID.
 * @property lookupKey Стабильный ключ для поиска контакта (ContactsContract.Contacts.LOOKUP_KEY).
 * @property name Отображаемое имя контакта (ContactsContract.Contacts.DISPLAY_NAME_PRIMARY).
 * @property photoUri URI **миниатюры** фото контакта (ContactsContract.Contacts.PHOTO_THUMBNAIL_URI) в виде строки. Может быть null.
 * @property numbers Список номеров телефона контакта.
 * @property isSelected Флаг для использования на экране конфигурации (выбран ли контакт).
 */
data class ContactItem(
    val id: Long,
    val lookupKey: String, // lookupKey не должен быть null для существующих контактов
    val name: String?,
    val photoUri: String?,
    val numbers: List<String>,
    var isSelected: Boolean = false
) {
    /**
     * Стабильный Content URI для данного контакта.
     * Использует LOOKUP_KEY для получения URI, который должен оставаться действительным
     * даже если внутренний ID контакта изменится (например, после синхронизации).
     * Возвращает null, если lookupKey пустой (хотя это маловероятно для реальных контактов).
     * Используется для сохранения ссылок на контакты в SharedPreferences и для Intent'ов.
     */
    val contentUri: String?
        get() = if (lookupKey.isNotEmpty()) {
            // Генерируем lookup URI: content://com.android.contacts/contacts/lookup/<lookupKey>
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey).toString()
        } else {
            null // Вряд ли lookupKey будет пустым, но на всякий случай
        }
}

// ========================================================================
// <<< УДАЛИТЬ ВЕСЬ КОД НИЖЕ, ЕСЛИ ОН ЗДЕСЬ ЕСТЬ (ОН ОТНОСИТСЯ К ДРУГИМ ФАЙЛАМ) >>>
// Например, если здесь есть объявление class ContactsGridRemoteViewsFactory(...)
// или его методы (loadFavoriteContacts, getViewAt и т.д.) - их нужно удалить.
// Ошибки типа:
// e: file:///C:/Users/toxic/AndroidStudioProjects/PhoneContacts/app/src/main/java/by/toxic/phonecontacts/ContactItem.kt:35:7 Redeclaration: ContactsGridRemoteViewsFactory
// e: file:///C:/Users/toxic/AndroidStudioProjects/PhoneContacts/app/src/main/java/by/toxic/phonecontacts/ContactItem.kt:606:70 Smart cast to 'Long' is impossible, because 'pair.second' is a public API property declared in different module
// e: file:///C:/Users/toxic/AndroidStudioProjects/PhoneContacts/app/src/main/java/by/toxic/phonecontacts/ContactItem.kt:661:101 Smart cast to 'Long' is impossible, because 'pair.second' is a public API property declared in different module
// e: file:///C:/Users/toxic/AndroidStudioProjects/PhoneContacts/app/src/main/java/by/toxic/phonecontacts/ContactItem.kt:962:61 Cannot access 'Companion': it is private in 'ContactsGridWidgetProvider'
// e: file:///C:/Users/toxic/AndroidStudioProjects/PhoneContacts/app/src/main/java/by/toxic/phonecontacts/ContactItem.kt:963:61 Cannot access 'Companion': it is private in 'ContactsGridWidgetProvider'
// e: file:///C:/Users/toxic/AndroidStudioProjects/PhoneContacts/app/src/main/java/by/toxic/phonecontacts/ContactItem.kt:964:61 Cannot access 'Companion': it is private in 'ContactsGridWidgetProvider'
// e: file:///C:/Users/toxic/AndroidStudioProjects/PhoneContacts/app/src/main/java/by/toxic/phonecontacts/ContactItem.kt:965:59 Cannot access 'Companion': it is private in 'ContactsGridWidgetProvider'
// говорят о том, что здесь есть лишний код.
// ========================================================================