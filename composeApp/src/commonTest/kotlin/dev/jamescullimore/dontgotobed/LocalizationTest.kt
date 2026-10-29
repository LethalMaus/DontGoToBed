package dev.jamescullimore.dontgotobed

import kotlin.test.*

class LocalizationTest {
    @Test fun regionalLocalesResolveAndUnknownFallsBack() {
        assertEquals(AppLanguage.Spanish, resolveLanguage(AppLanguage.System, "es-MX"))
        assertEquals(AppLanguage.Portuguese, resolveLanguage(AppLanguage.System, "pt_BR"))
        assertEquals(AppLanguage.English, resolveLanguage(AppLanguage.System, "ja-JP"))
        assertEquals(AppLanguage.Italian, resolveLanguage(AppLanguage.Italian, "ru"))
    }
    @Test fun parametersRemainLiteralAndCanBeReordered() {
        assertEquals("Выбрано: {1}", localized(AppLanguage.Russian, "Selected: {0}", "{1}"))
        assertEquals("Salud: 3 de 5 corazones", localized(AppLanguage.Spanish, "Health: {0} of 5 hearts", 3))
        assertEquals("Unknown text", localized(AppLanguage.French, "Unknown text"))
    }
    @Test fun allTranslationsRetainEveryPlaceholder() {
        val parameter = Regex("\\{\\d+\\}")
        translations.forEach { (source, variants) ->
            assertEquals(6, variants.size, source)
            variants.forEach { value ->
                assertTrue(value.isNotBlank(), source)
                assertEquals(parameter.findAll(source).map { it.value }.sorted().toList(),
                    parameter.findAll(value).map { it.value }.sorted().toList(), source)
            }
        }
    }
    @Test fun daddyNameDoesNotChangeSavedOrNetworkCharacterIds() {
        val before = AppLocale.language
        try {
            AppLocale.language = AppLanguage.English
            assertEquals("Daddy", Player.Papa.displayName)
            assertEquals("Papa", Player.Papa.name)
            AppLocale.language = AppLanguage.Spanish
            assertEquals("Papá", Player.Papa.displayName)
            assertEquals(Player.Papa, Player.valueOf("Papa"))
        } finally { AppLocale.language = before }
    }
}
