package by.toxic.phonecontacts

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
// import android.net.Uri // Не используется напрямую
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.MenuItem
// import android.view.View // Не используется напрямую
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import by.toxic.phonecontacts.databinding.ActivitySelectContactsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import by.toxic.phonecontacts.ContactItem // <<< ДОБАВЛЕН ИМПОРТ >>>

class SelectContactsActivity : AppCompatActivity(), ContactsInteractionListener {

    // <<< ИЗМЕНЕНИЕ: Убираем private >>>
    companion object {
        const val TAG = "SelectContactsActivity"
        // Ключи для Intent Extras
        const val EXTRA_SELECTED_URIS = "by.toxic.phonecontacts.EXTRA_SELECTED_URIS" // Входящий список URI
        const val RESULT_SELECTED_URIS = "by.toxic.phonecontacts.RESULT_SELECTED_URIS" // Исходящий результат
    }

    private lateinit var binding: ActivitySelectContactsBinding
    private lateinit var contactsAdapter: ConfigureContactsAdapter
    private var contactListItems = mutableListOf<ContactItem>()
    private var initialSelectedUris: List<String>? = null

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppSettings.getSelectedThemeResId(this))
        super.onCreate(savedInstanceState)
        binding = ActivitySelectContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate: Activity создана.")

        initialSelectedUris = intent.getStringArrayListExtra(EXTRA_SELECTED_URIS)
        Log.d(TAG, "onCreate: Получено ${initialSelectedUris?.size ?: 0} начальных URI.")

        initializePermissionLauncher()
        setupUI()
        checkOrRequestContactsPermission()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                Log.d(TAG, "Нажата кнопка Назад/Закрыть. Установка результата CANCELED.")
                setResult(Activity.RESULT_CANCELED)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializePermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.i(TAG, "Разрешение READ_CONTACTS предоставлено.")
                showLoadingState()
                loadContacts()
            } else {
                Log.w(TAG, "Разрешение READ_CONTACTS отклонено.")
                showPermissionState(getString(R.string.permission_needed_select))
                Toast.makeText(this, R.string.permission_denied_select, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupUI() {
        Log.d(TAG, "Настройка UI...")
        setSupportActionBar(binding.toolbarSelect)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        contactsAdapter = ConfigureContactsAdapter(this)
        binding.recyclerViewAllContacts.apply {
            layoutManager = LinearLayoutManager(this@SelectContactsActivity)
            adapter = contactsAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            setHasFixedSize(true)
        }

        binding.buttonGrantPermissionSelect.setOnClickListener {
            Log.d(TAG, "Нажата кнопка 'Предоставить разрешение'.")
            checkOrRequestContactsPermission()
        }

        binding.fabDoneSelect.setOnClickListener {
            Log.d(TAG, "Нажата FAB 'Готово'.")
            returnSelectedContacts()
        }
        Log.d(TAG, "Настройка UI завершена.")
    }

    private fun checkOrRequestContactsPermission() {
        Log.d(TAG, "Проверка разрешения READ_CONTACTS...")
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "Разрешение READ_CONTACTS уже предоставлено.")
                showLoadingState()
                loadContacts()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                Log.i(TAG, "Требуется показать объяснение для READ_CONTACTS.")
                showPermissionState(getString(R.string.permission_needed_select))
            }
            else -> {
                Log.i(TAG, "Запрос разрешения READ_CONTACTS.")
                showPermissionState(getString(R.string.permission_needed_select))
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun loadContacts() {
        Log.d(TAG, "Запуск загрузки контактов в фоновом потоке...")
        lifecycleScope.launch(Dispatchers.IO) {
            val fetchedContacts = fetchContactsFromProvider()

            if (fetchedContacts == null) {
                Log.e(TAG, "Получен null список контактов из fetchContactsFromProvider.")
                return@launch
            }

            Log.d(TAG, "Получено ${fetchedContacts.size} контактов из ContentProvider.")

            val selectedUriSet = initialSelectedUris?.toSet() ?: emptySet()
            fetchedContacts.forEach { contact ->
                contact.isSelected = contact.contentUri in selectedUriSet
            }
            Log.d(TAG, "Отмечено ${fetchedContacts.count { it.isSelected }} выбранных контактов.")

            val sortedList = sortContacts(fetchedContacts, initialSelectedUris)

            withContext(Dispatchers.Main) {
                contactListItems.clear()
                contactListItems.addAll(sortedList)
                Log.i(TAG, "Контакты загружены, отсортированы и готовы к отображению (${contactListItems.size} шт.)")

                // <<< ИЗМЕНЕНИЕ: Передаем копию в адаптер >>>
                contactsAdapter.submitList(contactListItems.toList())

                if (contactListItems.isNotEmpty()) {
                    showListState()
                } else {
                    showEmptyState()
                }
            }
        }
    }

    private fun fetchContactsFromProvider(): List<ContactItem>? {
        Log.d(TAG, "Начат запрос к ContentProvider для получения контактов...")
        val contactsList = mutableListOf<ContactItem>()
        val resolver: ContentResolver = contentResolver
        val projection = arrayOf(
            ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )
        val selection = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} > 0"
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        var cursor: Cursor? = null

        try {
            cursor = resolver.query( ContactsContract.Contacts.CONTENT_URI, projection, selection, null, sortOrder )
            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoUriIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)

                if (idIndex == -1 || lookupKeyIndex == -1 || nameIndex == -1 || photoUriIndex == -1) {
                    Log.e(TAG, "Одна или несколько колонок не найдены в курсоре контактов!")
                    return emptyList()
                }

                Log.d(TAG, "Найдено контактов в курсоре: ${it.count}")
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val lookupKey = it.getString(lookupKeyIndex) ?: ""
                    val name = it.getString(nameIndex)
                    val photoUri = it.getString(photoUriIndex)
                    val phoneNumbers = fetchPhoneNumbers(resolver, id)

                    if (phoneNumbers.isNotEmpty() && lookupKey.isNotEmpty()) {
                        contactsList.add(
                            ContactItem( id = id, lookupKey = lookupKey, name = name, photoUri = photoUri,
                                numbers = phoneNumbers, isSelected = false
                            )
                        )
                    } else {
                        if(phoneNumbers.isEmpty()) Log.w(TAG, "Контакт ID $id (${name ?: lookupKey}) пропущен, т.к. не найдены номера.")
                        if(lookupKey.isEmpty()) Log.w(TAG, "Контакт ID $id (${name ?: "N/A"}) пропущен, т.к. lookupKey пустой.")
                    }
                }
            } ?: run { Log.w(TAG, "Запрос контактов вернул null курсор.") }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException при запросе контактов: ${e.message}", e)
            lifecycleScope.launch(Dispatchers.Main) {
                showPermissionState(getString(R.string.permission_needed_select))
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при запросе контактов: ${e.message}", e)
            lifecycleScope.launch(Dispatchers.Main) {
                showErrorState(getString(R.string.error_loading_contacts) + ": ${e.localizedMessage}") // <<< Более детальная ошибка >>>
            }
            return emptyList()
        }
        Log.d(TAG, "Запрос к ContentProvider завершен. Получено ${contactsList.size} контактов с номерами.")
        return contactsList
    }

    private fun fetchPhoneNumbers(resolver: ContentResolver, contactId: Long): List<String> {
        val numbers = mutableListOf<String>()
        val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())
        var phoneCursor: Cursor? = null

        try {
            phoneCursor = resolver.query(phoneUri, projection, selection, selectionArgs, null)
            phoneCursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex != -1) {
                    while (it.moveToNext()) {
                        it.getString(numberIndex)?.takeIf { num -> num.isNotBlank() }?.let { validNum ->
                            numbers.add(validNum)
                        }
                    }
                } else { Log.w(TAG, "Колонка номера телефона не найдена для контакта ID $contactId") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при получении номеров для контакта $contactId: ${e.message}")
        }
        return numbers
    }

    // --- Реализация ContactsInteractionListener ---

    override fun onContactSelectedChanged(contact: ContactItem, isSelected: Boolean) {
        Log.d(TAG, "Listener: onContactSelectedChanged для ID ${contact.id}, isSelected: $isSelected")
        val index = contactListItems.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            contactListItems[index].isSelected = isSelected
        } else { Log.w(TAG, "Listener: Контакт с ID ${contact.id} не найден в contactListItems.") }
    }

    override fun moveContactUp(position: Int) {
        Log.d(TAG, "Listener: moveContactUp для позиции $position")
        if (position > 0 && position < contactListItems.size) {
            Collections.swap(contactListItems, position, position - 1)
            contactsAdapter.submitList(contactListItems.toList()) {
                Log.v(TAG, "Адаптер обновлен после moveContactUp.")
            }
        } else { Log.w(TAG, "Listener: moveContactUp вызван для невалидной позиции $position") }
    }

    override fun moveContactDown(position: Int) {
        Log.d(TAG, "Listener: moveContactDown для позиции $position")
        if (position >= 0 && position < contactListItems.size - 1) {
            Collections.swap(contactListItems, position, position + 1)
            contactsAdapter.submitList(contactListItems.toList()) {
                Log.v(TAG, "Адаптер обновлен после moveContactDown.")
            }
        } else { Log.w(TAG, "Listener: moveContactDown вызван для невалидной позиции $position") }
    }

    override fun getCurrentListSize(): Int {
        return contactListItems.size
    }

    override fun onSelectionOrderChanged() {
        Log.d(TAG, "Listener: onSelectionOrderChanged. Пересортировка и обновление адаптера.")
        contactListItems = sortContacts(contactListItems, null) // null => сортировка по текущему состоянию
        contactsAdapter.submitList(contactListItems.toList())
    }

    override fun getItemAt(position: Int): ContactItem? {
        return contactListItems.getOrNull(position)
    }

    // --- Вспомогательные функции ---

    private fun sortContacts(
        contacts: List<ContactItem>,
        initialUris: List<String>?
    ): MutableList<ContactItem> {
        Log.d(TAG, "Сортировка ${contacts.size} контактов. InitialUris is null: ${initialUris == null}")
        // <<< ИЗМЕНЕНИЕ: Явно указываем тип ContactItem в лямбдах >>>
        return contacts.sortedWith(
            compareBy<ContactItem> { contact -> !contact.isSelected } // Сначала невыбранные
                .thenBy { contact -> // Затем сортируем внутри групп
                    if (contact.isSelected && initialUris != null) {
                        val index = initialUris.indexOf(contact.contentUri)
                        if (index != -1) index else Int.MAX_VALUE
                    } else {
                        contact.name ?: "\uFFFF" // По имени (nulls last)
                    }
                }
        ).toMutableList()
    }

    private fun returnSelectedContacts() {
        val selectedUris = contactListItems.filter { it.isSelected }.mapNotNull { it.contentUri }
        Log.i(TAG, "Возвращение результата: ${selectedUris.size} выбранных URI.")
        Log.v(TAG, "Список URI: $selectedUris")

        val resultIntent = Intent()
        // <<< ИЗМЕНЕНИЕ: Явно указываем тип ArrayList<String> >>>
        resultIntent.putStringArrayListExtra(RESULT_SELECTED_URIS, ArrayList<String>(selectedUris))
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    // --- Управление состоянием UI ---
    // <<< ИЗМЕНЕНИЕ: Используем правильные ID из макета >>>

    private fun showLoadingState() {
        Log.d(TAG, "UI State: Loading")
        binding.progressBarSelect.isVisible = true
        binding.recyclerViewAllContacts.isVisible = false
        binding.layoutStatusInfo.isVisible = false // Скрываем контейнер статуса
        binding.textNoContactsSelect.isVisible = false
        binding.fabDoneSelect.isEnabled = false
    }

    private fun showListState() {
        Log.d(TAG, "UI State: List Visible")
        binding.progressBarSelect.isVisible = false
        binding.recyclerViewAllContacts.isVisible = true
        binding.layoutStatusInfo.isVisible = false
        binding.textNoContactsSelect.isVisible = false
        binding.fabDoneSelect.isEnabled = true
    }

    private fun showPermissionState(message: String) {
        Log.d(TAG, "UI State: Permission Needed")
        binding.progressBarSelect.isVisible = false
        binding.recyclerViewAllContacts.isVisible = false
        binding.textNoContactsSelect.isVisible = false
        // Настраиваем и показываем контейнер статуса
        binding.layoutStatusInfo.isVisible = true
        binding.textStatusMessage.text = message // Устанавливаем текст
        binding.buttonGrantPermissionSelect.isVisible = true // Показываем кнопку запроса
        binding.fabDoneSelect.isEnabled = false
    }

    private fun showEmptyState() {
        Log.d(TAG, "UI State: Empty List")
        binding.progressBarSelect.isVisible = false
        binding.recyclerViewAllContacts.isVisible = false
        binding.layoutStatusInfo.isVisible = false
        binding.textNoContactsSelect.isVisible = true // Показываем текст "Нет контактов"
        binding.fabDoneSelect.isEnabled = true
    }

    private fun showErrorState(message: String) {
        Log.d(TAG, "UI State: Error - $message")
        binding.progressBarSelect.isVisible = false
        binding.recyclerViewAllContacts.isVisible = false
        binding.textNoContactsSelect.isVisible = false
        // Настраиваем и показываем контейнер статуса
        binding.layoutStatusInfo.isVisible = true
        binding.textStatusMessage.text = message // Показываем текст ошибки
        binding.buttonGrantPermissionSelect.isVisible = false // Скрываем кнопку запроса при ошибке
        binding.fabDoneSelect.isEnabled = false
    }
}