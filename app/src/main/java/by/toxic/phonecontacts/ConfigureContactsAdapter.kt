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
import by.toxic.phonecontacts.ContactItem // <<< ДОБАВЛЕН ИМПОРТ >>>

// Интерфейс для взаимодействия с Activity/Fragment выбора контактов
interface ContactsInteractionListener {
    /** Вызывается при изменении состояния выбора контакта (чекбокс). */
    fun onContactSelectedChanged(contact: ContactItem, isSelected: Boolean)
    /** Вызывается для перемещения контакта вверх в списке. */
    fun moveContactUp(position: Int)
    /** Вызывается для перемещения контакта вниз в списке. */
    fun moveContactDown(position: Int)
    /** Возвращает текущий размер списка контактов в Activity/Fragment. */
    fun getCurrentListSize(): Int
    /** Вызывается, когда изменился порядок выбранных элементов или сам выбор. */
    fun onSelectionOrderChanged()
    /** Получает элемент по его текущей позиции в списке Activity/Fragment. */
    fun getItemAt(position: Int): ContactItem?
}

// <<< ИЗМЕНЕНИЕ: Указаны типы для ListAdapter >>>
class ConfigureContactsAdapter(
    private val listener: ContactsInteractionListener // Слушатель для обратной связи
) : ListAdapter<ContactItem, ConfigureContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    private companion object {
        const val TAG = "ConfigureContactsAdapt" // TAG для логов
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        Log.v(TAG, "onCreateViewHolder called") // Лог создания ViewHolder
        val binding = ConfigureContactItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val item = getItem(position) // Получаем элемент из адаптера
        if (item != null) {
            Log.v(TAG, "onBindViewHolder for position $position, item ID: ${item.id}")
            // <<< ИЗМЕНЕНИЕ: Передаем размер текущего списка из адаптера >>>
            holder.bind(item, position, itemCount)
        } else {
            Log.e(TAG, "onBindViewHolder called with null item at position $position")
        }
    }

    // ViewHolder для одного контакта
    class ContactViewHolder(
        private val binding: ConfigureContactItemBinding,
        private val listener: ContactsInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            // Обработчик клика по всему элементу для изменения состояния выбора
            binding.root.setOnClickListener {
                val currentPosition = absoluteAdapterPosition // Используем безопасный способ получения позиции
                if (currentPosition != RecyclerView.NO_POSITION) {
                    // Получаем актуальный элемент из listener'а (он хранит основной список)
                    val contactItem = listener.getItemAt(currentPosition)
                    if (contactItem != null) {
                        val newState = !contactItem.isSelected // Инвертируем состояние выбора
                        Log.d(TAG, "Item clicked at pos $currentPosition. Toggling selection for ID ${contactItem.id} to $newState")

                        // --- Немедленное обновление UI для отзывчивости ---
                        updateSelectionStateUI(newState, currentPosition)
                        // --- Конец немедленного обновления UI ---

                        // 1. Уведомляем Activity/Fragment об изменении состояния элемента
                        listener.onContactSelectedChanged(contactItem, newState)
                        // 2. Уведомляем Activity/Fragment, что порядок мог измениться (для пересортировки и submitList)
                        listener.onSelectionOrderChanged()
                    } else {
                        Log.e(TAG, "Could not get item at position $currentPosition from listener during root click")
                    }
                } else {
                    Log.w(TAG, "root click ignored, position is NO_POSITION")
                }
            }

            // Обработчики для кнопок перемещения
            binding.buttonMoveUp.setOnClickListener {
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    Log.d(TAG, "Move Up button clicked for position $currentPosition")
                    listener.moveContactUp(currentPosition)
                } else {
                    Log.w(TAG, "Move Up button click ignored, position is NO_POSITION")
                }
            }

            binding.buttonMoveDown.setOnClickListener {
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    Log.d(TAG, "Move Down button clicked for position $currentPosition")
                    listener.moveContactDown(currentPosition)
                } else {
                    Log.w(TAG, "Move Down button click ignored, position is NO_POSITION")
                }
            }
        }

        /**
         * Привязывает данные контакта к элементам View и настраивает их состояние.
         * @param contact Элемент контакта для отображения.
         * @param position Позиция элемента в списке адаптера.
         * @param listSize Общий размер списка в адаптере.
         */
        fun bind(contact: ContactItem, position: Int, listSize: Int) {
            val context = itemView.context // Получаем контекст из itemView

            // Отображение имени и номера
            binding.contactName.text = contact.name ?: context.getString(R.string.unknown_contact)
            // Отображаем первый номер или пустую строку
            binding.contactNumber.text = contact.numbers.firstOrNull() ?: ""
            binding.contactNumber.isVisible = !binding.contactNumber.text.isNullOrEmpty()

            // --- Настройка состояния выбора ---
            binding.contactCheckbox.setOnCheckedChangeListener(null)
            binding.contactCheckbox.isChecked = contact.isSelected

            // Обновляем видимость и доступность кнопок сортировки
            updateSortButtonsState(contact.isSelected, position, listSize)

            // Загрузка фото контакта с помощью Glide
            Glide.with(binding.contactPhoto.context)
                .load(contact.photoUri)
                .placeholder(R.drawable.ic_contact_placeholder)
                .error(R.drawable.ic_contact_placeholder)
                .circleCrop()
                .into(binding.contactPhoto)

            // Установка Content Description для доступности
            binding.buttonMoveUp.contentDescription = context.getString(R.string.content_desc_move_up)
            binding.buttonMoveDown.contentDescription = context.getString(R.string.content_desc_move_down)
        }

        /** Обновляет UI для состояния выбора (чекбокс, кнопки сортировки). */
        private fun updateSelectionStateUI(isSelected: Boolean, position: Int) {
            binding.contactCheckbox.isChecked = isSelected
            // Используем listener для получения актуального размера списка
            updateSortButtonsState(isSelected, position, listener.getCurrentListSize())
        }

        /** Обновляет видимость и доступность кнопок сортировки. */
        private fun updateSortButtonsState(isSelected: Boolean, position: Int, currentListSize: Int) {
            binding.sortButtonsContainer.isVisible = isSelected
            binding.buttonMoveUp.isEnabled = isSelected && position > 0
            binding.buttonMoveDown.isEnabled = isSelected && position < currentListSize - 1
        }
    }

    // DiffUtil для эффективного обновления списка контактов
    // <<< ИЗМЕНЕНИЕ: Указан тип для ItemCallback >>>
    class ContactDiffCallback : DiffUtil.ItemCallback<ContactItem>() {
        // Проверяем, тот же ли это элемент (обычно по ID)
        override fun areItemsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean {
            // Используем комбинацию ID и lookupKey для большей надежности
            return oldItem.id == newItem.id && oldItem.lookupKey == newItem.lookupKey
        }

        // Проверяем, изменилось ли содержимое элемента
        @SuppressLint("DiffUtilEquals") // Подавляем предупреждение, т.к. data class реализует equals()
        override fun areContentsTheSame(oldItem: ContactItem, newItem: ContactItem): Boolean {
            // Сравниваем все поля data class
            return oldItem == newItem
        }
    }
}