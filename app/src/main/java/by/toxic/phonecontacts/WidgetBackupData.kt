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
    val filterOldUnknown: Boolean,
    val compareDigitsCount: Int,
    val unknownAvatarMode: String, // <<< ДОБАВЛЕНО
    val unknownAvatarCustomUri: String? // <<< ДОБАВЛЕНО (nullable)
)

@Serializable
data class AppBackup(
    val widgetSettings: Map<Int, WidgetBackupData>
)