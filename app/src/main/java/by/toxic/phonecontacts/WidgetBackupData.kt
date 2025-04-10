package by.toxic.phonecontacts

import kotlinx.serialization.Serializable

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
    val filterOldUnknown: Boolean
)

@Serializable
data class AppBackup(
    val widgetSettings: Map<Int, WidgetBackupData> // Map<WidgetId, Settings>
)