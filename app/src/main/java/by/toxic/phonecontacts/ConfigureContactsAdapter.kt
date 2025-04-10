package by.toxic.phonecontacts

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import by.toxic.phonecontacts.databinding.ConfigureContactItemBinding // <<< Убедись, что этот import правильный
import com.bumptech.glide.Glide
import java.util.Collections

// Интерфейс для взаимодействия с Activity/Fragment выбора контактов
interface ContactsInteractionListener {
    fun onContactSelectedChanged(contact: ContactItem, isSelected: Boolean)
    fun moveContactUp(position: Int)
    fun moveContactDown(position: Int)
    fun getCurrentListSize(): Int
    fun onSelectionOrderChanged()
    fun getItemAt(position: Int): ContactItem?
}

// Адаптер для списка контактов на экране выбора/сортировки
class ConfigureContactsAdapter(
    private val listener: ContactsInteractionListener
) : ListAdapter<ContactItem, ConfigureContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ConfigureContactItemBinding.inflate( LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position), position, currentList.size)
    }

    // ViewHolder для одного контакта
    class ContactViewHolder(
        private val binding: ConfigureContactItemBinding, // <<< Используется ViewBinding для элемента контакта
        private val listener: ContactsInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Обработчик клика по всему элементу для изменения состояния выбора
            binding.root.setOnClickListener {
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    val contactItem = listener.getItemAt(currentPosition)
                    if (contactItem != null) {
                        val newState = !contactItem.isSelected
                        // Обновляем UI немедленно
                        binding.contactCheckbox.isChecked = newState
                        binding.sortButtonsContainer.isVisible = newState
                        val listSize = listener.getCurrentListSize()
                        binding.buttonMoveUp.isEnabled = newState && currentPosition > 0
                        binding.buttonMoveDown.isEnabled = newState && currentPosition < listSize - 1
                        // Уведомляем Activity
                        listener.onContactSelectedChanged(contactItem, newState)
                        listener.onSelectionOrderChanged() // Сообщаем, что порядок мог измениться (для пересортировки)
                    } else { Log.e("Adapter", "Could not get item at position $currentPosition") }
                }
            }
        }

        // Привязка данных контакта к элементам View
        fun bind(contact: ContactItem, position: Int, listSize: Int) {
            binding.contactName.text = contact.name ?: "Unknown" // Отображаем имя или "Unknown"
            binding.contactNumber.text = contact.numbers.firstOrNull() ?: "" // Отображаем первый номер
            // Сбрасываем листенер перед установкой isChecked, чтобы избежать ложного срабатывания
            binding.contactCheckbox.setOnCheckedChangeListener(null)
            binding.contactCheckbox.isChecked = contact.isSelected
            // Управляем видимостью и активностью кнопок сортировки
            binding.sortButtonsContainer.isVisible = contact.isSelected
            binding.buttonMoveUp.isEnabled = contact.isSelected && position > 0
            binding.buttonMoveDown.isEnabled = contact.isSelected && position < listSize - 1

            // Загрузка фото контакта с помощью Glide
            Glide.with(binding.contactPhoto.context)
                .load(contact.photoUri)
                .placeholder(R.drawable.ic_contact_placeholder)
                .error(R.drawable.ic_contact_placeholder)
                .circleCrop() // Делаем фото круглым
                .into(binding.contactPhoto)

            // Обработчики для кнопок перемещения
            binding.buttonMoveUp.setOnClickListener {
                if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                    listener.moveContactUp(absoluteAdapterPosition)
                }
            }
            binding.buttonMoveDown.setOnClickListener {
                if (absoluteAdapterPosition != RecyclerView.NO_POSITION) {
                    listener.moveContactDown(absoluteAdapterPosition)
                }
            }
        }
    }

    // DiffUtil для эффективного обновления списка контактов
    class ContactDiffCallback : DiffUtil.ItemCallback<ContactItem>() {
        // Проверяем, один ли это и тот же элемент (по ID)
        override fun areItemsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean {
            return oldItem.id == newItem.id && oldItem.lookupKey == newItem.lookupKey
        }
        // Проверяем, изменилось ли содержимое элемента (включая isSelected)
        @SuppressLint("DiffUtilEquals") // Подавляем предупреждение, т.к. data class сравнение подходит
        override fun areContentsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean {
            return oldItem == newItem
        }
    }
}