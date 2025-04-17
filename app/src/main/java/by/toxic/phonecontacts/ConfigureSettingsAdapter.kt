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

// Интерфейс для взаимодействия с Activity настроек
interface SettingInteractionListener {
    fun onSettingClicked(item: SettingItem.ClickableSetting)
    fun onSwitchChanged(item: SettingItem.SwitchSetting, isChecked: Boolean)
    // Метод для проверки состояния переключателя "Показывать неизвестные"
    fun isShowUnknownEnabled(): Boolean
}

// Адаптер для RecyclerView на экране настроек
class ConfigureSettingsAdapter(
    private val interactionListener: SettingInteractionListener // Листенер для событий
) : ListAdapter<SettingItem, RecyclerView.ViewHolder>(SettingDiffCallback()) {

    companion object {
        // Типы View для разных элементов настроек
        private const val TYPE_HEADER = 0
        private const val TYPE_CLICKABLE = 1
        private const val TYPE_SWITCH = 2
    }

    // Определяем тип View для элемента по его позиции
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is SettingItem.Header -> TYPE_HEADER
            is SettingItem.ClickableSetting -> TYPE_CLICKABLE
            is SettingItem.SwitchSetting -> TYPE_SWITCH
        }
    }

    // Создаем ViewHolder нужного типа
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(SettingItemHeaderBinding.inflate(inflater, parent, false))
            TYPE_CLICKABLE -> ClickableSettingViewHolder(SettingItemClickableBinding.inflate(inflater, parent, false), interactionListener)
            TYPE_SWITCH -> SwitchViewHolder(SettingItemSwitchBinding.inflate(inflater, parent, false), interactionListener)
            else -> throw IllegalArgumentException("Invalid view type: $viewType")
        }
    }

    // Привязываем данные (SettingItem) к ViewHolder'у
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SettingItem.ClickableSetting -> (holder as ClickableSettingViewHolder).bind(item)
            is SettingItem.SwitchSetting -> (holder as SwitchViewHolder).bind(item)
        }
    }

    // --- ViewHolders ---

    // ViewHolder для заголовка секции
    class HeaderViewHolder(private val binding: SettingItemHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingItem.Header) {
            binding.settingHeaderTitle.text = item.title
        }
    }

    // ViewHolder для кликабельного элемента (открывает диалог/экран)
    class ClickableSettingViewHolder(
        private val binding: SettingItemClickableBinding,
        private val listener: SettingInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingItem.ClickableSetting) {
            binding.settingClickableTitle.text = item.title

            // <<< ОБНОВЛЕННАЯ ЛОГИКА ДЛЯ SUMMARY >>>
            var summaryText = item.summary
            // Добавляем пояснение для кастомного аватара
            if (item.id == "unknown_avatar" && item.summary == itemView.context.getString(R.string.avatar_mode_custom)) {
                // Можно добавить имя файла, если оно хранится, или просто "Выбран"
                summaryText = itemView.context.getString(R.string.avatar_mode_custom_selected) // Новая строка
            } else if (item.id == "select_contacts" && item.summary?.startsWith("Выбрано: 0") == true) {
                summaryText = itemView.context.getString(R.string.select_favorites_summary_none) // Новая строка
            } else if (item.id == "select_contacts") {
                // Оставляем как есть "Выбрано: N"
            }

            binding.settingClickableSummary.text = summaryText
            binding.settingClickableSummary.isVisible = !summaryText.isNullOrEmpty()
            // <<< КОНЕЦ ОБНОВЛЕННОЙ ЛОГИКИ >>>

            binding.root.setOnClickListener {
                listener.onSettingClicked(item)
            }
        }
    }

    // ViewHolder для элемента с переключателем (Switch)
    class SwitchViewHolder(
        private val binding: SettingItemSwitchBinding,
        private val listener: SettingInteractionListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SettingItem.SwitchSetting) {
            binding.settingSwitchTitle.text = item.title
            val summaryText = if (item.isChecked) item.summaryOn else item.summaryOff
            binding.settingSwitchSummary.text = summaryText
            binding.settingSwitchSummary.isVisible = !summaryText.isNullOrEmpty()

            val isEffectivelyEnabled: Boolean
            if (item.id == "filter_old_unknown") {
                isEffectivelyEnabled = listener.isShowUnknownEnabled()
                Log.d("Adapter", "Binding 'filter_old_unknown', isEnabled=$isEffectivelyEnabled (based on listener)")
            } else {
                isEffectivelyEnabled = true
            }

            binding.root.isEnabled = isEffectivelyEnabled
            binding.settingSwitchControl.isEnabled = isEffectivelyEnabled
            binding.root.alpha = if (isEffectivelyEnabled) 1.0f else 0.5f

            binding.settingSwitchControl.setOnCheckedChangeListener(null)
            binding.settingSwitchControl.isChecked = item.isChecked

            binding.settingSwitchControl.setOnCheckedChangeListener { _, isChecked ->
                if (isEffectivelyEnabled) {
                    val newSummaryText = if (isChecked) item.summaryOn else item.summaryOff
                    binding.settingSwitchSummary.text = newSummaryText
                    binding.settingSwitchSummary.isVisible = !newSummaryText.isNullOrEmpty()
                    listener.onSwitchChanged(item, isChecked)
                } else {
                    binding.settingSwitchControl.isChecked = item.isChecked
                    Log.d("Adapter", "Change cancelled for disabled switch: ${item.id}")
                }
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
            return oldItem == newItem // Data class comparison works well here
        }

        override fun getChangePayload(oldItem: SettingItem, newItem: SettingItem): Any? {
            // Оптимизация: если изменился только summary или isChecked,
            // можно передать payload для частичного обновления.
            if (oldItem is SettingItem.ClickableSetting && newItem is SettingItem.ClickableSetting) {
                if (oldItem.summary != newItem.summary) return true // Payload для обновления summary
            }
            if (oldItem is SettingItem.SwitchSetting && newItem is SettingItem.SwitchSetting) {
                if (oldItem.isChecked != newItem.isChecked) return true // Payload для обновления switch/summary
            }
            return null // Полное обновление
        }
    }
}