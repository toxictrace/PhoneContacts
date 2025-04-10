package by.toxic.phonecontacts

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.MenuItem
import android.view.View
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

class SelectContactsActivity : AppCompatActivity(), ContactsInteractionListener {

    private lateinit var binding: ActivitySelectContactsBinding
    private lateinit var contactsAdapter: ConfigureContactsAdapter
    private var contactListItems = mutableListOf<ContactItem>()

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    companion object {
        const val EXTRA_SELECTED_URIS = "by.toxic.phonecontacts.EXTRA_SELECTED_URIS"
        const val RESULT_SELECTED_URIS = "by.toxic.phonecontacts.RESULT_SELECTED_URIS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializePermissionLauncher()
        setupUI()
        checkOrRequestContactsPermission()
    }

    private fun initializePermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { Log.d("SelectContactsActivity", "READ_CONTACTS permission granted."); showLoadingState(); loadContacts() }
            else { Log.w("SelectContactsActivity", "READ_CONTACTS permission denied."); showPermissionState(getString(R.string.permission_needed)); Toast.makeText(this, "Без разрешения невозможно выбрать контакты.", Toast.LENGTH_LONG).show() }
        }
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbarSelect)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        contactsAdapter = ConfigureContactsAdapter(this)
        binding.recyclerViewAllContacts.apply {
            layoutManager = LinearLayoutManager(this@SelectContactsActivity)
            adapter = contactsAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
        binding.buttonGrantPermissionSelect.setOnClickListener { checkOrRequestContactsPermission() }
        binding.fabDoneSelect.setOnClickListener { returnSelectedContacts() }
    }

    private fun checkOrRequestContactsPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> { Log.d("SelectContactsActivity", "READ_CONTACTS permission already granted."); showLoadingState(); loadContacts() }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> { Log.d("SelectContactsActivity", "Showing rationale for READ_CONTACTS."); showPermissionState(getString(R.string.permission_needed)) }
            else -> { Log.d("SelectContactsActivity", "Requesting READ_CONTACTS permission."); showPermissionState(getString(R.string.permission_needed)); requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val initialSelectedUris = intent.getStringArrayListExtra(EXTRA_SELECTED_URIS) ?: ArrayList()
            val contacts = fetchContacts()
            val selectedSet = initialSelectedUris.toSet() // Делаем Set для быстрой проверки
            contacts.forEach { it.isSelected = it.contentUri in selectedSet } // Отмечаем выбранные ДО сортировки
            val sortedList = sortContacts(contacts, initialSelectedUris) // Сортируем с уже отмеченными
            contactListItems = sortedList
            withContext(Dispatchers.Main) {
                Log.d("SelectContactsActivity", "Loaded and sorted ${contactListItems.size} contacts.")
                if (contactListItems.isNotEmpty()) { contactsAdapter.submitList(contactListItems.toList()); showListState() }
                else { showEmptyState() }
            }
        }
    }

    private fun fetchContacts(): List<ContactItem> {
        Log.d("SelectContactsActivity", "Fetching contacts...")
        val contactsList = mutableListOf<ContactItem>()
        val resolver: ContentResolver = contentResolver
        val cursor: Cursor? = try { resolver.query( ContactsContract.Contacts.CONTENT_URI, arrayOf( ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_THUMBNAIL_URI, ContactsContract.Contacts.HAS_PHONE_NUMBER), "${ContactsContract.Contacts.HAS_PHONE_NUMBER} > 0", null, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC")
        } catch (e: SecurityException) { Log.e("SelectContactsActivity", "SecurityException fetching contacts: ${e.message}"); lifecycleScope.launch(Dispatchers.Main) { showPermissionState(getString(R.string.permission_needed)) }; return emptyList()
        } catch (e: Exception) { Log.e("SelectContactsActivity", "Exception fetching contacts: ${e.message}"); return emptyList() }
        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID); val lookupKeyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY); val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY); val photoUriIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            if (idIndex == -1 || lookupKeyIndex == -1 || nameIndex == -1 || photoUriIndex == -1) { Log.e("SelectContactsActivity", "One or more contact columns not found in cursor."); return@use }
            while (it.moveToNext()) {
                val id = it.getLong(idIndex); val lookupKey = it.getString(lookupKeyIndex); val name = it.getString(nameIndex); val photoUri = it.getString(photoUriIndex); val phoneNumbers = fetchPhoneNumbers(resolver, id)
                if (phoneNumbers.isNotEmpty()) { contactsList.add( ContactItem(id, lookupKey, name, photoUri, phoneNumbers, false) ) } // isSelected = false по умолчанию
            }
        } ?: run { Log.w("SelectContactsActivity", "Contacts cursor is null.") }
        Log.d("SelectContactsActivity", "Finished fetching contacts. Found: ${contactsList.size}")
        return contactsList
    }

    private fun fetchPhoneNumbers(resolver: ContentResolver, contactId: Long): List<String> {
        val numbers = mutableListOf<String>()
        val phoneCursor: Cursor? = try { resolver.query( ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", arrayOf(contactId.toString()), null)
        } catch (e: Exception) { Log.e("SelectContactsActivity", "Exception fetching phone numbers for contact $contactId: ${e.message}"); null }
        phoneCursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (numberIndex != -1) { while (it.moveToNext()) { it.getString(numberIndex)?.let { num -> numbers.add(num) } } }
            else { Log.w("SelectContactsActivity", "Phone number column not found for contact $contactId") }
        }
        return numbers
    }

    // --- Реализация ContactsInteractionListener ---
    override fun onContactSelectedChanged(contact: ContactItem, isSelected: Boolean) {
        val index = contactListItems.indexOfFirst { it.id == contact.id }
        if (index != -1) {
            contactListItems[index].isSelected = isSelected
            // Не обновляем адаптер здесь, это сделает onSelectionOrderChanged
        }
    }

    // --- ИЗМЕНЕНО: Добавляем notifyItemChanged после submitList ---
    override fun moveContactUp(position: Int) {
        if (position > 0 && position < contactListItems.size) {
            Collections.swap(contactListItems, position, position - 1)
            contactsAdapter.submitList(contactListItems.toList()) {
                // Уведомляем об изменении ДВУХ элементов для обновления кнопок
                contactsAdapter.notifyItemChanged(position)
                contactsAdapter.notifyItemChanged(position - 1)
            }
        }
    }

    // --- ИЗМЕНЕНО: Добавляем notifyItemChanged после submitList ---
    override fun moveContactDown(position: Int) {
        if (position >= 0 && position < contactListItems.size - 1) {
            Collections.swap(contactListItems, position, position + 1)
            contactsAdapter.submitList(contactListItems.toList()) {
                contactsAdapter.notifyItemChanged(position)
                contactsAdapter.notifyItemChanged(position + 1)
            }
        }
    }

    override fun getCurrentListSize(): Int { return contactListItems.size }

    override fun onSelectionOrderChanged() {
        Log.d("SelectContactsActivity", "Selection order changed, resorting...")
        contactListItems = sortContacts(contactListItems, null) // Пересортировываем текущий список
        contactsAdapter.submitList(contactListItems.toList()) // Обновляем адаптер
    }

    override fun getItemAt(position: Int): ContactItem? {
        return contactListItems.getOrNull(position)
    }
    // --- Конец ContactsInteractionListener ---

    // --- ИЗМЕНЕНА ЛОГИКА СОРТИРОВКИ ---
    private fun sortContacts(
        contacts: List<ContactItem>,
        initialSelectionUris: List<String>?
    ): MutableList<ContactItem> {
        return contacts.sortedWith(compareBy(
            // Сначала те, кто НЕ выбран (isSelected = false)
            { !it.isSelected },
            // Затем, если это первая загрузка, сортируем ВЫБРАННЫХ по initialSelectionUris
            { if (it.isSelected && initialSelectionUris != null) initialSelectionUris.indexOf(it.contentUri) else Int.MAX_VALUE },
            // Если не первая загрузка, ВЫБРАННЫЕ остаются в своем порядке (их isSelected false, они уже наверху)
            // Наконец, сортируем НЕВЫБРАННЫХ по имени
            { if (!it.isSelected) it.name ?: "" else null }
        )).toMutableList()
    }


    private fun returnSelectedContacts() {
        // Берем отсортированный и отфильтрованный список URI
        val selectedUris = contactListItems.filter { it.isSelected }.mapNotNull { it.contentUri }
        Log.d("SelectContactsActivity", "Returning ${selectedUris.size} selected URIs in specific order.")
        val resultIntent = Intent(); resultIntent.putStringArrayListExtra(RESULT_SELECTED_URIS, ArrayList(selectedUris)); setResult(Activity.RESULT_OK, resultIntent); finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { setResult(Activity.RESULT_CANCELED); finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Функции состояния UI без изменений ---
    private fun showLoadingState() { binding.progressBarSelect.isVisible = true; binding.recyclerViewAllContacts.isVisible = false; binding.textPermissionSelect.isVisible = false; binding.buttonGrantPermissionSelect.isVisible = false; binding.textNoContactsSelect.isVisible = false; binding.fabDoneSelect.isEnabled = false }
    private fun showListState() { binding.progressBarSelect.isVisible = false; binding.recyclerViewAllContacts.isVisible = true; binding.textPermissionSelect.isVisible = false; binding.buttonGrantPermissionSelect.isVisible = false; binding.textNoContactsSelect.isVisible = false; binding.fabDoneSelect.isEnabled = true }
    private fun showPermissionState(message: String) { binding.progressBarSelect.isVisible = false; binding.recyclerViewAllContacts.isVisible = false; binding.textPermissionSelect.text = message; binding.textPermissionSelect.isVisible = true; binding.buttonGrantPermissionSelect.isVisible = true; binding.textNoContactsSelect.isVisible = false; binding.fabDoneSelect.isEnabled = false }
    private fun showEmptyState() { binding.progressBarSelect.isVisible = false; binding.recyclerViewAllContacts.isVisible = false; binding.textPermissionSelect.isVisible = false; binding.buttonGrantPermissionSelect.isVisible = false; binding.textNoContactsSelect.isVisible = true; binding.fabDoneSelect.isEnabled = true }
}