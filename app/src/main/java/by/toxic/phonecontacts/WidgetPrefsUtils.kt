package by.toxic.phonecontacts

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import java.io.File

object WidgetPrefsUtils {

    private const val PREFS_NAME = "by.toxic.phonecontacts.WidgetPrefs"
    private const val PREF_PREFIX_KEY = "widget_"
    private const val TAG = "WidgetPrefsUtils"

    // Суффиксы ключей
    private const val CONTACT_URIS_SUFFIX = "_contact_uris"
    private const val COLUMN_COUNT_SUFFIX = "_column_count"
    private const val CLICK_ACTION_SUFFIX = "_click_action"
    private const val SORT_ORDER_SUFFIX = "_sort_order"
    private const val MAX_ITEMS_SUFFIX = "_max_items"
    private const val ITEM_HEIGHT_SUFFIX = "_item_height"
    private const val SHOW_UNKNOWN_SUFFIX = "_show_unknown"
    private const val SHOW_DIALER_BTN_SUFFIX = "_show_dialer_btn"
    private const val SHOW_REFRESH_BTN_SUFFIX = "_show_refresh_btn"
    private const val FILTER_OLD_UNKNOWN_SUFFIX = "_filter_old_unknown"
    private const val COMPARE_DIGITS_SUFFIX = "_compare_digits"
    private const val UNKNOWN_AVATAR_MODE_SUFFIX = "_unknown_avatar_mode"
    private const val UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX = "_unknown_avatar_uri"

    // Константы и значения по умолчанию
    const val CLICK_ACTION_DIAL = "dial"; const val CLICK_ACTION_CALL = "call"; const val CLICK_ACTION_CONFIRM_CALL = "confirm_call"
    internal const val DEFAULT_CLICK_ACTION = CLICK_ACTION_DIAL

    // <<< ИЗМЕНЕНИЕ: Добавлена ЕЩЕ ОДНА константа для нового порядка сортировки >>>
    const val SORT_ORDER_FAVORITES = "favorites";
    const val SORT_ORDER_FAVORITES_RECENTS = "favorites_recents"
    const val SORT_ORDER_FAVORITES_RECENTS_FREQUENT = "favorites_recents_frequent"
    const val SORT_ORDER_FAVORITES_FREQUENT = "favorites_frequent" // <<< НОВАЯ КОНСТАНТА
    internal const val DEFAULT_SORT_ORDER = SORT_ORDER_FAVORITES // Оставляем "Избранные" по умолчанию

    internal const val DEFAULT_COLUMN_COUNT = 4; internal const val DEFAULT_ITEM_HEIGHT = 130
    internal const val DEFAULT_MAX_ITEMS = 15; internal const val DEFAULT_SHOW_UNKNOWN = true
    internal const val DEFAULT_SHOW_DIALER_BTN = false; internal const val DEFAULT_SHOW_REFRESH_BTN = false
    internal const val DEFAULT_FILTER_OLD_UNKNOWN = true
    internal const val DEFAULT_COMPARE_DIGITS_COUNT = 9

    const val AVATAR_MODE_DEFAULT = "default"
    const val AVATAR_MODE_PREDEFINED = "predefined"
    const val AVATAR_MODE_CUSTOM = "custom"
    internal const val DEFAULT_UNKNOWN_AVATAR_MODE = AVATAR_MODE_DEFAULT
    internal val DEFAULT_UNKNOWN_AVATAR_CUSTOM_URI: String? = null

    private const val URI_SEPARATOR = "||"
    private fun getPrefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Сохранение ---
    fun saveWidgetConfig(
        context: Context, appWidgetId: Int, contactContentUris: List<String>, columnCount: Int, clickAction: String,
        sortOrder: String, maxItems: Int, itemHeight: Int, showUnknown: Boolean, showDialerButton: Boolean,
        showRefreshButton: Boolean, filterOldUnknown: Boolean, compareDigitsCount: Int,
        unknownAvatarMode: String,
        unknownAvatarCustomUri: String?
    ) {
        val prefs = getPrefs(context).edit()
        val oldCustomUriString = loadUnknownAvatarCustomUri(context, appWidgetId)
        if (unknownAvatarMode != AVATAR_MODE_CUSTOM && oldCustomUriString != null) {
            deleteCustomAvatarFile(context, oldCustomUriString)
            prefs.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX)
            Log.d(TAG, "saveWidgetConfig: Deleted old avatar $oldCustomUriString because mode changed for widget $appWidgetId")
        } else if (unknownAvatarMode == AVATAR_MODE_CUSTOM && oldCustomUriString != null && oldCustomUriString != unknownAvatarCustomUri) {
            deleteCustomAvatarFile(context, oldCustomUriString)
            Log.d(TAG, "saveWidgetConfig: Deleted old avatar $oldCustomUriString because new one was selected for widget $appWidgetId")
        }

        prefs.putString(PREF_PREFIX_KEY + appWidgetId + CONTACT_URIS_SUFFIX, contactContentUris.joinToString(URI_SEPARATOR))
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + COLUMN_COUNT_SUFFIX, columnCount)
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + CLICK_ACTION_SUFFIX, clickAction)
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + SORT_ORDER_SUFFIX, sortOrder)
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + MAX_ITEMS_SUFFIX, maxItems)
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + ITEM_HEIGHT_SUFFIX, itemHeight)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_UNKNOWN_SUFFIX, showUnknown)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_DIALER_BTN_SUFFIX, showDialerButton)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_REFRESH_BTN_SUFFIX, showRefreshButton)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX, filterOldUnknown)
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + COMPARE_DIGITS_SUFFIX, compareDigitsCount)
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_MODE_SUFFIX, unknownAvatarMode)
        if (unknownAvatarMode == AVATAR_MODE_CUSTOM && unknownAvatarCustomUri != null) {
            prefs.putString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX, unknownAvatarCustomUri)
        } else {
            prefs.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX)
        }
        prefs.apply()
        Log.i(TAG, "Saved config for widget $appWidgetId: Cols=$columnCount, Click=$clickAction, Sort=$sortOrder, Max=$maxItems, Height=$itemHeight, Unk=$showUnknown, Dial=$showDialerButton, Refr=$showRefreshButton, FiltOld=$filterOldUnknown, Digits=$compareDigitsCount, AvMode=$unknownAvatarMode, AvUri=$unknownAvatarCustomUri")
    }

    // --- Загрузка ---
    fun loadContactUris(context: Context, appWidgetId: Int): List<String> { val p=getPrefs(context); val s=p.getString(PREF_PREFIX_KEY+appWidgetId+CONTACT_URIS_SUFFIX,null); return s?.split(URI_SEPARATOR)?.filter{it.isNotEmpty()}?:emptyList() }
    fun loadColumnCount(context: Context, appWidgetId: Int): Int = getPrefs(context).getInt(PREF_PREFIX_KEY+appWidgetId+COLUMN_COUNT_SUFFIX, DEFAULT_COLUMN_COUNT)
    fun loadClickAction(context: Context, appWidgetId: Int): String = getPrefs(context).getString(PREF_PREFIX_KEY+appWidgetId+CLICK_ACTION_SUFFIX, DEFAULT_CLICK_ACTION)?: DEFAULT_CLICK_ACTION
    fun loadSortOrder(context: Context, appWidgetId: Int): String = getPrefs(context).getString(PREF_PREFIX_KEY+appWidgetId+SORT_ORDER_SUFFIX, DEFAULT_SORT_ORDER)?: DEFAULT_SORT_ORDER
    fun loadMaxItems(context: Context, appWidgetId: Int): Int = getPrefs(context).getInt(PREF_PREFIX_KEY+appWidgetId+MAX_ITEMS_SUFFIX, DEFAULT_MAX_ITEMS)
    fun loadItemHeight(context: Context, appWidgetId: Int): Int = getPrefs(context).getInt(PREF_PREFIX_KEY+appWidgetId+ITEM_HEIGHT_SUFFIX, DEFAULT_ITEM_HEIGHT)
    fun loadShowUnknown(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY+appWidgetId+SHOW_UNKNOWN_SUFFIX, DEFAULT_SHOW_UNKNOWN)
    fun loadShowDialerButton(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_DIALER_BTN_SUFFIX, DEFAULT_SHOW_DIALER_BTN)
    fun loadShowRefreshButton(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_REFRESH_BTN_SUFFIX, DEFAULT_SHOW_REFRESH_BTN)
    fun loadFilterOldUnknown(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX, DEFAULT_FILTER_OLD_UNKNOWN)
    fun loadCompareDigitsCount(context: Context, appWidgetId: Int): Int {
        val count = getPrefs(context).getInt(PREF_PREFIX_KEY + appWidgetId + COMPARE_DIGITS_SUFFIX, DEFAULT_COMPARE_DIGITS_COUNT)
        return count.coerceIn(7, 10) // Ограничение 7-10 при загрузке
    }
    fun loadUnknownAvatarMode(context: Context, appWidgetId: Int): String {
        return getPrefs(context).getString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_MODE_SUFFIX, DEFAULT_UNKNOWN_AVATAR_MODE) ?: DEFAULT_UNKNOWN_AVATAR_MODE
    }
    fun loadUnknownAvatarCustomUri(context: Context, appWidgetId: Int): String? {
        return getPrefs(context).getString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX, null)
    }

    // --- Удаление ---
    fun deleteWidgetConfig(context: Context, appWidgetId: Int) {
        val customUriString = loadUnknownAvatarCustomUri(context, appWidgetId)
        if (customUriString != null) {
            deleteCustomAvatarFile(context, customUriString)
        }
        val prefs = getPrefs(context).edit();
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+CONTACT_URIS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+COLUMN_COUNT_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+CLICK_ACTION_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+SORT_ORDER_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+MAX_ITEMS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+ITEM_HEIGHT_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+SHOW_UNKNOWN_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+SHOW_DIALER_BTN_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+SHOW_REFRESH_BTN_SUFFIX); prefs.remove(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + COMPARE_DIGITS_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_MODE_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX)
        prefs.apply();
        Log.i(TAG, "Deleted config and custom avatar (if existed) for widget $appWidgetId")
    }

    fun deleteWidgetConfig(context: Context, appWidgetIds: IntArray) {
        val prefs = getPrefs(context).edit();
        for(id in appWidgetIds){
            val customUriString = loadUnknownAvatarCustomUri(context, id)
            if (customUriString != null) {
                deleteCustomAvatarFile(context, customUriString)
            }
            prefs.remove(PREF_PREFIX_KEY+id+CONTACT_URIS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+id+COLUMN_COUNT_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY+id+CLICK_ACTION_SUFFIX); prefs.remove(PREF_PREFIX_KEY+id+SORT_ORDER_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY+id+MAX_ITEMS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+id+ITEM_HEIGHT_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY+id+SHOW_UNKNOWN_SUFFIX); prefs.remove(PREF_PREFIX_KEY + id + SHOW_DIALER_BTN_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY + id + SHOW_REFRESH_BTN_SUFFIX); prefs.remove(PREF_PREFIX_KEY + id + FILTER_OLD_UNKNOWN_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY + id + COMPARE_DIGITS_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY + id + UNKNOWN_AVATAR_MODE_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY + id + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX)
        };
        prefs.apply();
        Log.i(TAG, "Deleted config and custom avatars (if existed) for widgets ${appWidgetIds.joinToString()}")
    }

    // --- Вспомогательные функции для аватаров ---
    fun getCustomAvatarFile(context: Context, appWidgetId: Int): File {
        val directory = File(context.filesDir, "widget_avatars")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, "avatar_$appWidgetId.jpg")
    }

    private fun deleteCustomAvatarFile(context: Context, uriString: String?) {
        if (uriString.isNullOrEmpty()) return
        try {
            val fileUri = Uri.parse(uriString)
            if (fileUri.scheme == "file" && fileUri.path?.startsWith(context.filesDir.path) == true) {
                if (fileUri.path?.contains("/widget_avatars/") == true) {
                    val file = File(fileUri.path!!)
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(TAG, "Successfully deleted avatar file: ${file.path}")
                        } else {
                            Log.w(TAG, "Failed to delete avatar file: ${file.path}")
                        }
                    } else {
                        Log.d(TAG, "Avatar file not found, no need to delete: ${file.path}")
                    }
                } else {
                    Log.w(TAG, "Attempted to delete file outside widget_avatars directory: $uriString")
                }
            } else {
                Log.w(TAG, "Attempted to delete non-internal or non-file URI: $uriString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting avatar file for URI $uriString: ${e.message}", e)
        }
    }
}