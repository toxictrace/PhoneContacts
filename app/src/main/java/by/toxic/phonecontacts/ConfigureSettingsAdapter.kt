package by.toxic.phonecontacts

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import by.toxic.phonecontacts.databinding.SettingItemClickableBinding
import by.toxic.phonecontacts.databinding.SettingItemHeaderBinding
import by.toxic.phonecontacts.databinding.SettingItemSwitchBinding

// Интерфейс для взаимодействия с Activity настроек (без изменений)
interface SettingInteractionListener {
    fun onSettingClicked(item: SettingItem.ClickableSetting)
    fun onSwitchChanged(item: SettingItem.SwitchSetting, isChecked: Boolean)
    fun isShowUnknownEnabled(): Boolean
}

// Адаптер для RecyclerView на экране настроек
class ConfigureSettingsAdapter(
    private val interactionListener: SettingInteractionListener // Листенер для событий
) : ListAdapter<SettingItem, RecyclerView.ViewHolder>(SettingDiffCallback()) {

    // <<< ИЗМЕНЕНИЕ: Убираем private >>>
    companion object {
        const val TAG = "ConfigureSettingsAdapt"
        // Типы View для разных элементов настроек
        private const val TYPE_HEADER = 0
        private const val TYPE_CLICKABLE = 1
        private const val TYPE_SWITCH = 2

        // Константы для Payloads (частичного обновления)
        // Сделаем их public или internal, чтобы WidgetConfigureActivity мог их использовать
        internal const val PAYLOAD_UPDATE_SUMMARY = "payload_summary"
        internal const val PAYLOAD_UPDATE_CHECKED = "payload_checked"
        internal const val PAYLOAD_UPDATE_ENABLED = "payload_enabled" // Для обновления состояния enabled
    }

    // Определяем тип View для элемента по его позиции (без изменений)
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SettingItem.Header -> TYPE_HEADER
            is SettingItem.ClickableSetting -> TYPE_CLICKABLE
            is SettingItem.SwitchSetting -> TYPE_SWITCH
            null -> throw IllegalStateException("Item at position $position is null")
        }
    }

    // Создаем ViewHolder нужного типа (без изменений)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(SettingItemHeaderBinding.inflate(inflater, parent, false))
            TYPE_CLICKABLE -> ClickableSettingViewHolder(SettingItemClickableBinding.inflate(inflater, parent, false), interactionListener)
            TYPE_SWITCH -> SwitchViewHolder(SettingItemSwitchBinding.inflate(inflater, parent, false), interactionListener)
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    // --- Привязка данных ---

    /** Полная привязка данных к ViewHolder */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        Log.v(TAG, "onBindViewHolder (FULL) for position $position")
        when (val item = getItem(position)) {
            is SettingItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SettingItem.ClickableSetting -> (holder as ClickableSettingViewHolder).bind(item)
            is SettingItem.SwitchSetting -> (holder as SwitchViewHolder).bind(item)
            null -> Log.e(TAG, "Cannot bind null item at position $position") // Обработка null
        }
    }

    /** Частичная привязка данных к ViewHolder с использованием Payloads */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // Если payloads пустой, вызываем полную привязку
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        Log.v(TAG, "onBindViewHolder (PAYLOADS) for position $position, payloads: $payloads")
        val item = getItem(position) ?: return // Выходим, если элемент null

        // Обрабатываем payload'ы. Можно комбинировать, если нужно.
        payloads.forEach { payload ->
            when (payload) {
                PAYLOAD_UPDATE_SUMMARY -> {
                    if (holder is ClickableSettingViewHolder && item is SettingItem.ClickableSetting) {
                        holder.updateSummary(item)
                    }
                    // Для Switch summary обновляется вместе с isChecked или enabled
                }
                PAYLOAD_UPDATE_CHECKED -> {
                    if (holder is SwitchViewHolder && item is SettingItem.SwitchSetting) {
                        holder.updateCheckedState(item)
                    }
                }
                PAYLOAD_UPDATE_ENABLED -> {
                    if (holder is SwitchViewHolder && item is SettingItem.SwitchSetting) {
                        holder.updateEnabledState(item)
                    }
                }
            }
        }
    }


    // --- ViewHolders ---

    // ViewHolder для заголовка секции
    class HeaderViewHolder(private val binding: SettingItemHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingItem.Header) {
            binding.settingHeaderTitle.text = item.title
        }
    }

    // ViewHolder для кликабельного элемента
    class ClickableSettingViewHolder(
        private val binding: SettingItemClickableBinding,
        private val listener: SettingInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {

        // Полная привязка
        fun bind(item: SettingItem.ClickableSetting) {
            binding.settingClickableTitle.text = item.title
            updateSummary(item) // Выносим обновление summary в отдельный метод
            binding.root.setOnClickListener {
                // Получаем актуальный элемент, чтобы передать его в listener
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    (bindingAdapter as? ConfigureSettingsAdapter)?.let { adapter ->
                        val clickedItem = adapter.getItem(currentPosition) as? SettingItem.ClickableSetting
                        if (clickedItem != null) {
                            listener.onSettingClicked(clickedItem)
                        } else {
                            Log.e(TAG, "ClickableSetting not found at pos $currentPosition for click listener")
                        }
                    }
                }
            }
        }

        // Метод для частичного обновления только summary
        fun updateSummary(item: SettingItem.ClickableSetting) {
            val context = itemView.context
            var summaryText = item.summary // Локальная копия

            // Логика для форматирования summary
            when (item.id) {
                SettingItem.ClickableSetting.ID_UNKNOWN_AVATAR -> {
                    if (summaryText == context.getString(R.string.avatar_mode_custom)) {
                        summaryText = context.getString(R.string.avatar_mode_custom_selected)
                    }
                }
                SettingItem.ClickableSetting.ID_SELECT_CONTACTS -> {
                    // <<< ИЗМЕНЕНИЕ: Используем локальную копию summaryText >>>
                    val summaryPrefix = context.getString(R.string.select_favorites_summary, 0).substringBefore("0")
                    if (summaryText?.startsWith(summaryPrefix) == true && summaryText.endsWith("0")) {
                        summaryText = context.getString(R.string.select_favorites_summary_none)
                    }
                }
            }

            binding.settingClickableSummary.text = summaryText
            binding.settingClickableSummary.isVisible = !summaryText.isNullOrEmpty()
        }
    }

    // ViewHolder для элемента с переключателем
    class SwitchViewHolder(
        private val binding: SettingItemSwitchBinding,
        private val listener: SettingInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: SettingItem.SwitchSetting? = null // Храним текущий элемент для listener'а

        init {
            binding.settingSwitchControl.setOnCheckedChangeListener { _, isChecked ->
                // Получаем текущий элемент из адаптера (более надежно, чем хранить локально)
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    (bindingAdapter as? ConfigureSettingsAdapter)?.let { adapter ->
                        val item = adapter.getItem(currentPosition) as? SettingItem.SwitchSetting
                        if (item != null) {
                            if (isEffectivelyEnabled(item)) {
                                Log.d(TAG, "Switch ID ${item.id} changed to $isChecked via UI interaction.")
                                updateSummaryText(item, isChecked)
                                listener.onSwitchChanged(item, isChecked)
                            } else {
                                Log.d(TAG, "Switch ID ${item.id} change cancelled (disabled). Reverting UI.")
                                binding.settingSwitchControl.isChecked = item.isChecked // Возвращаем в UI
                            }
                        } else {
                            Log.e(TAG, "SwitchSetting not found at pos $currentPosition for change listener")
                        }
                    }
                }
            }
        }

        // Полная привязка
        fun bind(item: SettingItem.SwitchSetting) {
            // currentItem = item // Больше не храним локально, берем из адаптера
            binding.settingSwitchTitle.text = item.title
            updateCheckedStateUI(item) // Обновляем состояние и summary
            updateEnabledState(item) // Обновляем доступность
        }

        // Частичное обновление: состояние переключателя и summary
        fun updateCheckedState(item: SettingItem.SwitchSetting) {
            // currentItem = item // Обновляем сохраненный элемент
            updateCheckedStateUI(item)
            // Также нужно обновить enabled state, т.к. он может зависеть от isChecked другого элемента
            updateEnabledState(item)
        }

        // Обновляет только UI переключателя и summary
        private fun updateCheckedStateUI(item: SettingItem.SwitchSetting) {
            binding.settingSwitchControl.setOnCheckedChangeListener(null) // Снимаем слушатель
            binding.settingSwitchControl.isChecked = item.isChecked
            binding.settingSwitchControl.setOnCheckedChangeListener { _, isChecked ->
                // Код слушателя остается тем же
                val currentPosition = absoluteAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    (bindingAdapter as? ConfigureSettingsAdapter)?.let { adapter ->
                        val currentItemFromAdapter = adapter.getItem(currentPosition) as? SettingItem.SwitchSetting
                        if (currentItemFromAdapter != null) {
                            if (isEffectivelyEnabled(currentItemFromAdapter)) {
                                Log.d(TAG, "Switch ID ${currentItemFromAdapter.id} changed to $isChecked via UI (listener re-attached).")
                                updateSummaryText(currentItemFromAdapter, isChecked)
                                listener.onSwitchChanged(currentItemFromAdapter, isChecked)
                            } else {
                                Log.d(TAG, "Switch ID ${currentItemFromAdapter.id} change cancelled (disabled, listener re-attached). Reverting UI.")
                                binding.settingSwitchControl.isChecked = currentItemFromAdapter.isChecked
                            }
                        }
                    }
                }
            } // Возвращаем слушатель
            updateSummaryText(item, item.isChecked)
        }


        // Частичное обновление: доступность элемента
        fun updateEnabledState(item: SettingItem.SwitchSetting) {
            // currentItem = item // Обновляем сохраненный элемент
            val enabled = isEffectivelyEnabled(item)
            Log.v(TAG, "Updating enabled state for ID ${item.id}: $enabled")
            binding.root.isEnabled = enabled
            binding.settingSwitchControl.isEnabled = enabled
            // Делаем элемент полупрозрачным, если он не доступен
            binding.root.alpha = if (enabled) 1.0f else 0.5f
        }


        // Обновляет текст summary в зависимости от состояния
        private fun updateSummaryText(item: SettingItem.SwitchSetting, isChecked: Boolean) {
            val summaryText = if (isChecked) item.summaryOn else item.summaryOff
            binding.settingSwitchSummary.text = summaryText
            binding.settingSwitchSummary.isVisible = !summaryText.isNullOrEmpty()
        }

        // Проверяет, должен ли переключатель быть активен (учитывает зависимость filter_old_unknown)
        private fun isEffectivelyEnabled(item: SettingItem.SwitchSetting): Boolean {
            return if (item.id == SettingItem.SwitchSetting.ID_FILTER_OLD_UNKNOWN) { // <<< Используем константу
                val showUnknownEnabled = listener.isShowUnknownEnabled()
                Log.v(TAG, "Checking enabled state for '${SettingItem.SwitchSetting.ID_FILTER_OLD_UNKNOWN}': showUnknownEnabled=$showUnknownEnabled")
                showUnknownEnabled
            } else {
                true
            }
        }
    }


    // --- DiffUtil Callback ---
    class SettingDiffCallback : DiffUtil.ItemCallback<SettingItem>() {
        override fun areItemsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            return when {
                oldItem is SettingItem.ClickableSetting && newItem is SettingItem.ClickableSetting -> oldItem.id == newItem.id
                oldItem is SettingItem.SwitchSetting && newItem is SettingItem.SwitchSetting -> oldItem.id == newItem.id
                oldItem is SettingItem.Header && newItem is SettingItem.Header -> oldItem.title == newItem.title
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SettingItem, newItem: SettingItem): Boolean {
            if (oldItem is SettingItem.SwitchSetting && newItem is SettingItem.SwitchSetting &&
                oldItem.id == SettingItem.SwitchSetting.ID_FILTER_OLD_UNKNOWN) { // <<< Используем константу
                // Для filter_old_unknown содержимое зависит от isShowUnknownEnabled(), но мы не можем проверить это здесь.
                // Сравним только поля самого item'а. Изменение enabled state будет обработано через payload или полную перепривязку.
                return oldItem == newItem
            }
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: SettingItem, newItem: SettingItem): Any? {
            return when {
                // Clickable: Изменился только summary
                oldItem is SettingItem.ClickableSetting && newItem is SettingItem.ClickableSetting &&
                        oldItem.title == newItem.title && oldItem.summary != newItem.summary -> PAYLOAD_UPDATE_SUMMARY

                // Switch: Изменилось только состояние isChecked (и, возможно, summary)
                oldItem is SettingItem.SwitchSetting && newItem is SettingItem.SwitchSetting &&
                        oldItem.title == newItem.title && oldItem.isChecked != newItem.isChecked -> PAYLOAD_UPDATE_CHECKED

                // Switch: Изменилась только доступность (например, для filter_old_unknown)
                // Это сложно определить здесь без listener'а.
                // Вместо этого Activity может явно вызвать notifyItemChanged с PAYLOAD_UPDATE_ENABLED
                // после изменения isShowUnknown.
                /* // Примерная логика, если бы был доступ к listener'у:
                oldItem is SettingItem.SwitchSetting && newItem is SettingItem.SwitchSetting &&
                oldItem.id == SettingItem.SwitchSetting.ID_FILTER_OLD_UNKNOWN &&
                oldItem == newItem && // Поля те же
                isEffectivelyEnabled(oldItem) != isEffectivelyEnabled(newItem) -> PAYLOAD_UPDATE_ENABLED
                */

                else -> null
            }
        }
    }
}