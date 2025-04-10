package by.toxic.phonecontacts

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast // <<<--- ДОБАВЛЕН ИМПОРТ
import by.toxic.phonecontacts.databinding.ActivityMainBinding // Используем ViewBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Проверяем, есть ли активные виджеты нашего типа
        val hasWidgets = hasActiveWidgets()

        if (hasWidgets) {
            // Если виджеты есть, показываем кнопку настроек
            binding.textViewInfo.text = "Настроить виджеты:" // Меняем текст
            binding.buttonConfigure.visibility = View.VISIBLE
            binding.buttonConfigure.setOnClickListener {
                // Просто открываем экран конфигурации первого найденного виджета
                // (или можно показать список виджетов для выбора)
                openFirstWidgetConfiguration()
            }
        } else {
            // Если виджетов нет, показываем инструкцию
            binding.textViewInfo.text = "Добавьте виджет 'Contacts Grid' на главный экран."
            binding.buttonConfigure.visibility = View.GONE
        }
    }

    // Проверка наличия активных виджетов нашего приложения
    private fun hasActiveWidgets(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, ContactsGridWidgetProvider::class.java)
        // Важно: getAppWidgetIds может вернуть null, если нет провайдера с таким именем
        val appWidgetIds: IntArray? = try {
            appWidgetManager.getAppWidgetIds(componentName)
        } catch (e: Exception) {
            // Ловим возможные исключения, если компонент не найден
            null
        }
        return appWidgetIds != null && appWidgetIds.isNotEmpty()
    }

    // Открывает конфигурацию для первого найденного виджета
    private fun openFirstWidgetConfiguration() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, ContactsGridWidgetProvider::class.java)
        val appWidgetIds : IntArray? = try {
            appWidgetManager.getAppWidgetIds(componentName)
        } catch (e: Exception) {
            null
        }

        if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
            val firstWidgetId = appWidgetIds[0]
            val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                // Явно указываем компонент Activity конфигурации
                component = ComponentName(this@MainActivity, WidgetConfigureActivity::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, firstWidgetId)
                // Добавляем флаг, чтобы запустить Activity из контекста приложения
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Проверяем, есть ли Activity для обработки Intent'а
            if (configureIntent.resolveActivity(packageManager) != null) {
                try {
                    startActivity(configureIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Не удалось открыть настройки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Activity конфигурации не найдена.", Toast.LENGTH_SHORT).show()
            }

        } else {
            // На всякий случай, если виджеты пропали между проверкой и нажатием
            Toast.makeText(this, "Активные виджеты не найдены.", Toast.LENGTH_SHORT).show()
            // Обновляем UI
            binding.textViewInfo.text = "Добавьте виджет 'Contacts Grid' на главный экран."
            binding.buttonConfigure.visibility = View.GONE
        }
    }
}