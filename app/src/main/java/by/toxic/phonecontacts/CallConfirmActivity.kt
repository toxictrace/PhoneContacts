package by.toxic.phonecontacts

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor // Убедитесь, что этот импорт есть
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.view.Window // <<<--- ДОБАВЛЕН ИМПОРТ ДЛЯ Window ---<<<
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
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

private const val TAG_CONFIRM = "CallConfirmActivity" // Тег для логов

class CallConfirmActivity : AppCompatActivity() {

    private lateinit var binding: DialogCallConfirmBinding

    private var phoneNumber: String? = null
    private var contactName: String? = null
    private var photoThumbnailUriString: String? = null
    private var contactId: Long = -1L

    // Лаунчер для запроса разрешения на звонок
    private val requestCallPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { Log.d(TAG_CONFIRM, "CALL_PHONE permission granted."); performCall() }
            else { Log.w(TAG_CONFIRM, "CALL_PHONE permission denied."); Toast.makeText(this, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show(); finish() }
        }

    // Класс для хранения статистики
    private data class CallStats(
        val lastCallTimestamp: Long?,
        val callCount: Int,
        val totalDurationSeconds: Long
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // <<<--- Запрашиваем окно без заголовка ДО вызова super.onCreate и setContentView ---<<<
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)

        binding = DialogCallConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // ActionBar уже не должен отображаться из-за темы и запроса выше
        // supportActionBar?.hide()

        // Получение данных из Intent
        phoneNumber = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_PHONE_NUMBER)
        contactName = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_CONTACT_NAME)
        photoThumbnailUriString = intent.getStringExtra(ContactsGridWidgetProvider.EXTRA_PHOTO_URI)
        contactId = intent.getLongExtra(ContactsGridWidgetProvider.EXTRA_CONTACT_ID, -1L)

        Log.d(TAG_CONFIRM, "Activity created. PN: $phoneNumber, Name: $contactName, ThumbUri: $photoThumbnailUriString, ContactID: $contactId")

        // Проверка наличия номера телефона
        if (phoneNumber.isNullOrEmpty()) {
            Log.e(TAG_CONFIRM, "Phone number is missing. Finishing.")
            Toast.makeText(this, R.string.error_phone_number_missing, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Отображение основной информации
        binding.contactName.text = contactName ?: getString(R.string.unknown_contact)
        binding.phoneNumber.text = phoneNumber
        loadPhotoWithFallback() // Загрузка фото

        // Назначение обработчиков кнопкам
        binding.buttonCall.setOnClickListener { checkPermissionAndCall() }
        binding.buttonCancel.setOnClickListener { finish() } // Закрыть Activity

        // Загрузка и отображение статистики звонков
        loadAndDisplayCallStats()
    }

    // Загрузка фото контакта с fallback на превью
    private fun loadPhotoWithFallback() {
        var primaryUri: Uri? = null
        var fallbackUri: Uri? = null

        if (contactId != -1L) {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            primaryUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Photo.DISPLAY_PHOTO)
        }
        if (!photoThumbnailUriString.isNullOrEmpty()) {
            try { fallbackUri = Uri.parse(photoThumbnailUriString) }
            catch (e: Exception) { Log.e(TAG_CONFIRM, "Error parsing thumbnail URI: $photoThumbnailUriString") }
        }
        Log.d(TAG_CONFIRM, "Photo URIs - Primary: $primaryUri, Fallback: $fallbackUri")

        // Запрос для Fallback (превью или плейсхолдер)
        val fallbackRequest = Glide.with(this)
            .load(fallbackUri)
            .apply(RequestOptions()
                .error(R.drawable.ic_contact_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop() // Масштабирование
            )

        // Основной запрос Glide
        Glide.with(this)
            .load(primaryUri)
            .apply(RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop() // Масштабирование
            )
            .error(fallbackRequest) // Fallback при ошибке
            .placeholder(R.drawable.ic_contact_placeholder) // Плейсхолдер во время загрузки
            .into(binding.contactPhoto) // Целевой ImageView
    }

    // Проверка разрешения и запуск звонка
    private fun checkPermissionAndCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG_CONFIRM, "CALL_PHONE permission already granted.")
            performCall()
        } else {
            Log.d(TAG_CONFIRM, "CALL_PHONE permission not granted. Requesting...")
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    // Выполнение звонка
    private fun performCall() {
        if (phoneNumber.isNullOrEmpty()) {
            Log.e(TAG_CONFIRM, "Cannot perform call, phone number is null.")
            finish()
            return
        }
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            if (callIntent.resolveActivity(packageManager) != null) {
                Log.d(TAG_CONFIRM, "Starting ACTION_CALL intent.")
                startActivity(callIntent)
            } else {
                Log.w(TAG_CONFIRM, "No activity found to handle ACTION_CALL.")
                Toast.makeText(this, R.string.error_no_call_app, Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e(TAG_CONFIRM, "SecurityException during ACTION_CALL: ${e.message}", e)
            Toast.makeText(this, R.string.permission_call_needed_for_call_options, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG_CONFIRM, "Exception during ACTION_CALL: ${e.message}", e)
            Toast.makeText(this, R.string.error_call_failed, Toast.LENGTH_SHORT).show()
        } finally {
            // Закрываем диалог после попытки звонка (успешной или нет)
            finish()
        }
    }

    // --- Методы для статистики ---

    // Запуск загрузки и отображения статистики
    private fun loadAndDisplayCallStats() {
        Log.d(TAG_CONFIRM, "Checking READ_CALL_LOG permission for stats...")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG_CONFIRM, "READ_CALL_LOG permission denied.")
            // Показываем сообщение о необходимости разрешения
            binding.statsProgressBar.visibility = View.GONE
            binding.statsContainer.visibility = View.VISIBLE
            binding.labelLastCall.text = getString(R.string.call_stats_permission_needed)
            binding.textLastCallDatetime.visibility = View.GONE
            binding.textCallCount.visibility = View.GONE
            binding.textTotalDuration.visibility = View.GONE
            return
        }

        Log.d(TAG_CONFIRM, "READ_CALL_LOG permission granted. Starting stats fetch.")
        // Показываем ProgressBar, скрываем текст статистики
        binding.statsProgressBar.visibility = View.VISIBLE
        binding.statsContainer.visibility = View.GONE

        // Запускаем корутину для фоновой загрузки
        lifecycleScope.launch(Dispatchers.IO) {
            val stats = fetchCallStatistics(phoneNumber!!) // Запрос данных
            // Возвращаемся в главный поток для обновления UI
            withContext(Dispatchers.Main) {
                displayCallStats(stats)
            }
        }
    }

    // Получение статистики из CallLog
    private fun fetchCallStatistics(number: String): CallStats? {
        Log.d(TAG_CONFIRM, "Fetching call stats for number: $number")
        val resolver = contentResolver
        val projection = arrayOf(CallLog.Calls.DATE, CallLog.Calls.DURATION)
        val selection = "${CallLog.Calls.NUMBER} = ?"
        val selectionArgs = arrayOf(number)
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        var lastCallTimestampResult: Long? = null
        var callCountResult = 0
        var totalDurationSecondsResult: Long = 0
        var statsCalculated = false

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder)
            cursor?.use { cursorNonNull -> // Используем явное имя параметра
                callCountResult = cursorNonNull.count
                Log.d(TAG_CONFIRM, "CallLog query returned $callCountResult entries.")

                if (cursorNonNull.moveToFirst()) {
                    val dateIndex = cursorNonNull.getColumnIndex(CallLog.Calls.DATE)
                    val durationIndex = cursorNonNull.getColumnIndex(CallLog.Calls.DURATION)

                    if (dateIndex != -1 && durationIndex != -1) {
                        lastCallTimestampResult = cursorNonNull.getLong(dateIndex)
                        do {
                            totalDurationSecondsResult += cursorNonNull.getLong(durationIndex)
                        } while (cursorNonNull.moveToNext())
                        statsCalculated = true
                        Log.d(TAG_CONFIRM, "Stats calculated: LastCall=$lastCallTimestampResult, Count=$callCountResult, TotalDuration=$totalDurationSecondsResult sec")
                    } else { Log.e(TAG_CONFIRM, "CallLog column indices not found!") }
                } else { Log.d(TAG_CONFIRM, "No call log entries found."); statsCalculated = true }
            }
        } catch (e: Exception) { Log.e(TAG_CONFIRM, "Error querying call log for stats: ${e.message}", e) }

        // Возвращаем результат после try-catch
        return if (statsCalculated) { CallStats(lastCallTimestampResult, callCountResult, totalDurationSecondsResult) }
        else { null }
    }

    // Отображение статистики в UI
    private fun displayCallStats(stats: CallStats?) {
        binding.statsProgressBar.visibility = View.GONE // Скрываем прогресс
        binding.statsContainer.visibility = View.VISIBLE // Показываем контейнер статистики

        if (stats != null) { // Если статистика успешно загружена
            if (stats.callCount > 0) { // Если были звонки
                binding.labelLastCall.text = getString(R.string.last_call_label)
                binding.textLastCallDatetime.text = formatLastCallTime(stats.lastCallTimestamp)
                binding.labelLastCall.visibility = View.VISIBLE
                binding.textLastCallDatetime.visibility = View.VISIBLE

                binding.textCallCount.text = getString(R.string.call_count_label) + " " + stats.callCount.toString()
                binding.textTotalDuration.text = getString(R.string.total_duration_label) + " " + formatDuration(stats.totalDurationSeconds)
                binding.textCallCount.visibility = View.VISIBLE
                binding.textTotalDuration.visibility = View.VISIBLE
                Log.d(TAG_CONFIRM, "Displaying call stats.")
            } else { // Звонков не было
                binding.labelLastCall.text = getString(R.string.call_stats_not_available) // Используем лейбл для сообщения
                binding.textLastCallDatetime.visibility = View.GONE // Скрываем поле даты
                binding.textCallCount.visibility = View.GONE
                binding.textTotalDuration.visibility = View.GONE
                Log.d(TAG_CONFIRM, "Zero calls found for this number.")
            }
        } else { // Ошибка загрузки статистики
            binding.labelLastCall.text = getString(R.string.call_stats_not_available)
            binding.textLastCallDatetime.visibility = View.GONE
            binding.textCallCount.visibility = View.GONE
            binding.textTotalDuration.visibility = View.GONE
            Log.d(TAG_CONFIRM, "Call stats calculation failed (returned null).")
        }
    }

    // Форматирование времени последнего звонка
    private fun formatLastCallTime(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return "-"
        val sdf = SimpleDateFormat("d MMM yyyy г. HH:mm", Locale("ru")) // Формат для русского языка
        return sdf.format(Date(timestamp))
    }

    // Форматирование общей длительности звонков
    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds < 0) return "-";
        val hours = TimeUnit.SECONDS.toHours(totalSeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
        val seconds = totalSeconds % 60
        return if (hours > 0) { String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds) }
        else { String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds) }
    }
}