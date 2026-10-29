package dev.jamescullimore.dontgotobed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Stable preference IDs; network identifiers and resource filenames are never translated. */
internal enum class AppLanguage(val id: Int, val code: String, val nativeName: String) {
    System(0, "", ""), English(1, "en", "English"), Spanish(2, "es", "Español"),
    Italian(3, "it", "Italiano"), French(4, "fr", "Français"),
    Portuguese(5, "pt", "Português"), Russian(6, "ru", "Русский"), German(7, "de", "Deutsch")
}
internal object AppLocale {
    var language by mutableStateOf(AppLanguage.System)
    var systemCode by mutableStateOf("en")
    val effective: AppLanguage get() = resolveLanguage(language, systemCode)
}
internal fun resolveLanguage(selected: AppLanguage, systemCode: String): AppLanguage =
    if (selected != AppLanguage.System) selected else AppLanguage.entries.firstOrNull {
        it != AppLanguage.System && it.code == systemCode.substringBefore('-').substringBefore('_').lowercase()
    } ?: AppLanguage.English

/** Numbered placeholders allow each language to reorder values without translating protocol data. */
internal fun localized(language: AppLanguage, source: String, vararg values: Any?): String {
    val template = if (language.id < 2) source else translations[source]?.getOrNull(language.id - 2) ?: source
    return Regex("\\{(\\d+)\\}").replace(template) { match ->
        values.getOrNull(match.groupValues[1].toInt())?.toString() ?: match.value
    }
}
internal fun tr(source: String, vararg values: Any?): String = localized(AppLocale.effective, source, *values)
internal expect fun systemLanguageCode(): String
internal fun SettingsStore.loadLanguage(): AppLanguage = AppLanguage.entries.firstOrNull { it.id == readInt("language", 0) } ?: AppLanguage.System
