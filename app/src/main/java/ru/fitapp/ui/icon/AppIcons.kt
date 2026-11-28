package ru.fitapp.ui.icon

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.fitapp.R
import ru.fitapp.core.LogTags

/**
 * Фон иконки: сплошной цвет или градиент со свечением как на сплэше.
 * Цвет хранится в ARGB-виде (Long), чтобы перечисление не тянуло Compose в домен.
 */
sealed interface IconBackground {
    data class Solid(val argb: Long) : IconBackground

    /** Градиент `#0F543B → #0D503A` с лаймовым и персиковым пятнами (`ic_splash_bg.xml`). */
    data object Glow : IconBackground
}

/**
 * Шесть иконок приложения (§9): те же варианты, что лежат в `icons/v6-*`.
 *
 * Сама иконка ярлыка в Android выбирается манифестом, а не кодом, поэтому каждая
 * иконка — отдельный `activity-alias` в AndroidManifest.xml: включён всегда ровно
 * один, остальные выключены. [alias] — имя этого алиаса.
 *
 * Знак у всех один — диафрагма со сплэша (`ic_launcher_foreground*.xml` генерируются
 * `icons/build_icons_v6.py` из `ic_splash_logo.xml`), отличается только фон.
 */
enum class AppIcon(
    val id: Int,
    val alias: String,
    @StringRes val title: Int,
    @DrawableRes val foreground: Int,
    val background: IconBackground,
) {
    SPLASH(
        id = 0,
        alias = "ru.fitapp.IconSplash",
        title = R.string.settings_icon_splash,
        foreground = R.drawable.ic_launcher_foreground,
        background = IconBackground.Solid(0xFF0F543B),
    ),

    GLOW(
        id = 1,
        alias = "ru.fitapp.IconGlow",
        title = R.string.settings_icon_glow,
        foreground = R.drawable.ic_launcher_foreground_glow,
        background = IconBackground.Glow,
    ),

    CREAM(
        id = 2,
        alias = "ru.fitapp.IconCream",
        title = R.string.settings_icon_cream,
        foreground = R.drawable.ic_launcher_foreground_cream,
        background = IconBackground.Solid(0xFFF4F1E8),
    ),

    LIME(
        id = 3,
        alias = "ru.fitapp.IconLime",
        title = R.string.settings_icon_lime,
        foreground = R.drawable.ic_launcher_foreground_lime,
        background = IconBackground.Solid(0xFF0E6B4F),
    ),

    NIGHT(
        id = 4,
        alias = "ru.fitapp.IconNight",
        title = R.string.settings_icon_night,
        foreground = R.drawable.ic_launcher_foreground_night,
        background = IconBackground.Solid(0xFF101613),
    ),

    RING(
        id = 5,
        alias = "ru.fitapp.IconRing",
        title = R.string.settings_icon_ring,
        foreground = R.drawable.ic_launcher_foreground_ring,
        background = IconBackground.Solid(0xFF0F543B),
    ),
    ;

    companion object {
        /** Значение из настроек → иконка; неизвестный id считается умолчанием. */
        fun byId(id: Int): AppIcon = entries.firstOrNull { it.id == id } ?: SPLASH

        /** Иконка ярлыка, включённая прямо сейчас (имя activity-alias). */
        fun enabledAlias(context: Context): String? {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(context.packageName)
            val resolved = runCatching {
                context.packageManager.queryIntentActivities(intent, 0)
            }.onFailure {
                Log.e(LogTags.UI, "icon: не удалось узнать текущий алиас", it)
            }.getOrNull().orEmpty()
            return resolved.firstOrNull()?.activityInfo?.name
        }

        /**
         * Переключает иконку ярлыка.
         *
         * Порядок важен: сначала включаем новый алиас, только потом гасим старый —
         * иначе между двумя вызовами на экране не остаётся ни одного ярлыка.
         * `DONT_KILL_APP` не даёт системе выгрузить процесс: приложение продолжает
         * работать, лончер просто перерисовывает ярлык.
         */
        fun apply(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            runCatching {
                enable(pm, context, icon.alias, true)
                entries.filter { it != icon }.forEach { enable(pm, context, it.alias, false) }
                Log.i(LogTags.UI, "icon: выбран ярлык ${icon.alias}")
            }.onFailure {
                Log.e(LogTags.UI, "icon: не удалось переключить ярлык на ${icon.alias}", it)
            }
        }

        /**
         * Приводит ярлык к сохранённому выбору при старте: после обновления приложения
         * манифест снова включает алиас по умолчанию, а выбранный пользователем — нет.
         */
        fun reconcile(context: Context, storedId: Int) {
            val wanted = byId(storedId)
            if (enabledAlias(context) == wanted.alias) return
            Log.i(LogTags.UI, "icon: восстанавливаю выбор после обновления ${wanted.alias}")
            apply(context, wanted)
        }

        private fun enable(pm: PackageManager, context: Context, alias: String, enabled: Boolean) {
            val component = ComponentName(context.packageName, alias)
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }
    }
}