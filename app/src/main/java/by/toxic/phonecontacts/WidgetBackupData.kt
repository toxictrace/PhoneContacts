package by.toxic.phonecontacts

import kotlinx.serialization.Serializable

/**
 * Data class для хранения настроек одного конкретного виджета при бэкапе/восстановлении.
 * Используется для сериализации в JSON.
 *
 * @property contactUris Список Content URI избранных контактов в виде строк.
 * @property columnCount Количество колонок в сетке виджета.
 * @property clickAction Действие по клику на контакт (см. `WidgetPrefsUtils.CLICK_ACTION_*`).
 * @property sortOrder Порядок сортировки контактов (см. `WidgetPrefsUtils.SORT_ORDER_*`).
 * @property maxItems Максимальное количество отображаемых контактов.
 * @property itemHeight Высота элемента сетки в dp.
 * @property showUnknown Флаг, показывать ли неизвестные номера.
 * @property showDialerButton Флаг, показывать ли кнопку звонилки.
 * @property showRefreshButton Флаг, показывать ли кнопку обновления.
 * @property filterOldUnknown Флаг, фильтровать ли старые неизвестные номера.
 * @property compareDigitsCount Количество последних цифр для сравнения номеров в журнале звонков.
 * @property unknownAvatarMode Режим аватара для неизвестных контактов (см. `WidgetPrefsUtils.AVATAR_MODE_*`).
 * @property unknownAvatarCustomUri Строковое представление URI кастомного аватара (обычно имя файла в архиве бэкапа) или null.
 */
@Serializable
data class WidgetBackupData(
    val contactUris: List<String>,
    val columnCount: Int,
    val clickAction: String,
    val sortOrder: String,
    val maxItems: Int,
    val itemHeight: Int,
    val showUnknown: Boolean,
    val showDialerButton: Boolean,
    val showRefreshButton: Boolean,
    val filterOldUnknown: Boolean,
    val compareDigitsCount: Int,
    val unknownAvatarMode: String,
    val unknownAvatarCustomUri: String? // Nullable, т.к. может не быть кастомного аватара
)

/**
 * Корневой data class для хранения данных бэкапа всего приложения.
 * Содержит карту настроек для каждого активного виджета на момент бэкапа.
 *
 * @property widgetSettings Карта, где ключ - ID виджета (`appWidgetId`), значение - `WidgetBackupData` для этого виджета.
 */
@Serializable
data class AppBackup(
    val widgetSettings: Map<Int, WidgetBackupData>
)