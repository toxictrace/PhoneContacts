package by.toxic.phonecontacts

import android.content.Context
import android.content.SharedPreferences

object WidgetPrefsUtils {

    private const val PREFS_NAME = "by.toxic.phonecontacts.WidgetPrefs"
    private const val PREF_PREFIX_KEY = "widget_"

    // Суффиксы ключей
    private const val CONTACT_URIS_SUFFIX = "_contact_uris"; private const val COLUMN_COUNT_SUFFIX = "_column_count"
    private const val CLICK_ACTION_SUFFIX = "_click_action"; private const val SORT_ORDER_SUFFIX = "_sort_order"
    private const val MAX_ITEMS_SUFFIX = "_max_items"; private const val ITEM_HEIGHT_SUFFIX = "_item_height"
    private const val SHOW_UNKNOWN_SUFFIX = "_show_unknown"; private const val SHOW_DIALER_BTN_SUFFIX = "_show_dialer_btn"
    private const val SHOW_REFRESH_BTN_SUFFIX = "_show_refresh_btn"
    private const val FILTER_OLD_UNKNOWN_SUFFIX = "_filter_old_unknown" // Добавлено

    // Константы и значения по умолчанию
    const val CLICK_ACTION_DIAL = "dial"; const val CLICK_ACTION_CALL = "call"; const val CLICK_ACTION_CONFIRM_CALL = "confirm_call"
    internal const val DEFAULT_CLICK_ACTION = CLICK_ACTION_DIAL
    const val SORT_ORDER_FAVORITES = "favorites"; const val SORT_ORDER_FAVORITES_RECENTS = "favorites_recents"
    internal const val DEFAULT_SORT_ORDER = SORT_ORDER_FAVORITES
    internal const val DEFAULT_COLUMN_COUNT = 4; internal const val DEFAULT_ITEM_HEIGHT = 130
    internal const val DEFAULT_MAX_ITEMS = 15; internal const val DEFAULT_SHOW_UNKNOWN = true
    internal const val DEFAULT_SHOW_DIALER_BTN = false; internal const val DEFAULT_SHOW_REFRESH_BTN = false
    internal const val DEFAULT_FILTER_OLD_UNKNOWN = true // Добавлено

    private const val URI_SEPARATOR = "||"
    private fun getPrefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Сохранение ---
    fun saveWidgetConfig(
        context: Context, appWidgetId: Int, contactContentUris: List<String>, columnCount: Int, clickAction: String,
        sortOrder: String, maxItems: Int, itemHeight: Int, showUnknown: Boolean, showDialerButton: Boolean,
        showRefreshButton: Boolean,
        filterOldUnknown: Boolean // Добавлено
    ) {
        val prefs = getPrefs(context).edit()
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + CONTACT_URIS_SUFFIX, contactContentUris.joinToString(URI_SEPARATOR))
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + COLUMN_COUNT_SUFFIX, columnCount)
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + CLICK_ACTION_SUFFIX, clickAction)
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + SORT_ORDER_SUFFIX, sortOrder)
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + MAX_ITEMS_SUFFIX, maxItems)
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId + ITEM_HEIGHT_SUFFIX, itemHeight)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_UNKNOWN_SUFFIX, showUnknown)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_DIALER_BTN_SUFFIX, showDialerButton)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + SHOW_REFRESH_BTN_SUFFIX, showRefreshButton)
        prefs.putBoolean(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX, filterOldUnknown) // Добавлено
        prefs.apply()
        println("Saved config for widget $appWidgetId: ..., showUnknown: $showUnknown, showDialer: $showDialerButton, showRefresh: $showRefreshButton, filterOldUnknown: $filterOldUnknown")
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

    // --- Удаление ---
    fun deleteWidgetConfig(context: Context, appWidgetId: Int) {
        val prefs = getPrefs(context).edit();
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+CONTACT_URIS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+COLUMN_COUNT_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+CLICK_ACTION_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+SORT_ORDER_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+MAX_ITEMS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+ITEM_HEIGHT_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+SHOW_UNKNOWN_SUFFIX); prefs.remove(PREF_PREFIX_KEY+appWidgetId+SHOW_DIALER_BTN_SUFFIX)
        prefs.remove(PREF_PREFIX_KEY+appWidgetId+SHOW_REFRESH_BTN_SUFFIX); prefs.remove(PREF_PREFIX_KEY + appWidgetId + FILTER_OLD_UNKNOWN_SUFFIX) // Добавлено
        prefs.apply(); println("Deleted config for widget $appWidgetId")
    }
    fun deleteWidgetConfig(context: Context, appWidgetIds: IntArray) {
        val prefs = getPrefs(context).edit(); for(id in appWidgetIds){
            prefs.remove(PREF_PREFIX_KEY+id+CONTACT_URIS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+id+COLUMN_COUNT_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY+id+CLICK_ACTION_SUFFIX); prefs.remove(PREF_PREFIX_KEY+id+SORT_ORDER_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY+id+MAX_ITEMS_SUFFIX); prefs.remove(PREF_PREFIX_KEY+id+ITEM_HEIGHT_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY+id+SHOW_UNKNOWN_SUFFIX); prefs.remove(PREF_PREFIX_KEY + id + SHOW_DIALER_BTN_SUFFIX)
            prefs.remove(PREF_PREFIX_KEY + id + SHOW_REFRESH_BTN_SUFFIX); prefs.remove(PREF_PREFIX_KEY + id + FILTER_OLD_UNKNOWN_SUFFIX) // Добавлено
        }; prefs.apply(); println("Deleted config for widgets ${appWidgetIds.joinToString()}")
    }
}