package by.toxic.phonecontacts

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.SystemClock
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Calendar
import kotlin.math.roundToInt

// <<< ИЗМЕНЕНИЕ: Убрали дублирующее объявление (если было) >>>
private const val TAG = "RVFactory"

class ContactsGridRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    // Используем applicationContext для избежания утечек
    private val appContext: Context = context.applicationContext

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    ).also {
        Log.d(TAG, "Factory создана для widget ID: $it")
    }

    // Ресурс заглушки по умолчанию
    private val defaultPlaceholderResId = R.drawable.anonim

    /** Data class для хранения информации о контакте в виджете */
    private data class WidgetContact(
        val contactId: Long, // ID из ContactsContract или -1L для неизвестных
        val lookupKey: String?, // Стабильный ключ или null
        val name: String?, // Имя контакта или номер/null
        val phoneNumber: String?, // Номер телефона (один, первый найденный)
        val photoThumbnailUri: String?, // URI миниатюры (строка) или null
        val lastCallType: Int? = null, // Тип последнего звонка из CallLog.Calls или null
        val isFavorite: Boolean = false, // Является ли избранным из настроек
        val isUnknown: Boolean = false, // Является ли неизвестным (не найден в контактах)
        val callCount: Int = 0 // Количество звонков (для сортировки по частоте)
    ) {
        // Стабильный URI для Intent'ов (использует lookupKey)
        val contentUri: Uri?
            get() = if (contactId != -1L && !lookupKey.isNullOrEmpty()) {
                ContactsContract.Contacts.getLookupUri(contactId, lookupKey)
            } else {
                null
            }

        override fun toString(): String {
            val typeStr = when (lastCallType) {
                CallLog.Calls.INCOMING_TYPE -> "IN"
                CallLog.Calls.OUTGOING_TYPE -> "OUT"
                CallLog.Calls.MISSED_TYPE -> "MISSED"
                CallLog.Calls.REJECTED_TYPE -> "REJ"
                null -> "NULL"
                else -> "TYPE($lastCallType)"
            }
            return "ID=$contactId, LK=$lookupKey, Name=$name, Phone=$phoneNumber, Thumb=$photoThumbnailUri, Type=$typeStr, IsFav=$isFavorite, IsUnk=$isUnknown, Calls=$callCount"
        }
    }

    private var widgetContacts: List<WidgetContact> = emptyList()
    private var itemHeightDp: Int = WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT
    private var itemLayoutId: Int = R.layout.widget_item_default_height
    private val layoutIdCache = SparseArray<Int>()
    private var unknownAvatarMode: String = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_MODE
    private var unknownAvatarCustomUri: String? = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_CUSTOM_URI

    // --- Lifecycle Methods ---

    override fun onCreate() {
        Log.d(TAG, "onCreate для widget $appWidgetId")
    }

    override fun onDataSetChanged() {
        Log.i(TAG, "====== onDataSetChanged START для widget $appWidgetId ======")
        val startTime = SystemClock.elapsedRealtime()

        updateVisualSettings()
        unknownAvatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(appContext, appWidgetId)
        unknownAvatarCustomUri = WidgetPrefsUtils.loadUnknownAvatarCustomUri(appContext, appWidgetId)
        Log.d(TAG, "Widget $appWidgetId - Avatar Mode=$unknownAvatarMode, Avatar URI=$unknownAvatarCustomUri")

        val sortOrder = WidgetPrefsUtils.loadSortOrder(appContext, appWidgetId)
        val maxItems = WidgetPrefsUtils.loadMaxItems(appContext, appWidgetId)
        val favoriteContactUris = WidgetPrefsUtils.loadContactUris(appContext, appWidgetId)
        val showUnknown = WidgetPrefsUtils.loadShowUnknown(appContext, appWidgetId)
        val filterOldUnknown = WidgetPrefsUtils.loadFilterOldUnknown(appContext, appWidgetId)

        Log.d(TAG, "Widget $appWidgetId - Settings: Sort='$sortOrder', MaxItems=$maxItems, FavURIs=${favoriteContactUris.size}, ShowUnknown=$showUnknown, FilterOld=$filterOldUnknown")

        val resolver = appContext.contentResolver
        val identityToken = Binder.clearCallingIdentity()
        var loadedContacts: List<WidgetContact> = emptyList()

        try {
            Log.d(TAG, "Загрузка контактов с порядком сортировки: $sortOrder")
            loadedContacts = when (sortOrder) {
                WidgetPrefsUtils.SORT_ORDER_FAVORITES ->
                    loadFavoriteContacts(resolver, favoriteContactUris).take(maxItems)
                WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS ->
                    loadFavoritesAndRecents(resolver, favoriteContactUris, maxItems, showUnknown, filterOldUnknown)
                WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT ->
                    loadFavoritesRecentsAndFrequent(resolver, favoriteContactUris, maxItems, showUnknown, filterOldUnknown)
                WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT ->
                    loadFavoritesAndFrequent(resolver, favoriteContactUris, maxItems, showUnknown)
                else -> {
                    Log.w(TAG, "Неизвестный порядок сортировки '$sortOrder'. Загрузка избранных по умолчанию.")
                    loadFavoriteContacts(resolver, favoriteContactUris).take(maxItems)
                }
            }
            Log.i(TAG, "Загружено ${loadedContacts.size} контактов для widget $appWidgetId.")
            widgetContacts = loadedContacts

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException при доступе к ContentProvider: ${e.message}.", e)
            widgetContacts = emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Неожиданная ошибка при загрузке данных: ${e.message}", e)
            widgetContacts = emptyList()
        } finally {
            Binder.restoreCallingIdentity(identityToken)
            val duration = SystemClock.elapsedRealtime() - startTime
            Log.i(TAG, "====== onDataSetChanged END для widget $appWidgetId (заняло ${duration}ms) ======")
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy для widget $appWidgetId")
        widgetContacts = emptyList()
        layoutIdCache.clear()
    }

    // --- Методы загрузки данных ---

    private fun loadFavoriteContacts(resolver: ContentResolver, favoriteUris: List<String>): List<WidgetContact> {
        if (favoriteUris.isEmpty()) {
            Log.d(TAG, "Нет избранных URI для загрузки.")
            return emptyList()
        }
        Log.d(TAG, "--- Загрузка избранных контактов (${favoriteUris.size} URIs) ---")
        val favorites = mutableListOf<WidgetContact>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        val hasCallLogPerm = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

        favoriteUris.forEach { uriString ->
            val contactUri = try { Uri.parse(uriString) } catch (e: Exception) { Log.w(TAG, "Неверный URI избранного: $uriString"); return@forEach }
            var cursor: Cursor? = null
            try {
                cursor = resolver.query(contactUri, projection, null, null, null)
                if (cursor?.moveToFirst() == true) {
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                    val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    if (idIndex != -1 && lookupKeyIndex != -1 && nameIndex != -1 && photoUriIndex != -1 && hasPhoneIndex != -1) {
                        val id = cursor.getLong(idIndex)
                        val lookupKey = cursor.getString(lookupKeyIndex)
                        val name = cursor.getString(nameIndex)
                        val photoThumbnailUri = cursor.getString(photoUriIndex)
                        val hasPhoneNumber = cursor.getInt(hasPhoneIndex) > 0
                        var phoneNumber: String? = null

                        if (hasPhoneNumber) {
                            phoneNumber = fetchFirstPhoneNumber(resolver, id.toString())
                        } else {
                            Log.w(TAG, "Избранный контакт ID=$id, Name=$name не имеет номеров телефона. Пропускается.")
                        }

                        if (!phoneNumber.isNullOrEmpty() && lookupKey != null) {
                            var lastCallType: Int? = null
                            if (hasCallLogPerm) {
                                lastCallType = fetchLastCallTypeForNumber(resolver, phoneNumber)
                            }
                            val contact = WidgetContact(
                                contactId = id, lookupKey = lookupKey, name = name,
                                phoneNumber = phoneNumber, photoThumbnailUri = photoThumbnailUri,
                                lastCallType = lastCallType, isFavorite = true, isUnknown = false
                            )
                            favorites.add(contact)
                            Log.v(TAG, "Добавлен избранный: $contact")
                        } else {
                            if(phoneNumber.isNullOrEmpty()) Log.w(TAG, "Не удалось получить номер для избранного ID=$id, Name=$name. Пропускается.")
                            if(lookupKey == null) Log.w(TAG, "LookupKey is null для избранного ID=$id, Name=$name. Пропускается.")
                        }
                    } else {
                        Log.e(TAG, "Ошибка индекса колонки для избранного URI $uriString")
                    }
                } else {
                    Log.w(TAG, "Не удалось найти контакт для избранного URI $uriString")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка запроса избранного контакта $uriString: ${e.message}", e)
            } finally {
                cursor?.close()
            }
        }
        Log.d(TAG, "--- Загрузка избранных завершена (Загружено: ${favorites.size}) ---")
        return favorites
    }

    private fun fetchLastCallTypeForNumber(resolver: ContentResolver, numberToCompare: String): Int? {
        val digitsToCompare = WidgetPrefsUtils.loadCompareDigitsCount(appContext, appWidgetId)
        val normalizedNumberToCompareLastN = getLastNdigits(numberToCompare, digitsToCompare)

        if (normalizedNumberToCompareLastN.length < WidgetPrefsUtils.MIN_COMPARE_DIGITS) {
            return null
        }

        var callTypeResult: Int? = null
        val sortOrder = "${CallLog.Calls.DATE} DESC"
        val queryLimit = 50
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, queryLimit.toString())
            .build()
        val projection = arrayOf(CallLog.Calls.TYPE, CallLog.Calls.NUMBER)
        var cursor: Cursor? = null

        try {
            cursor = resolver.query(queryUri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
                if (typeIndex != -1 && numberIndex != -1) {
                    while (c.moveToNext() && callTypeResult == null) {
                        val callLogNumberRaw = c.getString(numberIndex) ?: continue
                        val callLogNumberLastN = getLastNdigits(callLogNumberRaw, digitsToCompare)
                        if (callLogNumberLastN.length >= WidgetPrefsUtils.MIN_COMPARE_DIGITS && callLogNumberLastN == normalizedNumberToCompareLastN) {
                            callTypeResult = c.getInt(typeIndex)
                            break
                        }
                    }
                } else {
                    Log.w(TAG, "Не найдены колонки TYPE или NUMBER в CallLog при поиске типа.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка получения типа последнего звонка для $numberToCompare: ${e.message}")
            return null
        } finally {
            cursor?.close()
        }
        Log.v(TAG,"Тип последнего звонка для (...${normalizedNumberToCompareLastN}): $callTypeResult")
        return callTypeResult
    }

    private fun getLastNdigits(number: String?, n: Int): String {
        if (number.isNullOrEmpty()) return ""
        return number.replace(Regex("\\D"), "").takeLast(n)
    }

    private fun loadRecentContacts(resolver: ContentResolver): List<Pair<WidgetContact, Long?>> {
        val recentsLimit = 250
        Log.d(TAG, "--- Загрузка недавних контактов (Лимит журнала: $recentsLimit) ---")

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Нет разрешения READ_CALL_LOG для загрузки недавних.")
            return emptyList()
        }

        val recents = mutableListOf<Pair<WidgetContact, Long?>>()
        val uniqueCleanPhoneNumbers = mutableSetOf<String>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_LOOKUP_URI, CallLog.Calls.DATE, CallLog.Calls.TYPE
        )
        val sortOrder = "${CallLog.Calls.DATE} DESC"
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, recentsLimit.toString())
            .build()
        var cursor: Cursor? = null

        try {
            cursor = resolver.query(queryUri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val lookupUriIndex = c.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
                val dateIndex = c.getColumnIndex(CallLog.Calls.DATE)
                val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)

                if (numberIndex == -1 || dateIndex == -1 || typeIndex == -1) {
                    Log.e(TAG, "Не найдены колонки NUMBER, DATE или TYPE в CallLog для недавних.")
                    return@use
                }

                while (c.moveToNext()) {
                    val phoneNumber = c.getString(numberIndex)
                    val callDate = c.getLong(dateIndex)
                    val callType = c.getInt(typeIndex)

                    if (phoneNumber.isNullOrEmpty()) continue
                    val cleanNumber = phoneNumber.replace(Regex("[()\\-\\s]"), "")
                    if (cleanNumber.isEmpty() || !uniqueCleanPhoneNumbers.add(cleanNumber)) continue

                    val cachedName = if (nameIndex != -1) c.getString(nameIndex) else null
                    val cachedLookupUriString = if (lookupUriIndex != -1) c.getString(lookupUriIndex) else null
                    var contact: WidgetContact? = null

                    if (!cachedLookupUriString.isNullOrEmpty()) {
                        try { contact = findContactByUri(resolver, Uri.parse(cachedLookupUriString), phoneNumber) }
                        catch (e: Exception) { Log.w(TAG,"Ошибка парсинга cachedLookupUriString: $cachedLookupUriString") }
                    }
                    if (contact == null) {
                        contact = findContactByNumber(resolver, phoneNumber)
                    }

                    val recentContact: WidgetContact = if (contact != null) {
                        contact.copy(lastCallType = callType, isFavorite = false, isUnknown = false)
                    } else {
                        WidgetContact(
                            contactId = -1L, lookupKey = null, name = cachedName ?: phoneNumber,
                            phoneNumber = phoneNumber, photoThumbnailUri = null,
                            lastCallType = callType, isFavorite = false, isUnknown = true
                        )
                    }
                    recents.add(Pair(recentContact, callDate))
                    Log.v(TAG, "Добавлен недавний: $recentContact (Дата: $callDate)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запроса CallLog для недавних: ${e.message}", e)
        } finally {
            cursor?.close()
        }
        Log.d(TAG, "--- Загрузка недавних завершена (Обработано уникальных номеров: ${recents.size}) ---")
        return recents
    }

    private fun loadFrequentContacts(resolver: ContentResolver, excludeContactIds: Set<Long>, excludeCleanPhoneNumbers: Set<String>): List<WidgetContact> {
        val frequentLimit = 500
        val daysToConsider = 60
        Log.d(TAG, "--- Загрузка частых контактов (Лимит журнала: $frequentLimit, Период: $daysToConsider дней) ---")
        Log.d(TAG, "Исключить IDs: $excludeContactIds")
        Log.d(TAG, "Исключить Номера: $excludeCleanPhoneNumbers")

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Нет разрешения READ_CALL_LOG для загрузки частых.")
            return emptyList()
        }

        val sixtyDaysAgoMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysToConsider) }.timeInMillis
        val callCounts = mutableMapOf<String, Pair<Int, Int>>()
        val numberToContactDetails = mutableMapOf<String, WidgetContact>()
        val projection = arrayOf( CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_LOOKUP_URI )
        val selection = "${CallLog.Calls.DATE} >= ? AND (${CallLog.Calls.TYPE} = ? OR ${CallLog.Calls.TYPE} = ?)"
        val selectionArgs = arrayOf( sixtyDaysAgoMillis.toString(), CallLog.Calls.INCOMING_TYPE.toString(), CallLog.Calls.OUTGOING_TYPE.toString() )
        val sortOrder = "${CallLog.Calls.DATE} DESC"
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, frequentLimit.toString())
            .build()
        var cursor: Cursor? = null

        try {
            cursor = resolver.query(queryUri, projection, selection, selectionArgs, sortOrder)
            cursor?.use { c ->
                val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                val nameIndex = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val lookupUriIndex = c.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)

                if (numberIndex == -1 || typeIndex == -1) {
                    Log.e(TAG, "Не найдены колонки NUMBER или TYPE в CallLog для частых.")
                    return@use
                }

                while (c.moveToNext()) {
                    val phoneNumber = c.getString(numberIndex)
                    if (phoneNumber.isNullOrEmpty()) continue
                    val cleanNumber = phoneNumber.replace(Regex("[()\\-\\s]"), "")
                    if (cleanNumber.isEmpty() || cleanNumber in excludeCleanPhoneNumbers) continue

                    val currentCallType = c.getInt(typeIndex)
                    val currentCountPair = callCounts.getOrDefault(cleanNumber, Pair(0, currentCallType))
                    callCounts[cleanNumber] = Pair(currentCountPair.first + 1, currentCountPair.second) // Тип не меняем, берем от самой свежей записи

                    if (!numberToContactDetails.containsKey(cleanNumber)) {
                        val cachedName = if (nameIndex != -1) c.getString(nameIndex) else null
                        val cachedLookupUriString = if (lookupUriIndex != -1) c.getString(lookupUriIndex) else null
                        var contact: WidgetContact? = null

                        if (!cachedLookupUriString.isNullOrEmpty()) {
                            try { contact = findContactByUri(resolver, Uri.parse(cachedLookupUriString), phoneNumber) } catch (e:Exception){}
                        }
                        if (contact == null) {
                            contact = findContactByNumber(resolver, phoneNumber)
                        }
                        if (contact != null && contact.contactId != -1L && contact.contactId in excludeContactIds) continue

                        val contactDetails = if (contact != null) {
                            contact.copy(isFavorite = false, isUnknown = false)
                        } else {
                            WidgetContact( -1L, null, cachedName ?: phoneNumber, phoneNumber, null, null, false, true )
                        }
                        numberToContactDetails[cleanNumber] = contactDetails
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запроса CallLog для частых: ${e.message}", e)
        } finally {
            cursor?.close()
        }

        val frequentContacts = callCounts
            .mapNotNull { (cleanNumber, countPair) ->
                numberToContactDetails[cleanNumber]?.let { contact ->
                    if (contact.contactId != -1L && contact.contactId in excludeContactIds) null
                    else contact.copy(callCount = countPair.first, lastCallType = countPair.second)
                }
            }
            .sortedByDescending { it.callCount }

        Log.d(TAG, "--- Загрузка частых завершена (Найдено: ${frequentContacts.size} уникальных номеров) ---")
        return frequentContacts
    }

    private fun loadFavoritesAndRecents(resolver: ContentResolver, favoriteUris: List<String>, maxItems: Int, showUnknown: Boolean, filterOldUnknown: Boolean): List<WidgetContact> {
        Log.d(TAG, "--- Объединение: Избранные + Недавние ---")
        val startTime = SystemClock.elapsedRealtime()

        val favorites = loadFavoriteContacts(resolver, favoriteUris)
        val favoriteContactIds = favorites.mapNotNull { if (it.contactId != -1L) it.contactId else null }.toSet()
        val favoritePhoneNumbers = favorites.mapNotNull { it.phoneNumber?.replace(Regex("[()\\-\\s]"), "") }.toSet()

        val recentsWithDate = loadRecentContacts(resolver)
        val uniqueRecentsWithDate = recentsWithDate.filter { pair ->
            val recent = pair.first
            val cleanNumber = recent.phoneNumber?.replace(Regex("[()\\-\\s]"), "")
            val isDuplicateById = recent.contactId != -1L && recent.contactId in favoriteContactIds
            val isDuplicateByNumber = cleanNumber != null && cleanNumber in favoritePhoneNumbers
            !(isDuplicateById || isDuplicateByNumber)
        }

        val twoDaysAgoMillis = if (filterOldUnknown) Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis else 0L
        val filteredRecents: List<WidgetContact> = if (filterOldUnknown) {
            uniqueRecentsWithDate
                .filter { pair ->
                    // <<< ИЗМЕНЕНИЕ: Исправляем Smart cast >>>
                    val callDate = pair.second
                    !pair.first.isUnknown || (callDate != null && callDate >= twoDaysAgoMillis)
                }
                .map { it.first }
        } else {
            uniqueRecentsWithDate.map { it.first }
        }

        val combinedList = favorites + filteredRecents
        val filteredListByShowUnknown = if (!showUnknown) combinedList.filter { !it.isUnknown } else combinedList
        val finalList = filteredListByShowUnknown.take(maxItems)

        Log.d(TAG, "--- Объединение Избранные+Недавние завершено (за ${SystemClock.elapsedRealtime() - startTime}мс). Final size: ${finalList.size} (Fav:${favorites.size}, Rec:${filteredRecents.size}) ---")
        return finalList
    }

    private fun loadFavoritesRecentsAndFrequent(resolver: ContentResolver, favoriteUris: List<String>, maxItems: Int, showUnknown: Boolean, filterOldUnknown: Boolean): List<WidgetContact> {
        Log.d(TAG, "--- Объединение: Избранные + Недавние + Частые ---")
        val startTime = SystemClock.elapsedRealtime()

        val favorites = loadFavoriteContacts(resolver, favoriteUris)
        val favoriteContactIds = favorites.mapNotNull { if(it.contactId != -1L) it.contactId else null }.toSet()
        val favoritePhoneNumbers = favorites.mapNotNull { it.phoneNumber?.replace(Regex("[()\\-\\s]"), "") }.toSet()

        val recentsWithDate = loadRecentContacts(resolver)
        val recentContactIds = mutableSetOf<Long>()
        val recentPhoneNumbers = mutableSetOf<String>()
        val uniqueRecentsWithDate = recentsWithDate.filter { pair ->
            val recent = pair.first
            val cleanNumber = recent.phoneNumber?.replace(Regex("[()\\-\\s]"), "")
            val isDuplicateById = recent.contactId != -1L && recent.contactId in favoriteContactIds
            val isDuplicateByNumber = cleanNumber != null && cleanNumber in favoritePhoneNumbers
            val isDuplicate = isDuplicateById || isDuplicateByNumber
            if (!isDuplicate) {
                if (recent.contactId != -1L) recentContactIds.add(recent.contactId)
                if (cleanNumber != null) recentPhoneNumbers.add(cleanNumber)
            }
            !isDuplicate
        }

        val twoDaysAgoMillis = if (filterOldUnknown) Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis else 0L
        val filteredRecents: List<WidgetContact> = if (filterOldUnknown) {
            uniqueRecentsWithDate.filter { pair ->
                // <<< ИЗМЕНЕНИЕ: Исправляем Smart cast >>>
                val callDate = pair.second
                !pair.first.isUnknown || (callDate != null && callDate >= twoDaysAgoMillis)
            }.map { it.first }
        } else {
            uniqueRecentsWithDate.map { it.first }
        }

        val remainingSlotsForFrequent = maxItems - favorites.size - filteredRecents.size
        val frequentContacts: List<WidgetContact>

        if (remainingSlotsForFrequent > 0) {
            val excludeIds = favoriteContactIds + recentContactIds
            val excludeNumbers = favoritePhoneNumbers + recentPhoneNumbers
            frequentContacts = loadFrequentContacts(resolver, excludeIds, excludeNumbers)
        } else {
            frequentContacts = emptyList()
        }

        val combinedList = favorites + filteredRecents + frequentContacts
        val filteredListByShowUnknown = if (!showUnknown) combinedList.filter { !it.isUnknown } else combinedList
        val finalList = filteredListByShowUnknown.take(maxItems)

        Log.d(TAG, "--- Объединение Избранные+Недавние+Частые завершено (за ${SystemClock.elapsedRealtime() - startTime}мс). Final: ${finalList.size} (Fav:${favorites.size}, Rec:${filteredRecents.size}, Freq:${frequentContacts.size}) ---")
        return finalList
    }

    private fun loadFavoritesAndFrequent(resolver: ContentResolver, favoriteUris: List<String>, maxItems: Int, showUnknown: Boolean): List<WidgetContact> {
        Log.d(TAG, "--- Объединение: Избранные + Частые ---")
        val startTime = SystemClock.elapsedRealtime()

        val favorites = loadFavoriteContacts(resolver, favoriteUris)
        val favoriteContactIds = favorites.mapNotNull { if(it.contactId != -1L) it.contactId else null }.toSet()
        val favoritePhoneNumbers = favorites.mapNotNull { it.phoneNumber?.replace(Regex("[()\\-\\s]"), "") }.toSet()

        val remainingSlotsForFrequent = maxItems - favorites.size
        val frequentContacts: List<WidgetContact>

        if (remainingSlotsForFrequent > 0) {
            frequentContacts = loadFrequentContacts(resolver, favoriteContactIds, favoritePhoneNumbers)
        } else {
            frequentContacts = emptyList()
        }

        val combinedList = favorites + frequentContacts
        val filteredListByShowUnknown = if (!showUnknown) combinedList.filter { !it.isUnknown } else combinedList
        val finalList = filteredListByShowUnknown.take(maxItems)

        Log.d(TAG, "--- Объединение Избранные+Частые завершено (за ${SystemClock.elapsedRealtime() - startTime}мс). Final: ${finalList.size} (Fav:${favorites.size}, Freq:${frequentContacts.size}) ---")
        return finalList
    }

    // --- Вспомогательные методы поиска контактов ---

    private fun findContactByNumber(resolver: ContentResolver, phoneNumber: String): WidgetContact? {
        if (phoneNumber.isBlank()) return null
        Log.v(TAG, "Поиск контакта по номеру: $phoneNumber")
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf( ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI )
        var contact: WidgetContact? = null
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val photoUriIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)

                if (idIndex != -1 && lookupKeyIndex != -1 && nameIndex != -1 && photoUriIndex != -1) {
                    contact = WidgetContact( cursor.getLong(idIndex), cursor.getString(lookupKeyIndex), cursor.getString(nameIndex), phoneNumber, cursor.getString(photoUriIndex), isUnknown = false )
                    Log.v(TAG, "Контакт найден по номеру: $contact")
                } else { Log.w(TAG, "Не найдены все колонки в PhoneLookup для номера $phoneNumber") }
            } else { Log.v(TAG, "Контакт по номеру $phoneNumber не найден.") }
        } catch (e: Exception) { Log.e(TAG, "Ошибка поиска контакта по номеру $phoneNumber: ${e.message}") }
        finally { cursor?.close() }
        return contact
    }

    private fun findContactByUri(resolver: ContentResolver, lookupUri: Uri, fallbackPhoneNumber: String?): WidgetContact? {
        Log.v(TAG, "Поиск контакта по URI: $lookupUri")
        val projection = arrayOf( ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI )
        var contact: WidgetContact? = null
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(lookupUri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

                if (idIndex != -1 && lookupKeyIndex != -1 && nameIndex != -1 && photoUriIndex != -1) {
                    contact = WidgetContact( cursor.getLong(idIndex), cursor.getString(lookupKeyIndex), cursor.getString(nameIndex), fallbackPhoneNumber, cursor.getString(photoUriIndex), isUnknown = false )
                    Log.v(TAG, "Контакт найден по URI: $contact")
                } else { Log.w(TAG, "Не найдены все колонки в Contacts для URI $lookupUri") }
            } else { Log.v(TAG, "Контакт по URI $lookupUri не найден.") }
        } catch (e: Exception) { Log.e(TAG, "Ошибка поиска контакта по URI $lookupUri: ${e.message}") }
        finally { cursor?.close() }
        return contact
    }

    private fun fetchFirstPhoneNumber(resolver: ContentResolver, contactId: String): String? {
        if (contactId == "-1") return null
        var phoneNumber: String? = null
        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val phoneSelection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        var phoneCursor: Cursor? = null
        try {
            phoneCursor = resolver.query(phoneUri, phoneProjection, phoneSelection, arrayOf(contactId), null)
            if (phoneCursor?.moveToFirst() == true) {
                val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    phoneNumber = phoneCursor.getString(numberIndex)
                    Log.v(TAG, "Найден номер $phoneNumber для контакта ID $contactId")
                }
            } else { Log.w(TAG,"Номера не найдены для контакта ID $contactId") }
        } catch (e: Exception) { Log.e(TAG, "Ошибка получения номера для контакта $contactId: ${e.message}", e) }
        finally { phoneCursor?.close() }
        return phoneNumber
    }

    // --- Методы интерфейса RemoteViewsFactory ---

    override fun getCount(): Int = widgetContacts.size

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int {
        // <<< ИЗМЕНЕНИЕ: Используем константу из WidgetConfigureActivity >>>
        // +1 для макета по умолчанию/ошибки
        return WidgetConfigureActivity.supportedHeights.size + 1
    }

    override fun getItemId(position: Int): Long {
        val contact = widgetContacts.getOrNull(position)
        return contact?.contactId?.takeIf { it != -1L }
            ?: contact?.phoneNumber?.hashCode()?.toLong()
            ?: position.toLong()
    }

    override fun hasStableIds(): Boolean = true

    private fun getCallTypeIcon(callType: Int?): Int {
        return when (callType) {
            CallLog.Calls.INCOMING_TYPE -> R.drawable.incoming
            CallLog.Calls.OUTGOING_TYPE -> R.drawable.outgoing
            CallLog.Calls.MISSED_TYPE -> R.drawable.missed
            CallLog.Calls.REJECTED_TYPE -> R.drawable.rejected
            else -> R.drawable.unknown
        }
    }

    override fun getViewAt(position: Int): RemoteViews? {
        if (position < 0 || position >= widgetContacts.size) {
            Log.w(TAG, "getViewAt: Невалидная позиция $position (Размер списка: ${widgetContacts.size}). Возвращаем пустой View.")
            return RemoteViews(appContext.packageName, itemLayoutId)
        }

        val contact = widgetContacts[position]
        Log.v(TAG, "getViewAt START для pos $position, contact: $contact")
        val views = RemoteViews(appContext.packageName, itemLayoutId)

        try {
            views.setTextViewText(R.id.widget_item_name, contact.name ?: contact.phoneNumber ?: appContext.getString(R.string.unknown))
            views.setImageViewResource(R.id.widget_item_photo, defaultPlaceholderResId)
            val targetHeightPx = dpToPx(appContext, itemHeightDp)
            val targetWidthPx = (targetHeightPx * 0.85).roundToInt()
            var bitmap: Bitmap? = null

            bitmap = loadContactPhotoWithGlide(
                appContext,
                contact.contactId.takeIf { it != -1L },
                contact.photoThumbnailUri,
                targetWidthPx,
                targetHeightPx
            )

            if (bitmap != null && !bitmap.isRecycled) {
                views.setImageViewBitmap(R.id.widget_item_photo, bitmap)
                Log.v(TAG, "-> Установлен bitmap для pos $position")
            } else {
                Log.w(TAG, "-> Не удалось загрузить фото для pos $position. Используется fallback/placeholder.")
                loadFallbackAvatar(views, contact.isUnknown, targetWidthPx, targetHeightPx)
            }

            val iconResIdToShow = getCallTypeIcon(contact.lastCallType)
            views.setImageViewResource(R.id.widget_item_call_type_icon, iconResIdToShow)
            views.setViewVisibility(R.id.widget_item_call_type_icon, View.VISIBLE)
            views.setInt(R.id.widget_item_call_type_icon, "setImageAlpha", if (contact.lastCallType != null) 255 else 128)

            val fillInIntent = Intent().apply {
                val extras = Bundle()
                // <<< ИЗМЕНЕНИЕ: Используем прямой доступ к константам >>>
                extras.putString(ContactsGridWidgetProvider.EXTRA_PHONE_NUMBER, contact.phoneNumber ?: "")
                extras.putString(ContactsGridWidgetProvider.EXTRA_CONTACT_NAME, contact.name ?: appContext.getString(R.string.unknown))
                extras.putString(ContactsGridWidgetProvider.EXTRA_PHOTO_URI, contact.photoThumbnailUri ?: "")
                extras.putLong(ContactsGridWidgetProvider.EXTRA_CONTACT_ID, contact.contactId)
                extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtras(extras)
            }
            views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        } catch (e: Exception) {
            Log.e(TAG, "!!! ИСКЛЮЧЕНИЕ в getViewAt для pos $position, contact: $contact !!!", e)
            return RemoteViews(appContext.packageName, itemLayoutId)
        }

        Log.v(TAG, "getViewAt END для pos $position")
        return views
    }

    // --- Вспомогательные методы ---

    private fun loadFallbackAvatar(views: RemoteViews, isUnknown: Boolean, targetWidthPx: Int, targetHeightPx: Int) {
        Log.d(TAG, "Загрузка fallback аватара. Mode: $unknownAvatarMode, Custom URI: $unknownAvatarCustomUri")
        var fallbackBitmap: Bitmap? = null
        if (unknownAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && !unknownAvatarCustomUri.isNullOrEmpty()) {
            try {
                val customUri = Uri.parse(unknownAvatarCustomUri)
                Log.v(TAG, "-> Попытка загрузки КАСТОМНОГО аватара: $customUri")
                fallbackBitmap = Glide.with(appContext)
                    .asBitmap().load(customUri)
                    .apply( RequestOptions()
                        .override(targetWidthPx, targetHeightPx).centerCrop()
                        .diskCacheStrategy(DiskCacheStrategy.NONE).skipMemoryCache(true)
                        .error(defaultPlaceholderResId) )
                    .submit().get()

                if (fallbackBitmap != null) Log.v(TAG, "-> Кастомный аватар fallback загружен.")
                else Log.w(TAG, "-> Кастомный аватар fallback НЕ загружен.")
            } catch (e: Exception) { Log.e(TAG, "-> Ошибка загрузки КАСТОМНОГО аватара URI $unknownAvatarCustomUri", e) }
        }

        if (fallbackBitmap != null && !fallbackBitmap.isRecycled) {
            views.setImageViewBitmap(R.id.widget_item_photo, fallbackBitmap)
        } else {
            views.setImageViewResource(R.id.widget_item_photo, defaultPlaceholderResId)
            Log.w(TAG, "-> Установлен дефолтный placeholder как fallback.")
        }
    }

    private fun updateVisualSettings() {
        itemHeightDp = WidgetPrefsUtils.loadItemHeight(appContext, appWidgetId)
        itemLayoutId = getLayoutIdForHeight(itemHeightDp)
        Log.d(TAG, "Визуальные настройки обновлены: Height=${itemHeightDp}dp, LayoutId=$itemLayoutId")
    }

    private fun getLayoutIdForHeight(heightDp: Int): Int {
        var layoutId = layoutIdCache.get(heightDp)
        if (layoutId == null || layoutId == 0) {
            layoutId = when (heightDp) {
                70 -> R.layout.widget_item_h70; 80 -> R.layout.widget_item_h80
                90 -> R.layout.widget_item_h90; 100 -> R.layout.widget_item_h100
                110 -> R.layout.widget_item_h110; 120 -> R.layout.widget_item_h120
                130 -> R.layout.widget_item_h130; 140 -> R.layout.widget_item_h140
                150 -> R.layout.widget_item_h150; 160 -> R.layout.widget_item_h160
                else -> R.layout.widget_item_default_height
            }
            layoutIdCache.put(heightDp, layoutId)
            Log.v(TAG,"Layout ID для высоты $heightDp dp определен как $layoutId и закэширован.")
        } else {
            Log.v(TAG,"Layout ID для высоты $heightDp dp взят из кэша: $layoutId.")
        }
        return layoutId
    }

    private fun loadContactPhotoWithGlide(
        context: Context,
        contactId: Long?,
        thumbnailUriString: String?,
        targetWidthPx: Int,
        targetHeightPx: Int
    ): Bitmap? {
        val primaryUri: Uri? = contactId?.let {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, it)
            Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO)
        }
        val fallbackUri: Uri? = thumbnailUriString?.let { try { Uri.parse(it) } catch (e: Exception) { null } }
        Log.v(TAG, "loadContactPhoto: ID=$contactId, PrimaryUri=$primaryUri, FallbackUri=$fallbackUri")

        val width = if (targetWidthPx > 0) targetWidthPx else 100
        val height = if (targetHeightPx > 0) targetHeightPx else 100
        val requestOptions = RequestOptions()
            .override(width, height).centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL).skipMemoryCache(false)
            .placeholder(defaultPlaceholderResId)

        var bitmap: Bitmap? = null

        if (primaryUri != null) {
            try {
                bitmap = Glide.with(context).asBitmap().load(primaryUri)
                    .apply(requestOptions.clone().error(null)).submit().get()
                if (bitmap != null) Log.v(TAG, "Успешно загружен primaryUri: $primaryUri")
            } catch (e: Exception) {
                if (e !is FileNotFoundException && e !is IOException) Log.w(TAG, "Glide ошибка загрузки primaryUri '$primaryUri': ${e.javaClass.simpleName} - ${e.message}")
                else Log.v(TAG, "primaryUri '$primaryUri' не найден (FileNotFound or IO), пробуем fallback.")
                bitmap = null
            } catch (oom: OutOfMemoryError) { Log.e(TAG, "Glide OOM при загрузке primaryUri '$primaryUri'"); bitmap = null }
        }

        if (bitmap == null && fallbackUri != null) {
            Log.v(TAG, "Пробуем загрузить fallbackUri: $fallbackUri")
            try {
                bitmap = Glide.with(context).asBitmap().load(fallbackUri)
                    .apply(requestOptions.clone().error(defaultPlaceholderResId)).submit().get()
                if (bitmap != null) Log.v(TAG, "Успешно загружен fallbackUri: $fallbackUri")
            } catch (e: Exception) {
                if (e !is FileNotFoundException && e !is IOException) Log.w(TAG, "Glide ошибка загрузки fallbackUri '$fallbackUri': ${e.javaClass.simpleName} - ${e.message}")
                else Log.v(TAG, "fallbackUri '$fallbackUri' не найден (FileNotFound or IO).")
                bitmap = null
            } catch (oom: OutOfMemoryError) { Log.e(TAG, "Glide OOM при загрузке fallbackUri '$fallbackUri'"); bitmap = null }
        }

        return bitmap
    }

    private fun dpToPx(context: Context?, dp: Int): Int {
        if (context == null) return dp
        return (dp * context.resources.displayMetrics.density).roundToInt()
    }
}