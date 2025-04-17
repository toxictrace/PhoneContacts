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
import java.util.Calendar
import kotlin.math.roundToInt

private const val TAG = "RVFactory"

class ContactsGridRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    ).also {
        Log.d(TAG, "Factory created for widget ID: $it")
    }

    private val defaultPlaceholderResId = R.drawable.anonim

    private data class WidgetContact(
        val contactId: Long,
        val lookupKey: String?,
        val name: String?,
        val phoneNumber: String?,
        val photoThumbnailUri: String?,
        val lastCallType: Int? = null,
        val isFavorite: Boolean = false,
        val isUnknown: Boolean = false,
        val callCount: Int = 0
    ) {
        val contentUri: Uri? get() = lookupKey?.let { ContactsContract.Contacts.getLookupUri(contactId, it) }
        override fun toString(): String {
            val typeStr = when(lastCallType) {
                CallLog.Calls.INCOMING_TYPE -> "IN"
                CallLog.Calls.OUTGOING_TYPE -> "OUT"
                CallLog.Calls.MISSED_TYPE -> "MISSED"
                CallLog.Calls.REJECTED_TYPE -> "REJ"
                null -> "NULL"
                else -> "TYPE($lastCallType)"
            }
            return "ID=$contactId, Name=$name, Phone=$phoneNumber, Type=$typeStr, IsFav=$isFavorite, IsUnknown=$isUnknown, Calls=$callCount"
        }
    }

    private var widgetContacts: List<WidgetContact> = emptyList()
    private var itemHeightDp: Int = WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT
    private var itemLayoutId: Int = R.layout.widget_item_default_height
    private val layoutIdCache = SparseArray<Int>()
    private var unknownAvatarMode: String = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_MODE
    private var unknownAvatarCustomUri: String? = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_CUSTOM_URI

    override fun onCreate() { Log.d(TAG, "onCreate for widget $appWidgetId") }

    override fun onDataSetChanged() {
        Log.i(TAG, "====== onDataSetChanged START for widget $appWidgetId ======")
        val startTime = SystemClock.elapsedRealtime()
        updateVisualSettings()
        val sortOrder = WidgetPrefsUtils.loadSortOrder(context, appWidgetId)
        val maxItems = WidgetPrefsUtils.loadMaxItems(context, appWidgetId)
        val favoriteContactUris = WidgetPrefsUtils.loadContactUris(context, appWidgetId)
        val showUnknown = WidgetPrefsUtils.loadShowUnknown(context, appWidgetId)
        val filterOldUnknown = WidgetPrefsUtils.loadFilterOldUnknown(context, appWidgetId)
        val compareDigits = WidgetPrefsUtils.loadCompareDigitsCount(context, appWidgetId)
        unknownAvatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(context, appWidgetId)
        unknownAvatarCustomUri = WidgetPrefsUtils.loadUnknownAvatarCustomUri(context, appWidgetId)
        Log.d(TAG, "Widget $appWidgetId - Settings: Sort='$sortOrder', MaxItems=$maxItems, FavURIs=${favoriteContactUris.size}, ShowUnknown=$showUnknown, FilterOldUnknown=$filterOldUnknown, CompareDigits=$compareDigits, AvatarMode=$unknownAvatarMode, AvatarUri=$unknownAvatarCustomUri")
        val resolver = context.contentResolver
        val identityToken = Binder.clearCallingIdentity()
        var loadedContacts: List<WidgetContact> = emptyList()
        try {
            // <<< ИЗМЕНЕНИЕ: Обработка нового порядка сортировки >>>
            loadedContacts = when (sortOrder) {
                WidgetPrefsUtils.SORT_ORDER_FAVORITES -> loadFavoriteContacts(resolver, favoriteContactUris).take(maxItems)
                WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS -> loadFavoritesAndRecents(resolver, favoriteContactUris, maxItems, showUnknown, filterOldUnknown)
                WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT -> loadFavoritesRecentsAndFrequent(resolver, favoriteContactUris, maxItems, showUnknown, filterOldUnknown)
                WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT -> loadFavoritesAndFrequent(resolver, favoriteContactUris, maxItems, showUnknown) // <<< НОВЫЙ ВЫЗОВ >>>
                else -> { Log.w(TAG, "Unknown sort order '$sortOrder'."); emptyList() }
            }
            // <<< КОНЕЦ ИЗМЕНЕНИЯ >>>
            widgetContacts = loadedContacts
        } catch (e: SecurityException) { Log.e(TAG, "SecurityException: ${e.message}.", e); widgetContacts = emptyList() }
        catch (e: Exception) { Log.e(TAG, "Exception: ${e.message}", e); widgetContacts = emptyList() }
        finally { Binder.restoreCallingIdentity(identityToken); Log.i(TAG, "====== onDataSetChanged END (took ${SystemClock.elapsedRealtime() - startTime}ms) ======") }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy for widget $appWidgetId");
        widgetContacts = emptyList()
    }

    // --- Методы загрузки данных ---

    private fun loadFavoriteContacts(resolver: ContentResolver, favoriteUris: List<String>): List<WidgetContact> {
        Log.d(TAG, "--- loadFavoriteContacts START (URIs: ${favoriteUris.size}) ---")
        val favorites = mutableListOf<WidgetContact>()
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.HAS_PHONE_NUMBER)
        val hasCallLogPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        favoriteUris.forEachIndexed { index, uriString ->
            val contactUri = Uri.parse(uriString); var cursor: Cursor? = null
            try {
                cursor = resolver.query(contactUri, projection, null, null, null)
                if (cursor?.moveToFirst() == true) {
                    val idIndex=cursor.getColumnIndex(ContactsContract.Contacts._ID); val lookupKeyIndex=cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY); val nameIndex=cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY); val photoUriIndex=cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI); val hasPhoneIndex=cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    if (idIndex!=-1&&lookupKeyIndex!=-1&&nameIndex!=-1&&photoUriIndex!=-1&&hasPhoneIndex!=-1) {
                        val id=cursor.getLong(idIndex); val lookupKey=cursor.getString(lookupKeyIndex); val name=cursor.getString(nameIndex); val photoThumbnailUri=cursor.getString(photoUriIndex); val hasPhoneNumber=cursor.getInt(hasPhoneIndex)>0; var phoneNumber:String?=null; var lastCallType: Int? = null
                        if(hasPhoneNumber){ phoneNumber=fetchFirstPhoneNumber(resolver,id.toString()) }
                        if(phoneNumber!=null){
                            if (hasCallLogPerm) { lastCallType = fetchLastCallTypeForNumber(resolver, phoneNumber) }
                            val contact=WidgetContact(id,lookupKey,name,phoneNumber,photoThumbnailUri, lastCallType = lastCallType, isFavorite = true, isUnknown = false); favorites.add(contact);
                        } else {Log.w(TAG,"Fav ID=$id, Name=$name has no phone number. Skipping.")}
                    } else {Log.e(TAG,"Column index error for fav URI $uriString")}
                } else {Log.w(TAG,"Could not find contact for fav URI $uriString")}
            } catch (e: Exception) {Log.e(TAG,"Error querying fav contact $uriString: ${e.message}", e)}
            finally {cursor?.close()}
        }
        Log.d(TAG, "--- loadFavoriteContacts END (Loaded: ${favorites.size}) ---")
        return favorites
    }

    private fun fetchLastCallTypeForNumber(resolver: ContentResolver, numberToCompare: String): Int? {
        val digitsToCompare = WidgetPrefsUtils.loadCompareDigitsCount(context, appWidgetId)
        val normalizedNumberToCompareLastN = getLastNdigits(numberToCompare, digitsToCompare)
        if (normalizedNumberToCompareLastN.length < 7) { return null }
        var callTypeResult: Int? = null; val sortOrder = "${CallLog.Calls.DATE} DESC"; val queryLimit = 250
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, queryLimit.toString()).build()
        val projection = arrayOf(CallLog.Calls.TYPE, CallLog.Calls.NUMBER); var cursor: Cursor? = null
        try {
            cursor = resolver.query(queryUri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE); val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
                if (typeIndex != -1 && numberIndex != -1) {
                    while (c.moveToNext() && callTypeResult == null) {
                        val callLogNumberRaw = c.getString(numberIndex) ?: continue
                        val callLogNumberLastN = getLastNdigits(callLogNumberRaw, digitsToCompare)
                        if (callLogNumberLastN.length >= 7 && callLogNumberLastN == normalizedNumberToCompareLastN) { callTypeResult = c.getInt(typeIndex); break }
                    }
                } else { Log.w(TAG, "CallLog TYPE or NUMBER column not found.") }
            }
        } catch (e: Exception) { Log.e(TAG, "Error fetching last call type for $numberToCompare: ${e.message}"); return null }
        finally { cursor?.close() }
        return callTypeResult
    }

    private fun getLastNdigits(number: String?, n: Int): String {
        if (number.isNullOrEmpty()) return ""
        return number.replace(Regex("\\D"), "").takeLast(n)
    }

    private fun loadRecentContacts(resolver: ContentResolver): List<Pair<WidgetContact, Long?>> {
        val recentsLimit = 250; Log.d(TAG, "--- loadRecentContacts START (Limit: $recentsLimit) ---")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "READ_CALL_LOG denied."); return emptyList() }
        val recents = mutableListOf<Pair<WidgetContact, Long?>>(); val uniqueCleanPhoneNumbers = mutableSetOf<String>()
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_LOOKUP_URI, CallLog.Calls.DATE, CallLog.Calls.TYPE); val sortOrder = "${CallLog.Calls.DATE} DESC"
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, recentsLimit.toString()).build()
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(queryUri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val numberIndex=c.getColumnIndex(CallLog.Calls.NUMBER); val nameIndex=c.getColumnIndex(CallLog.Calls.CACHED_NAME); val lookupUriIndex=c.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI); val dateIndex=c.getColumnIndex(CallLog.Calls.DATE); val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                if (numberIndex == -1 || typeIndex == -1 || dateIndex == -1) { Log.e(TAG, "CallLog columns not found."); return@use }
                while (c.moveToNext()) {
                    val phoneNumber=c.getString(numberIndex); val callDate=c.getLong(dateIndex); val callType = c.getInt(typeIndex)
                    if (phoneNumber.isNullOrEmpty()) continue
                    val cleanNumber = phoneNumber.replace(Regex("[()\\-\\s]"), "")
                    if (cleanNumber.isEmpty() || !uniqueCleanPhoneNumbers.add(cleanNumber)) continue
                    val cachedName = if(nameIndex!=-1)c.getString(nameIndex)else null; val cachedLookupUriString=if(lookupUriIndex!=-1)c.getString(lookupUriIndex)else null; var contact: WidgetContact? = null
                    if(cachedLookupUriString!=null){ contact=findContactByUri(resolver,Uri.parse(cachedLookupUriString),phoneNumber) }
                    if(contact==null){ contact=findContactByNumber(resolver,phoneNumber) }
                    val recentContact: WidgetContact = if(contact!=null){ contact.copy(lastCallType = callType, isFavorite = false, isUnknown = false) }
                    else{ WidgetContact(-1L,null,cachedName?:phoneNumber,phoneNumber,null, callType, isFavorite = false, isUnknown = true) }
                    recents.add(Pair(recentContact, callDate))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error querying CallLog: ${e.message}", e) }
        finally { cursor?.close() }
        Log.d(TAG, "--- loadRecentContacts END (Found: ${recents.size} unique numbers processed) ---")
        return recents
    }

    private fun loadFrequentContacts(resolver: ContentResolver, excludeContactIds: Set<Long>, excludePhoneNumbers: Set<String>): List<WidgetContact> {
        val frequentLimit = 500
        val daysToConsider = 60
        Log.d(TAG, "--- loadFrequentContacts START (Limit: $frequentLimit entries, Period: $daysToConsider days) ---")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG denied. Cannot load frequent contacts.")
            return emptyList()
        }
        val sixtyDaysAgoMillis = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysToConsider)
        }.timeInMillis
        val callCounts = mutableMapOf<String, Pair<Int, Int?>>()
        val numberToContactDetails = mutableMapOf<String, WidgetContact>()
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_LOOKUP_URI)
        val selection = "${CallLog.Calls.DATE} >= ? AND (${CallLog.Calls.TYPE} = ? OR ${CallLog.Calls.TYPE} = ?)"
        val selectionArgs = arrayOf(sixtyDaysAgoMillis.toString(), CallLog.Calls.INCOMING_TYPE.toString(), CallLog.Calls.OUTGOING_TYPE.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC"
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, frequentLimit.toString())
            .build()
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(queryUri, projection, selection, selectionArgs, sortOrder)
            cursor?.use { c ->
                val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER); val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                val nameIndex = c.getColumnIndex(CallLog.Calls.CACHED_NAME); val lookupUriIndex = c.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI)
                if (numberIndex == -1 || typeIndex == -1) { Log.e(TAG, "CallLog columns NUMBER or TYPE not found for frequent contacts."); return@use }
                while (c.moveToNext()) {
                    val phoneNumber = c.getString(numberIndex); if (phoneNumber.isNullOrEmpty()) continue
                    val cleanNumber = phoneNumber.replace(Regex("[()\\-\\s]"), ""); if (cleanNumber.isEmpty()) continue
                    if (cleanNumber in excludePhoneNumbers) continue // Пропускаем номера, которые уже есть в исключенных
                    val currentCountPair = callCounts.getOrDefault(cleanNumber, Pair(0, null))
                    val lastType = currentCountPair.second ?: c.getInt(typeIndex)
                    callCounts[cleanNumber] = Pair(currentCountPair.first + 1, lastType)
                    if (!numberToContactDetails.containsKey(cleanNumber)) {
                        val cachedName = if (nameIndex != -1) c.getString(nameIndex) else null; val cachedLookupUriString = if (lookupUriIndex != -1) c.getString(lookupUriIndex) else null
                        var contact: WidgetContact? = null; if (cachedLookupUriString != null) { contact = findContactByUri(resolver, Uri.parse(cachedLookupUriString), phoneNumber) }; if (contact == null) { contact = findContactByNumber(resolver, phoneNumber) }
                        if (contact != null && contact.contactId in excludeContactIds) continue // Пропускаем, если ID уже в исключенных
                        val contactDetails = if (contact != null) { contact.copy(isFavorite = false, isUnknown = false) } else { WidgetContact(-1L, null, cachedName ?: phoneNumber, phoneNumber, null, null, isFavorite = false, isUnknown = true) }
                        numberToContactDetails[cleanNumber] = contactDetails
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error querying CallLog for frequent contacts: ${e.message}", e) }
        finally { cursor?.close() }
        val frequentContacts = callCounts.mapNotNull { (cleanNumber, countPair) ->
            numberToContactDetails[cleanNumber]?.let { contact ->
                if (contact.contactId != -1L && contact.contactId in excludeContactIds) { null }
                else { contact.copy(callCount = countPair.first, lastCallType = countPair.second) }
            }
        }.sortedByDescending { it.callCount }
        Log.d(TAG, "--- loadFrequentContacts END (Found: ${frequentContacts.size} unique numbers) ---")
        return frequentContacts
    }

    private fun loadFavoritesAndRecents(resolver: ContentResolver, favoriteUris: List<String>, maxItems: Int, showUnknown: Boolean, filterOldUnknown: Boolean): List<WidgetContact> {
        Log.d(TAG, "--- loadFavoritesAndRecents START ---")
        val favorites = loadFavoriteContacts(resolver, favoriteUris)
        val recentsWithDate = loadRecentContacts(resolver)
        val favoriteContactIds = favorites.map { it.contactId }.toSet()
        val favoritePhoneNumbers = favorites.mapNotNull { it.phoneNumber?.replace(Regex("[()\\-\\s]"), "") }.toSet()
        val uniqueRecentsWithDate = recentsWithDate.filter { pair ->
            val recent = pair.first; !( (recent.contactId != -1L && recent.contactId in favoriteContactIds) || (recent.phoneNumber?.replace(Regex("[()\\-\\s]"), "")?.let{ it in favoritePhoneNumbers } == true) )
        }
        val twoDaysAgoMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis
        val filteredRecents: List<WidgetContact> = if (filterOldUnknown) { uniqueRecentsWithDate.filter { pair -> !pair.first.isUnknown || (pair.second != null && pair.second!! >= twoDaysAgoMillis) }.map { it.first } }
        else { uniqueRecentsWithDate.map { it.first } }
        val combinedList = favorites + filteredRecents
        val filteredListByShowUnknown = if (!showUnknown) { combinedList.filter { !it.isUnknown } } else { combinedList }
        val finalList = filteredListByShowUnknown.take(maxItems)
        Log.d(TAG, "--- loadFavoritesAndRecents END (Final list size: ${finalList.size}) ---")
        return finalList
    }

    private fun loadFavoritesRecentsAndFrequent(resolver: ContentResolver, favoriteUris: List<String>, maxItems: Int, showUnknown: Boolean, filterOldUnknown: Boolean): List<WidgetContact> {
        Log.d(TAG, "--- loadFavoritesRecentsAndFrequent START ---")
        val favorites = loadFavoriteContacts(resolver, favoriteUris); val favoriteContactIds = favorites.map { it.contactId }.toSet(); val favoritePhoneNumbers = favorites.mapNotNull { it.phoneNumber?.replace(Regex("[()\\-\\s]"), "") }.toSet()
        val recentsWithDate = loadRecentContacts(resolver); val recentContactIds = mutableSetOf<Long>(); val recentPhoneNumbers = mutableSetOf<String>()
        val uniqueRecentsWithDate = recentsWithDate.filter { pair ->
            val recent = pair.first; val cleanNumber = recent.phoneNumber?.replace(Regex("[()\\-\\s]"), ""); val isDuplicate = (recent.contactId != -1L && recent.contactId in favoriteContactIds) || (cleanNumber != null && cleanNumber in favoritePhoneNumbers)
            if (!isDuplicate) { if (recent.contactId != -1L) recentContactIds.add(recent.contactId); if (cleanNumber != null) recentPhoneNumbers.add(cleanNumber) }
            !isDuplicate
        }
        val twoDaysAgoMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis
        val filteredRecents: List<WidgetContact> = if (filterOldUnknown) { uniqueRecentsWithDate.filter { pair -> !pair.first.isUnknown || (pair.second != null && pair.second!! >= twoDaysAgoMillis) }.map { it.first } } else { uniqueRecentsWithDate.map { it.first } }
        val remainingSlotsForFrequent = maxItems - favorites.size - filteredRecents.size
        val frequentContacts: List<WidgetContact> = if (remainingSlotsForFrequent > 0) {
            val excludeIds = favoriteContactIds + recentContactIds; val excludeNumbers = favoritePhoneNumbers + recentPhoneNumbers; loadFrequentContacts(resolver, excludeIds, excludeNumbers)
        } else { emptyList() }
        val combinedList = favorites + filteredRecents + frequentContacts
        val filteredListByShowUnknown = if (!showUnknown) { combinedList.filter { !it.isUnknown } } else { combinedList }
        val finalList = filteredListByShowUnknown.take(maxItems)
        Log.d(TAG, "--- loadFavoritesRecentsAndFrequent END (Fav: ${favorites.size}, Rec: ${filteredRecents.size}, Freq: ${frequentContacts.size}, Final: ${finalList.size}) ---")
        return finalList
    }

    // <<< НОВЫЙ МЕТОД: Комбинирует Избранные и Частые >>>
    private fun loadFavoritesAndFrequent(resolver: ContentResolver, favoriteUris: List<String>, maxItems: Int, showUnknown: Boolean): List<WidgetContact> {
        Log.d(TAG, "--- loadFavoritesAndFrequent START ---")

        // 1. Загружаем избранные
        val favorites = loadFavoriteContacts(resolver, favoriteUris)
        val favoriteContactIds = favorites.map { it.contactId }.toSet()
        val favoritePhoneNumbers = favorites.mapNotNull { it.phoneNumber?.replace(Regex("[()\\-\\s]"), "") }.toSet()

        // 2. Определяем, сколько места осталось для частых
        val remainingSlotsForFrequent = maxItems - favorites.size

        // 3. Загружаем частые, если есть место, исключая избранные
        val frequentContacts: List<WidgetContact> = if (remainingSlotsForFrequent > 0) {
            // Передаем ID и номера избранных для исключения
            loadFrequentContacts(resolver, favoriteContactIds, favoritePhoneNumbers)
        } else {
            emptyList()
        }

        // 4. Объединяем списки: Избранные + Частые (уникальные, отсортированные)
        val combinedList = favorites + frequentContacts

        // 5. Применяем фильтр "Показывать неизвестные" ко всему списку
        //    (В данном режиме это актуально только для частых, т.к. избранные всегда известны)
        val filteredListByShowUnknown = if (!showUnknown) {
            combinedList.filter { !it.isUnknown }
        } else {
            combinedList
        }

        // 6. Ограничиваем финальный список по maxItems
        val finalList = filteredListByShowUnknown.take(maxItems)

        Log.d(TAG, "--- loadFavoritesAndFrequent END (Fav: ${favorites.size}, Freq: ${frequentContacts.size}, Final: ${finalList.size}) ---")
        return finalList
    }
    // <<< КОНЕЦ НОВОГО МЕТОДА >>>

    private fun findContactByNumber(resolver: ContentResolver, phoneNumber: String): WidgetContact? {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)); val projection = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI); var contact: WidgetContact? = null; var cursor: Cursor? = null;
        try {
            cursor = resolver.query(uri, projection, null, null, null);
            if (cursor?.moveToFirst() == true) {
                val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID); val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY); val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME); val photoUriIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI);
                if (idIndex != -1 && lookupKeyIndex != -1 && nameIndex != -1 && photoUriIndex != -1) { contact = WidgetContact(cursor.getLong(idIndex), cursor.getString(lookupKeyIndex), cursor.getString(nameIndex), phoneNumber, cursor.getString(photoUriIndex), isUnknown = false) }
            }
        } catch (e: Exception) { Log.e(TAG, "Error finding contact by number $phoneNumber: ${e.message}") }
        finally { cursor?.close() }; return contact
    }

    private fun findContactByUri(resolver: ContentResolver, lookupUri: Uri, fallbackPhoneNumber: String?): WidgetContact? {
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI); var contact: WidgetContact? = null; var cursor: Cursor? = null;
        try {
            cursor = resolver.query(lookupUri, projection, null, null, null);
            if (cursor?.moveToFirst() == true) {
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID); val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY); val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY); val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI);
                if (idIndex != -1 && lookupKeyIndex != -1 && nameIndex != -1 && photoUriIndex != -1) { contact = WidgetContact(cursor.getLong(idIndex), cursor.getString(lookupKeyIndex), cursor.getString(nameIndex), fallbackPhoneNumber, cursor.getString(photoUriIndex), isUnknown = false) }
            }
        } catch (e: Exception) { Log.e(TAG, "Error finding contact by URI $lookupUri: ${e.message}") }
        finally { cursor?.close() }; return contact
    }

    private fun fetchFirstPhoneNumber(resolver: ContentResolver, contactId: String): String? {
        var phoneNumber: String? = null; val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI; val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER); val phoneSelection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"; var phoneCursor: Cursor? = null;
        try {
            phoneCursor = resolver.query(phoneUri, phoneProjection, phoneSelection, arrayOf(contactId), null);
            if (phoneCursor?.moveToFirst() == true) { val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER); if (numberIndex != -1) { phoneNumber = phoneCursor.getString(numberIndex) } }
        } catch (e: Exception) { Log.e(TAG, "Error fetching phone number for contact $contactId: ${e.message}", e) }
        finally { phoneCursor?.close() }; return phoneNumber
    }

    // --- Методы интерфейса RemoteViewsFactory ---

    override fun getCount(): Int = widgetContacts.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = WidgetConfigureActivity.supportedHeights.size + 1
    override fun getItemId(position: Int): Long {
        val contact = widgetContacts.getOrNull(position)
        return contact?.contactId?.takeIf { it != -1L } ?: contact?.phoneNumber?.hashCode()?.toLong() ?: position.toLong()
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
            Log.w(TAG, "getViewAt invalid pos $position"); return RemoteViews(context.packageName, itemLayoutId)
        }
        val contact = widgetContacts[position]
        Log.v(TAG, "getViewAt START for pos $position, contact: $contact")
        val views = RemoteViews(context.packageName, itemLayoutId)
        try {
            views.setTextViewText(R.id.widget_item_name, contact.name ?: contact.phoneNumber ?: context.getString(R.string.unknown))
            views.setImageViewResource(R.id.widget_item_photo, defaultPlaceholderResId)
            val targetHeightPx = dpToPx(context, itemHeightDp); val targetWidthPx = (targetHeightPx * 0.85).roundToInt()
            var bitmap: Bitmap? = null; var photoLoadedSuccessfully = false
            if (contact.contactId != -1L) {
                bitmap = loadContactPhotoWithGlide(context, contact.contactId, true, null, targetWidthPx, targetHeightPx)
                if (bitmap != null) { photoLoadedSuccessfully = true }
                else if (!contact.photoThumbnailUri.isNullOrEmpty()) {
                    try { bitmap = loadContactPhotoWithGlide(context, null, false, Uri.parse(contact.photoThumbnailUri), targetWidthPx, targetHeightPx); if (bitmap != null) { photoLoadedSuccessfully = true } } catch (e: Exception) { /* ignore */ }
                }
            }
            if (!photoLoadedSuccessfully) {
                Log.d(TAG, "-> Contact photo not loaded for pos $position. Fallback (mode: $unknownAvatarMode).")
                if (unknownAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && !unknownAvatarCustomUri.isNullOrEmpty()) {
                    try {
                        val customUri = Uri.parse(unknownAvatarCustomUri); Log.v(TAG, "-> Attempting CUSTOM avatar: $customUri")
                        bitmap = loadContactPhotoWithGlide(context, null, false, customUri, targetWidthPx, targetHeightPx)
                        if (bitmap != null) { photoLoadedSuccessfully = true; Log.v(TAG, "-> CUSTOM avatar loaded.") } else { Log.w(TAG, "-> CUSTOM avatar FAILED.") }
                    } catch (e: Exception) { Log.e(TAG, "-> Error loading CUSTOM avatar URI", e) }
                }
            }
            if (photoLoadedSuccessfully && bitmap != null && !bitmap.isRecycled) {
                views.setImageViewBitmap(R.id.widget_item_photo, bitmap); Log.v(TAG, "-> Setting final bitmap.")
            } else {
                views.setImageViewResource(R.id.widget_item_photo, defaultPlaceholderResId); if (!photoLoadedSuccessfully) Log.w(TAG, "-> Final fallback to default placeholder.")
            }
            val iconResIdToShow = getCallTypeIcon(contact.lastCallType)
            views.setImageViewResource(R.id.widget_item_call_type_icon, iconResIdToShow)
            views.setViewVisibility(R.id.widget_item_call_type_icon, View.VISIBLE)
            val fillInIntent = Intent().apply { val extras = Bundle(); extras.putString(ContactsGridWidgetProvider.EXTRA_PHONE_NUMBER, contact.phoneNumber ?: ""); extras.putString(ContactsGridWidgetProvider.EXTRA_CONTACT_NAME, contact.name ?: context.getString(R.string.unknown)); extras.putString(ContactsGridWidgetProvider.EXTRA_PHOTO_URI, contact.photoThumbnailUri ?: ""); extras.putLong(ContactsGridWidgetProvider.EXTRA_CONTACT_ID, contact.contactId); putExtras(extras) }; views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        } catch (e: Exception) { Log.e(TAG, "!!! EXCEPTION in getViewAt for pos $position !!!", e); return RemoteViews(context.packageName, itemLayoutId) }
        Log.v(TAG, "getViewAt END for pos $position")
        return views
    }

    // --- Вспомогательные методы ---

    private fun updateVisualSettings() {
        itemHeightDp = WidgetPrefsUtils.loadItemHeight(context, appWidgetId)
        itemLayoutId = getLayoutIdForHeight(itemHeightDp)
        Log.d(TAG, "Visual settings updated: Height=${itemHeightDp}dp, LayoutId=$itemLayoutId")
    }

    private fun getLayoutIdForHeight(heightDp: Int): Int {
        var layoutId = layoutIdCache.get(heightDp)
        if (layoutId == null || layoutId == 0) {
            layoutId = when (heightDp) {
                70->R.layout.widget_item_h70; 80->R.layout.widget_item_h80; 90->R.layout.widget_item_h90
                100->R.layout.widget_item_h100; 110->R.layout.widget_item_h110; 120->R.layout.widget_item_h120
                130->R.layout.widget_item_h130; 140->R.layout.widget_item_h140; 150->R.layout.widget_item_h150
                160->R.layout.widget_item_h160; else->R.layout.widget_item_default_height
            }
            layoutIdCache.put(heightDp, layoutId)
        }
        return layoutId
    }

    private fun loadContactPhotoWithGlide(context: Context, contactId: Long?, isDisplayPhoto: Boolean, fallbackUri: Uri?, targetWidthPx: Int, targetHeightPx: Int): Bitmap? {
        val photoUri: Uri? = when {
            isDisplayPhoto && contactId != null && contactId != -1L -> { val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId); Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO) }
            fallbackUri != null -> fallbackUri
            else -> null
        }
        if (photoUri == null) { return null }
        val width = if (targetWidthPx > 0) targetWidthPx else 100; val height = if (targetHeightPx > 0) targetHeightPx else 100
        return try {
            Glide.with(context.applicationContext).asBitmap().load(photoUri).apply(RequestOptions().override(width, height).centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL).skipMemoryCache(false).placeholder(defaultPlaceholderResId).error(defaultPlaceholderResId)).submit().get()
        } catch (e: Exception) { if (!(e is FileNotFoundException && isDisplayPhoto)) { Log.w(TAG, "Glide error loading '$photoUri': ${e.javaClass.simpleName} - ${e.message}") }; null }
        catch (oom: OutOfMemoryError) { Log.e(TAG, "Glide OOM loading '$photoUri'"); null }
    }

    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()
}