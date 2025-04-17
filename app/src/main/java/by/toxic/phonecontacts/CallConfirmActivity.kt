package by.toxic.phonecontacts

import android.Manifest
// import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri // <<< Убедитесь, что этот импорт есть >>>
import android.os.Build // Добавлен импорт
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
// import android.telephony.PhoneNumberUtils
import android.util.Log
import android.view.Gravity // <<< Добавлен импорт
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager // <<< Добавлен импорт
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import by.toxic.phonecontacts.databinding.DialogCallConfirmBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG_CONFIRM = "CallConfirmActivity"

class CallConfirmActivity : AppCompatActivity() {

    private lateinit var binding: DialogCallConfirmBinding
    private var phoneNumber: String? = null
    private var contactName: String? = null
    private var photoThumbnailUriString: String? = null
    private var contactId: Long = -1L
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val requestCallPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { Log.d(TAG_CONFIRM, "CALL_PHONE permission granted."); performCall() }
            else { Log.w(TAG_CONFIRM, "CALL_PHONE permission denied."); Toast.makeText(this, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show(); finish() }
        }
    private data class CallStats( val lastCallTimestamp: Long?, val callCount: Int, val totalDurationSeconds: Long )

    override fun onCreate(savedInstanceState: Bundle?) {
        // Устанавливаем базовую тему ДО super.onCreate()
        // Важно: Используем ПРОСТУЮ тему диалога, а не нашу кастомную с фоном
        // setTheme(R.style.Theme_Material3_DayNight_Dialog_Alert) // Попробуем так
        // ИЛИ оставим как было, если базовая тема приложения влияет на цвета текста
        setTheme(AppSettings.getSelectedThemeResId(this))

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE) // Убираем системный заголовок
        super.onCreate(savedInstanceState)

        binding = DialogCallConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root) // Устанавливаем разметку

        // >>> НАЧАЛО: Программная настройка окна <<<
        window?.let { win ->
            // 1. Устанавливаем наш фон со скруглением для самого ОКНА
            win.setBackgroundDrawableResource(R.drawable.dialog_call_confirm_background)

            // 2. Настраиваем размеры и гравитацию
            val params = win.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT // Используем доступную ширину
            params.height = WindowManager.LayoutParams.WRAP_CONTENT // Высота по контенту
            params.gravity = Gravity.CENTER // По центру

            // (Опционально) Убедимся, что затемнение включено (хотя тема диалога должна это делать)
            // params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            // params.dimAmount = 0.7f // Степень затемнения

            win.attributes = params // Применяем параметры

            Log.d(TAG_CONFIRM, "Window attributes set programmatically (Width: ${params.width}, Height: ${params.height})")
        }
        // >>> КОНЕЦ: Программная настройка окна <<<


        phoneNumber = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_PHONE_NUMBER)
        contactName = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_CONTACT_NAME)
        photoThumbnailUriString = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_PHOTO_URI)
        contactId = intent.getLongExtra(ContactsGridWidgetProvider.EXTRA_CONTACT_ID, -1L)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        Log.d(TAG_CONFIRM, "Activity created. PN: $phoneNumber, Name: $contactName, ThumbUri: $photoThumbnailUriString, ContactID: $contactId, WidgetID: $appWidgetId")
        if (phoneNumber.isNullOrEmpty()) { Log.e(TAG_CONFIRM, "Phone number missing."); Toast.makeText(this, R.string.error_phone_number_missing, Toast.LENGTH_SHORT).show(); finish(); return }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { Log.e(TAG_CONFIRM, "AppWidgetId missing in Intent."); Toast.makeText(this, R.string.error_widget_id_missing, Toast.LENGTH_SHORT).show(); finish(); return }
        binding.contactName.text = contactName ?: getString(R.string.unknown_contact)
        binding.phoneNumber.text = phoneNumber // Отображаем номер как есть
        loadPhotoWithFallback()
        binding.buttonCall.setOnClickListener { checkPermissionAndCall() }
        binding.buttonCancel.setOnClickListener { finish() }
        loadAndDisplayCallStats()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun loadPhotoWithFallback() {
        val defaultAvatarResId = R.drawable.anonim
        val avatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(this, appWidgetId)
        val customAvatarUriString = WidgetPrefsUtils.loadUnknownAvatarCustomUri(this, appWidgetId)
        var settingsFallbackSource: Any = defaultAvatarResId
        if (avatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && !customAvatarUriString.isNullOrEmpty()) {
            try { val customUri = Uri.parse(customAvatarUriString); if (customUri.scheme == "file" && File(customUri.path!!).exists()) { settingsFallbackSource = customUri }
            } catch (e: Exception) { /* Use default */ }
        }
        Log.d(TAG_CONFIRM, "Settings fallback source: $settingsFallbackSource")
        val baseRequestOptions = RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL).centerCrop().placeholder(defaultAvatarResId)
        if (contactId != -1L) {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            val displayPhotoUri: Uri? = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO)
            val contactThumbnailUri: Uri? = if (!photoThumbnailUriString.isNullOrEmpty()) { try { Uri.parse(photoThumbnailUriString) } catch (e: Exception) { null } } else { null }
            Log.d(TAG_CONFIRM, "Known contact. Display: $displayPhotoUri, Thumb: $contactThumbnailUri, Fallback: $settingsFallbackSource")
            val settingsFallbackRequest = Glide.with(this).load(settingsFallbackSource).apply(baseRequestOptions.error(defaultAvatarResId))
            val thumbnailRequest = Glide.with(this).load(contactThumbnailUri).apply(baseRequestOptions).error(settingsFallbackRequest)
            Glide.with(this).load(displayPhotoUri).apply(baseRequestOptions.placeholder(defaultAvatarResId)).error(thumbnailRequest).into(binding.contactPhoto)
        } else {
            Log.d(TAG_CONFIRM, "Unknown contact. Fallback: $settingsFallbackSource")
            Glide.with(this).load(settingsFallbackSource).apply(baseRequestOptions.error(defaultAvatarResId)).into(binding.contactPhoto)
        }
    }

    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) { Log.d(TAG_CONFIRM, "CALL_PHONE granted."); performCall() }
        else { Log.d(TAG_CONFIRM, "CALL_PHONE not granted. Requesting..."); requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE) }
    }

    private fun performCall() {
        if (phoneNumber.isNullOrEmpty()) { Log.e(TAG_CONFIRM, "Cannot call, phone number null."); finish(); return }
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                // <<< ИЗМЕНЕНИЕ: Используем Uri.fromParts для корректной обработки '#' >>>
                data = Uri.fromParts("tel", phoneNumber, null)
                // Добавляем флаг, чтобы Activity запустилась как новая задача (важно для запуска из сервиса/ресивера)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (callIntent.resolveActivity(packageManager) != null) { Log.d(TAG_CONFIRM, "Starting ACTION_CALL."); startActivity(callIntent) }
            else { Log.w(TAG_CONFIRM, "No activity found to handle ACTION_CALL."); Toast.makeText(this, R.string.error_no_call_app, Toast.LENGTH_SHORT).show() }
        } catch (e: SecurityException) { Log.e(TAG_CONFIRM, "SecurityException during ACTION_CALL", e); Toast.makeText(this, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show() }
        catch (e: Exception) { Log.e(TAG_CONFIRM, "Exception during ACTION_CALL", e); Toast.makeText(this, R.string.error_call_failed, Toast.LENGTH_SHORT).show() }
        finally { finish() }
    }

    private fun loadAndDisplayCallStats() {
        Log.d(TAG_CONFIRM, "Checking READ_CALL_LOG permission...")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG_CONFIRM, "READ_CALL_LOG permission denied.")
            binding.statsProgressBar.visibility = View.GONE; binding.statsContainer.visibility = View.VISIBLE
            binding.labelLastCall.text = getString(R.string.call_stats_permission_needed)
            binding.textLastCallDatetime.visibility = View.GONE; binding.textCallCount.visibility = View.GONE; binding.textTotalDuration.visibility = View.GONE
            return
        }
        Log.d(TAG_CONFIRM, "READ_CALL_LOG granted. Fetching stats...")
        binding.statsProgressBar.visibility = View.VISIBLE; binding.statsContainer.visibility = View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            val stats = fetchCallStatistics(phoneNumber!!)
            withContext(Dispatchers.Main) { displayCallStats(stats) }
        }
    }

    private fun fetchCallStatistics(numberToCompare: String): CallStats? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { Log.e(TAG_CONFIRM, "Cannot fetch stats: Invalid appWidgetId"); return null }
        val digitsToCompare = WidgetPrefsUtils.loadCompareDigitsCount(this, appWidgetId)
        val numberToCompareLastN = getLastNdigits(numberToCompare, digitsToCompare)
        if (numberToCompareLastN.length < 7) { Log.w(TAG_CONFIRM, "Number $numberToCompare too short after normalization."); return CallStats(null, 0, 0) }
        val resolver = contentResolver; val projection = arrayOf(CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.NUMBER); val sortOrder = "${CallLog.Calls.DATE} DESC"
        var lastCallTimestampResult: Long? = null; var callCount = 0; var totalDurationSecondsResult: Long = 0; var statsCalculated = false; var cursor: Cursor? = null
        try {
            // Ограничим выборку для производительности, если журнал очень большой
            val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "500")
                .build()
            cursor = resolver.query(queryUri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val dateIndex = c.getColumnIndex(CallLog.Calls.DATE); val durationIndex = c.getColumnIndex(CallLog.Calls.DURATION); val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
                if (dateIndex != -1 && durationIndex != -1 && numberIndex != -1) {
                    statsCalculated = true
                    while (c.moveToNext()) {
                        val callLogNumberRaw = c.getString(numberIndex) ?: continue
                        val callLogNumberLastN = getLastNdigits(callLogNumberRaw, digitsToCompare)
                        if (callLogNumberLastN.length >= 7 && callLogNumberLastN == numberToCompareLastN) { callCount++; totalDurationSecondsResult += c.getLong(durationIndex); if (lastCallTimestampResult == null) { lastCallTimestampResult = c.getLong(dateIndex) } }
                    }
                } else { Log.e(TAG_CONFIRM, "CallLog column indices not found!"); statsCalculated = false }
            } ?: run { Log.w(TAG_CONFIRM, "CallLog query returned null cursor."); statsCalculated = false }
        } catch (e: Exception) { Log.e(TAG_CONFIRM, "Error querying call log for stats: ${e.message}", e); statsCalculated = false }
        finally { cursor?.close() }
        return if (statsCalculated) { CallStats(lastCallTimestampResult, callCount, totalDurationSecondsResult) } else { null }
    }

    private fun getLastNdigits(number: String?, n: Int): String {
        if (number.isNullOrEmpty()) return ""
        val digitsOnly = number.replace(Regex("\\D"), "")
        return digitsOnly.takeLast(n)
    }

    private fun displayCallStats(stats: CallStats?) {
        binding.statsProgressBar.visibility = View.GONE; binding.statsContainer.visibility = View.VISIBLE
        if (stats != null) {
            if (stats.callCount > 0) {
                binding.labelLastCall.text = getString(R.string.last_call_label)
                binding.textLastCallDatetime.text = formatLastCallTime(stats.lastCallTimestamp)
                binding.textCallCount.text = getString(R.string.call_count_label_with_value, stats.callCount)
                binding.textTotalDuration.text = getString(R.string.total_duration_label_with_value, formatDuration(stats.totalDurationSeconds))
                binding.labelLastCall.visibility = View.VISIBLE; binding.textLastCallDatetime.visibility = View.VISIBLE
                binding.textCallCount.visibility = View.VISIBLE; binding.textTotalDuration.visibility = View.VISIBLE; Log.d(TAG_CONFIRM, "Displaying call stats.")
            } else {
                binding.labelLastCall.text = getString(R.string.call_stats_not_available)
                binding.textLastCallDatetime.visibility = View.GONE; binding.textCallCount.visibility = View.GONE; binding.textTotalDuration.visibility = View.GONE; Log.d(TAG_CONFIRM, "Zero calls found.")
            }
        } else {
            binding.labelLastCall.text = getString(R.string.call_stats_not_available)
            binding.textLastCallDatetime.visibility = View.GONE; binding.textCallCount.visibility = View.GONE; binding.textTotalDuration.visibility = View.GONE; Log.w(TAG_CONFIRM, "Stats calculation failed.")
        }
    }

    private fun formatLastCallTime(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return "-"
        val dateTimeFormat = DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM, // Стиль даты
            DateFormat.SHORT,  // Стиль времени
            Locale.getDefault() // Локаль по умолчанию
        )
        return dateTimeFormat.format(Date(timestamp))
    }

    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "-"; val hours = TimeUnit.SECONDS.toHours(totalSeconds); val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60; val seconds = totalSeconds % 60
        return if (hours > 0) { String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds) }
        else { String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds) }
    }
} // Конец класса CallConfirmActivity