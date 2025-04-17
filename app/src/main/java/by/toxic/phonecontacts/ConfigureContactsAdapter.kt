package by.toxic.phonecontacts

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import by.toxic.phonecontacts.databinding.ConfigureContactItemBinding
import com.bumptech.glide.Glide
import java.util.Collections

// Интерфейс для взаимодействия с Activity/Fragment выбора контактов
interface ContactsInteractionListener {
    fun onContactSelectedChanged(contact: ContactItem, isSelected: Boolean)
    fun moveContactUp(position: Int)
    fun moveContactDown(position: Int)
    fun getCurrentListSize(): Int
    fun onSelectionOrderChanged() // Вызывается при изменении выбора или порядка
    fun getItemAt(position: Int): ContactItem? // Получить элемент по позиции
}

// Адаптер для списка контактов на экране выбора/сортировки
class ConfigureContactsAdapter(
    private val listener: ContactsInteractionListener
) : ListAdapter<ContactItem, ConfigureContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ConfigureContactItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position), position, currentList.size)
    }

    // ViewHolder для одного контакта
    class ContactViewHolder(
        private val binding: ConfigureContactItemBinding,
        private val listener: ContactsInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Обработчик клика по всему элементу для изменения состояния выбора
            binding.root.setOnClickListener {
                val currentPosition = absoluteAdapterPosition // Используем absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    val contactItem = listener.getItemAt(currentPosition)
                    if (contactItem != null) {
                        val newState = !contactItem.isSelected // Инвертируем состояние

                        // Обновляем UI немедленно для отзывчивости
                        binding.contactCheckbox.isChecked = newState
                        binding.sortButtonsContainer.isVisible = newState
                        val listSize = listener.getCurrentListSize()
                        binding.buttonMoveUp.isEnabled = newState && currentPosition > 0
                        binding.buttonMoveDown.isEnabled = newState && currentPosition < listSize - 1

                        // Уведомляем Activity об изменении состояния элемента
                        listener.onContactSelectedChanged(contactItem, newState)
                        // Уведомляем Activity, что порядок мог измениться (для пересортировки)
                        listener.onSelectionOrderChanged()
                    } else {
                        Log.e("Adapter", "Could not get item at position $currentPosition from listener")
                    }
                }
            }

            // Обработчики для кнопок перемещения
            binding.buttonMoveUp.setOnClickListener {
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    listener.moveContactUp(currentPosition)
                }
            }
            binding.buttonMoveDown.setOnClickListener {
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    listener.moveContactDown(currentPosition)
                }
            }
        }

        // Привязка данных контакта к элементам View
        fun bind(contact: ContactItem, position: Int, listSize: Int) {
            // <<< ИСПОЛЬЗУЕМ СТРОКУ ИЗ РЕСУРСОВ >>>
            binding.contactName.text = contact.name ?: itemView.context.getString(R.string.unknown_contact)
            binding.contactNumber.text = contact.numbers.firstOrNull() ?: "" // Отображаем первый номер

            binding.contactCheckbox.setOnCheckedChangeListener(null)
            binding.contactCheckbox.isChecked = contact.isSelected

            binding.sortButtonsContainer.isVisible = contact.isSelected
            binding.buttonMoveUp.isEnabled = contact.isSelected && position > 0
            binding.buttonMoveDown.isEnabled = contact.isSelected && position < listSize - 1

            // Загрузка фото контакта с помощью Glide
            Glide.with(binding.contactPhoto.context)
                .load(contact.photoUri) // Загружаем URI фото
                .placeholder(R.drawable.ic_contact_placeholder) // Заглушка на время загрузки
                .error(R.drawable.ic_contact_placeholder) // Заглушка при ошибке
                .circleCrop() // Делаем фото круглым
                .into(binding.contactPhoto)

            // <<< УСТАНАВЛИВАЕМ CONTENT DESCRIPTION ДЛЯ КНОПОК СОРТИРОВКИ >>>
            binding.buttonMoveUp.contentDescription = itemView.context.getString(R.string.content_desc_move_up)
            binding.buttonMoveDown.contentDescription = itemView.context.getString(R.string.content_desc_move_down)
        }
    }

    // DiffUtil для эффективного обновления списка контактов
    class ContactDiffCallback : DiffUtil.ItemCallback<ContactItem>() {
        override fun areItemsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean {
            return oldItem.id == newItem.id && oldItem.lookupKey == newItem.lookupKey
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean {
            return oldItem == newItem
        }
    }
}