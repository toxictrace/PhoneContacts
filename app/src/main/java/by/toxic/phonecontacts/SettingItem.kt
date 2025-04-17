package by.toxic.phonecontacts

/**
 * Базовый запечатанный класс (sealed class) для представления различных типов
 * элементов на экране настроек виджета.
 */
sealed class SettingItem {

    /**
     * Представляет заголовок секции на экране настроек.
     * @property title Текст заголовка.
     */
    data class Header(val title: String) : SettingItem()

    /**
     * Представляет кликабельный элемент настроек, который обычно
     * открывает диалог или другую Activity для изменения значения.
     * @property id Уникальный строковый идентификатор настройки.
     * @property title Отображаемое название настройки.
     * @property summary Дополнительное описание, часто показывающее текущее значение (может быть null).
     */
    data class ClickableSetting(
        val id: String,
        val title: String,
        var summary: String? = null
    ) : SettingItem() {
        companion object {
            // Константы для ID кликабельных настроек
            const val ID_COLUMNS = "columns"
            const val ID_ITEM_HEIGHT = "item_height"
            const val ID_SORT_ORDER = "sort_order"
            const val ID_MAX_ITEMS = "max_items"
            const val ID_SELECT_CONTACTS = "select_contacts"
            const val ID_CLICK_ACTION = "click_action"
            const val ID_COMPARE_DIGITS = "compare_digits"
            const val ID_UNKNOWN_AVATAR = "unknown_avatar"
            const val ID_EXPORT_SETTINGS = "export_settings"
            const val ID_IMPORT_SETTINGS = "import_settings"
        }
    }

    /**
     * Представляет настройку с переключателем (Switch).
     * @property id Уникальный строковый идентификатор настройки.
     * @property title Отображаемое название настройки.
     * @property summaryOn Описание, отображаемое когда переключатель включен (может быть null).
     * @property summaryOff Описание, отображаемое когда переключатель выключен (может быть null).
     * @property isChecked Текущее состояние переключателя (true - включен, false - выключен).
     */
    data class SwitchSetting(
        val id: String,
        val title: String,
        val summaryOn: String? = null,
        val summaryOff: String? = null,
        var isChecked: Boolean
    ) : SettingItem() {
        companion object {
            // Константы для ID настроек с переключателем
            const val ID_USE_MONET = "use_monet_theme"
            const val ID_SHOW_UNKNOWN = "show_unknown"
            const val ID_FILTER_OLD_UNKNOWN = "filter_old_unknown"
            const val ID_SHOW_DIALER = "show_dialer"
            const val ID_SHOW_REFRESH = "show_refresh"
        }
    }

    // Устаревшие/удаленные типы настроек (Spinner, RadioGroup, Action) больше не нужны.
}