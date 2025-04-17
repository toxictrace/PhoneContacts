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

    const val CLICK_ACTION_DIAL = "dial"; const val CLICK_ACTION_CALL = "call"; const val CLICK_ACTION_CONFIRM_CALL = "confirm_call"
    internal const val DEFAULT_CLICK_ACTION = CLICK_ACTION_DIAL
    const val SORT_ORDER_FAVORITES = "favorites"; const val SORT_ORDER_FAVORITES_RECENTS = "favorites_recents"; const val SORT_ORDER_FAVORITES_RECENTS_FREQUENT = "favorites_recents_frequent"; const val SORT_ORDER_FAVORITES_FREQUENT = "favorites_frequent"
    internal const val DEFAULT_SORT_ORDER = SORT_ORDER_FAVORITES
    internal const val DEFAULT_COLUMN_COUNT = 4; internal const val DEFAULT_ITEM_HEIGHT = 130
    internal const val DEFAULT_MAX_ITEMS = 15; internal const val DEFAULT_SHOW_UNKNOWN = true
    internal const val DEFAULT_SHOW_DIALER_BTN = false; internal const val DEFAULT_SHOW_REFRESH_BTN = false
    internal const val DEFAULT_FILTER_OLD_UNKNOWN = true; internal const val DEFAULT_COMPARE_DIGITS_COUNT = 9
    internal const val MIN_COMPARE_DIGITS = 7; internal const val MAX_COMPARE_DIGITS = 10

    const val AVATAR_MODE_DEFAULT = "default"; const val AVATAR_MODE_CUSTOM = "custom"
    internal const val DEFAULT_UNKNOWN_AVATAR_MODE = AVATAR_MODE_DEFAULT
    internal val DEFAULT_UNKNOWN_AVATAR_CUSTOM_URI: String? = null
    private const val URI_SEPARATOR = "||"
    private const val AVATAR_DIR_NAME = "widget_avatars"
    // <<< ИЗМЕНЕНИЕ: Убираем private >>>
    internal const val AVATAR_FILE_PREFIX = "avatar_"
    internal const val AVATAR_FILE_SUFFIX = ".jpg"

    private fun getPrefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveWidgetConfig( context: Context, appWidgetId: Int, contactContentUris: List<String>, columnCount: Int, clickAction: String, sortOrder: String, maxItems: Int, itemHeight: Int, showUnknown: Boolean, showDialerButton: Boolean, showRefreshButton: Boolean, filterOldUnknown: Boolean, compareDigitsCount: Int, unknownAvatarMode: String, unknownAvatarCustomUri: String? ) {
        val prefs = getPrefs(context); val editor = prefs.edit(); val oldCustomUriString = loadUnknownAvatarCustomUri(context, appWidgetId)
        if (unknownAvatarMode != AVATAR_MODE_CUSTOM) { if (oldCustomUriString != null) deleteCustomAvatarFile(context, oldCustomUriString); editor.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX) }
        else { if (oldCustomUriString != null && oldCustomUriString != unknownAvatarCustomUri) deleteCustomAvatarFile(context, oldCustomUriString); if (unknownAvatarCustomUri != null) editor.putString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX, unknownAvatarCustomUri) else editor.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX) }
        editor.putString(PREF_PREFIX_KEY + appWidgetId + CONTACT_URIS_SUFFIX, contactContentUris.joinToString(URI_SEPARATOR))
        editor.putInt(PREF_PREFIX_KEY + appWidgetId + COLUMN_COUNT_SUFFIX, columnCount)
        editor.putString(PREF_PREFIX_KEY + appWidgetId + CLICK_ACTION_SUFFIX, clickAction)
        editor.putString(PREF_PREFIX_KEY + appWidgetId + SORT_ORDER_SUFFIX, sortOrder)
        editor.putInt(PREF_PREFIX_KEY + appWidgetId + MAX_ITEMS_SUFFIX, maxItems)
        editor.putInt(PREF_PREFIX_KEY + appWidgetId + ITEM_HEIGHT_SUFFIX, itemHeight)
        editor.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_UNKNOWN_SUFFIX, showUnknown)
        editor.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_DIALER_BTN_SUFFIX, showDialerButton)
        editor.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_REFRESH_BTN_SUFFIX, showRefreshButton)
        editor.putBoolean(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX, filterOldUnknown)
        editor.putInt(PREF_PREFIX_KEY + appWidgetId + COMPARE_DIGITS_SUFFIX, compareDigitsCount.coerceIn(MIN_COMPARE_DIGITS, MAX_COMPARE_DIGITS))
        editor.putString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_MODE_SUFFIX, unknownAvatarMode)
        editor.apply()
        Log.i(TAG, "Saved config for widget $appWidgetId: Cols=$columnCount, Click=$clickAction, Sort=$sortOrder, Max=$maxItems, Height=$itemHeight, Unk=$showUnknown, Dial=$showDialerButton, Refr=$showRefreshButton, FiltOld=$filterOldUnknown, Digits=$compareDigitsCount, AvMode=$unknownAvatarMode, AvUri=$unknownAvatarCustomUri")
    }

    fun loadContactUris(context: Context, appWidgetId: Int): List<String> { val prefs = getPrefs(context); val savedString = prefs.getString(PREF_PREFIX_KEY + appWidgetId + CONTACT_URIS_SUFFIX, null); return savedString?.split(URI_SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList() }
    fun loadColumnCount(context: Context, appWidgetId: Int): Int = getPrefs(context).getInt(PREF_PREFIX_KEY + appWidgetId + COLUMN_COUNT_SUFFIX, DEFAULT_COLUMN_COUNT)
    fun loadClickAction(context: Context, appWidgetId: Int): String = getPrefs(context).getString(PREF_PREFIX_KEY + appWidgetId + CLICK_ACTION_SUFFIX, DEFAULT_CLICK_ACTION) ?: DEFAULT_CLICK_ACTION
    fun loadSortOrder(context: Context, appWidgetId: Int): String = getPrefs(context).getString(PREF_PREFIX_KEY + appWidgetId + SORT_ORDER_SUFFIX, DEFAULT_SORT_ORDER) ?: DEFAULT_SORT_ORDER
    fun loadMaxItems(context: Context, appWidgetId: Int): Int = getPrefs(context).getInt(PREF_PREFIX_KEY + appWidgetId + MAX_ITEMS_SUFFIX, DEFAULT_MAX_ITEMS)
    fun loadItemHeight(context: Context, appWidgetId: Int): Int = getPrefs(context).getInt(PREF_PREFIX_KEY + appWidgetId + ITEM_HEIGHT_SUFFIX, DEFAULT_ITEM_HEIGHT)
    fun loadShowUnknown(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_UNKNOWN_SUFFIX, DEFAULT_SHOW_UNKNOWN)
    fun loadShowDialerButton(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_DIALER_BTN_SUFFIX, DEFAULT_SHOW_DIALER_BTN)
    fun loadShowRefreshButton(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_REFRESH_BTN_SUFFIX, DEFAULT_SHOW_REFRESH_BTN)
    fun loadFilterOldUnknown(context: Context, appWidgetId: Int): Boolean = getPrefs(context).getBoolean(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX, DEFAULT_FILTER_OLD_UNKNOWN)
    fun loadCompareDigitsCount(context: Context, appWidgetId: Int): Int { val count = getPrefs(context).getInt(PREF_PREFIX_KEY + appWidgetId + COMPARE_DIGITS_SUFFIX, DEFAULT_COMPARE_DIGITS_COUNT); return count.coerceIn(MIN_COMPARE_DIGITS, MAX_COMPARE_DIGITS) }
    fun loadUnknownAvatarMode(context: Context, appWidgetId: Int): String = getPrefs(context).getString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_MODE_SUFFIX, DEFAULT_UNKNOWN_AVATAR_MODE) ?: DEFAULT_UNKNOWN_AVATAR_MODE
    fun loadUnknownAvatarCustomUri(context: Context, appWidgetId: Int): String? { val uriString = getPrefs(context).getString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX, null); if (uriString != null) { try { val fileUri = Uri.parse(uriString); if (fileUri.scheme == "file" && fileUri.path != null) { val file = File(fileUri.path!!); if (file.exists()) return uriString else { Log.w(TAG, "Custom avatar file for widget $appWidgetId not found: ${file.path}. Returning null.") } } } catch (e: Exception) { Log.e(TAG, "Error checking custom avatar file existence for widget $appWidgetId: ${e.message}") } }; return null }

    fun deleteWidgetConfig(context: Context, appWidgetId: Int) { val customUriString = getPrefs(context).getString(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX, null); if (customUriString != null) deleteCustomAvatarFile(context, customUriString); val editor = getPrefs(context).edit(); editor.remove(PREF_PREFIX_KEY + appWidgetId + CONTACT_URIS_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + COLUMN_COUNT_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + CLICK_ACTION_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + SORT_ORDER_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + MAX_ITEMS_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + ITEM_HEIGHT_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + SHOW_UNKNOWN_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + SHOW_DIALER_BTN_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + SHOW_REFRESH_BTN_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + COMPARE_DIGITS_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_MODE_SUFFIX); editor.remove(PREF_PREFIX_KEY + appWidgetId + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX); editor.apply(); Log.i(TAG, "Deleted config and custom avatar (if existed) for widget $appWidgetId") }
    fun deleteWidgetConfig(context: Context, appWidgetIds: IntArray) { val prefs = getPrefs(context); val editor = prefs.edit(); for (id in appWidgetIds) { val customUriString = prefs.getString(PREF_PREFIX_KEY + id + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX, null); if (customUriString != null) deleteCustomAvatarFile(context, customUriString); editor.remove(PREF_PREFIX_KEY + id + CONTACT_URIS_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + COLUMN_COUNT_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + CLICK_ACTION_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + SORT_ORDER_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + MAX_ITEMS_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + ITEM_HEIGHT_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + SHOW_UNKNOWN_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + SHOW_DIALER_BTN_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + SHOW_REFRESH_BTN_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + FILTER_OLD_UNKNOWN_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + COMPARE_DIGITS_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + UNKNOWN_AVATAR_MODE_SUFFIX); editor.remove(PREF_PREFIX_KEY + id + UNKNOWN_AVATAR_CUSTOM_URI_SUFFIX) }; editor.apply(); Log.i(TAG, "Deleted config and custom avatars (if existed) for widgets ${appWidgetIds.joinToString()}") }

    fun getCustomAvatarFile(context: Context, appWidgetId: Int): File { val appContext = context.applicationContext; val directory = File(appContext.filesDir, AVATAR_DIR_NAME); if (!directory.exists()) if (!directory.mkdirs()) Log.e(TAG, "Failed to create directory for widget avatars: ${directory.path}"); return File(directory, "$AVATAR_FILE_PREFIX$appWidgetId$AVATAR_FILE_SUFFIX") }

    // <<< ИЗМЕНЕНИЕ: Убираем private >>>
    internal fun deleteCustomAvatarFile(context: Context, uriString: String?) {
        if (uriString.isNullOrEmpty()) { Log.v(TAG, "deleteCustomAvatarFile: URI is null or empty, skipping."); return }
        try { val fileUri = Uri.parse(uriString); if (fileUri.scheme == "file" && fileUri.path != null) { val appContext = context.applicationContext; if (fileUri.path!!.startsWith(appContext.filesDir.path)) { val expectedDirPath = File(appContext.filesDir, AVATAR_DIR_NAME).path; val file = File(fileUri.path!!); if (file.parentFile?.path == expectedDirPath) { if (file.exists()) { if (file.delete()) Log.d(TAG, "Successfully deleted avatar file: ${file.path}") else Log.w(TAG, "Failed to delete avatar file: ${file.path}") } else Log.d(TAG, "Avatar file not found, no need to delete: ${file.path}") } else Log.w(TAG, "Attempted to delete file outside '$AVATAR_DIR_NAME' directory: $uriString") } else Log.w(TAG, "Attempted to delete file outside app's internal storage: $uriString") } else Log.w(TAG, "Attempted to delete non-internal or non-file URI: $uriString") }
        catch (e: SecurityException) { Log.e(TAG, "SecurityException deleting avatar file for URI $uriString: ${e.message}", e) }
        catch (e: Exception) { Log.e(TAG, "Error deleting avatar file for URI $uriString: ${e.message}", e) }
    }
}