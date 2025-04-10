package by.toxic.phonecontacts

// Базовый тип для элемента настроек
sealed class SettingItem {
    data class Header(val title: String) : SettingItem()

    // Кликабельный пункт (открывает диалог или другую Activity)
    data class ClickableSetting(
        val id: String, // Уникальный ID (напр., "columns", "click_action", "select_contacts")
        val title: String,
        var summary: String? = null // Дополнительное описание (напр., текущее значение)
    ) : SettingItem()

    // Настройка с переключателем
    data class SwitchSetting(
        val id: String, // Уникальный ID (напр., "show_refresh_button")
        val title: String,
        val summaryOn: String? = null, // Описание, когда включено
        val summaryOff: String? = null, // Описание, когда выключено
        var isChecked: Boolean
    ) : SettingItem()

    // ActionSetting больше не нужен, используем ClickableSetting
    // SpinnerSetting и RadioGroupSetting убраны
}