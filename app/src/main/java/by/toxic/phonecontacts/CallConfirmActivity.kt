package by.toxic.phonecontacts

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.drawable.Drawable // <<< Явный импорт Drawable >>>
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import by.toxic.phonecontacts.databinding.DialogCallConfirmBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException // <<< Импорт GlideException >>>
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target // <<< Импорт Target >>>
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CallConfirmActivity : AppCompatActivity() {

    // Используем константу для TAG
    private companion object {
        const val TAG = "CallConfirmActivity"
        const val INVALID_CONTACT_ID = -1L
        const val CALL_LOG_QUERY_LIMIT = "500"
        const val MIN_DIGITS_FOR_COMPARISON = 7
    }

    private lateinit var binding: DialogCallConfirmBinding
    private var phoneNumber: String? = null
    private var contactName: String? = null
    private var photoThumbnailUriString: String? = null
    private var contactId: Long = INVALID_CONTACT_ID
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // --- ActivityResultLaunchers ---
    private val requestCallPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Разрешение CALL_PHONE предоставлено.")
                performCall()
            } else {
                Log.w(TAG, "Разрешение CALL_PHONE отклонено.")
                Toast.makeText(this, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show()
                finish()
            }
        }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppSettings.getSelectedThemeResId(this))
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        binding = DialogCallConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindow()
        extractIntentData()

        if (phoneNumber.isNullOrEmpty()) {
            Log.e(TAG, "Критическая ошибка: номер телефона отсутствует в Intent.")
            Toast.makeText(this, R.string.error_phone_number_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Критическая ошибка: ID виджета отсутствует в Intent.")
            Toast.makeText(this, R.string.error_widget_id_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI()
        loadData()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // --- Setup ---

    private fun setupWindow() {
        window?.let { win ->
            win.setBackgroundDrawableResource(R.drawable.dialog_call_confirm_background)
            val params = win.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.CENTER
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            params.dimAmount = 0.7f
            win.attributes = params
            Log.d(TAG, "Параметры окна настроены программно.")
        } ?: Log.w(TAG, "Не удалось получить Window для настройки.")
    }

    private fun extractIntentData() {
        // <<< ИЗМЕНЕНИЕ: Используем прямой доступ к константам >>>
        phoneNumber = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_PHONE_NUMBER)
        contactName = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_CONTACT_NAME)
        photoThumbnailUriString = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_PHOTO_URI)
        contactId = intent.getLongExtra(ContactsGridWidgetProvider.EXTRA_CONTACT_ID, INVALID_CONTACT_ID)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        Log.d(TAG, "Intent data: PN=$phoneNumber, Name=$contactName, ThumbUri=$photoThumbnailUriString, ContactID=$contactId, WidgetID=$appWidgetId")
    }

    private fun setupUI() {
        binding.contactName.text = contactName ?: getString(R.string.unknown_contact)
        binding.phoneNumber.text = phoneNumber
        binding.buttonCall.setOnClickListener { checkPermissionAndCall() }
        binding.buttonCancel.setOnClickListener { finish() }
    }

    private fun loadData() {
        loadPhotoWithFallback()
        loadAndDisplayCallStats()
    }

    // --- Photo Loading ---

    private fun loadPhotoWithFallback() {
        if (isDestroyed || isFinishing) {
            Log.w(TAG, "Activity уничтожается, загрузка фото отменена.")
            return
        }

        val defaultAvatarResId = R.drawable.anonim
        val avatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(this, appWidgetId)
        val customAvatarUriString = WidgetPrefsUtils.loadUnknownAvatarCustomUri(this, appWidgetId)
        var settingsFallbackSource: Any = defaultAvatarResId

        if (avatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && !customAvatarUriString.isNullOrEmpty()) {
            try {
                val customUri = Uri.parse(customAvatarUriString)
                if (customUri.scheme == "file" && customUri.path?.let { File(it).exists() } == true) {
                    settingsFallbackSource = customUri
                    Log.d(TAG, "Используется кастомный аватар как fallback: $customUri")
                } else {
                    Log.w(TAG, "Кастомный URI некорректен или файл не найден: $customAvatarUriString. Используется default.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ошибка парсинга кастомного URI $customAvatarUriString. Используется default.", e)
            }
        } else {
            Log.d(TAG, "Используется стандартный аватар как fallback.")
        }

        val baseRequestOptions = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .placeholder(defaultAvatarResId)
            .error(defaultAvatarResId)

        if (contactId != INVALID_CONTACT_ID) {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            val displayPhotoUri: Uri? = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO)
            val contactThumbnailUri: Uri? = photoThumbnailUriString?.let {
                try { Uri.parse(it) } catch (e: Exception) { Log.w(TAG, "Ошибка парсинга photoThumbnailUriString: $it"); null }
            }

            Log.d(TAG, "Загрузка для известного контакта ID $contactId. DisplayUri: $displayPhotoUri, ThumbUri: $contactThumbnailUri, Fallback: $settingsFallbackSource")

            val settingsFallbackRequest = Glide.with(this)
                .load(settingsFallbackSource)
                .apply(baseRequestOptions.clone().error(defaultAvatarResId))

            val thumbnailRequest = Glide.with(this)
                .load(contactThumbnailUri)
                .apply(baseRequestOptions.clone())
                .error(settingsFallbackRequest)

            Glide.with(this)
                .load(displayPhotoUri)
                .apply(baseRequestOptions.clone())
                .error(thumbnailRequest)
                .listener(createGlideListener("DisplayPhoto"))
                .into(binding.contactPhoto)

        } else {
            Log.d(TAG, "Загрузка для неизвестного контакта. Fallback: $settingsFallbackSource")
            Glide.with(this)
                .load(settingsFallbackSource)
                .apply(baseRequestOptions.clone().error(defaultAvatarResId))
                .listener(createGlideListener("UnknownContactFallback"))
                .into(binding.contactPhoto)
        }
    }

    // <<< ИЗМЕНЕНИЕ: Исправлена сигнатура RequestListener >>>
    private fun createGlideListener(source: String): RequestListener<Drawable> {
        return object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<Drawable>, // Явный тип Target<Drawable>
                isFirstResource: Boolean
            ): Boolean {
                Log.w(TAG, "Glide: Ошибка загрузки '$source' из модели '$model'. Ошибка: ${e?.message}")
                return false // Позволяем Glide обработать ошибку (показать error drawable)
            }

            override fun onResourceReady(
                resource: Drawable, // Явный тип Drawable
                model: Any, // Явный тип Any
                target: Target<Drawable>?, // Target может быть null? Проверяем сигнатуру. Да, может быть nullable.
                dataSource: DataSource, // Явный тип DataSource
                isFirstResource: Boolean
            ): Boolean {
                Log.d(TAG, "Glide: Успешно загружено '$source' из модели '$model' (DataSource: $dataSource)")
                return false // Позволяем Glide отобразить ресурс
            }
        }
    }


    // --- Call Handling ---

    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Разрешение CALL_PHONE уже есть. Выполняем звонок.")
            performCall()
        } else {
            Log.d(TAG, "Разрешение CALL_PHONE отсутствует. Запрашиваем...")
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun performCall() {
        if (phoneNumber.isNullOrEmpty()) {
            Log.e(TAG, "Невозможно позвонить, номер телефона отсутствует.")
            finish()
            return
        }

        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.fromParts("tel", phoneNumber, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (callIntent.resolveActivity(packageManager) != null) {
                try {
                    Log.d(TAG, "Запуск ACTION_CALL для номера: $phoneNumber")
                    startActivity(callIntent)
                } catch (se: SecurityException) {
                    Log.e(TAG, "SecurityException при запуске ACTION_CALL (даже после проверки?): ${se.message}", se)
                    Toast.makeText(this, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Неизвестная ошибка при запуске ACTION_CALL", e)
                    Toast.makeText(this, R.string.error_call_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "Не найдено Activity для обработки ACTION_CALL.")
                Toast.makeText(this, R.string.error_no_call_app, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) { // Ловим общие ошибки подготовки Intent, если такие возможны
            Log.e(TAG, "Ошибка подготовки ACTION_CALL Intent", e)
            Toast.makeText(this, R.string.error_call_failed, Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }

    // --- Call Stats ---
    private data class CallStats(
        val lastCallTimestamp: Long?,
        val callCount: Int,
        val totalDurationSeconds: Long
    )

    private fun loadAndDisplayCallStats() {
        Log.d(TAG, "Проверка разрешения READ_CALL_LOG...")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Разрешение READ_CALL_LOG отсутствует.")
            showStatsPermissionNeeded()
            return
        }

        Log.d(TAG, "Разрешение READ_CALL_LOG есть. Загрузка статистики...")
        showStatsLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            val currentPhoneNumber = phoneNumber
            if (currentPhoneNumber.isNullOrEmpty()) {
                Log.e(TAG, "Невозможно загрузить статистику: номер телефона пуст.")
                withContext(Dispatchers.Main) { showStatsError() }
                return@launch
            }

            val stats = fetchCallStatistics(currentPhoneNumber)
            withContext(Dispatchers.Main) {
                // Проверяем, не закрылась ли Activity, пока статистика грузилась
                if (!isFinishing && !isDestroyed) {
                    displayCallStats(stats)
                } else {
                    Log.w(TAG,"Activity уничтожена, отображение статистики отменено.")
                }
            }
        }
    }

    private fun fetchCallStatistics(numberToCompare: String): CallStats? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Невозможно получить статистику: невалидный appWidgetId")
            return null
        }

        val digitsToCompare = WidgetPrefsUtils.loadCompareDigitsCount(this, appWidgetId)
        val numberToCompareLastN = getLastNdigits(numberToCompare, digitsToCompare)

        if (numberToCompareLastN.length < MIN_DIGITS_FOR_COMPARISON) {
            Log.w(TAG, "Номер '$numberToCompare' ($numberToCompareLastN) слишком короткий для сравнения (нужно $MIN_DIGITS_FOR_COMPARISON цифр). Статистика не будет загружена.")
            return CallStats(null, 0, 0)
        }

        val resolver = contentResolver
        val projection = arrayOf(
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.NUMBER
        )
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        var lastCallTimestampResult: Long? = null
        var callCount = 0
        var totalDurationSecondsResult: Long = 0
        var cursor: Cursor? = null

        try {
            val queryUri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, CALL_LOG_QUERY_LIMIT)
                .build()

            Log.d(TAG, "Запрос статистики для номера (последние $digitsToCompare цифр): $numberToCompareLastN")
            cursor = resolver.query(queryUri, projection, null, null, sortOrder)

            cursor?.use { c ->
                val dateIndex = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = c.getColumnIndex(CallLog.Calls.DURATION)
                val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)

                if (dateIndex == -1 || durationIndex == -1 || numberIndex == -1) {
                    Log.e(TAG, "Не найдены необходимые колонки в CallLog!")
                    return null
                }

                while (c.moveToNext()) {
                    val callLogNumberRaw = c.getString(numberIndex) ?: continue
                    val callLogNumberLastN = getLastNdigits(callLogNumberRaw, digitsToCompare)

                    if (callLogNumberLastN.length >= MIN_DIGITS_FOR_COMPARISON && callLogNumberLastN == numberToCompareLastN) {
                        callCount++
                        totalDurationSecondsResult += c.getLong(durationIndex)
                        if (lastCallTimestampResult == null) {
                            lastCallTimestampResult = c.getLong(dateIndex)
                        }
                    }
                }
                Log.d(TAG, "Статистика найдена: Count=$callCount, Duration=$totalDurationSecondsResult, LastCall=$lastCallTimestampResult")
            } ?: run {
                Log.w(TAG, "Запрос к CallLog вернул null cursor.")
                return null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException при запросе статистики CallLog: ${e.message}", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при запросе статистики CallLog: ${e.message}", e)
            return null
        }
        // finally { cursor закрывается в use {} }
        return CallStats(lastCallTimestampResult, callCount, totalDurationSecondsResult)
    }

    private fun getLastNdigits(number: String?, n: Int): String {
        if (number.isNullOrEmpty()) return ""
        val digitsOnly = number.replace(Regex("\\D"), "")
        return digitsOnly.takeLast(n)
    }

    // --- UI Updates for Stats ---

    private fun showStatsLoading() {
        binding.statsProgressBar.visibility = View.VISIBLE
        binding.statsContainer.visibility = View.GONE
    }

    private fun showStatsPermissionNeeded() {
        binding.statsProgressBar.visibility = View.GONE
        binding.statsContainer.visibility = View.VISIBLE
        binding.labelLastCall.text = getString(R.string.call_stats_permission_needed)
        binding.textLastCallDatetime.visibility = View.GONE
        binding.textCallCount.visibility = View.GONE
        binding.textTotalDuration.visibility = View.GONE
    }

    private fun showStatsError() {
        binding.statsProgressBar.visibility = View.GONE
        binding.statsContainer.visibility = View.VISIBLE
        binding.labelLastCall.text = getString(R.string.call_stats_not_available)
        binding.textLastCallDatetime.visibility = View.GONE
        binding.textCallCount.visibility = View.GONE
        binding.textTotalDuration.visibility = View.GONE
        Log.w(TAG, "Не удалось загрузить или рассчитать статистику.")
    }

    private fun displayCallStats(stats: CallStats?) {
        binding.statsProgressBar.visibility = View.GONE
        binding.statsContainer.visibility = View.VISIBLE

        if (stats != null) {
            if (stats.callCount > 0) {
                binding.labelLastCall.text = getString(R.string.last_call_label)
                binding.textLastCallDatetime.text = formatLastCallTime(stats.lastCallTimestamp)
                binding.textCallCount.text = getString(R.string.call_count_label_with_value, stats.callCount)
                binding.textTotalDuration.text = getString(R.string.total_duration_label_with_value, formatDuration(stats.totalDurationSeconds))

                binding.labelLastCall.visibility = View.VISIBLE
                binding.textLastCallDatetime.visibility = View.VISIBLE
                binding.textCallCount.visibility = View.VISIBLE
                binding.textTotalDuration.visibility = View.VISIBLE
                Log.d(TAG, "Отображена статистика звонков.")
            } else {
                binding.labelLastCall.text = getString(R.string.call_stats_not_available) // Или "Нет звонков"
                binding.textLastCallDatetime.visibility = View.GONE
                binding.textCallCount.visibility = View.GONE
                binding.textTotalDuration.visibility = View.GONE
                Log.d(TAG, "Звонков для данного номера не найдено.")
            }
        } else {
            showStatsError()
        }
    }

    // --- Formatting ---

    private fun formatLastCallTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0L) return "-"
        return try {
            val dateTimeFormat = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            )
            dateTimeFormat.format(Date(timestamp))
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка форматирования времени последнего звонка: $timestamp", e)
            "-"
        }
    }

    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "-"
        if (totalSeconds == 0L) return "00:00"

        val hours = TimeUnit.SECONDS.toHours(totalSeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
        val seconds = totalSeconds % 60

        return try {
            if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка форматирования длительности: $totalSeconds", e)
            "-"
        }
    }

}