package by.toxic.phonecontacts

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.toxic.phonecontacts.databinding.ActivityWidgetConfigureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class WidgetConfigureActivity : AppCompatActivity(), SettingInteractionListener {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: ActivityWidgetConfigureBinding
    private lateinit var settingsAdapter: ConfigureSettingsAdapter
    private val settingsList = mutableListOf<SettingItem>()

    private val supportedColumns = listOf("3", "4", "5", "6")
    companion object {
        val supportedHeights = listOf("70", "80", "90", "100", "110", "120", "130", "140", "150", "160")
    }
    private lateinit var clickActionOptions: Map<String, String>; private lateinit var clickActionDisplayNames: List<String>
    private lateinit var sortOrderOptions: Map<String, String>; private lateinit var sortOrderDisplayNames: List<String>

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

    private lateinit var selectContactsLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestCallPhonePermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestCallLogPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var backupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>

    private val callPhonePermissionRationale = R.string.permission_call_needed_for_call_options
    private val callLogPermissionRationale = R.string.permission_call_log_needed_favorites_recents
    private val RC_SELECT_CONTACTS = 101
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent?.extras?.getInt( AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { Log.e("WidgetConfigure", "Invalid AppWidget ID. Finishing."); finish(); return }
        Log.d("WidgetConfigure", "Configuring widget ID: $appWidgetId")

        clickActionOptions = mapOf( WidgetPrefsUtils.CLICK_ACTION_DIAL to getString(R.string.click_action_dial), WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL to getString(R.string.click_action_confirm_call), WidgetPrefsUtils.CLICK_ACTION_CALL to getString(R.string.click_action_call) ); clickActionDisplayNames = clickActionOptions.values.toList()
        sortOrderOptions = mapOf( WidgetPrefsUtils.SORT_ORDER_FAVORITES to getString(R.string.sort_order_favorites), WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS to getString(R.string.sort_order_favorites_recents) ); sortOrderDisplayNames = sortOrderOptions.values.toList()

        initializeLaunchers()
        loadCurrentWidgetSettings()
        setupUI()
        buildSettingsList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == android.R.id.home) { finish(); return true }; return super.onOptionsItemSelected(item) }

    private fun loadCurrentWidgetSettings() {
        Log.d("WidgetConfigure", "Loading settings for widget $appWidgetId")
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
        Log.d("WidgetConfigure", "Settings loaded: Cols=$currentColumnCount, Action=$currentClickAction, Sort=$currentSortOrder, Max=$currentMaxItems, Height=$currentItemHeight, FavURIs=${currentSelectedContactUris.size}, ShowUnknown=$currentShowUnknown, ShowDialer=$currentShowDialerButton, ShowRefresh=$currentShowRefreshButton, FilterOldUnknown=$currentFilterOldUnknown")
    }

    private fun initializeLaunchers() {
        requestCallPhonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) { Log.d("WidgetConfigure", "CALL_PHONE granted."); checkMiuiAndProceedSave() } else { Log.w("WidgetConfigure", "CALL_PHONE denied."); Toast.makeText(this, getString(callPhonePermissionRationale), Toast.LENGTH_LONG).show() } }
        selectContactsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> Log.d("WidgetConfigure", "SelectContactsLauncher result: ${result.resultCode}"); if (result.resultCode == Activity.RESULT_OK) { val selectedUris = result.data?.getStringArrayListExtra(SelectContactsActivity.RESULT_SELECTED_URIS); if (selectedUris != null) { Log.d("WidgetConfigure", "Received ${selectedUris.size} URIs."); currentSelectedContactUris = selectedUris; updateSettingSummary("select_contacts", "Выбрано: ${selectedUris.size}") } else { Log.w("WidgetConfigure", "No URIs received.") } } }
        requestCallLogPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted -> if (isGranted) { Log.d("WidgetConfigure", "READ_CALL_LOG granted."); checkMiuiAndProceedSave() } else { Log.w("WidgetConfigure", "READ_CALL_LOG denied."); Toast.makeText(this, getString(R.string.warning_recents_unavailable), Toast.LENGTH_LONG).show(); checkMiuiAndProceedSave() } }
        backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? -> if (uri != null) { performBackup(uri) } else { Log.d("WidgetConfigure", "Backup cancelled."); Toast.makeText(this, "Экспорт отменен", Toast.LENGTH_SHORT).show() } }
        restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) { performRestore(uri) } else { Log.d("WidgetConfigure", "Restore cancelled."); Toast.makeText(this, "Импорт отменен", Toast.LENGTH_SHORT).show() } }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar); supportActionBar?.title = getString(R.string.widget_config_title) + " #$appWidgetId"; supportActionBar?.setDisplayHomeAsUpEnabled(false)
        settingsAdapter = ConfigureSettingsAdapter(this); binding.recyclerViewSettings.apply { layoutManager = LinearLayoutManager(this@WidgetConfigureActivity); adapter = settingsAdapter; itemAnimator = null }
        binding.fabSave.setOnClickListener { Log.d("WidgetConfigure", "Save button clicked."); checkPermissionsBeforeSave() }
    }

    private fun buildSettingsList() {
        Log.d("WidgetConfigure", "Building settings list...")
        settingsList.clear()
        settingsList.add(SettingItem.Header("Внешний вид")); settingsList.add(SettingItem.ClickableSetting("columns", getString(R.string.num_columns), currentColumnCount)); settingsList.add(SettingItem.ClickableSetting("item_height", getString(R.string.item_height_title), currentItemHeight))
        settingsList.add(SettingItem.Header("Контакты")); settingsList.add(SettingItem.ClickableSetting("sort_order", getString(R.string.contacts_order_title), sortOrderOptions[currentSortOrder])); settingsList.add(SettingItem.ClickableSetting("max_items", getString(R.string.max_items_title), currentMaxItems.toString())); settingsList.add(SettingItem.ClickableSetting(id = "select_contacts", title = getString(R.string.select_favorites_title), summary = "Выбрано: ${currentSelectedContactUris.size}" ));
        settingsList.add(SettingItem.SwitchSetting(id = "show_unknown", title = getString(R.string.setting_show_unknown_title), summaryOn = getString(R.string.setting_show_unknown_summary_on), summaryOff = getString(R.string.setting_show_unknown_summary_off), isChecked = currentShowUnknown))
        settingsList.add(SettingItem.SwitchSetting(id = "filter_old_unknown", title = getString(R.string.setting_filter_old_unknown_title), summaryOn = getString(R.string.setting_filter_old_unknown_summary_on), summaryOff = getString(R.string.setting_filter_old_unknown_summary_off), isChecked = currentFilterOldUnknown))
        settingsList.add(SettingItem.Header("Действия")); settingsList.add(SettingItem.ClickableSetting("click_action", getString(R.string.click_action_title), clickActionOptions[currentClickAction]))
        settingsList.add(SettingItem.Header("Кнопки виджета")); settingsList.add(SettingItem.SwitchSetting(id = "show_dialer", title = getString(R.string.setting_show_dialer_button_title), summaryOn = getString(R.string.setting_show_dialer_button_summary_on), summaryOff = getString(R.string.setting_show_dialer_button_summary_off), isChecked = currentShowDialerButton)); settingsList.add(SettingItem.SwitchSetting(id = "show_refresh", title = getString(R.string.setting_show_refresh_button_title), summaryOn = getString(R.string.setting_show_refresh_button_summary_on), summaryOff = getString(R.string.setting_show_refresh_button_summary_off), isChecked = currentShowRefreshButton))
        settingsList.add(SettingItem.Header("Сохранение/Загрузка")); settingsList.add(SettingItem.ClickableSetting(id = "export_settings", title = getString(R.string.settings_export_title), summary = getString(R.string.settings_export_summary))); settingsList.add(SettingItem.ClickableSetting(id = "import_settings", title = getString(R.string.settings_import_title), summary = getString(R.string.settings_import_summary)))
        settingsAdapter.submitList(settingsList.toList()); Log.d("WidgetConfigure", "Settings list submitted with ${settingsList.size} items.")
        updateFilterSwitchState()
    }

    override fun onSettingClicked(item: SettingItem.ClickableSetting) { Log.d("WidgetConfigure", "Setting clicked: ID=${item.id}"); when (item.id) { "columns" -> showColumnsDialog(); "item_height" -> showItemHeightDialog(); "sort_order" -> showSortOrderDialog(); "max_items" -> showMaxItemsDialog(); "click_action" -> showClickActionDialog(); "select_contacts" -> launchSelectContactsActivity(); "export_settings" -> startBackup(); "import_settings" -> startRestore(); else -> { Toast.makeText(this, "Not implemented: ${item.title}", Toast.LENGTH_SHORT).show() } } }

    override fun onSwitchChanged(item: SettingItem.SwitchSetting, isChecked: Boolean) {
        Log.d("WidgetConfigure", "Switch changed: ID=${item.id}, IsChecked=$isChecked");
        var updateUiNeeded = true
        when (item.id) { "show_unknown" -> { currentShowUnknown = isChecked; updateFilterSwitchState() }; "show_dialer" -> currentShowDialerButton = isChecked; "show_refresh" -> currentShowRefreshButton = isChecked; "filter_old_unknown" -> { if (currentShowUnknown) { currentFilterOldUnknown = isChecked } else { updateUiNeeded = false; Log.w("WidgetConfigure", "filter_old_unknown changed while disabled.")} }; else -> { updateUiNeeded = false; Log.w("WidgetConfigure", "Unhandled switch ID: ${item.id}") } }
        if (updateUiNeeded) { updateSettingItem(item.copy(isChecked = isChecked)) }
    }

    override fun isShowUnknownEnabled(): Boolean = currentShowUnknown

    private fun updateFilterSwitchState() { val filterIndex = settingsList.indexOfFirst { it is SettingItem.SwitchSetting && it.id == "filter_old_unknown" }; if (filterIndex != -1) { settingsAdapter.notifyItemChanged(filterIndex); Log.d("WidgetConfigure", "Requesting UI update for 'filter_old_unknown' switch.") } }
    private fun launchSelectContactsActivity() { if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) { Log.d("WidgetConfigure", "Launching SelectContactsActivity..."); val intent = Intent(this, SelectContactsActivity::class.java); intent.putStringArrayListExtra(SelectContactsActivity.EXTRA_SELECTED_URIS, currentSelectedContactUris); selectContactsLauncher.launch(intent) } else { Log.w("WidgetConfigure", "READ_CONTACTS permission needed."); requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), RC_SELECT_CONTACTS); Toast.makeText(this, "Требуется разрешение на чтение контактов", Toast.LENGTH_LONG).show() } }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == RC_SELECT_CONTACTS) { if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) { Log.d("WidgetConfigure", "READ_CONTACTS granted."); launchSelectContactsActivity() } else { Log.w("WidgetConfigure", "READ_CONTACTS denied."); Toast.makeText(this, "Невозможно выбрать контакты без разрешения.", Toast.LENGTH_LONG).show() } } }
    private fun showColumnsDialog() { val o=supportedColumns.toTypedArray(); val cI=o.indexOf(currentColumnCount); AlertDialog.Builder(this).setTitle(R.string.num_columns).setSingleChoiceItems(o,cI){ d,w->val sV=o[w]; if(currentColumnCount!=sV){currentColumnCount=sV;updateSettingSummary("columns",sV)}; d.dismiss()}.setNegativeButton(android.R.string.cancel,null).show() }
    private fun showItemHeightDialog() { val o=supportedHeights.toTypedArray(); val cI=o.indexOf(currentItemHeight); AlertDialog.Builder(this).setTitle(R.string.item_height_title).setSingleChoiceItems(o,cI){ d,w->val sV=o[w]; if(currentItemHeight!=sV){currentItemHeight=sV;updateSettingSummary("item_height",sV)}; d.dismiss()}.setNegativeButton(android.R.string.cancel,null).show() }
    private fun showSortOrderDialog() { val o=sortOrderDisplayNames.toTypedArray(); val cI=sortOrderOptions.entries.indexOfFirst{it.key==currentSortOrder}; AlertDialog.Builder(this).setTitle(R.string.contacts_order_title).setSingleChoiceItems(o,cI){ d,w->val sDN=o[w]; val sV=sortOrderOptions.entries.find{it.value==sDN}?.key?:WidgetPrefsUtils.DEFAULT_SORT_ORDER; if(currentSortOrder!=sV){currentSortOrder=sV;updateSettingSummary("sort_order",sortOrderOptions[sV])}; d.dismiss()}.setNegativeButton(android.R.string.cancel,null).show() }
    private fun showMaxItemsDialog() { val e=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER;setText(currentMaxItems.toString());setSelection(text.length)}; AlertDialog.Builder(this).setTitle(R.string.max_items_title).setMessage(R.string.max_items_summary).setView(e).setPositiveButton(android.R.string.ok){d,_->val nV=e.text.toString().toIntOrNull();if(nV!=null&&nV>0){if(currentMaxItems!=nV){currentMaxItems=nV;updateSettingSummary("max_items",nV.toString())};d.dismiss()}else{Toast.makeText(this,R.string.error_invalid_number,Toast.LENGTH_SHORT).show()}}.setNegativeButton(android.R.string.cancel,null).show() }
    private fun showClickActionDialog() { val o=clickActionDisplayNames.toTypedArray(); val cI=clickActionOptions.entries.indexOfFirst{it.key==currentClickAction}; AlertDialog.Builder(this).setTitle(R.string.click_action_title).setSingleChoiceItems(o,cI){ d,w->val sDN=o[w]; val sV=clickActionOptions.entries.find{it.value==sDN}?.key?:WidgetPrefsUtils.DEFAULT_CLICK_ACTION; if(currentClickAction!=sV){currentClickAction=sV;updateSettingSummary("click_action",clickActionOptions[sV])}; d.dismiss()}.setNegativeButton(android.R.string.cancel,null).show() }
    private fun updateSettingSummary(settingId: String, newSummary: String?) { Log.d("WidgetConfigure", "Attempting update summary ID '$settingId' to '$newSummary'"); val index=settingsList.indexOfFirst{it is SettingItem.ClickableSetting&&it.id==settingId}; if(index!=-1){val currentItem=settingsList[index] as SettingItem.ClickableSetting;if(currentItem.summary!=newSummary){Log.d("WidgetConfigure","Updating item at $index");settingsList[index]=currentItem.copy(summary=newSummary);Log.d("WidgetConfigure","Submitting list...");settingsAdapter.submitList(settingsList.toList()){Log.d("WidgetConfigure","submitList completed.")}}else{Log.d("WidgetConfigure","Summary same.")}}else{Log.w("WidgetConfigure","ID '$settingId' not found.")} }
    private fun updateSettingItem(updatedItem: SettingItem.SwitchSetting) { Log.d("WidgetConfigure", "Attempting update switch ID '${updatedItem.id}' to ${updatedItem.isChecked}"); val index=settingsList.indexOfFirst{it is SettingItem.SwitchSetting&&it.id==updatedItem.id}; if(index!=-1){Log.d("WidgetConfigure","Found at $index. Updating.");settingsList[index]=updatedItem;Log.d("WidgetConfigure","Submitting list...");settingsAdapter.submitList(settingsList.toList()){Log.d("WidgetConfigure","submitList completed.")}}else{Log.w("WidgetConfigure","ID '${updatedItem.id}' not found.")} }
    private fun checkPermissionsBeforeSave() { Log.d("WidgetConfigure", "Checking permissions..."); val needsCallPhone=currentClickAction==WidgetPrefsUtils.CLICK_ACTION_CALL||currentClickAction==WidgetPrefsUtils.CLICK_ACTION_CONFIRM_CALL; if(needsCallPhone&&ContextCompat.checkSelfPermission(this,Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){Log.d("WidgetConfigure","CALL_PHONE needed.");AlertDialog.Builder(this).setTitle(R.string.permission_needed_title).setMessage(callPhonePermissionRationale).setPositiveButton(R.string.grant_permission){_,_->requestCallPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)}.setNegativeButton(android.R.string.cancel){d,_->d.dismiss();Toast.makeText(this,getString(callPhonePermissionRationale),Toast.LENGTH_LONG).show()}.setCancelable(false).show();return}; if(currentSortOrder==WidgetPrefsUtils.SORT_ORDER_FAVORITES_RECENTS&&ContextCompat.checkSelfPermission(this,Manifest.permission.READ_CALL_LOG)!=PackageManager.PERMISSION_GRANTED){Log.d("WidgetConfigure","READ_CALL_LOG needed.");AlertDialog.Builder(this).setTitle(R.string.permission_needed_title).setMessage(callLogPermissionRationale).setPositiveButton(R.string.grant_permission){_,_->requestCallLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)}.setNegativeButton(R.string.continue_without_permission){d,_->d.dismiss();Log.w("WidgetConfigure","Proceeding without READ_CALL_LOG.");Toast.makeText(this,getString(R.string.warning_recents_unavailable),Toast.LENGTH_LONG).show();checkMiuiAndProceedSave()}.setCancelable(false).show();return}; Log.d("WidgetConfigure","Permissions check passed.");checkMiuiAndProceedSave() }
    private fun checkMiuiAndProceedSave() { if(DeviceUtils.isXiaomiDevice()&&!AppSettings.hasShownMiuiWarning(this)){Log.d("WidgetConfigure","Xiaomi detected, showing dialog...");DeviceUtils.showMiuiPermissionsDialog(this,onSettingsOpened={AppSettings.setMiuiWarningShown(this,true);Toast.makeText(this,getString(R.string.miui_dialog_check_permissions_later),Toast.LENGTH_LONG).show()},onAcknowledged={AppSettings.setMiuiWarningShown(this,true);Log.d("WidgetConfigure","MIUI warning acknowledged.");proceedSaveConfiguration()})}else{Log.d("WidgetConfigure","MIUI check passed/not needed.");proceedSaveConfiguration()} }

    // <<< ИЗМЕНЕНА ФУНКЦИЯ startBackup >>>
    private fun startBackup() {
        Log.d("WidgetConfigure", "Backup started.")
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // Формируем имя файла с расширением .backup
        val fileName = "PhoneContacts_${timeStamp}.backup"
        // Запускаем SAF, предлагая это имя файла
        backupLauncher.launch(fileName)
        Log.d("WidgetConfigure", "Launching SAF to create document with suggested name: $fileName")
    }

    private fun performBackup(targetUri: Uri) { Log.d("WidgetConfigure", "Performing backup to $targetUri"); lifecycleScope.launch(Dispatchers.IO) { val am=AppWidgetManager.getInstance(applicationContext); val cn=ComponentName(applicationContext,ContactsGridWidgetProvider::class.java); val ids:IntArray?=try{am.getAppWidgetIds(cn)}catch(e:Exception){null}; if(ids==null||ids.isEmpty()){withContext(Dispatchers.Main){Toast.makeText(applicationContext,R.string.no_widgets_to_backup,Toast.LENGTH_SHORT).show()};return@launch}; val bm=mutableMapOf<Int,WidgetBackupData>(); ids.forEach{wId->Log.d("WidgetConfigure","Backing up widget ID: $wId");val d=WidgetBackupData(WidgetPrefsUtils.loadContactUris(applicationContext,wId),WidgetPrefsUtils.loadColumnCount(applicationContext,wId),WidgetPrefsUtils.loadClickAction(applicationContext,wId),WidgetPrefsUtils.loadSortOrder(applicationContext,wId),WidgetPrefsUtils.loadMaxItems(applicationContext,wId),WidgetPrefsUtils.loadItemHeight(applicationContext,wId),WidgetPrefsUtils.loadShowUnknown(applicationContext,wId),WidgetPrefsUtils.loadShowDialerButton(applicationContext,wId),WidgetPrefsUtils.loadShowRefreshButton(applicationContext,wId),WidgetPrefsUtils.loadFilterOldUnknown(applicationContext,wId));bm[wId]=d}; val bc=AppBackup(bm); var eM:String?=null; try{val jS=json.encodeToString(bc);Log.d("WidgetConfigure","Serialized: ${jS.take(200)}...");applicationContext.contentResolver.openOutputStream(targetUri)?.use{os->os.write(jS.toByteArray(Charsets.UTF_8));Log.i("WidgetConfigure","Backup JSON written.")}?:throw Exception("OutputStream is null")}catch(e:Exception){Log.e("WidgetConfigure","Error writing backup",e);eM=getString(R.string.backup_failed,e.localizedMessage)}; withContext(Dispatchers.Main){Toast.makeText(applicationContext,eM?:getString(R.string.backup_successful),Toast.LENGTH_SHORT).show()} } }
    private fun startRestore() { Log.d("WidgetConfigure", "Restore started."); restoreLauncher.launch(arrayOf("application/json")) }
    private fun performRestore(sourceUri: Uri) { Log.d("WidgetConfigure", "Performing restore from $sourceUri"); lifecycleScope.launch(Dispatchers.IO) { var bc:AppBackup?=null; var eM:String?=null; try{applicationContext.contentResolver.openInputStream(sourceUri)?.use{ips->BufferedReader(InputStreamReader(ips,Charsets.UTF_8)).use{r->val jS=r.readText();Log.d("WidgetConfigure","Read JSON: ${jS.take(200)}...");bc=json.decodeFromString<AppBackup>(jS);Log.i("WidgetConfigure","Parsed backup for ${bc?.widgetSettings?.size} widgets.")}}?:throw Exception("InputStream is null")}catch(e:Exception){Log.e("WidgetConfigure","Error reading/parsing backup",e);eM=when(e){is kotlinx.serialization.SerializationException->getString(R.string.error_parsing_backup,e.localizedMessage);else->getString(R.string.error_reading_file,e.localizedMessage)}}; if(bc!=null){val am=AppWidgetManager.getInstance(applicationContext);val cn=ComponentName(applicationContext,ContactsGridWidgetProvider::class.java);val cIds:IntArray?=try{am.getAppWidgetIds(cn)}catch(e:Exception){null};if(cIds==null||cIds.isEmpty()){eM=getString(R.string.no_widgets_to_restore)}else{val rIds=mutableListOf<Int>();cIds.forEach{wId->bc!!.widgetSettings[wId]?.let{d->Log.d("WidgetConfigure","Restoring widget ID: $wId");WidgetPrefsUtils.saveWidgetConfig(applicationContext,wId,d.contactUris,d.columnCount,d.clickAction,d.sortOrder,d.maxItems,d.itemHeight,d.showUnknown,d.showDialerButton,d.showRefreshButton,d.filterOldUnknown);rIds.add(wId)}?:Log.w("WidgetConfigure","Settings for widget ID $wId not in backup.")};if(rIds.isNotEmpty()){Log.d("WidgetConfigure","Triggering update for: ${rIds.joinToString()}");rIds.forEach{wId->ContactsGridWidgetProvider.updateAppWidget(applicationContext,am,wId)};eM=null;if(appWidgetId in rIds){withContext(Dispatchers.Main){Toast.makeText(applicationContext,R.string.settings_reloading,Toast.LENGTH_SHORT).show();loadCurrentWidgetSettings();buildSettingsList()}}}else{eM="Настройки для виджетов не найдены."}}}; withContext(Dispatchers.Main){Toast.makeText(applicationContext,eM?:getString(R.string.restore_successful),Toast.LENGTH_LONG).show()} } }
    private fun proceedSaveConfiguration() { Log.d("WidgetConfigure", "Saving config for widget $appWidgetId..."); val sCU=ArrayList(currentSelectedContactUris); val cCTS=currentColumnCount.toIntOrNull()?:WidgetPrefsUtils.DEFAULT_COLUMN_COUNT; val cATS=currentClickAction; val sOTS=currentSortOrder; val mITS=currentMaxItems; val iHTS=currentItemHeight.toIntOrNull()?:WidgetPrefsUtils.DEFAULT_ITEM_HEIGHT; val sUTS=currentShowUnknown; val sDBTS=currentShowDialerButton; val sRBTS=currentShowRefreshButton; val fOUTS=currentFilterOldUnknown; WidgetPrefsUtils.saveWidgetConfig(this,appWidgetId,sCU,cCTS,cATS,sOTS,mITS,iHTS,sUTS,sDBTS,sRBTS,fOUTS); val am=AppWidgetManager.getInstance(this);ContactsGridWidgetProvider.updateAppWidget(this,am,appWidgetId);val rV=Intent().apply{putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId)};setResult(Activity.RESULT_OK,rV);Log.d("WidgetConfigure","Config saved. Finishing.");finish() }
}