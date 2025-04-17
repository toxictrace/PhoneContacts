package by.toxic.phonecontacts

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build // Добавить, если нет
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater // <<< Добавлен импорт >>>
import android.view.MenuItem
import android.widget.EditText
import android.widget.RadioGroup // <<< Добавлен импорт >>>
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding // <<< Добавлен импорт >>>
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import by.toxic.phonecontacts.databinding.ActivityWidgetConfigureBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton // <<< Добавлен импорт >>>
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


class WidgetConfigureActivity : AppCompatActivity(), SettingInteractionListener {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: ActivityWidgetConfigureBinding
    private lateinit var settingsAdapter: ConfigureSettingsAdapter
    private var settingsList: List<SettingItem> = emptyList()

    private val supportedColumns = listOf("3", "4", "5", "6")
    companion object {
        val supportedHeights = listOf("70", "80", "90", "100", "110", "120", "130", "140", "150", "160")
        private const val TAG = "WidgetConfigure"
    }
    private lateinit var clickActionOptions: Map<String, String>; private lateinit var clickActionDisplayNames: List<String>
    private lateinit var sortOrderOptions: Map<String, String>; private lateinit var sortOrderDisplayNames: List<String>
    private val compareDigitsOptions = arrayOf("7", "8", "9", "10")
    private lateinit var avatarModeOptions: Map<String, String>; private lateinit var avatarModeDisplayNames: List<String>

    var currentColumnCount: String = WidgetPrefsUtils.DEFAULT_COLUMN_COUNT.toString()
    var currentClickAction: String = WidgetPrefsUtils.DEFAULT_CLICK_ACTION
    var currentSortOrder: String = WidgetPrefsUtils.DEFAULT_SORT_ORDER
    var currentMaxItems: Int = WidgetPrefsUtils.DEFAULT_MAX_ITEMS
    var currentItemHeight: String = WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT.toString()
    var currentSelectedContactUris = ArrayList<String>()
    var currentShowUnknown: Boolean = WidgetPrefsUtils.DEFAULT_SHOW_UNKNOWN
    var currentShowDialerButton: Boolean = WidgetPrefsUtils.DEFAULT_SHOW_DIALER_BTN
    var currentShowRefreshButton: Boolean = WidgetPrefsUtils.DEFAULT_SHOW_REFRESH_BTN
    var currentFilterOldUnknown: Boolean = WidgetPrefsUtils.DEFAULT_FILTER_OLD_UNKNOWN
    var currentCompareDigitsCount: Int = WidgetPrefsUtils.DEFAULT_COMPARE_DIGITS_COUNT
    var currentAvatarMode: String = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_MODE
    var currentAvatarCustomUri: String? = WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_CUSTOM_URI

    private lateinit var selectContactsLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestCallPhonePermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestCallLogPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var selectAvatarLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestStoragePermissionLauncher: ActivityResultLauncher<String>

    private val callPhonePermissionRationale = R.string.permission_call_needed_for_call_options
    private val callLogPermissionRationale = R.string.permission_call_log_needed_favorites_recents
    private val storagePermissionRationale = R.string.permission_storage_needed_for_avatar

    private val RC_SELECT_CONTACTS = 101
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val requiredStoragePermission: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppSettings.getSelectedThemeResId(this))
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val intentWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (intentWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { Log.e(TAG, "Invalid AppWidget ID received. Finishing."); finish(); return }

        appWidgetId = intentWidgetId
        Log.d(TAG, "Configuring widget ID: $appWidgetId")
        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        clickActionOptions = mapOf( WidgetPrefsUtils.CLICK_ACTION_DIAL to getString(R.string.click_action_dial), WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL to getString(R.string.click_action_confirm_call), WidgetPrefsUtils.CLICK_ACTION_CALL to getString(R.string.click_action_call) ); clickActionDisplayNames = clickActionOptions.values.toList()
        sortOrderOptions = mapOf(
            WidgetPrefsUtils.SORT_ORDER_FAVORITES to getString(R.string.sort_order_favorites),
            WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS to getString(R.string.sort_order_favorites_recents),
            WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT to getString(R.string.sort_order_favorites_recents_frequent),
            WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT to getString(R.string.sort_order_favorites_frequent)
        ); sortOrderDisplayNames = sortOrderOptions.values.toList()
        avatarModeOptions = mapOf(
            WidgetPrefsUtils.AVATAR_MODE_DEFAULT to getString(R.string.avatar_mode_default),
            WidgetPrefsUtils.AVATAR_MODE_CUSTOM to getString(R.string.avatar_mode_custom)
        ); avatarModeDisplayNames = avatarModeOptions.values.toList()

        initializeLaunchers()
        loadCurrentWidgetSettings()
        setupUI()
        buildSettingsList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadCurrentWidgetSettings() {
        Log.d(TAG, "Loading settings for widget $appWidgetId")
        currentColumnCount = WidgetPrefsUtils.loadColumnCount(this, appWidgetId).toString(); if (currentColumnCount !in supportedColumns) { currentColumnCount = WidgetPrefsUtils.DEFAULT_COLUMN_COUNT.toString() }
        currentClickAction = WidgetPrefsUtils.loadClickAction(this, appWidgetId)
        currentSortOrder = WidgetPrefsUtils.loadSortOrder(this, appWidgetId); if (currentSortOrder !in sortOrderOptions.keys) { currentSortOrder = WidgetPrefsUtils.DEFAULT_SORT_ORDER }
        currentMaxItems = WidgetPrefsUtils.loadMaxItems(this, appWidgetId); if (currentMaxItems <= 0) { currentMaxItems = WidgetPrefsUtils.DEFAULT_MAX_ITEMS }
        currentItemHeight = WidgetPrefsUtils.loadItemHeight(this, appWidgetId).toString(); if (currentItemHeight !in supportedHeights) { currentItemHeight = WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT.toString() }
        currentSelectedContactUris = ArrayList(WidgetPrefsUtils.loadContactUris(this, appWidgetId))
        currentShowUnknown = WidgetPrefsUtils.loadShowUnknown(this, appWidgetId)
        currentShowDialerButton = WidgetPrefsUtils.loadShowDialerButton(this, appWidgetId)
        currentShowRefreshButton = WidgetPrefsUtils.loadShowRefreshButton(this, appWidgetId)
        currentFilterOldUnknown = WidgetPrefsUtils.loadFilterOldUnknown(this, appWidgetId)
        currentCompareDigitsCount = WidgetPrefsUtils.loadCompareDigitsCount(this, appWidgetId)
        currentAvatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(this, appWidgetId)
        currentAvatarCustomUri = WidgetPrefsUtils.loadUnknownAvatarCustomUri(this, appWidgetId)
        Log.d(TAG, "Settings loaded: Col=$currentColumnCount, Click=$currentClickAction, Sort=$currentSortOrder, Max=$currentMaxItems, Height=$currentItemHeight, Uris=${currentSelectedContactUris.size}, Unk=$currentShowUnknown, Dial=$currentShowDialerButton, Refr=$currentShowRefreshButton, FiltOld=$currentFilterOldUnknown, Digits=$currentCompareDigitsCount, AvMode=$currentAvatarMode, AvUri=$currentAvatarCustomUri")
    }

    private fun initializeLaunchers() {
        requestCallPhonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { Log.d(TAG, "CALL_PHONE permission granted."); checkMiuiAndProceedSave() }
            else { Log.w(TAG, "CALL_PHONE permission denied."); Toast.makeText(this, getString(callPhonePermissionRationale), Toast.LENGTH_LONG).show() }
        }

        selectContactsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "SelectContactsLauncher result: ${result.resultCode}")
            if (result.resultCode == Activity.RESULT_OK) {
                val selectedUris = result.data?.getStringArrayListExtra(SelectContactsActivity.RESULT_SELECTED_URIS)
                if (selectedUris != null) {
                    Log.d(TAG, "Received ${selectedUris.size} URIs from SelectContactsActivity.")
                    currentSelectedContactUris = selectedUris
                    rebuildSettingsList()
                } else {
                    Log.w(TAG, "No URIs received from SelectContactsActivity.")
                }
            }
        }

        requestCallLogPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { Log.d(TAG, "READ_CALL_LOG permission granted."); checkMiuiAndProceedSave() }
            else { Log.w(TAG, "READ_CALL_LOG permission denied."); Toast.makeText(this, getString(R.string.warning_recents_unavailable), Toast.LENGTH_LONG).show(); checkMiuiAndProceedSave() }
        }

        backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            if (uri != null) { performBackup(uri) }
            else { Log.d(TAG, "Backup cancelled by user."); Toast.makeText(this, R.string.export_cancelled, Toast.LENGTH_SHORT).show() }
        }

        restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) { performRestore(uri) }
            else { Log.d(TAG, "Restore cancelled by user."); Toast.makeText(this, R.string.import_cancelled, Toast.LENGTH_SHORT).show() }
        }

        selectAvatarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                val selectedImageUri: Uri? = result.data?.data
                Log.d(TAG, "Avatar selected: $selectedImageUri")
                if (selectedImageUri != null) {
                    try {
                        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(selectedImageUri, takeFlags)
                        Log.d(TAG, "Successfully took persistable URI permission for $selectedImageUri")
                    } catch (e: SecurityException) { Log.w(TAG, "Failed to take persistable URI permission (might be okay): ${e.message}") }
                    catch (e: Exception) { Log.e(TAG, "Error taking persistable URI permission: ${e.message}", e) }
                    copyImageToInternalStorage(selectedImageUri)
                } else {
                    Log.w(TAG, "Selected image URI is null.")
                    Toast.makeText(this, R.string.error_selecting_avatar, Toast.LENGTH_SHORT).show()
                    rebuildSettingsList()
                }
            } else {
                Log.d(TAG, "Avatar selection cancelled or failed (resultCode=${result.resultCode}, data=${result.data}).")
                rebuildSettingsList()
            }
        }

        requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Storage permission granted ($requiredStoragePermission). Launching image picker.")
                launchImagePicker()
            } else {
                Log.w(TAG, "Storage permission denied ($requiredStoragePermission).")
                Toast.makeText(this, R.string.permission_storage_needed_for_avatar, Toast.LENGTH_LONG).show()
                rebuildSettingsList()
            }
        }
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
        }
        binding.fabSave.setOnClickListener {
            Log.d(TAG, "Save button clicked.")
            checkPermissionsBeforeSave()
        }
    }


    private fun createSettingsList(): List<SettingItem> {
        val newList = mutableListOf<SettingItem>()
        Log.d(TAG, "Creating settings list with current values...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            newList.add(SettingItem.Header(getString(R.string.settings_theme_header)))
            newList.add(SettingItem.SwitchSetting(
                id = "use_monet_theme",
                title = getString(R.string.setting_monet_theme_title),
                summaryOn = getString(R.string.setting_monet_theme_summary_on),
                summaryOff = getString(R.string.setting_monet_theme_summary_off),
                isChecked = AppSettings.isMonetEnabled(this)
            ))
        }

        newList.add(SettingItem.Header(getString(R.string.settings_appearance_header)))
        newList.add(SettingItem.ClickableSetting("columns", getString(R.string.num_columns), currentColumnCount))
        newList.add(SettingItem.ClickableSetting("item_height", getString(R.string.item_height_title), currentItemHeight))

        newList.add(SettingItem.Header(getString(R.string.settings_contacts_header)))
        newList.add(SettingItem.ClickableSetting("sort_order", getString(R.string.contacts_order_title), sortOrderOptions[currentSortOrder]))
        newList.add(SettingItem.ClickableSetting("max_items", getString(R.string.max_items_title), currentMaxItems.toString()))
        newList.add(SettingItem.ClickableSetting(id = "select_contacts", title = getString(R.string.select_favorites_title), summary = getString(R.string.select_favorites_summary, currentSelectedContactUris.size) ))
        newList.add(SettingItem.SwitchSetting(id = "show_unknown", title = getString(R.string.setting_show_unknown_title), summaryOn = getString(R.string.setting_show_unknown_summary_on), summaryOff = getString(R.string.setting_show_unknown_summary_off), isChecked = currentShowUnknown))
        newList.add(SettingItem.SwitchSetting(id = "filter_old_unknown", title = getString(R.string.setting_filter_old_unknown_title), summaryOn = getString(R.string.setting_filter_old_unknown_summary_on), summaryOff = getString(R.string.setting_filter_old_unknown_summary_off), isChecked = currentFilterOldUnknown))
        newList.add(SettingItem.ClickableSetting(id = "compare_digits", title = getString(R.string.setting_compare_digits_title), summary = currentCompareDigitsCount.toString()))

        val avatarSummary = if (currentAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && currentAvatarCustomUri != null) {
            getString(R.string.avatar_mode_custom_selected)
        } else {
            avatarModeOptions[currentAvatarMode]
        }
        newList.add(SettingItem.ClickableSetting(id = "unknown_avatar", title = getString(R.string.setting_unknown_avatar_title), summary = avatarSummary))

        newList.add(SettingItem.Header(getString(R.string.settings_actions_header)))
        newList.add(SettingItem.ClickableSetting("click_action", getString(R.string.click_action_title), clickActionOptions[currentClickAction]))

        newList.add(SettingItem.Header(getString(R.string.settings_widget_buttons_header)))
        newList.add(SettingItem.SwitchSetting(id = "show_dialer", title = getString(R.string.setting_show_dialer_button_title), summaryOn = getString(R.string.setting_show_dialer_button_summary_on), summaryOff = getString(R.string.setting_show_dialer_button_summary_off), isChecked = currentShowDialerButton))
        newList.add(SettingItem.SwitchSetting(id = "show_refresh", title = getString(R.string.setting_show_refresh_button_title), summaryOn = getString(R.string.setting_show_refresh_button_summary_on), summaryOff = getString(R.string.setting_show_refresh_button_summary_off), isChecked = currentShowRefreshButton))

        newList.add(SettingItem.Header(getString(R.string.settings_backup_header)))
        newList.add(SettingItem.ClickableSetting(id = "export_settings", title = getString(R.string.settings_export_title), summary = getString(R.string.settings_export_summary)))
        newList.add(SettingItem.ClickableSetting(id = "import_settings", title = getString(R.string.settings_import_title), summary = getString(R.string.settings_import_summary)))

        return newList.toList()
    }

    private fun buildSettingsList() {
        settingsList = createSettingsList()
        settingsAdapter.submitList(settingsList)
        Log.d(TAG, "New settings list submitted to adapter.")
    }

    private fun rebuildSettingsList() {
        buildSettingsList()
    }

    override fun onSettingClicked(item: SettingItem.ClickableSetting) {
        Log.d(TAG, "Setting clicked: ID=${item.id}")
        when (item.id) {
            "columns" -> showColumnsDialog()
            "item_height" -> showItemHeightDialog()
            "sort_order" -> showSortOrderDialog()
            "max_items" -> showMaxItemsDialog()
            "click_action" -> showClickActionDialog()
            "select_contacts" -> launchSelectContactsActivity()
            "export_settings" -> startBackup()
            "import_settings" -> startRestore()
            "compare_digits" -> showCompareDigitsDialog()
            "unknown_avatar" -> showUnknownAvatarDialog()
            else -> { Toast.makeText(this, getString(R.string.error_not_implemented) + ": " + item.title, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onSwitchChanged(item: SettingItem.SwitchSetting, isChecked: Boolean) {
        Log.d(TAG, "Switch changed: ID=${item.id}, IsChecked=$isChecked")
        var needsExplicitUpdateForFilter = false
        var themeChanged = false

        when (item.id) {
            "use_monet_theme" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val currentSetting = AppSettings.isMonetEnabled(this)
                    if (currentSetting != isChecked) {
                        AppSettings.setMonetEnabled(this, isChecked)
                        themeChanged = true
                        Log.d(TAG, "Monet theme setting changed to: $isChecked")
                    }
                }
            }
            "show_unknown" -> {
                currentShowUnknown = isChecked
                needsExplicitUpdateForFilter = true
            }
            "show_dialer" -> currentShowDialerButton = isChecked
            "show_refresh" -> currentShowRefreshButton = isChecked
            "filter_old_unknown" -> {
                if (isShowUnknownEnabled()) {
                    currentFilterOldUnknown = isChecked
                } else {
                    Log.w(TAG, "filter_old_unknown changed while 'show_unknown' is disabled. Ignoring logical change.")
                }
            }
            else -> Log.w(TAG, "Unhandled switch ID: ${item.id}")
        }

        if (!themeChanged) {
            rebuildSettingsList()
            if (needsExplicitUpdateForFilter) {
                val filterItemPosition = settingsList.indexOfFirst { it is SettingItem.SwitchSetting && it.id == "filter_old_unknown" }
                if (filterItemPosition != -1) {
                    binding.recyclerViewSettings.post {
                        if (::settingsAdapter.isInitialized && settingsAdapter.itemCount > filterItemPosition) {
                            settingsAdapter.notifyItemChanged(filterItemPosition)
                            Log.d(TAG,"Explicitly notified item change for filter_old_unknown at pos $filterItemPosition")
                        } else {
                            Log.w(TAG,"filter_old_unknown position ($filterItemPosition) is out of bounds or adapter not ready. Cannot notify.")
                        }
                    }
                } else {
                    Log.w(TAG,"Could not find filter_old_unknown item in the list to notify change.")
                }
            }
        } else {
            Log.d(TAG, "Theme changed, recreating activity...")
            Toast.makeText(this, R.string.applying_theme_toast, Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

    override fun isShowUnknownEnabled(): Boolean = currentShowUnknown

    private fun launchSelectContactsActivity() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Launching SelectContactsActivity...")
            val intent = Intent(this, SelectContactsActivity::class.java)
            intent.putStringArrayListExtra(SelectContactsActivity.EXTRA_SELECTED_URIS, currentSelectedContactUris)
            selectContactsLauncher.launch(intent)
        } else {
            Log.w(TAG, "READ_CONTACTS permission needed to launch SelectContactsActivity.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), RC_SELECT_CONTACTS)
            } else {
                Toast.makeText(this, R.string.permission_needed_select, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_SELECT_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "READ_CONTACTS permission granted via requestPermissions.")
                launchSelectContactsActivity()
            } else {
                Log.w(TAG, "READ_CONTACTS permission denied via requestPermissions.")
                Toast.makeText(this, R.string.permission_denied_select, Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun showColumnsDialog() {
        val options = supportedColumns.toTypedArray()
        val currentSelectionIndex = options.indexOf(currentColumnCount).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.num_columns)
            .setSingleChoiceItems(options, currentSelectionIndex) { dialog, which ->
                val selectedValue = options[which]
                if (currentColumnCount != selectedValue) { currentColumnCount = selectedValue; rebuildSettingsList() }
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showItemHeightDialog() {
        val options = supportedHeights.toTypedArray()
        val currentSelectionIndex = options.indexOf(currentItemHeight).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.item_height_title)
            .setSingleChoiceItems(options, currentSelectionIndex) { dialog, which ->
                val selectedValue = options[which]
                if (currentItemHeight != selectedValue) { currentItemHeight = selectedValue; rebuildSettingsList() }
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    // <<< ИЗМЕНЕНИЕ: Использование custom view для Sort Order >>>
    private fun showSortOrderDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_radio_group, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_radio_group)
        val sortedOptions = sortOrderOptions.entries.toList() // Используем Map для ключа и значения

        // Создаем RadioButton для каждой опции
        sortedOptions.forEachIndexed { index, entry ->
            val radioButton = MaterialRadioButton(this).apply {
                text = entry.value // Отображаемое имя
                id = index // Используем индекс как ID (просто для уникальности)
                tag = entry.key // Сохраняем ключ ("favorites", "favorites_recents", etc.) в теге
                // Добавляем отступы для нормального вида
                val padding = (12 * resources.displayMetrics.density).toInt() // 12dp в px
                setPadding(0, padding, 0, padding)
            }
            radioGroup.addView(radioButton)

            // Отмечаем текущую выбранную опцию
            if (entry.key == currentSortOrder) {
                radioGroup.check(radioButton.id)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.contacts_order_title)
            .setView(dialogView) // Устанавливаем наш кастомный View
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                if (checkedId != -1) { // Если что-то выбрано
                    val selectedRadioButton = radioGroup.findViewById<MaterialRadioButton>(checkedId)
                    val selectedValue = selectedRadioButton.tag as? String // Получаем ключ из тега
                    if (selectedValue != null && currentSortOrder != selectedValue) {
                        currentSortOrder = selectedValue
                        rebuildSettingsList()
                    }
                }
                dialog.dismiss()
            }
            .show()
    }
    // <<< КОНЕЦ ИЗМЕНЕНИЯ >>>

    private fun showMaxItemsDialog() {
        val editText = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(currentMaxItems.toString()); setSelection(text.length) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.max_items_title).setMessage(R.string.max_items_summary).setView(editText)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val newValue = editText.text.toString().toIntOrNull()
                if (newValue != null && newValue > 0) { if (currentMaxItems != newValue) { currentMaxItems = newValue; rebuildSettingsList() }; dialog.dismiss() }
                else { Toast.makeText(this, R.string.error_invalid_number, Toast.LENGTH_SHORT).show() }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    // <<< ИЗМЕНЕНИЕ: Использование custom view для Click Action >>>
    private fun showClickActionDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_radio_group, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_radio_group)
        val sortedOptions = clickActionOptions.entries.toList()

        sortedOptions.forEachIndexed { index, entry ->
            val radioButton = MaterialRadioButton(this).apply {
                text = entry.value
                id = index
                tag = entry.key
                val padding = (12 * resources.displayMetrics.density).toInt()
                setPadding(0, padding, 0, padding)
            }
            radioGroup.addView(radioButton)
            if (entry.key == currentClickAction) {
                radioGroup.check(radioButton.id)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.click_action_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                if (checkedId != -1) {
                    val selectedRadioButton = radioGroup.findViewById<MaterialRadioButton>(checkedId)
                    val selectedValue = selectedRadioButton.tag as? String
                    if (selectedValue != null && currentClickAction != selectedValue) {
                        currentClickAction = selectedValue
                        rebuildSettingsList()
                    }
                }
                dialog.dismiss()
            }
            .show()
    }
    // <<< КОНЕЦ ИЗМЕНЕНИЯ >>>

    private fun showCompareDigitsDialog() {
        val options = compareDigitsOptions
        val currentSelectionIndex = options.indexOf(currentCompareDigitsCount.toString()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_compare_digits_title)
            .setSingleChoiceItems(options, currentSelectionIndex) { dialog, which ->
                val selectedValueString = options[which]
                val selectedValueInt = selectedValueString.toIntOrNull() ?: WidgetPrefsUtils.DEFAULT_COMPARE_DIGITS_COUNT
                if (currentCompareDigitsCount != selectedValueInt) { Log.d(TAG, "Compare digits count selected: $selectedValueInt"); currentCompareDigitsCount = selectedValueInt; rebuildSettingsList() }
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    // <<< ИЗМЕНЕНИЕ: Использование custom view для Avatar Mode >>>
    private fun showUnknownAvatarDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_radio_group, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.dialog_radio_group)
        val sortedOptions = avatarModeOptions.entries.toList()

        sortedOptions.forEachIndexed { index, entry ->
            val radioButton = MaterialRadioButton(this).apply {
                text = entry.value
                id = index
                tag = entry.key
                val padding = (12 * resources.displayMetrics.density).toInt()
                setPadding(0, padding, 0, padding)
            }
            radioGroup.addView(radioButton)
            if (entry.key == currentAvatarMode) {
                radioGroup.check(radioButton.id)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_unknown_avatar_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val checkedId = radioGroup.checkedRadioButtonId
                if (checkedId != -1) {
                    val selectedRadioButton = radioGroup.findViewById<MaterialRadioButton>(checkedId)
                    val selectedMode = selectedRadioButton.tag as? String ?: WidgetPrefsUtils.DEFAULT_UNKNOWN_AVATAR_MODE
                    if (currentAvatarMode != selectedMode) {
                        Log.d(TAG, "Avatar mode selected: $selectedMode")
                        if (selectedMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM) {
                            // Выбор кастомного аватара запускается немедленно при выборе опции
                            checkStoragePermissionAndPickImage()
                        } else {
                            currentAvatarMode = selectedMode
                            currentAvatarCustomUri = null // Сбрасываем URI, если выбран не кастомный режим
                            rebuildSettingsList()
                        }
                    }
                }
                dialog.dismiss()
            }
            .show()
    }
    // <<< КОНЕЦ ИЗМЕНЕНИЯ >>>

    private fun checkStoragePermissionAndPickImage() {
        when {
            ContextCompat.checkSelfPermission(this, requiredStoragePermission) == PackageManager.PERMISSION_GRANTED -> { Log.d(TAG, "Storage permission already granted ($requiredStoragePermission)."); launchImagePicker() }
            shouldShowRequestPermissionRationale(requiredStoragePermission) -> {
                Log.d(TAG, "Showing rationale for storage permission ($requiredStoragePermission).")
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.permission_needed_title)
                    .setMessage(storagePermissionRationale)
                    .setPositiveButton(R.string.grant_permission) { _, _ -> requestStoragePermissionLauncher.launch(requiredStoragePermission) }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        // <<< ИЗМЕНЕНИЕ: Если отменили запрос разрешения, нужно вернуть выбор в диалоге аватара на предыдущее значение >>>
                        currentAvatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(this, appWidgetId) // Восстанавливаем старое значение
                        rebuildSettingsList()
                        dialog.dismiss()
                    }
                    .show()
            }
            else -> { Log.d(TAG, "Requesting storage permission ($requiredStoragePermission)."); requestStoragePermissionLauncher.launch(requiredStoragePermission) }
        }
    }

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        try { selectAvatarLauncher.launch(intent) }
        catch (e: Exception) {
            Log.e(TAG, "Could not launch image picker: ${e.message}", e); Toast.makeText(this, R.string.error_launching_picker, Toast.LENGTH_SHORT).show()
            // <<< ИЗМЕНЕНИЕ: Восстанавливаем предыдущий режим аватара при ошибке запуска >>>
            currentAvatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(this, appWidgetId)
            rebuildSettingsList()
        }
    }

    private fun copyImageToInternalStorage(sourceUri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val targetFile = WidgetPrefsUtils.getCustomAvatarFile(this@WidgetConfigureActivity, appWidgetId)
            var success = false; var errorMessage: String? = null
            val previousAvatarMode = currentAvatarMode // Сохраняем на случай ошибки
            val previousAvatarUri = currentAvatarCustomUri
            try {
                Log.d(TAG, "Attempting to copy avatar from $sourceUri to $targetFile")
                // Сначала установим новый режим в UI, чтобы пользователь видел изменение
                withContext(Dispatchers.Main) {
                    currentAvatarMode = WidgetPrefsUtils.AVATAR_MODE_CUSTOM
                    currentAvatarCustomUri = Uri.fromFile(targetFile).toString() // Временный URI для отображения
                    rebuildSettingsList() // Обновляем UI немедленно
                }
                // Выполняем копирование в фоне
                contentResolver.openInputStream(sourceUri)?.use { inputStream -> FileOutputStream(targetFile).use { outputStream -> inputStream.copyTo(outputStream); success = true } } ?: throw Exception("InputStream is null")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying image: ${e.message}", e); errorMessage = getString(R.string.error_copying_avatar, e.localizedMessage ?: e.javaClass.simpleName); if (targetFile.exists()) { targetFile.delete() }
                success = false // Явно ставим false при ошибке
            }
            // Обрабатываем результат
            withContext(Dispatchers.Main) {
                if (success) {
                    // URI уже установлен, просто показываем сообщение
                    Toast.makeText(this@WidgetConfigureActivity, R.string.avatar_set_successfully, Toast.LENGTH_SHORT).show()
                } else {
                    // Откатываем изменения в UI и показываем ошибку
                    currentAvatarMode = previousAvatarMode
                    currentAvatarCustomUri = previousAvatarUri
                    rebuildSettingsList() // Восстанавливаем предыдущее состояние
                    Toast.makeText(this@WidgetConfigureActivity, errorMessage ?: getString(R.string.error_copying_avatar_unknown), Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun checkPermissionsBeforeSave() {
        Log.d(TAG, "Checking permissions before save...")
        val needsCallPhone = currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CALL || currentClickAction == WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL
        if (needsCallPhone && ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_needed_title)
                .setMessage(callPhonePermissionRationale)
                .setPositiveButton(R.string.grant_permission) { _, _ -> requestCallPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE) }
                .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss(); Toast.makeText(this, getString(callPhonePermissionRationale), Toast.LENGTH_LONG).show() }
                .setCancelable(false).show(); return
        }

        val needsCallLog = currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS ||
                currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS_FREQUENT ||
                currentSortOrder == WidgetPrefsUtils.SORT_ORDER_FAVORITES_FREQUENT
        if (needsCallLog && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_needed_title)
                .setMessage(callLogPermissionRationale)
                .setPositiveButton(R.string.grant_permission) { _, _ -> requestCallLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) }
                .setNegativeButton(R.string.continue_without_permission) { d, _ -> d.dismiss(); Log.w(TAG, "Proceeding without READ_CALL_LOG permission."); Toast.makeText(this, getString(R.string.warning_recents_unavailable), Toast.LENGTH_LONG).show(); checkMiuiAndProceedSave() }
                .setCancelable(false).show(); return
        }

        if (currentAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && currentAvatarCustomUri != null && ContextCompat.checkSelfPermission(this, requiredStoragePermission) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Storage permission ($requiredStoragePermission) is missing, custom avatar might not load correctly.")
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_needed_title)
                .setMessage(R.string.permission_storage_needed_for_avatar)
                .setPositiveButton(R.string.grant_permission) { _, _ -> requestStoragePermissionLauncher.launch(requiredStoragePermission) }
                .setNegativeButton(R.string.ok) { d, _ -> d.dismiss(); checkMiuiAndProceedSave() }
                .show()
            return
        }
        Log.d(TAG, "Permissions check passed or warnings noted."); checkMiuiAndProceedSave()
    }

    private fun checkMiuiAndProceedSave() {
        if (DeviceUtils.isXiaomiDevice() && !AppSettings.hasShownMiuiWarning(this)) {
            DeviceUtils.showMiuiPermissionsDialog(this,
                onSettingsOpened = { AppSettings.setMiuiWarningShown(this, true); Toast.makeText(this, getString(R.string.miui_dialog_check_permissions_later), Toast.LENGTH_LONG).show() },
                onAcknowledged = { AppSettings.setMiuiWarningShown(this, true); Log.d(TAG, "MIUI warning acknowledged."); proceedSaveConfiguration() }
            )
        } else { proceedSaveConfiguration() }
    }

    private fun startBackup() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()); val fileName = "PhoneContacts_${timeStamp}.zip"
        backupLauncher.launch(fileName)
    }

    private fun performBackup(targetUri: Uri) {
        Log.d(TAG, "Performing backup to URI: $targetUri")
        lifecycleScope.launch(Dispatchers.IO) {
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext); val componentName = ComponentName(applicationContext, ContactsGridWidgetProvider::class.java)
            val widgetIds: IntArray? = try { appWidgetManager.getAppWidgetIds(componentName) } catch (e: Exception) { null }
            if (widgetIds == null || widgetIds.isEmpty()) {
                withContext(Dispatchers.Main) { Toast.makeText(applicationContext, R.string.no_widgets_to_backup, Toast.LENGTH_SHORT).show() }; return@launch
            }
            val backupMap = mutableMapOf<Int, WidgetBackupData>(); val backupAvatarDir = File(cacheDir, "backup_avatars_temp"); if (backupAvatarDir.exists()) backupAvatarDir.deleteRecursively(); backupAvatarDir.mkdirs(); val avatarFileMap = mutableMapOf<Int, String>()
            widgetIds?.forEach { widgetId ->
                val avatarMode = WidgetPrefsUtils.loadUnknownAvatarMode(applicationContext, widgetId); val avatarUriStr = WidgetPrefsUtils.loadUnknownAvatarCustomUri(applicationContext, widgetId); var backedUpAvatarFileName: String? = null
                if (avatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && avatarUriStr != null) {
                    try {
                        val sourceFileUri = Uri.parse(avatarUriStr)
                        if (sourceFileUri.scheme == "file" && sourceFileUri.path?.startsWith(applicationContext.filesDir.path) == true) {
                            val sourceFile = File(sourceFileUri.path!!); if (sourceFile.exists()) { val targetFileName = "avatar_${widgetId}.jpg"; val targetFile = File(backupAvatarDir, targetFileName); sourceFile.copyTo(targetFile, true); avatarFileMap[widgetId] = targetFileName; backedUpAvatarFileName = targetFileName }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Error copying avatar during backup for widget $widgetId: ${e.message}") }
                }
                backupMap[widgetId] = WidgetBackupData(
                    contactUris = WidgetPrefsUtils.loadContactUris(applicationContext, widgetId),
                    columnCount = WidgetPrefsUtils.loadColumnCount(applicationContext, widgetId),
                    clickAction = WidgetPrefsUtils.loadClickAction(applicationContext, widgetId),
                    sortOrder = WidgetPrefsUtils.loadSortOrder(applicationContext, widgetId),
                    maxItems = WidgetPrefsUtils.loadMaxItems(applicationContext, widgetId),
                    itemHeight = WidgetPrefsUtils.loadItemHeight(applicationContext, widgetId),
                    showUnknown = WidgetPrefsUtils.loadShowUnknown(applicationContext, widgetId),
                    showDialerButton = WidgetPrefsUtils.loadShowDialerButton(applicationContext, widgetId),
                    showRefreshButton = WidgetPrefsUtils.loadShowRefreshButton(applicationContext, widgetId),
                    filterOldUnknown = WidgetPrefsUtils.loadFilterOldUnknown(applicationContext, widgetId),
                    compareDigitsCount = WidgetPrefsUtils.loadCompareDigitsCount(applicationContext, widgetId),
                    unknownAvatarMode = avatarMode,
                    unknownAvatarCustomUri = backedUpAvatarFileName
                )
            }
            val appBackupData = AppBackup(backupMap); var errorMessage: String? = null
            try {
                val jsonString = json.encodeToString(appBackupData)
                applicationContext.contentResolver.openOutputStream(targetUri)?.use { os -> ZipOutputStream(os).use { zos ->
                    val jsonEntry = ZipEntry("settings.json"); zos.putNextEntry(jsonEntry); zos.write(jsonString.toByteArray(Charsets.UTF_8)); zos.closeEntry()
                    avatarFileMap.forEach { (_, fileName) -> val fileToZip = File(backupAvatarDir, fileName); if (fileToZip.exists()) { val avatarEntry = ZipEntry("avatars/$fileName"); zos.putNextEntry(avatarEntry); FileInputStream(fileToZip).use { fis -> fis.copyTo(zos) }; zos.closeEntry() } } }
                } ?: throw Exception(getString(R.string.error_writing_file))
            } catch (e: Exception) { errorMessage = getString(R.string.backup_failed, e.localizedMessage ?: e.javaClass.simpleName) }
            finally { if (backupAvatarDir.exists()) { backupAvatarDir.deleteRecursively() } }
            withContext(Dispatchers.Main) { Toast.makeText(applicationContext, errorMessage ?: getString(R.string.backup_successful), Toast.LENGTH_LONG).show() }
        }
    }

    private fun startRestore() { restoreLauncher.launch(arrayOf("application/zip", "*/*")) }

    private fun performRestore(sourceUri: Uri) {
        Log.d(TAG, "Performing restore from URI: $sourceUri")
        var fileName: String? = null; try { contentResolver.query(sourceUri, null, null, null, null)?.use { c -> if (c.moveToFirst()) { val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) fileName = c.getString(idx) } } } catch (e: Exception) { Log.e(TAG, "Error getting file name", e) }
        val isValidBackupFile = fileName != null && fileName!!.startsWith("PhoneContacts_", true) && fileName!!.endsWith(".zip", true)
        if (!isValidBackupFile) { Log.w(TAG, "Invalid backup file: '$fileName'. Aborting."); lifecycleScope.launch(Dispatchers.Main) { Toast.makeText(applicationContext, R.string.error_invalid_backup_file_name, Toast.LENGTH_LONG).show() }; return }

        lifecycleScope.launch(Dispatchers.IO) {
            var backupData: AppBackup? = null; var errorMessage: String? = null; val restoreTempDir = File(cacheDir, "restore_avatars_temp"); if (restoreTempDir.exists()) restoreTempDir.deleteRecursively(); restoreTempDir.mkdirs(); val restoredAvatarMap = mutableMapOf<String, File>()
            try {
                applicationContext.contentResolver.openInputStream(sourceUri)?.use { ips -> ZipInputStream(ips).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry; while (entry != null) {
                    when { entry.name == "settings.json" -> { val jS = String(zis.readBytes(), Charsets.UTF_8); backupData = json.decodeFromString<AppBackup>(jS) }
                        entry.name.startsWith("avatars/") && !entry.isDirectory -> { val fName = File(entry.name).name; if (fName.isNotBlank()) { val tFile = File(restoreTempDir, fName); FileOutputStream(tFile).use { fos -> zis.copyTo(fos) }; restoredAvatarMap[fName] = tFile } } }
                    zis.closeEntry(); entry = zis.nextEntry } } } ?: throw Exception(getString(R.string.error_input_stream_null))
                if (backupData == null) throw Exception(getString(R.string.error_settings_json_not_found))
            } catch (e: Exception) { errorMessage = when (e) { is kotlinx.serialization.SerializationException -> getString(R.string.error_parsing_backup, e.localizedMessage ?: e.javaClass.simpleName); is ZipException -> getString(R.string.error_reading_zip, e.localizedMessage ?: e.javaClass.simpleName); else -> getString(R.string.error_reading_file, e.localizedMessage ?: e.javaClass.simpleName) }; backupData = null }

            if (backupData != null) {
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext); val componentName = ComponentName(applicationContext, ContactsGridWidgetProvider::class.java); val currentWidgetIds: IntArray? = try { appWidgetManager.getAppWidgetIds(componentName) } catch (e: Exception) { null }
                if (currentWidgetIds == null || currentWidgetIds.isEmpty()) { errorMessage = getString(R.string.no_widgets_to_restore) }
                else {
                    val restoredWidgetIds = mutableListOf<Int>()
                    currentWidgetIds?.forEach { widgetId ->
                        backupData!!.widgetSettings[widgetId]?.let { widgetData ->
                            var finalAvatarUriString: String? = null
                            if (widgetData.unknownAvatarMode == WidgetPrefsUtils.AVATAR_MODE_CUSTOM && widgetData.unknownAvatarCustomUri != null) {
                                restoredAvatarMap[widgetData.unknownAvatarCustomUri]?.let { tempFile -> val targetFile = WidgetPrefsUtils.getCustomAvatarFile(applicationContext, widgetId); try { if (targetFile.exists()) targetFile.delete(); tempFile.copyTo(targetFile, true); finalAvatarUriString = Uri.fromFile(targetFile).toString() } catch (e: Exception) { Log.e(TAG, "Error restoring avatar for widget $widgetId", e) } }
                            }
                            WidgetPrefsUtils.saveWidgetConfig( context=applicationContext, appWidgetId=widgetId, contactContentUris=widgetData.contactUris, columnCount=widgetData.columnCount, clickAction=widgetData.clickAction, sortOrder=widgetData.sortOrder, maxItems=widgetData.maxItems, itemHeight=widgetData.itemHeight, showUnknown=widgetData.showUnknown, showDialerButton=widgetData.showDialerButton, showRefreshButton=widgetData.showRefreshButton, filterOldUnknown=widgetData.filterOldUnknown, compareDigitsCount=widgetData.compareDigitsCount, unknownAvatarMode=widgetData.unknownAvatarMode, unknownAvatarCustomUri=finalAvatarUriString); restoredWidgetIds.add(widgetId)
                        } ?: Log.w(TAG, "Settings for widget $widgetId not found in backup.")
                    }
                    if (restoredWidgetIds.isNotEmpty()) { restoredWidgetIds.forEach { ContactsGridWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, it) }; errorMessage = null; if (appWidgetId in restoredWidgetIds) { withContext(Dispatchers.Main) { Toast.makeText(applicationContext, R.string.settings_reloading, Toast.LENGTH_SHORT).show(); loadCurrentWidgetSettings(); rebuildSettingsList() } } }
                    else { errorMessage = getString(R.string.no_widget_settings_found_in_backup) }
                }
            }
            if (restoreTempDir.exists()) { restoreTempDir.deleteRecursively() }
            withContext(Dispatchers.Main) { Toast.makeText(applicationContext, errorMessage ?: getString(R.string.restore_successful), Toast.LENGTH_LONG).show() }
        }
    }

    private fun proceedSaveConfiguration() {
        Log.d(TAG, "Proceeding to save configuration for widget $appWidgetId...")
        WidgetPrefsUtils.saveWidgetConfig(
            context = this, appWidgetId = appWidgetId,
            contactContentUris = ArrayList(currentSelectedContactUris), columnCount = currentColumnCount.toIntOrNull() ?: WidgetPrefsUtils.DEFAULT_COLUMN_COUNT,
            clickAction = currentClickAction, sortOrder = currentSortOrder,
            maxItems = currentMaxItems, itemHeight = currentItemHeight.toIntOrNull() ?: WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT,
            showUnknown = currentShowUnknown, showDialerButton = currentShowDialerButton,
            showRefreshButton = currentShowRefreshButton, filterOldUnknown = currentFilterOldUnknown,
            compareDigitsCount = currentCompareDigitsCount,
            unknownAvatarMode = currentAvatarMode,
            unknownAvatarCustomUri = currentAvatarCustomUri
        )
        val appWidgetManager = AppWidgetManager.getInstance(this)
        ContactsGridWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)
        val resultValue = Intent().apply { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) }
        setResult(Activity.RESULT_OK, resultValue)
        Log.d(TAG, "Configuration saved successfully. Finishing activity.")
        finish()
    }

} // Конец класса WidgetConfigureActivity