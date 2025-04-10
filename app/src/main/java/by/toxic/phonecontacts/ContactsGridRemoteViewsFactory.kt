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
import java.io.FileNotFoundException
import java.util.Calendar // Добавлено для работы с датой
import java.util.Date    // Добавлено для работы с датой
import kotlin.math.roundToInt

private const val TAG = "RVFactory"

class ContactsGridRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    private data class WidgetContact(
        val contactId: Long,
        val lookupKey: String?,
        val name: String?,
        val phoneNumber: String?,
        val photoThumbnailUri: String?,
        val lastCallType: Int? = null,
        val isFavorite: Boolean = false
    ) {
        val contentUri: Uri? get() = lookupKey?.let { ContactsContract.Contacts.getLookupUri(contactId, it) }
        override fun toString(): String {
            val typeStr = when(lastCallType) { CallLog.Calls.INCOMING_TYPE -> "IN"; CallLog.Calls.OUTGOING_TYPE -> "OUT"; CallLog.Calls.MISSED_TYPE -> "MISSED"; CallLog.Calls.REJECTED_TYPE -> "REJ"; null -> "NULL"; else -> "TYPE($lastCallType)"}
            return "ID=$contactId, Name=$name, Phone=$phoneNumber, Type=$typeStr, IsFav=$isFavorite"
        }
    }

    private var widgetContacts: List<WidgetContact> = emptyList()
    private var itemHeightDp: Int = WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT
    private var itemLayoutId: Int = R.layout.widget_item_default_height
    private val layoutIdCache = SparseArray<Int>()

    override fun onCreate() { Log.d(TAG, "onCreate for widget $appWidgetId") }

    override fun onDataSetChanged() {
        Log.i(TAG, "====== onDataSetChanged START for widget $appWidgetId ======")
        val startTime = SystemClock.elapsedRealtime()
        updateVisualSettings()
        val sortOrder = WidgetPrefsUtils.loadSortOrder(context, appWidgetId)
        val maxItems = WidgetPrefsUtils.loadMaxItems(context, appWidgetId)
        val favoriteContactUris = WidgetPrefsUtils.loadContactUris(context, appWidgetId)
        val showUnknown = WidgetPrefsUtils.loadShowUnknown(context, appWidgetId)
        val filterOldUnknown = WidgetPrefsUtils.loadFilterOldUnknown(context, appWidgetId) // Загружаем флаг

        Log.d(TAG, "Widget $appWidgetId - Settings: Sort='$sortOrder', MaxItems=$maxItems, FavURIs=${favoriteContactUris.size}, ShowUnknown=$showUnknown, FilterOldUnknown=$filterOldUnknown")

        val resolver = context.contentResolver
        val identityToken = Binder.clearCallingIdentity()
        var loadedContacts: List<WidgetContact> = emptyList()
        try {
            loadedContacts = when (sortOrder) {
                WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS ->
                    loadFavoritesAndRecents(resolver, favoriteContactUris, maxItems, showUnknown, filterOldUnknown) // Передаем флаг
                WidgetPrefsUtils.SORT_ORDER_FAVORITES ->
                    loadFavoriteContacts(resolver, favoriteContactUris).take(maxItems)
                else -> { Log.w(TAG, "Unknown sort order '$sortOrder'."); emptyList() }
            }
            widgetContacts = loadedContacts
            Log.i(TAG, "Finished loading data. Total items to display: ${widgetContacts.size}")
            widgetContacts.forEachIndexed { i, c -> Log.d(TAG, "  [$i]: $c") }
        } catch (e: SecurityException) { Log.e(TAG, "SecurityException: ${e.message}.", e); widgetContacts = emptyList() }
        catch (e: Exception) { Log.e(TAG, "Exception: ${e.message}", e); widgetContacts = emptyList() }
        finally { Binder.restoreCallingIdentity(identityToken); Log.i(TAG, "====== onDataSetChanged END (took ${SystemClock.elapsedRealtime() - startTime}ms) ======") }
    }

    override fun onDestroy() { Log.d(TAG, "onDestroy for widget $appWidgetId"); widgetContacts = emptyList() }

    private fun loadFavoriteContacts(resolver: ContentResolver, favoriteUris: List<String>): List<WidgetContact> {
        Log.d(TAG, "--- loadFavoriteContacts START (URIs: ${favoriteUris.size}) ---")
        val favorites = mutableListOf<WidgetContact>()
        val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.HAS_PHONE_NUMBER)
        val hasCallLogPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        if (!hasCallLogPerm) Log.w(TAG, "READ_CALL_LOG permission denied. Cannot fetch last call types for favorites.")

        favoriteUris.forEachIndexed { index, uriString ->
            val contactUri = Uri.parse(uriString); Log.v(TAG, "Querying favorite URI [$index]: $uriString"); var cursor: Cursor? = null
            try {
                cursor = resolver.query(contactUri, projection, null, null, null)
                if (cursor?.moveToFirst() == true) {
                    val idIndex=cursor.getColumnIndex(ContactsContract.Contacts._ID); val lookupKeyIndex=cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY); val nameIndex=cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY); val photoUriIndex=cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI); val hasPhoneIndex=cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                    if (idIndex!=-1&&lookupKeyIndex!=-1&&nameIndex!=-1&&photoUriIndex!=-1&&hasPhoneIndex!=-1) {
                        val id=cursor.getLong(idIndex); val lookupKey=cursor.getString(lookupKeyIndex); val name=cursor.getString(nameIndex); val photoThumbnailUri=cursor.getString(photoUriIndex); val hasPhoneNumber=cursor.getInt(hasPhoneIndex)>0; var phoneNumber:String?=null; var lastCallType: Int? = null
                        if(hasPhoneNumber){ phoneNumber=fetchFirstPhoneNumber(resolver,id.toString()) }
                        if(phoneNumber!=null){
                            if (hasCallLogPerm) { lastCallType = fetchLastCallTypeForNumber(resolver, phoneNumber); Log.d(TAG, "      -> For Fav [$index] $name ($phoneNumber), fetched lastCallType: $lastCallType") }
                            else { lastCallType = null; Log.w(TAG, "      -> For Fav [$index] $name ($phoneNumber), NO CallLog permission, lastCallType set to null") }
                            val contact=WidgetContact(id,lookupKey,name,phoneNumber,photoThumbnailUri, lastCallType = lastCallType, isFavorite = true); favorites.add(contact);
                        } else {Log.w(TAG,"  -> Favorite contact ID=$id, Name=$name has no phone number. Skipping.")}
                    } else {Log.e(TAG,"  -> Column index error for favorite URI $uriString")}
                } else {Log.w(TAG,"  -> Could not find contact for favorite URI $uriString")}
            } catch (e: Exception) {Log.e(TAG,"  -> Error querying favorite contact $uriString: ${e.message}", e)}
            finally {cursor?.close()}
        }
        Log.d(TAG, "--- loadFavoriteContacts END (Loaded: ${favorites.size}) ---")
        return favorites
    }

    private fun fetchLastCallTypeForNumber(resolver: ContentResolver, number: String): Int? {
        var callType: Int? = null; val uri = CallLog.Calls.CONTENT_URI; val projection = arrayOf(CallLog.Calls.TYPE); val selection = "${CallLog.Calls.NUMBER} = ?"; val selectionArgs = arrayOf(number)
        val sortOrder = "${CallLog.Calls.DATE} DESC" // <<< LIMIT убран >>>
        var cursor: Cursor? = null; Log.v(TAG, "      -> Fetching last call type for number: $number")
        try {
            cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            if (cursor?.moveToFirst() == true) { val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE); if (typeIndex != -1) { callType = cursor.getInt(typeIndex); Log.d(TAG, "      -> SUCCESS: Found last call type $callType for $number") } else { Log.w(TAG, "      -> FAILED: CallLog TYPE column not found for $number.") } } else { Log.d(TAG, "      -> INFO: No CallLog entries found for $number.") }
        } catch (e: Exception) { Log.e(TAG, "      -> ERROR fetching last call type for $number: ${e.message}") } finally { cursor?.close() }
        return callType
    }

    // Возвращает список пар (Контакт, ДатаЗвонка)
    private fun loadRecentContacts(resolver: ContentResolver): List<Pair<WidgetContact, Long?>> {
        val recentsLimit = 250; Log.d(TAG, "--- loadRecentContacts START (Query Limit: $recentsLimit) ---")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "READ_CALL_LOG permission denied."); return emptyList() } else { Log.d(TAG, "READ_CALL_LOG permission granted.") }
        val recents = mutableListOf<Pair<WidgetContact, Long?>>() // Список пар
        val uniqueCleanPhoneNumbers = mutableSetOf<String>()
        val projection = arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_LOOKUP_URI, CallLog.Calls.DATE, CallLog.Calls.TYPE); val sortOrder = "${CallLog.Calls.DATE} DESC"
        val queryUri = CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, recentsLimit.toString()).build()
        Log.d(TAG, "Querying CallLog with URI: $queryUri"); var cursor: Cursor? = null; var queriedCount = 0
        try {
            cursor = resolver.query(queryUri, projection, null, null, sortOrder); queriedCount = cursor?.count ?: 0
            Log.d(TAG, "CallLog query returned cursor with $queriedCount entries.")
            cursor?.use {
                val idIndex=it.getColumnIndex(CallLog.Calls._ID); val numberIndex=it.getColumnIndex(CallLog.Calls.NUMBER); val nameIndex=it.getColumnIndex(CallLog.Calls.CACHED_NAME); val lookupUriIndex=it.getColumnIndex(CallLog.Calls.CACHED_LOOKUP_URI); val dateIndex=it.getColumnIndex(CallLog.Calls.DATE); val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                if (numberIndex == -1 || typeIndex == -1 || dateIndex == -1) { Log.e(TAG, "CallLog NUMBER, TYPE or DATE column not found."); return@use }
                var processedCount = 0
                while (it.moveToNext()) {
                    processedCount++; val callId=if(idIndex!=-1)it.getLong(idIndex)else-1L; val phoneNumber=it.getString(numberIndex); val callDate=it.getLong(dateIndex); val callType = it.getInt(typeIndex)
                    Log.v(TAG, "Processing CallLog entry $processedCount/$queriedCount: CallID=$callId, Num=$phoneNumber, Date=$callDate, Type=$callType")
                    if (phoneNumber.isNullOrEmpty()) { Log.v(TAG,"  -> Skipping: Empty phone number."); continue }
                    val cleanNumber = phoneNumber.replace(Regex("[()\\-\\s]"), "")
                    if (cleanNumber.isEmpty() || !uniqueCleanPhoneNumbers.add(cleanNumber)) { Log.v(TAG,"  -> Skipping: Empty or duplicate cleaned phone number '$cleanNumber'."); continue }
                    val cachedName = if(nameIndex!=-1)it.getString(nameIndex)else null; val cachedLookupUriString=if(lookupUriIndex!=-1)it.getString(lookupUriIndex)else null; var contact: WidgetContact? = null
                    if(cachedLookupUriString!=null){ contact=findContactByUri(resolver,Uri.parse(cachedLookupUriString),phoneNumber) }
                    if(contact==null){ contact=findContactByNumber(resolver,phoneNumber) }
                    val recentContact: WidgetContact
                    if(contact!=null){ recentContact = contact.copy(lastCallType = callType, isFavorite = false); Log.d(TAG,"  -> Created recent (found contact): $recentContact") }
                    else{ recentContact = WidgetContact(-1L,null,cachedName?:phoneNumber,phoneNumber,null, callType, isFavorite = false); Log.d(TAG,"  -> Created recent (contact not found): $recentContact") }
                    recents.add(Pair(recentContact, callDate)) // Добавляем пару
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error querying CallLog: ${e.message}", e) }
        Log.d(TAG, "--- loadRecentContacts END (Found: ${recents.size} unique numbers processed) ---")
        return recents
    }

    // Объединяет избранные и недавние, фильтрует, применяет лимит
    private fun loadFavoritesAndRecents(resolver: ContentResolver, favoriteUris: List<String>, maxItems: Int, showUnknown: Boolean, filterOldUnknown: Boolean): List<WidgetContact> {
        Log.d(TAG, "--- loadFavoritesAndRecents START (MaxItems: $maxItems, ShowUnknown: $showUnknown, FilterOldUnknown: $filterOldUnknown) ---")
        val favorites = loadFavoriteContacts(resolver, favoriteUris)
        Log.d(TAG, "Loaded ${favorites.size} favorites."); favorites.forEachIndexed { i, c -> Log.v(TAG, "  Fav[$i]: $c") }
        val recentsWithDate = loadRecentContacts(resolver) // Получаем List<Pair<WidgetContact, Long?>>
        Log.d(TAG, "Processed ${recentsWithDate.size} unique recent calls."); recentsWithDate.forEachIndexed { i, p -> Log.v(TAG, "  Rec[$i]: ${p.first} (Date: ${p.second})") }
        val favoriteContactIds = favorites.map { it.contactId }.toSet()
        val favoritePhoneNumbers = favorites.mapNotNull { it.phoneNumber?.replace(Regex("[()\\-\\s]"), "") }.toSet()
        Log.d(TAG, "Filtering recents against favorites...")
        val uniqueRecentsWithDate = recentsWithDate.filter { pair -> val recent = pair.first; val isDuplicate: Boolean; if (recent.contactId != -1L && recent.contactId != 0L) { isDuplicate = recent.contactId in favoriteContactIds; if (isDuplicate) Log.v(TAG, "  -> Filtering recent (ID duplicate): $recent") } else { val cleanRecentNumber = recent.phoneNumber?.replace(Regex("[()\\-\\s]"), ""); isDuplicate = cleanRecentNumber != null && cleanRecentNumber in favoritePhoneNumbers; if (isDuplicate) Log.v(TAG, "  -> Filtering recent (number duplicate): $recent (Clean: $cleanRecentNumber)") }; !isDuplicate }
        Log.d(TAG, "Found ${uniqueRecentsWithDate.size} unique recent contacts after filtering favs."); uniqueRecentsWithDate.forEachIndexed { i, p -> Log.v(TAG, "  UniqRec[$i]: ${p.first} (Date: ${p.second})") }

        // <<<--- ФИЛЬТРАЦИЯ СТАРЫХ НЕИЗВЕСТНЫХ ---<<<
        val twoDaysAgoMillis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis
        Log.d(TAG, "Filtering old unknown contacts? $filterOldUnknown. Threshold: $twoDaysAgoMillis (${Date(twoDaysAgoMillis)})")
        val filteredRecents: List<WidgetContact>
        if (filterOldUnknown) {
            val tempFilteredPairs = mutableListOf<Pair<WidgetContact, Long?>>()
            uniqueRecentsWithDate.forEach { pair ->
                val contact = pair.first; val callDate = pair.second; val keep: Boolean
                if (contact.contactId != -1L) { keep = true; Log.v(TAG, "  -> KEEPING known recent: $contact") }
                else { if (callDate != null && callDate >= twoDaysAgoMillis) { keep = true; Log.v(TAG, "  -> KEEPING recent unknown (not old): $contact (Date: $callDate >= $twoDaysAgoMillis)") }
                else { keep = false; Log.w(TAG, "  -> FILTERING old unknown: $contact (Date: $callDate < $twoDaysAgoMillis)") } }
                if (keep) { tempFilteredPairs.add(pair) }
            }
            filteredRecents = tempFilteredPairs.map { it.first }
            Log.d(TAG, "Kept ${filteredRecents.size} recents after filtering old unknown.")
        } else { Log.d(TAG, "Filtering old unknown is disabled."); filteredRecents = uniqueRecentsWithDate.map { it.first } }
        // <<<--------------------------------------->>>

        Log.d(TAG, "List size after filtering old unknown: ${filteredRecents.size}"); filteredRecents.forEachIndexed { i, c -> Log.v(TAG, "  FiltOld[$i]: $c") }
        val combinedList = favorites + filteredRecents
        Log.d(TAG, "Combined list size before unknown filtering: ${combinedList.size}")
        val filteredListByShowUnknown = if (!showUnknown) { Log.d(TAG, "Filtering out ALL unknown numbers (contactId == -1L)"); combinedList.filter { it.contactId != -1L } } else { Log.d(TAG, "Showing unknown numbers is enabled."); combinedList }
        Log.d(TAG, "List size after filtering by showUnknown: ${filteredListByShowUnknown.size}"); filteredListByShowUnknown.forEachIndexed { i, c -> Log.v(TAG, "  FiltShow[$i]: $c") }
        val finalList = filteredListByShowUnknown.take(maxItems)
        Log.d(TAG, "--- loadFavoritesAndRecents END (Final list size: ${finalList.size}) ---")
        return finalList
    }

    private fun findContactByNumber(resolver: ContentResolver, phoneNumber: String): WidgetContact? { val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber)); val projection = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY, ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI); var contact: WidgetContact? = null; var cursor: Cursor? = null; try { cursor = resolver.query(uri, projection, null, null, null); if (cursor?.moveToFirst() == true) { val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID); val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY); val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME); val photoUriIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI); if (idIndex != -1 && lookupKeyIndex != -1 && nameIndex != -1 && photoUriIndex != -1) { contact = WidgetContact(cursor.getLong(idIndex), cursor.getString(lookupKeyIndex), cursor.getString(nameIndex), phoneNumber, cursor.getString(photoUriIndex)) } } } catch (e: Exception) { Log.e(TAG, "Error finding contact by number $phoneNumber: ${e.message}") } finally { cursor?.close() }; return contact }
    private fun findContactByUri(resolver: ContentResolver, lookupUri: Uri, fallbackPhoneNumber: String?): WidgetContact? { val projection = arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI); var contact: WidgetContact? = null; var cursor: Cursor? = null; try { cursor = resolver.query(lookupUri, projection, null, null, null); if (cursor?.moveToFirst() == true) { val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID); val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY); val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY); val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI); if (idIndex != -1 && lookupKeyIndex != -1 && nameIndex != -1 && photoUriIndex != -1) { contact = WidgetContact(cursor.getLong(idIndex), cursor.getString(lookupKeyIndex), cursor.getString(nameIndex), fallbackPhoneNumber, cursor.getString(photoUriIndex)) } } } catch (e: Exception) { Log.e(TAG, "Error finding contact by URI $lookupUri: ${e.message}") } finally { cursor?.close() }; return contact }
    private fun fetchFirstPhoneNumber(resolver: ContentResolver, contactId: String): String? { var phoneNumber: String? = null; val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI; val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER); val phoneSelection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"; var phoneCursor: Cursor? = null; try { phoneCursor = resolver.query(phoneUri, phoneProjection, phoneSelection, arrayOf(contactId), null); if (phoneCursor?.moveToFirst() == true) { val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER); if (numberIndex != -1) { phoneNumber = phoneCursor.getString(numberIndex) } } } catch (e: Exception) { Log.e(TAG, "Error fetching phone number for contact $contactId: ${e.message}", e) } finally { phoneCursor?.close() }; return phoneNumber }

    override fun getCount(): Int = widgetContacts.size
    override fun getViewAt(position: Int): RemoteViews? {
        Log.v(TAG, "getViewAt START for pos $position")
        if (position < 0 || position >= widgetContacts.size) { Log.w(TAG, "getViewAt invalid pos $position"); return RemoteViews(context.packageName, itemLayoutId) }
        val contact = widgetContacts[position]; Log.v(TAG, "  -> Contact data: $contact"); val views = RemoteViews(context.packageName, itemLayoutId)
        try {
            views.setTextViewText(R.id.widget_item_name, contact.name ?: contact.phoneNumber ?: "Unknown")
            views.setImageViewResource(R.id.widget_item_photo, R.drawable.ic_contact_placeholder)
            val targetHeightPx = dpToPx(context, itemHeightDp); val targetWidthPx = (targetHeightPx * 0.8).roundToInt(); var bitmapLoaded = false; var bitmap: Bitmap? = null
            if (contact.contactId != -1L) { bitmap = loadContactPhotoWithGlide(context, contact.contactId, true, null, targetWidthPx, targetHeightPx); if (bitmap != null) bitmapLoaded = true }
            if (!bitmapLoaded && !contact.photoThumbnailUri.isNullOrEmpty()) { try { val thumbnailUri = Uri.parse(contact.photoThumbnailUri); bitmap = loadContactPhotoWithGlide(context, null, false, thumbnailUri, targetWidthPx, targetHeightPx); if (bitmap != null) bitmapLoaded = true } catch (e: Exception) {} }
            if (bitmapLoaded && bitmap != null) { views.setImageViewBitmap(R.id.widget_item_photo, bitmap) }
            val iconResIdToShow = getCallTypeIcon(contact.lastCallType); Log.v(TAG, "  -> Setting call type icon for pos $position, type ${contact.lastCallType}, resId $iconResIdToShow")
            views.setImageViewResource(R.id.widget_item_call_type_icon, iconResIdToShow); views.setViewVisibility(R.id.widget_item_call_type_icon, View.VISIBLE)
            val fillInIntent = Intent().apply { val extras = Bundle(); extras.putString(ContactsGridWidgetProvider.EXTRA_PHONE_NUMBER, contact.phoneNumber ?: ""); extras.putString(ContactsGridWidgetProvider.EXTRA_CONTACT_NAME, contact.name ?: "Unknown"); extras.putString(ContactsGridWidgetProvider.EXTRA_PHOTO_URI, contact.photoThumbnailUri ?: ""); extras.putLong(ContactsGridWidgetProvider.EXTRA_CONTACT_ID, contact.contactId); putExtras(extras) }; views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)
        } catch (e: Exception) { Log.e(TAG, "!!! EXCEPTION in getViewAt for position $position, contact $contact !!!", e); return RemoteViews(context.packageName, itemLayoutId) }
        Log.v(TAG, "getViewAt END for pos $position"); return views
    }
    private fun getCallTypeIcon(callType: Int?): Int { return when (callType) { CallLog.Calls.INCOMING_TYPE -> R.drawable.incoming; CallLog.Calls.OUTGOING_TYPE -> R.drawable.outgoing; CallLog.Calls.MISSED_TYPE -> R.drawable.missed; CallLog.Calls.REJECTED_TYPE -> R.drawable.rejected; else -> R.drawable.unknown } }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = WidgetConfigureActivity.supportedHeights.size + 1
    fun getItemViewType(position: Int): Int { val index=WidgetConfigureActivity.supportedHeights.indexOf(itemHeightDp.toString()); return if(index>=0)index else 0 }
    override fun getItemId(position: Int): Long { val contact=widgetContacts.getOrNull(position); return contact?.contactId?.takeIf{it!=-1L}?:contact?.phoneNumber?.hashCode()?.toLong()?:position.toLong() }
    override fun hasStableIds(): Boolean = true
    private fun updateVisualSettings() { itemHeightDp=WidgetPrefsUtils.loadItemHeight(context,appWidgetId); itemLayoutId=getLayoutIdForHeight(itemHeightDp); Log.d(TAG,"Visual settings updated: Height=${itemHeightDp}dp, LayoutId=$itemLayoutId") }
    private fun getLayoutIdForHeight(heightDp: Int): Int { var layoutId=layoutIdCache.get(heightDp); if(layoutId==null||layoutId==0){layoutId=when(heightDp){70->R.layout.widget_item_h70;80->R.layout.widget_item_h80;90->R.layout.widget_item_h90;100->R.layout.widget_item_h100;110->R.layout.widget_item_h110;120->R.layout.widget_item_h120;130->R.layout.widget_item_h130;140->R.layout.widget_item_h140;150->R.layout.widget_item_h150;160->R.layout.widget_item_h160;else->R.layout.widget_item_default_height};layoutIdCache.put(heightDp,layoutId)}; return layoutId }
    private fun loadContactPhotoWithGlide(context: Context, contactId: Long?, isDisplayPhoto: Boolean, fallbackUri: Uri?, targetWidthPx: Int, targetHeightPx: Int): Bitmap? { val photoUri:Uri?=when{isDisplayPhoto&&contactId!=null&&contactId!=-1L->{val contactUri=ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,contactId);Uri.withAppendedPath(contactUri,ContactsContract.Contacts.Photo.DISPLAY_PHOTO)}!isDisplayPhoto&&fallbackUri!=null->fallbackUri;else->null};if(photoUri==null)return null;val width=if(targetWidthPx>0)targetWidthPx else 1;val height=if(targetHeightPx>0)targetHeightPx else 1;return try{Glide.with(context.applicationContext).asBitmap().load(photoUri).apply(RequestOptions().override(width,height).centerCrop().diskCacheStrategy(DiskCacheStrategy.ALL).skipMemoryCache(false)).submit().get()}catch(e:Exception){if(!(e is FileNotFoundException&&isDisplayPhoto)){Log.w(TAG,"Glide error loading '$photoUri': ${e.message}")};null}catch(oom:OutOfMemoryError){Log.e(TAG,"Glide OOM loading '$photoUri'");null}}
    private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()
}