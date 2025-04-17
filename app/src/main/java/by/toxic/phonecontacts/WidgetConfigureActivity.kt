package by.toxic.phonecontacts

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
// import android.provider.MediaStore // Не используется напрямую
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View // Импорт View нужен для setView в диалоге
import android.widget.EditText
import android.widget.FrameLayout // Импорт FrameLayout
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import by.toxic.phonecontacts.databinding.ActivityWidgetConfigureBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
// import java.io.InputStreamReader // Не используется
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.collections.ArrayList


class WidgetConfigureActivity : AppCompatActivity(), SettingInteractionListener {

    // Companion object теперь public (по умолчанию)
    companion object {
        private const val TAG = "WidgetConfigure"
        // Поддерживаемые значения для диалогов
        val supportedColumns = listOf("3", "4", "5", "6")
        val supportedHeights = listOf("70", "80", "90", "100", "110", "120", "130", "140", "150", "160")
        val compareDigitsOptions = (WidgetPrefsUtils.MIN_COMPARE_DIGITS..WidgetPrefsUtils.MAX_COMPARE_DIGITS).map { it.toString() }.toTypedArray()
        // Константы для бэкапа
        private const val BACKUP_FILENAME_PREFIX = "PhoneContacts_"
        private const val BACKUP_FILENAME_SUFFIX = ".zip"
        private const val BACKUP_MIME_TYPE = "application/zip"
        private const val BACKUP_SETTINGS_FILENAME = "settings.json"
        private const val BACKUP_AVATARS_DIR = "avatars/"
        // Константы для разрешений
        private val CALL_LOG_PERMISSION = Manifest.permission.READ_CALL_LOG
        private val CONTACTS_PERMISSION = Manifest.permission.READ_CONTACTS
        private val CALL_PHONE_PERMISSION = Manifest.permission.CALL_PHONE
        private val STORAGE_PERMISSION_IMAGES: String by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        // private val STORAGE_PERMISSION_READ: String = Manifest.permission.READ_EXTERNAL_STORAGE // Не используется
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: ActivityWidgetConfigureBinding
    private lateinit var settingsAdapter: ConfigureSettingsAdapter
    private var settingsList: List<SettingItem> = emptyList()

    private lateinit var clickActionOptions: Map<String, String>
    private lateinit var sortOrderOptions: Map<String, String>
    private lateinit var avatarModeOptions: Map<String, String>

    private var currentColumnCount: String = WidgetPrefsUtils.DEFAULT_COLUMN_COUNT.toString()
    private var currentClickAction: String = WidgetPrefsUtils.DEFAULT_CLICK_ACTION
    private var currentSortOrder: String = WidgetPrefsUtils.DEFAULT_SORT_ORDER
    private var currentMaxItems: Int = WidgetPrefsUtils.DEFAULT_MAX_ITEMS
    private var currentItemHeight: String = WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT.toString()
    private var currentSelectedContactUris = ArrayList<String>()
    private var currentShowUnknown: Boolean = WidgetPrefsUtils.DEFAULT_SHOW_UNKNOWN
    private var currentShowDialerButton: Boolean = WidgetPrefsUtils.DEFAULT_SHOW_DIALER_BTN
    private var currentShowRefreshButton: Boolean = WidgetPrefsUtils.DEFAULT_SHOW_REFRESH_BTN
    private var currentFilterOldUnknown: Boolean = WidgetPrefsUtils.DEFAULT_FILTER_OLD_UNKNOWN
    private var currentCompareDigitsCount: Int = WidgetPrefsUtils.DEFAULT_COMPARE_DIGITS_COUNT
    private var currentAvatarMode: String = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_MODE
    private var currentAvatarCustomUri: String? = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_CUSTOM_URI

    private lateinit var selectContactsLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var selectAvatarLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestSinglePermissionLauncher: ActivityResultLauncher<String>

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // --- Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppSettings.getSelectedThemeResId(this))
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val intentWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (intentWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Невалидный AppWidget ID получен. Завершение Activity.")
            Toast.makeText(this, R.string.error_widget_id_missing, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        appWidgetId = intentWidgetId
        Log.i(TAG, "Конфигурация для widget ID: $appWidgetId")
        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeOptionsMaps()
        initializeLaunchers()
        loadCurrentWidgetSettings()
        setupUI()
        buildSettingsList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            Log.d(TAG, "Нажата кнопка Назад/Закрыть в Toolbar.")
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // --- Initialization ---
    private fun initializeOptionsMaps() {
        clickActionOptions = mapOf( WidgetPrefsUtils.CLICK_ACTION_DIAL to getString(R.string.click_action_dial), WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL to getString(R.string.click_action_confirm_call), WidgetPrefsUtils.CLICK_ACTION_CALL to getString(R.string.click_action_call) )
        sortOrderOptions = mapOf( WidgetPrefsUtils.SORT_ORDER_FAVORITES to getString(R.string.sort_order_favorites), WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS to getString(R.string.sort_order_favorites_recents), WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT to getString(R.string.sort_order_favorites_recents_frequent), WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT to getString(R.string.sort_order_favorites_frequent) )
        avatarModeOptions = mapOf( WidgetPrefsUtils.AVATAR_MODE_DEFAULT to getString(R.string.avatar_mode_default), WidgetPrefsUtils.AVATAR_MODE_CUSTOM to getString(R.string.avatar_mode_custom) )
    }

    private fun initializeLaunchers() {
        selectContactsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "Результат выбора контактов: resultCode=${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedUris = result.data?.getStringArrayListExtra(SelectContactsActivity.RESULT_SELECTED_URIS)
                if (selectedUris != null) {
                    Log.i(TAG, "Получено ${selectedUris.size} URI из SelectContactsActivity.")
                    if (currentSelectedContactUris != selectedUris) {
                        currentSelectedContactUris = selectedUris
                        rebuildSettingsList()
                    } else { Log.d(TAG, "Список выбранных контактов не изменился.") }
                } else { Log.w(TAG, "Не получены URI из SelectContactsActivity (data или extra is null).") }
            } else { Log.d(TAG, "Выбор контактов отменен пользователем.") }
        }

        requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            Log.d(TAG, "Результат запроса нескольких разрешений: $permissions")
            if (checkRequiredPermissions(showToast = false)) {
                Log.d(TAG, "Все необходимые разрешения есть после запроса. Продолжение сохранения.")
                checkMiuiAndProceedSave()
            } else {
                Log.w(TAG, "Не все необходимые разрешения были предоставлены после запроса.")
                showRequiredPermissionsToast()
            }
        }

        requestSinglePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "Результат запроса одного разрешения ($STORAGE_PERMISSION_IMAGES): $isGranted")
            if (isGranted) {
                Log.d(TAG, "Разрешение на доступ к изображениям предоставлено. Запуск выбора изображения.")
                launchImagePicker()
            } else {
                Log.w(TAG, "Разрешение на доступ к изображениям отклонено.")
                Toast.makeText(this, R.string.permission_storage_needed_for_avatar, Toast.LENGTH_LONG).show()
                rebuildSettingsList()
            }
        }

        backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri: Uri? ->
            if (uri != null) { Log.i(TAG, "Получен URI для сохранения бэкапа: $uri"); performBackup(uri) }
            else { Log.d(TAG, "Создание файла бэкапа отменено пользователем."); Toast.makeText(this, R.string.export_cancelled, Toast.LENGTH_SHORT).show() }
        }

        restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) { Log.i(TAG, "Выбран файл для восстановления: $uri"); performRestore(uri) }
            else { Log.d(TAG, "Выбор файла для восстановления отменен пользователем."); Toast.makeText(this, R.string.import_cancelled, Toast.LENGTH_SHORT).show() }
        }

        selectAvatarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "Результат выбора аватара: resultCode=${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                val selectedImageUri: Uri = result.data!!.data!!
                Log.i(TAG, "Выбран URI изображения для аватара: $selectedImageUri")
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(selectedImageUri, takeFlags)
                    Log.d(TAG, "Успешно получено постоянное разрешение для URI: $selectedImageUri")
                } catch (e: SecurityException) { Log.w(TAG, "Не удалось получить постоянное разрешение для URI: ${e.message}") }
                catch (e: Exception) { Log.e(TAG, "Ошибка при получении постоянного разрешения для URI: ${e.message}", e) }
                copyImageToInternalStorage(selectedImageUri)
            } else {
                Log.d(TAG, "Выбор аватара отменен или не удался.")
                rebuildSettingsList()
            }
        }
    }

    private fun loadCurrentWidgetSettings() {
        Log.d(TAG, "Загрузка настроек для widget $appWidgetId")
        val context = applicationContext
        currentColumnCount = WidgetPrefsUtils.loadColumnCount(context, appWidgetId).toString()
        if (currentColumnCount !in supportedColumns) currentColumnCount = WidgetPrefsUtils.DEFAULT_COLUMN_COUNT.toString()
        currentClickAction = WidgetPrefsUtils.loadClickAction(context, appWidgetId)
        if (currentClickAction !in clickActionOptions.keys) currentClickAction = WidgetPrefsUtils.DEFAULT_CLICK_ACTION
        currentSortOrder = WidgetPrefsUtils.loadSortOrder(context, appWidgetId)
        if (currentSortOrder !in sortOrderOptions.keys) currentSortOrder = WidgetPrefsUtils.DEFAULT_SORT_ORDER
        currentMaxItems = WidgetPrefsUtils.loadMaxItems(context, appWidgetId)
        if (currentMaxItems <= 0) currentMaxItems = WidgetPrefsUtils.DEFAULT_MAX_ITEMS
        currentItemHeight = WidgetPrefsUtils.loadItemHeight(context, appWidgetId).toString()
        if (currentItemHeight !in supportedHeights) currentItemHeight = WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT.toString()
        currentSelectedContactUris = ArrayList(WidgetPrefsUtils.loadContactUris(context, appWidgetId))
        currentShowUnknown = WidgetPrefsUtils.loadShowUnknown(context, appWidgetId)
        currentShowDialerButton = WidgetPrefsUtils.loadShowDialerButton(context, appWidgetId)
        currentShowRefreshButton = WidgetPrefsUtils.loadShowRefreshButton(context, appWidgetId)
        currentFilterOldUnknown = WidgetPrefsUtils.loadFilterOldUnknown(context, appWidgetId)
        currentCompareDigitsCount = WidgetPrefsUtils.loadCompareDigitsCount(context, appWidgetId)
        currentAvatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(context, appWidgetId)
        if (currentAvatarMode !in avatarModeOptions.keys) currentAvatarMode = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_MODE
        currentAvatarCustomUri = WidgetPrefsUtils.loadUnknownAvatarCustomUri(context, appWidgetId)
        if (currentAvatarMode != WidgetPrefsUtils.AVATAR_MODE_CUSTOM) currentAvatarCustomUri = null
        Log.d(TAG, "Настройки загружены: Col=$currentColumnCount, Click=$currentClickAction, Sort=$currentSortOrder, Max=$currentMaxItems, Height=$currentItemHeight, Uris=${currentSelectedContactUris.size}, Unk=$currentShowUnknown, Dial=$currentShowDialerButton, Refr=$currentShowRefreshButton, FiltOld=$currentFilterOldUnknown, Digits=$currentCompareDigitsCount, AvMode=$currentAvatarMode, AvUri=$currentAvatarCustomUri")
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.widget_config_title) + " #$appWidgetId"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        settingsAdapter = ConfigureSettingsAdapter(this)
        binding.recyclerViewSettings.apply {
            layoutManager = LinearLayoutManager(this@WidgetConfigureActivity)
            adapter = settingsAdapter
            itemAnimator = null
            clipToPadding = false
            setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.fab_bottom_padding))
        }
        binding.fabSave.setOnClickListener { Log.d(TAG, "Нажата кнопка 'Сохранить'."); checkPermissionsBeforeSave() }
    }

    // --- Построение списка настроек ---
    private fun createSettingsList(): List<SettingItem> {
        Log.d(TAG, "Создание списка настроек с текущими значениями...")
        val newList = mutableListOf<SettingItem>()
        val context = this
        // --- Секция Темы ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            newList.add(SettingItem.Header(getString(R.string.settings_theme_header)))
            newList.add(SettingItem.SwitchSetting( SettingItem.SwitchSetting.ID_USE_MONET, getString(R.string.setting_monet_theme_title), getString(R.string.setting_monet_theme_summary_on), getString(R.string.setting_monet_theme_summary_off), AppSettings.isMonetEnabled(context) ))
        }
        // --- Секция Внешний вид ---
        newList.add(SettingItem.Header(getString(R.string.settings_appearance_header)))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_COLUMNS, getString(R.string.num_columns), currentColumnCount ))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_ITEM_HEIGHT, getString(R.string.item_height_title), "$currentItemHeight dp" ))
        // --- Секция Контакты ---
        newList.add(SettingItem.Header(getString(R.string.settings_contacts_header)))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_SORT_ORDER, getString(R.string.contacts_order_title), sortOrderOptions[currentSortOrder] ))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_MAX_ITEMS, getString(R.string.max_items_title), currentMaxItems.toString() ))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_SELECT_CONTACTS, getString(R.string.select_favorites_title), if (currentSelectedContactUris.isEmpty()) getString(R.string.select_favorites_summary_none) else getString(R.string.select_favorites_summary, currentSelectedContactUris.size) ))
        newList.add(SettingItem.SwitchSetting( SettingItem.SwitchSetting.ID_SHOW_UNKNOWN, getString(R.string.setting_show_unknown_title), getString(R.string.setting_show_unknown_summary_on), getString(R.string.setting_show_unknown_summary_off), currentShowUnknown ))
        newList.add(SettingItem.SwitchSetting( SettingItem.SwitchSetting.ID_FILTER_OLD_UNKNOWN, getString(R.string.setting_filter_old_unknown_title), getString(R.string.setting_filter_old_unknown_summary_on), getString(R.string.setting_filter_old_unknown_summary_off), currentFilterOldUnknown ))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_COMPARE_DIGITS, getString(R.string.setting_compare_digits_title), getString(R.string.setting_compare_digits_summary_with_value, currentCompareDigitsCount) ))
        val avatarSummary = if (currentAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && currentAvatarCustomUri != null) getString(R.string.avatar_mode_custom_selected) else avatarModeOptions[currentAvatarMode] ?: getString(R.string.avatar_mode_default)
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_UNKNOWN_AVATAR, getString(R.string.setting_unknown_avatar_title), avatarSummary ))
        // --- Секция Действия ---
        newList.add(SettingItem.Header(getString(R.string.settings_actions_header)))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_CLICK_ACTION, getString(R.string.click_action_title), clickActionOptions[currentClickAction] ))
        // --- Секция Кнопки виджета ---
        newList.add(SettingItem.Header(getString(R.string.settings_widget_buttons_header)))
        newList.add(SettingItem.SwitchSetting( SettingItem.SwitchSetting.ID_SHOW_DIALER, getString(R.string.setting_show_dialer_button_title), getString(R.string.setting_show_dialer_button_summary_on), getString(R.string.setting_show_dialer_button_summary_off), currentShowDialerButton ))
        newList.add(SettingItem.SwitchSetting( SettingItem.SwitchSetting.ID_SHOW_REFRESH, getString(R.string.setting_show_refresh_button_title), getString(R.string.setting_show_refresh_button_summary_on), getString(R.string.setting_show_refresh_button_summary_off), currentShowRefreshButton ))
        // --- Секция Сохранение/Загрузка ---
        newList.add(SettingItem.Header(getString(R.string.settings_backup_header)))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_EXPORT_SETTINGS, getString(R.string.settings_export_title), getString(R.string.settings_export_summary) ))
        newList.add(SettingItem.ClickableSetting( SettingItem.ClickableSetting.ID_IMPORT_SETTINGS, getString(R.string.settings_import_title), getString(R.string.settings_import_summary) ))
        return newList.toList()
    }

    private fun buildSettingsList() {
        settingsList = createSettingsList()
        settingsAdapter.submitList(settingsList)
        Log.d(TAG, "Список настроек перестроен и отправлен в адаптер.")
    }

    private fun rebuildSettingsList() {
        buildSettingsList()
    }

    // --- Реализация SettingInteractionListener ---
    override fun onSettingClicked(item: SettingItem.ClickableSetting) {
        if (isFinishing || isDestroyed) return
        Log.d(TAG, "Клик по настройке: ID=${item.id}")
        when (item.id) {
            SettingItem.ClickableSetting.ID_COLUMNS -> showColumnsDialog()
            SettingItem.ClickableSetting.ID_ITEM_HEIGHT -> showItemHeightDialog()
            SettingItem.ClickableSetting.ID_SORT_ORDER -> showSortOrderDialog()
            SettingItem.ClickableSetting.ID_MAX_ITEMS -> showMaxItemsDialog()
            SettingItem.ClickableSetting.ID_CLICK_ACTION -> showClickActionDialog()
            SettingItem.ClickableSetting.ID_SELECT_CONTACTS -> launchSelectContactsActivity()
            SettingItem.ClickableSetting.ID_EXPORT_SETTINGS -> startBackup()
            SettingItem.ClickableSetting.ID_IMPORT_SETTINGS -> startRestore()
            SettingItem.ClickableSetting.ID_COMPARE_DIGITS -> showCompareDigitsDialog()
            SettingItem.ClickableSetting.ID_UNKNOWN_AVATAR -> showUnknownAvatarDialog()
            else -> { Log.w(TAG, "Необработанный клик по настройке с ID: ${item.id}"); Toast.makeText(this, getString(R.string.error_not_implemented) + ": " + item.title, Toast.LENGTH_SHORT).show() }
        }
    }

    // !!! Важно: Этот метод содержит основную логику обработки изменений Switch !!!
    override fun onSwitchChanged(item: SettingItem.SwitchSetting, isChecked: Boolean) {
        Log.d(TAG, "Изменение переключателя: ID=${item.id}, Новое состояние=$isChecked")
        var needsListRebuild = true // Флаг: нужно ли перестраивать весь список UI
        var needsFilterItemUpdate = false // Флаг: нужно ли обновить доступность filter_old_unknown
        var themeChanged = false // Флаг: изменилась ли тема

        // Обновляем соответствующее текущее значение
        when (item.id) {
            SettingItem.SwitchSetting.ID_USE_MONET -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val currentSetting = AppSettings.isMonetEnabled(this)
                    if (currentSetting != isChecked) {
                        AppSettings.setMonetEnabled(this, isChecked) // Сохраняем настройку
                        themeChanged = true // Устанавливаем флаг смены темы
                        needsListRebuild = false // Пересоздание Activity обновит список
                        Log.i(TAG, "Настройка темы Monet изменена на: $isChecked. Пересоздание Activity...")
                    } else {
                        needsListRebuild = false // Состояние не изменилось
                    }
                } else {
                    needsListRebuild = false // На старых версиях эта настройка не должна меняться
                }
            }
            SettingItem.SwitchSetting.ID_SHOW_UNKNOWN -> {
                if (currentShowUnknown != isChecked) {
                    currentShowUnknown = isChecked
                    needsFilterItemUpdate = true // Зависимый элемент нужно обновить
                } else {
                    needsListRebuild = false // Состояние не изменилось
                }
            }
            SettingItem.SwitchSetting.ID_SHOW_DIALER -> {
                if (currentShowDialerButton != isChecked) {
                    currentShowDialerButton = isChecked
                } else {
                    needsListRebuild = false
                }
            }
            SettingItem.SwitchSetting.ID_SHOW_REFRESH -> {
                if (currentShowRefreshButton != isChecked) {
                    currentShowRefreshButton = isChecked
                } else {
                    needsListRebuild = false
                }
            }
            SettingItem.SwitchSetting.ID_FILTER_OLD_UNKNOWN -> {
                // Проверяем, активно ли изменение (зависит от isShowUnknownEnabled())
                if (isShowUnknownEnabled()) {
                    if (currentFilterOldUnknown != isChecked) {
                        currentFilterOldUnknown = isChecked
                    } else {
                        needsListRebuild = false
                    }
                } else {
                    // Логическое состояние не меняем, если родительский switch выключен
                    Log.w(TAG, "Изменение 'filter_old_unknown' проигнорировано, т.к. 'show_unknown' выключен.")
                    needsListRebuild = false // Не перестраиваем список, т.к. значение не изменилось
                }
            }
            else -> {
                Log.w(TAG, "Необработанный ID переключателя: ${item.id}")
                needsListRebuild = false
            }
        }

        // Обновляем UI после изменения
        if (themeChanged) {
            // Если тема изменилась, пересоздаем Activity
            Toast.makeText(this, R.string.applying_theme_toast, Toast.LENGTH_SHORT).show()
            recreate() // Этот вызов перестроит весь UI с новой темой
        } else if (needsListRebuild) {
            // Если нужно перестроить список (изменилось значение или зависимость)
            rebuildSettingsList() // Перестраивает весь список и обновляет адаптер
            // Если нужно явно обновить зависимый элемент 'filter_old_unknown'
            // (например, после изменения 'show_unknown')
            if (needsFilterItemUpdate) {
                notifyFilterOldUnknownItem()
            }
        }
    }


    override fun isShowUnknownEnabled(): Boolean = currentShowUnknown

    private fun notifyFilterOldUnknownItem() {
        val filterItemPosition = settingsList.indexOfFirst { it is SettingItem.SwitchSetting && it.id == SettingItem.SwitchSetting.ID_FILTER_OLD_UNKNOWN }
        if (filterItemPosition != -1) {
            binding.recyclerViewSettings.post { // Выполняем после текущего цикла отрисовки/расчета
                // Проверяем, что адаптер инициализирован и позиция валидна
                if (::settingsAdapter.isInitialized && settingsAdapter.itemCount > filterItemPosition && !binding.recyclerViewSettings.isComputingLayout && !isDestroyed && !isFinishing) {
                    // Обновляем с payload'ом, чтобы ViewHolder мог изменить alpha/enabled state
                    settingsAdapter.notifyItemChanged(filterItemPosition, ConfigureSettingsAdapter.PAYLOAD_UPDATE_ENABLED)
                    Log.d(TAG,"Отправлено уведомление об изменении доступности для filter_old_unknown в позиции $filterItemPosition")
                } else {
                    Log.w(TAG,"Не удалось уведомить об изменении filter_old_unknown: позиция=$filterItemPosition, адаптер/recyclerView не готовы или Activity завершается.")
                }
            }
        } else {
            Log.w(TAG,"Не найден элемент filter_old_unknown в списке для уведомления об изменении.")
        }
    }

    // --- Запуск других Activity ---
    private fun launchSelectContactsActivity() {
        if (isFinishing || isDestroyed) return
        if (ContextCompat.checkSelfPermission(this, CONTACTS_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Запуск SelectContactsActivity...")
            val intent = Intent(this, SelectContactsActivity::class.java)
            intent.putStringArrayListExtra(SelectContactsActivity.EXTRA_SELECTED_URIS, currentSelectedContactUris)
            selectContactsLauncher.launch(intent)
        } else {
            Log.w(TAG, "Требуется разрешение READ_CONTACTS для запуска SelectContactsActivity.")
            requestPermissionsLauncher.launch(arrayOf(CONTACTS_PERMISSION))
            Toast.makeText(this, R.string.permission_needed_select, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Диалоги Настроек ---
    private fun showColumnsDialog() {
        if (isFinishing || isDestroyed) return
        val options = supportedColumns.toTypedArray()
        val currentSelectionIndex = options.indexOf(currentColumnCount).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this).setTitle(R.string.num_columns)
            .setSingleChoiceItems(options, currentSelectionIndex) { dialog, which ->
                val selectedValue = options[which]; if (currentColumnCount != selectedValue) { Log.d(TAG, "Выбрано колонок: $selectedValue"); currentColumnCount = selectedValue; rebuildSettingsList() }; dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showItemHeightDialog() {
        if (isFinishing || isDestroyed) return
        val options = supportedHeights.toTypedArray()
        val currentSelectionIndex = options.indexOf(currentItemHeight).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this).setTitle(R.string.item_height_title)
            .setSingleChoiceItems(options, currentSelectionIndex) { dialog, which ->
                val selectedValue = options[which]; if (currentItemHeight != selectedValue) { Log.d(TAG, "Выбрана высота элемента: $selectedValue dp"); currentItemHeight = selectedValue; rebuildSettingsList() }; dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showSortOrderDialog() {
        if (isFinishing || isDestroyed) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_radio_group, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_radio_group)
        val sortedKeys = sortOrderOptions.keys.toList()
        sortedKeys.forEachIndexed { index, key -> val radioButton = MaterialRadioButton(this).apply { text = sortOrderOptions[key]; id = index; tag = key; val padding = (12 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding) }; radioGroup.addView(radioButton); if (key == currentSortOrder) radioGroup.check(radioButton.id) }
        MaterialAlertDialogBuilder(this).setTitle(R.string.contacts_order_title).setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> val checkedId = radioGroup.checkedRadioButtonId; if (checkedId != -1) { val selectedRadioButton = radioGroup.findViewById<MaterialRadioButton>(checkedId); val selectedValue = selectedRadioButton.tag as? String; if (selectedValue != null && currentSortOrder != selectedValue) { Log.d(TAG, "Выбран порядок сортировки: $selectedValue"); currentSortOrder = selectedValue; rebuildSettingsList() } }; dialog.dismiss() }.show()
    }

    private fun showMaxItemsDialog() {
        if (isFinishing || isDestroyed) return
        val editTextLayout: FrameLayout? = try { LayoutInflater.from(this).inflate(R.layout.dialog_edittext, null) as? FrameLayout } catch (e: Exception) { Log.e(TAG, "Ошибка инфлейта dialog_edittext", e); null }
        val editText: EditText? = editTextLayout?.findViewById(R.id.dialog_edit_text)
        if (editTextLayout == null || editText == null) { Log.e(TAG, "Не удалось найти EditText в макете dialog_edittext.xml"); Toast.makeText(this, "Ошибка отображения диалога", Toast.LENGTH_SHORT).show(); return }
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        editText.setText(currentMaxItems.toString())
        editText.selectAll()
        MaterialAlertDialogBuilder(this).setTitle(R.string.max_items_title)
            .setMessage(R.string.max_items_summary).setView(editTextLayout)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val newValue = editText.text.toString().toIntOrNull()
                if (newValue != null && newValue > 0) { if (currentMaxItems != newValue) { Log.d(TAG, "Установлено макс. элементов: $newValue"); currentMaxItems = newValue; rebuildSettingsList() }; dialog.dismiss() }
                else { Toast.makeText(this, R.string.error_invalid_number, Toast.LENGTH_SHORT).show() }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showClickActionDialog() {
        if (isFinishing || isDestroyed) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_radio_group, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_radio_group)
        val actionKeys = clickActionOptions.keys.toList()
        actionKeys.forEachIndexed { index, key -> val radioButton = MaterialRadioButton(this).apply { text = clickActionOptions[key]; id = index; tag = key; val padding = (12 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding) }; radioGroup.addView(radioButton); if (key == currentClickAction) radioGroup.check(radioButton.id) }
        MaterialAlertDialogBuilder(this).setTitle(R.string.click_action_title).setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> val checkedId = radioGroup.checkedRadioButtonId; if (checkedId != -1) { val selectedRadioButton = radioGroup.findViewById<MaterialRadioButton>(checkedId); val selectedValue = selectedRadioButton.tag as? String; if (selectedValue != null && currentClickAction != selectedValue) { Log.d(TAG, "Выбрано действие по клику: $selectedValue"); currentClickAction = selectedValue; rebuildSettingsList() } }; dialog.dismiss() }.show()
    }

    private fun showCompareDigitsDialog() {
        if (isFinishing || isDestroyed) return
        val options = compareDigitsOptions
        val currentSelectionIndex = options.indexOf(currentCompareDigitsCount.toString()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this).setTitle(R.string.setting_compare_digits_title)
            .setSingleChoiceItems(options, currentSelectionIndex) { dialog, which ->
                val selectedValueString = options[which]; val selectedValueInt = selectedValueString.toIntOrNull() ?: WidgetPrefsUtils.DEFAULT_COMPARE_DIGITS_COUNT; if (currentCompareDigitsCount != selectedValueInt) { Log.d(TAG, "Выбрано цифр для сравнения: $selectedValueInt"); currentCompareDigitsCount = selectedValueInt; rebuildSettingsList() }; dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showUnknownAvatarDialog() {
        if (isFinishing || isDestroyed) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_radio_group, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_radio_group)
        val modeKeys = avatarModeOptions.keys.toList()
        modeKeys.forEachIndexed { index, key -> val radioButton = MaterialRadioButton(this).apply { text = avatarModeOptions[key]; id = index; tag = key; val padding = (12 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding) }; radioGroup.addView(radioButton); if (key == currentAvatarMode) radioGroup.check(radioButton.id) }
        MaterialAlertDialogBuilder(this).setTitle(R.string.setting_unknown_avatar_title).setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ -> val checkedId = radioGroup.checkedRadioButtonId; if (checkedId != -1) { val selectedRadioButton = radioGroup.findViewById<MaterialRadioButton>(checkedId); val selectedMode = selectedRadioButton.tag as? String ?: WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_MODE; if (currentAvatarMode != selectedMode) { Log.d(TAG, "Выбран режим аватара: $selectedMode"); if (selectedMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM) { checkStoragePermissionAndPickImage() } else { val oldCustomUri = currentAvatarCustomUri; currentAvatarMode = selectedMode; currentAvatarCustomUri = null; if (oldCustomUri != null) WidgetPrefsUtils.deleteCustomAvatarFile(this, oldCustomUri); rebuildSettingsList() } } }; dialog.dismiss() }.show()
    }

    // --- Работа с аватаром ---
    private fun checkStoragePermissionAndPickImage() {
        Log.d(TAG, "Проверка разрешения на доступ к изображениям ($STORAGE_PERMISSION_IMAGES)...")
        when {
            ContextCompat.checkSelfPermission(this, STORAGE_PERMISSION_IMAGES) == PackageManager.PERMISSION_GRANTED -> { Log.d(TAG, "Разрешение на доступ к изображениям есть. Запуск выбора."); launchImagePicker() }
            shouldShowRequestPermissionRationale(STORAGE_PERMISSION_IMAGES) -> { Log.d(TAG, "Требуется объяснение для разрешения на доступ к изображениям."); if (isFinishing || isDestroyed) return; MaterialAlertDialogBuilder(this).setTitle(R.string.permission_needed_title).setMessage(R.string.permission_storage_needed_for_avatar).setPositiveButton(R.string.grant_permission) { _, _ -> requestSinglePermissionLauncher.launch(STORAGE_PERMISSION_IMAGES) }.setNegativeButton(android.R.string.cancel) { d, _ -> Log.d(TAG, "Запрос разрешения на изображения отменен пользователем в rationale."); rebuildSettingsList(); d.dismiss() }.show() }
            else -> { Log.d(TAG, "Запрос разрешения на доступ к изображениям."); requestSinglePermissionLauncher.launch(STORAGE_PERMISSION_IMAGES) }
        }
    }

    private fun launchImagePicker() {
        if (isFinishing || isDestroyed) return
        Log.d(TAG, "Запуск Intent.ACTION_OPEN_DOCUMENT для выбора изображения.")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        try { selectAvatarLauncher.launch(intent) }
        catch (e: Exception) { Log.e(TAG, "Не удалось запустить выбор изображения: ${e.message}", e); Toast.makeText(this, R.string.error_launching_picker, Toast.LENGTH_SHORT).show(); currentAvatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(this, appWidgetId); rebuildSettingsList() }
    }

    private fun copyImageToInternalStorage(sourceUri: Uri) {
        Log.d(TAG, "Начало копирования аватара из $sourceUri")
        Toast.makeText(this, R.string.processing_avatar, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            val targetFile = WidgetPrefsUtils.getCustomAvatarFile(this@WidgetConfigureActivity, appWidgetId)
            var success = false; var errorMessage: String? = null
            val previousAvatarMode = currentAvatarMode; val previousAvatarUri = currentAvatarCustomUri
            try {
                Log.d(TAG, "Копирование в файл: ${targetFile.absolutePath}")
                contentResolver.openInputStream(sourceUri)?.use { inputStream -> FileOutputStream(targetFile).use { outputStream -> inputStream.copyTo(outputStream); success = true; Log.i(TAG, "Аватар успешно скопирован в ${targetFile.absolutePath}") } } ?: run { throw Exception(getString(R.string.error_opening_input_stream)) }
            } catch (e: Exception) { Log.e(TAG, "Ошибка копирования изображения: ${e.message}", e); errorMessage = getString(R.string.error_copying_avatar, e.localizedMessage ?: e.javaClass.simpleName); if (targetFile.exists()) targetFile.delete(); success = false }
            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                if (success) { currentAvatarMode = WidgetPrefsUtils.AVATAR_MODE_CUSTOM; currentAvatarCustomUri = Uri.fromFile(targetFile).toString(); Toast.makeText(this@WidgetConfigureActivity, R.string.avatar_set_successfully, Toast.LENGTH_SHORT).show(); rebuildSettingsList() }
                else { currentAvatarMode = previousAvatarMode; currentAvatarCustomUri = previousAvatarUri; Toast.makeText(this@WidgetConfigureActivity, errorMessage ?: getString(R.string.error_copying_avatar_unknown), Toast.LENGTH_LONG).show(); rebuildSettingsList() }
            }
        }
    }

    // --- Сохранение конфигурации ---
    private fun checkPermissionsBeforeSave() {
        Log.d(TAG, "Проверка разрешений перед сохранением...")
        val requiredPermissions = mutableListOf<String>()
        val needsCallPhone = currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CALL || currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL; if (needsCallPhone && ContextCompat.checkSelfPermission(this, CALL_PHONE_PERMISSION) != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "Требуется разрешение CALL_PHONE."); requiredPermissions.add(CALL_PHONE_PERMISSION) }
        val needsCallLog = currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS || currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT || currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT; if (needsCallLog && ContextCompat.checkSelfPermission(this, CALL_LOG_PERMISSION) != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "Требуется разрешение READ_CALL_LOG."); requiredPermissions.add(CALL_LOG_PERMISSION) }
        if (ContextCompat.checkSelfPermission(this, CONTACTS_PERMISSION) != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "Требуется разрешение READ_CONTACTS."); requiredPermissions.add(CONTACTS_PERMISSION) }
        if (currentAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && currentAvatarCustomUri != null && ContextCompat.checkSelfPermission(this, STORAGE_PERMISSION_IMAGES) != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, "Требуется разрешение READ_MEDIA_IMAGES (или READ_EXTERNAL_STORAGE)."); requiredPermissions.add(STORAGE_PERMISSION_IMAGES) }
        if (requiredPermissions.isNotEmpty()) { Log.i(TAG, "Запрос недостающих разрешений: ${requiredPermissions.joinToString()}"); Toast.makeText(this, R.string.permissions_needed_for_save, Toast.LENGTH_LONG).show(); requestPermissionsLauncher.launch(requiredPermissions.toTypedArray()) }
        else { Log.d(TAG, "Все необходимые разрешения предоставлены."); checkMiuiAndProceedSave() }
    }

    private fun checkRequiredPermissions(showToast: Boolean = true): Boolean {
        var allGranted = true; val missingPermissions = mutableListOf<String>()
        val needsCallPhone = currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CALL || currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL; if (needsCallPhone && ContextCompat.checkSelfPermission(this, CALL_PHONE_PERMISSION) != PackageManager.PERMISSION_GRANTED) { allGranted = false; missingPermissions.add("CALL_PHONE") }
        val needsCallLog = currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS || currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT || currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT; if (needsCallLog && ContextCompat.checkSelfPermission(this, CALL_LOG_PERMISSION) != PackageManager.PERMISSION_GRANTED) { allGranted = false; missingPermissions.add("READ_CALL_LOG") }
        if (ContextCompat.checkSelfPermission(this, CONTACTS_PERMISSION) != PackageManager.PERMISSION_GRANTED) { allGranted = false; missingPermissions.add("READ_CONTACTS") }
        if (currentAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && currentAvatarCustomUri != null && ContextCompat.checkSelfPermission(this, STORAGE_PERMISSION_IMAGES) != PackageManager.PERMISSION_GRANTED) { allGranted = false; missingPermissions.add("READ_MEDIA_IMAGES/STORAGE") }
        if (!allGranted && showToast) { Log.w(TAG, "Отсутствуют разрешения: ${missingPermissions.joinToString()}"); showRequiredPermissionsToast() }; return allGranted
    }

    private fun showRequiredPermissionsToast() {
        val requiredPermissionsText = mutableListOf<String>()
        val needsCallPhone = currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CALL || currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL; val needsCallLog = currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS || currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT || currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT
        if (ContextCompat.checkSelfPermission(this, CONTACTS_PERMISSION) != PackageManager.PERMISSION_GRANTED) requiredPermissionsText.add(getString(R.string.permission_contacts_name))
        if (needsCallLog && ContextCompat.checkSelfPermission(this, CALL_LOG_PERMISSION) != PackageManager.PERMISSION_GRANTED) requiredPermissionsText.add(getString(R.string.permission_call_log_name))
        if (needsCallPhone && ContextCompat.checkSelfPermission(this, CALL_PHONE_PERMISSION) != PackageManager.PERMISSION_GRANTED) requiredPermissionsText.add(getString(R.string.permission_call_phone_name))
        if (currentAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && currentAvatarCustomUri != null && ContextCompat.checkSelfPermission(this, STORAGE_PERMISSION_IMAGES) != PackageManager.PERMISSION_GRANTED) requiredPermissionsText.add(getString(R.string.permission_storage_images_name))
        if (requiredPermissionsText.isNotEmpty()) { val message = getString(R.string.permissions_missing_toast, requiredPermissionsText.joinToString(", ")); Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun checkMiuiAndProceedSave() {
        if (isFinishing || isDestroyed) return
        if (DeviceUtils.isXiaomiDevice() && !AppSettings.hasShownMiuiWarning(this)) { DeviceUtils.showMiuiPermissionsDialog(this, onSettingsOpened = { AppSettings.setMiuiWarningShown(this, true); Log.d(TAG, "MIUI диалог: пользователь перешел в настройки."); Toast.makeText(this, getString(R.string.miui_dialog_check_permissions_later), Toast.LENGTH_LONG).show() }, onAcknowledged = { AppSettings.setMiuiWarningShown(this, true); Log.d(TAG, "MIUI диалог: пользователь нажал 'Понятно / Позже'. Продолжение сохранения."); proceedSaveConfiguration() } ) }
        else { Log.d(TAG, "Проверка MIUI пройдена или не требуется. Продолжение сохранения."); proceedSaveConfiguration() }
    }

    private fun proceedSaveConfiguration() {
        Log.i(TAG, "Сохранение конфигурации для widget $appWidgetId...")
        WidgetPrefsUtils.saveWidgetConfig( this, appWidgetId, currentSelectedContactUris, currentColumnCount.toIntOrNull() ?: WidgetPrefsUtils.DEFAULT_COLUMN_COUNT, currentClickAction, currentSortOrder, currentMaxItems, currentItemHeight.toIntOrNull() ?: WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT, currentShowUnknown, currentShowDialerButton, currentShowRefreshButton, currentFilterOldUnknown, currentCompareDigitsCount, currentAvatarMode, currentAvatarCustomUri )
        val appWidgetManager = AppWidgetManager.getInstance(this)
        ContactsGridWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        val resultValue = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
        setResult(Activity.RESULT_OK, resultValue)
        Log.i(TAG, "Конфигурация для widget $appWidgetId сохранена успешно. Завершение Activity.")
        finish()
    }

    // --- Бэкап и Восстановление ---
    private fun startBackup() {
        if (isFinishing || isDestroyed) return
        Log.d(TAG, "Инициирование процесса бэкапа...")
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()); val fileName = "$BACKUP_FILENAME_PREFIX${timeStamp}$BACKUP_FILENAME_SUFFIX"
        backupLauncher.launch(fileName)
    }

    private fun performBackup(targetUri: Uri) {
        Log.i(TAG, "Выполнение бэкапа в URI: $targetUri"); Toast.makeText(applicationContext, R.string.backup_started, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = applicationContext; val appWidgetManager = AppWidgetManager.getInstance(appContext); val componentName = ComponentName(appContext, ContactsGridWidgetProvider::class.java); val widgetIds: IntArray? = try { appWidgetManager.getAppWidgetIds(componentName) } catch (e: Exception) { Log.e(TAG, "Ошибка получения ID виджетов для бэкапа", e); null }
            if (widgetIds == null || widgetIds.isEmpty()) { Log.w(TAG, "Нет активных виджетов для бэкапа."); withContext(Dispatchers.Main) { Toast.makeText(appContext, R.string.no_widgets_to_backup, Toast.LENGTH_SHORT).show() }; return@launch }
            Log.d(TAG, "Начинается сбор данных для виджетов: ${widgetIds.joinToString()}"); val backupMap = mutableMapOf<Int, WidgetBackupData>(); val backupAvatarTempDir = File(appContext.cacheDir, "backup_avatars_temp"); if (backupAvatarTempDir.exists()) backupAvatarTempDir.deleteRecursively(); backupAvatarTempDir.mkdirs(); val avatarFileMap = mutableMapOf<Int, String>()
            widgetIds.forEach { widgetId -> val avatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(appContext, widgetId); val avatarUriStr = WidgetPrefsUtils.loadUnknownAvatarCustomUri(appContext, widgetId); var backedUpAvatarFileName: String? = null
                if (avatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && avatarUriStr != null) {
                    try { val sourceFileUri = Uri.parse(avatarUriStr); if (sourceFileUri.scheme == "file" && sourceFileUri.path?.startsWith(appContext.filesDir.path) == true) { val sourceFile = File(sourceFileUri.path!!); if (sourceFile.exists()) { val targetFileName = "${WidgetPrefsUtils.AVATAR_FILE_PREFIX}${widgetId}${WidgetPrefsUtils.AVATAR_FILE_SUFFIX}"; val targetFile = File(backupAvatarTempDir, targetFileName); sourceFile.copyTo(targetFile, overwrite = true); avatarFileMap[widgetId] = targetFileName; backedUpAvatarFileName = targetFileName; Log.d(TAG, "Аватар для widget $widgetId скопирован во временную папку: $targetFileName") } else { Log.w(TAG, "Файл аватара для widget $widgetId не найден по пути: ${sourceFile.path}") } } else { Log.w(TAG, "URI аватара для widget $widgetId не является внутренним файлом: $avatarUriStr") } }
                    catch (e: Exception) { Log.e(TAG, "Ошибка копирования аватара для widget $widgetId во время бэкапа: ${e.message}") } }
                backupMap[widgetId] = WidgetBackupData( WidgetPrefsUtils.loadContactUris(appContext, widgetId), WidgetPrefsUtils.loadColumnCount(appContext, widgetId), WidgetPrefsUtils.loadClickAction(appContext, widgetId), WidgetPrefsUtils.loadSortOrder(appContext, widgetId), WidgetPrefsUtils.loadMaxItems(appContext, widgetId), WidgetPrefsUtils.loadItemHeight(appContext, widgetId), WidgetPrefsUtils.loadShowUnknown(appContext, widgetId), WidgetPrefsUtils.loadShowDialerButton(appContext, widgetId), WidgetPrefsUtils.loadShowRefreshButton(appContext, widgetId), WidgetPrefsUtils.loadFilterOldUnknown(appContext, widgetId), WidgetPrefsUtils.loadCompareDigitsCount(appContext, widgetId), avatarMode, backedUpAvatarFileName ); Log.v(TAG, "Данные для widget $widgetId собраны.") }
            val appBackupData = AppBackup(backupMap); var errorMessage: String? = null; var backupSuccess = false
            try { val jsonString = json.encodeToString(appBackupData); appContext.contentResolver.openOutputStream(targetUri)?.use { outputStream -> ZipOutputStream(outputStream).use { zos -> val jsonEntry = ZipEntry(BACKUP_SETTINGS_FILENAME); zos.putNextEntry(jsonEntry); zos.write(jsonString.toByteArray(Charsets.UTF_8)); zos.closeEntry(); Log.d(TAG, "$BACKUP_SETTINGS_FILENAME записан в архив."); avatarFileMap.forEach { (widgetId, fileName) -> val fileToZip = File(backupAvatarTempDir, fileName); if (fileToZip.exists()) { val avatarEntry = ZipEntry("$BACKUP_AVATARS_DIR$fileName"); zos.putNextEntry(avatarEntry); FileInputStream(fileToZip).use { fis -> fis.copyTo(zos) }; zos.closeEntry(); Log.d(TAG, "Файл аватара $fileName для widget $widgetId записан в архив.") } else { Log.w(TAG, "Временный файл аватара $fileName для widget $widgetId не найден для записи в архив.") } } } } ?: throw Exception(getString(R.string.error_opening_output_stream)); backupSuccess = true }
            catch (e: Exception) { Log.e(TAG, "Ошибка во время создания ZIP-архива бэкапа: ${e.message}", e); errorMessage = getString(R.string.backup_failed, e.localizedMessage ?: e.javaClass.simpleName); backupSuccess = false }
            finally { if (backupAvatarTempDir.exists()) { backupAvatarTempDir.deleteRecursively(); Log.d(TAG, "Временная папка бэкапа удалена.") } }
            withContext(Dispatchers.Main) { Toast.makeText(appContext, errorMessage ?: getString(R.string.backup_successful), Toast.LENGTH_LONG).show() }
        }
    }

    private fun startRestore() { if (isFinishing || isDestroyed) return; Log.d(TAG, "Инициирование процесса восстановления..."); restoreLauncher.launch(arrayOf(BACKUP_MIME_TYPE, "*/*")) }

    private fun performRestore(sourceUri: Uri) {
        Log.i(TAG, "Выполнение восстановления из URI: $sourceUri"); Toast.makeText(applicationContext, R.string.restore_started, Toast.LENGTH_SHORT).show()
        var localFileName: String? = null; try { contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) { val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (nameIndex >= 0) localFileName = cursor.getString(nameIndex) } } } catch (e: Exception) { Log.e(TAG, "Не удалось получить имя файла из URI: $sourceUri", e) }
        val finalFileName = localFileName
        val isValidBackupFile = finalFileName != null && finalFileName.startsWith(BACKUP_FILENAME_PREFIX, ignoreCase = true) && finalFileName.endsWith(BACKUP_FILENAME_SUFFIX, ignoreCase = true); if (!isValidBackupFile) { Log.w(TAG, "Выбран невалидный файл бэкапа: '$finalFileName'. Восстановление отменено."); lifecycleScope.launch(Dispatchers.Main) { Toast.makeText(applicationContext, R.string.error_invalid_backup_file_name, Toast.LENGTH_LONG).show() }; return }
        Log.d(TAG,"Имя файла '$finalFileName' прошло проверку.")

        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = applicationContext; var backupData: AppBackup? = null; var errorMessage: String? = null; val restoreTempDir = File(appContext.cacheDir, "restore_avatars_temp"); if (restoreTempDir.exists()) restoreTempDir.deleteRecursively(); restoreTempDir.mkdirs(); val restoredAvatarMap = mutableMapOf<String, File>()
            try {
                appContext.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    // Используем имя zipInputStream
                    ZipInputStream(inputStream).use { zipInputStream ->
                        var entry: ZipEntry? = zipInputStream.nextEntry // Используем zipInputStream
                        while (entry != null) {
                            Log.d(TAG, "Обработка элемента архива: ${entry.name}")
                            when {
                                entry.name.equals(BACKUP_SETTINGS_FILENAME, ignoreCase = true) -> {
                                    // Используем zipInputStream
                                    val jsonString = zipInputStream.reader(Charsets.UTF_8).readText()
                                    backupData = json.decodeFromString<AppBackup>(jsonString)
                                    Log.i(TAG, "Файл настроек $BACKUP_SETTINGS_FILENAME успешно прочитан и десериализован.")
                                }
                                entry.name.startsWith(BACKUP_AVATARS_DIR, ignoreCase = true) && !entry.isDirectory -> {
                                    val avatarFileName = File(entry.name).name
                                    if (avatarFileName.isNotBlank()) {
                                        val tempFile = File(restoreTempDir, avatarFileName)
                                        // Используем zipInputStream
                                        FileOutputStream(tempFile).use { fos -> zipInputStream.copyTo(fos) }
                                        restoredAvatarMap[avatarFileName] = tempFile
                                        Log.d(TAG, "Файл аватара $avatarFileName извлечен во временную папку: ${tempFile.path}")
                                    } else {
                                        Log.w(TAG, "Обнаружен пустой путь к файлу аватара в архиве: ${entry.name}")
                                    }
                                }
                                else -> Log.d(TAG, "Пропуск элемента архива: ${entry.name}")
                            }
                            zipInputStream.closeEntry() // Используем zipInputStream
                            entry = zipInputStream.nextEntry // Используем zipInputStream
                        }
                    } // zipInputStream закроется здесь
                } ?: throw Exception(getString(R.string.error_opening_input_stream))

                if (backupData == null) {
                    throw Exception(getString(R.string.error_settings_json_not_found))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка во время распаковки или чтения архива: ${e.message}", e)
                errorMessage = when (e) {
                    is kotlinx.serialization.SerializationException -> getString(R.string.error_parsing_backup, e.localizedMessage ?: e.javaClass.simpleName)
                    is ZipException -> getString(R.string.error_reading_zip, e.localizedMessage ?: e.javaClass.simpleName)
                    else -> getString(R.string.error_reading_file, e.localizedMessage ?: e.javaClass.simpleName)
                }
                backupData = null
            }

            // Применение настроек (код ниже без изменений)
            if (backupData != null) {
                val appWidgetManager = AppWidgetManager.getInstance(appContext); val componentName = ComponentName(appContext, ContactsGridWidgetProvider::class.java); val currentWidgetIds: IntArray? = try { appWidgetManager.getAppWidgetIds(componentName) } catch (e: Exception) { null }
                if (currentWidgetIds == null || currentWidgetIds.isEmpty()) { Log.w(TAG, "Нет активных виджетов для восстановления настроек."); errorMessage = getString(R.string.no_widgets_to_restore) }
                else { val restoredWidgetIds = mutableListOf<Int>(); Log.i(TAG, "Применение настроек для виджетов: ${currentWidgetIds.joinToString()}")
                    currentWidgetIds.forEach { widgetId -> backupData!!.widgetSettings[widgetId]?.let { widgetData -> Log.d(TAG, "Найдены настройки для widget $widgetId. Применение..."); var finalAvatarUriString: String? = null
                        if (widgetData.unknownAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && widgetData.unknownAvatarCustomUri != null) { val avatarFileNameInBackup = widgetData.unknownAvatarCustomUri; restoredAvatarMap[avatarFileNameInBackup]?.let { tempFile -> val targetFile = WidgetPrefsUtils.getCustomAvatarFile(appContext, widgetId); try { if (targetFile.exists()) targetFile.delete(); tempFile.copyTo(targetFile, overwrite = true); finalAvatarUriString = Uri.fromFile(targetFile).toString(); Log.d(TAG, "Аватар для widget $widgetId восстановлен в ${targetFile.path}") } catch (e: Exception) { Log.e(TAG, "Ошибка копирования восстановленного аватара для widget $widgetId", e) } } ?: Log.w(TAG, "Файл аватара '$avatarFileNameInBackup' для widget $widgetId не найден среди извлеченных.") }
                        WidgetPrefsUtils.saveWidgetConfig( appContext, widgetId, widgetData.contactUris, widgetData.columnCount, widgetData.clickAction, widgetData.sortOrder, widgetData.maxItems, widgetData.itemHeight, widgetData.showUnknown, widgetData.showDialerButton, widgetData.showRefreshButton, widgetData.filterOldUnknown, widgetData.compareDigitsCount, widgetData.unknownAvatarMode, finalAvatarUriString ); restoredWidgetIds.add(widgetId); Log.i(TAG, "Настройки для widget $widgetId успешно применены.") } ?: Log.w(TAG, "Настройки для widget $widgetId не найдены в файле бэкапа.") }
                    if (restoredWidgetIds.isNotEmpty()) { Log.i(TAG, "Обновление восстановленных виджетов: ${restoredWidgetIds.joinToString()}"); restoredWidgetIds.forEach { id -> ContactsGridWidgetProvider.updateAppWidget(appContext, appWidgetManager, id) }; errorMessage = null; if (appWidgetId in restoredWidgetIds) { withContext(Dispatchers.Main) { if (!isFinishing && !isDestroyed) { Toast.makeText(appContext, R.string.settings_reloading, Toast.LENGTH_SHORT).show(); loadCurrentWidgetSettings(); rebuildSettingsList() } } } }
                    else { Log.w(TAG, "В файле бэкапа не найдено настроек для текущих активных виджетов."); errorMessage = getString(R.string.no_widget_settings_found_in_backup) } } }
            if (restoreTempDir.exists()) { restoreTempDir.deleteRecursively(); Log.d(TAG, "Временная папка восстановления удалена.") }
            withContext(Dispatchers.Main) { Toast.makeText(appContext, errorMessage ?: getString(R.string.restore_successful), Toast.LENGTH_LONG).show() }
        }
    }

} // Конец класса WidgetConfigureActivity