package by.toxic.phonecontacts

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log // <<< Добавлен импорт Log >>>
import android.view.View
import android.widget.Toast
import by.toxic.phonecontacts.databinding.ActivityMainBinding
// import android.os.Build // Не используется здесь напрямую

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val TAG = "MainActivity" // <<< Добавлен TAG для логов >>>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Устанавливаем тему ДО super.onCreate()
        setTheme(AppSettings.getSelectedThemeResId(this))

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Проверяем, есть ли активные виджеты нашего типа
        val hasWidgets = hasActiveWidgets()
        updateUI(hasWidgets)
    }

    // Обновляет UI в зависимости от наличия виджетов
    private fun updateUI(hasWidgets: Boolean) {
        if (hasWidgets) {
            // Если виджеты есть, показываем кнопку настроек
            Log.d(TAG, "Active widgets found. Showing configuration button.")
            binding.textViewInfo.text = getString(R.string.info_configure_widgets) // <<< Строка из ресурсов >>>
            binding.buttonConfigure.visibility = View.VISIBLE
            binding.buttonConfigure.setOnClickListener {
                openFirstWidgetConfiguration()
            }
        } else {
            // Если виджетов нет, показываем инструкцию
            Log.d(TAG, "No active widgets found. Showing instructions.")
            // Получаем название виджета из ресурсов для подстановки
            val widgetLabel = getString(R.string.widget_label)
            binding.textViewInfo.text = getString(R.string.info_add_widget, widgetLabel) // <<< Строка из ресурсов с форматированием >>>
            binding.buttonConfigure.visibility = View.GONE
        }
    }


    // Проверка наличия активных виджетов нашего приложения
    private fun hasActiveWidgets(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, ContactsGridWidgetProvider::class.java)
        val appWidgetIds: IntArray? = try {
            appWidgetManager.getAppWidgetIds(componentName)
        } catch (e: Exception) {
            // Ловим возможные исключения, если компонент не найден или другая ошибка
            Log.e(TAG, "Error getting widget IDs", e)
            null
        }
        val hasActive = appWidgetIds != null && appWidgetIds.isNotEmpty()
        Log.d(TAG, "hasActiveWidgets check result: $hasActive")
        return hasActive
    }

    // Открывает конфигурацию для первого найденного виджета
    private fun openFirstWidgetConfiguration() {
        Log.d(TAG, "Attempting to open configuration for the first widget.")
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, ContactsGridWidgetProvider::class.java)
        val appWidgetIds : IntArray? = try {
            appWidgetManager.getAppWidgetIds(componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting widget IDs before opening config", e)
            null
        }

        if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
            val firstWidgetId = appWidgetIds[0]
            Log.d(TAG, "Found widget ID: $firstWidgetId. Creating config intent.")
            val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                // Явно указываем компонент Activity конфигурации
                component = ComponentName(this@MainActivity, WidgetConfigureActivity::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, firstWidgetId)
                // Флаг FLAG_ACTIVITY_NEW_TASK не обязателен при запуске из Activity, но не вредит
                // addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Проверяем, есть ли Activity для обработки Intent'а
            if (configureIntent.resolveActivity(packageManager) != null) {
                try {
                    Log.d(TAG, "Starting configuration activity for widget $firstWidgetId.")
                    startActivity(configureIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start configuration activity", e)
                    // <<< Строка из ресурсов с форматированием >>>
                    Toast.makeText(this, getString(R.string.error_opening_config, e.localizedMessage ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "Configuration activity component not found!")
                // <<< Строка из ресурсов >>>
                Toast.makeText(this, R.string.error_config_activity_not_found, Toast.LENGTH_SHORT).show()
            }

        } else {
            // На всякий случай, если виджеты пропали между проверкой и нажатием
            Log.w(TAG, "No active widgets found when trying to open configuration.")
            // <<< Строка из ресурсов >>>
            Toast.makeText(this, R.string.error_no_active_widgets, Toast.LENGTH_SHORT).show()
            // Обновляем UI, чтобы скрыть кнопку
            updateUI(false)
        }
    }
}