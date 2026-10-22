package zed.rainxch.details.presentation.utils

import zed.rainxch.core.domain.model.settings.TranslationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoTranslateTest {
    @Test
    fun the_explicit_target_wins_over_the_app_and_device_languages() {
        assertEquals(
            "fr",
            autoTranslateTargetLanguage(
                explicitTarget = "fr",
                appLanguage = "de",
                deviceLanguage = "en",
            ),
        )
    }

    @Test
    fun a_blank_setting_falls_through_to_the_next_language() {
        assertEquals(
            "de",
            autoTranslateTargetLanguage(
                explicitTarget = " ",
                appLanguage = "de",
                deviceLanguage = "en",
            ),
        )
        assertEquals(
            "en",
            autoTranslateTargetLanguage(
                explicitTarget = null,
                appLanguage = "",
                deviceLanguage = "en",
            ),
        )
        assertNull(
            autoTranslateTargetLanguage(
                explicitTarget = null,
                appLanguage = null,
                deviceLanguage = "",
            ),
        )
    }

    @Test
    fun only_content_that_changes_language_is_translated() {
        assertTrue(shouldAutoTranslate("hello", "de", "en", alreadyTranslated = false))
        assertFalse(shouldAutoTranslate("hello", "en", "en", alreadyTranslated = false))
        assertFalse(shouldAutoTranslate("hello", "EN", "en", alreadyTranslated = false))
        assertFalse(shouldAutoTranslate(" ", "de", "en", alreadyTranslated = false))
        assertFalse(shouldAutoTranslate(null, "de", "en", alreadyTranslated = false))
        assertFalse(shouldAutoTranslate("hello", " ", "en", alreadyTranslated = false))
        assertFalse(shouldAutoTranslate("hello", "de", "en", alreadyTranslated = true))
        assertTrue(shouldAutoTranslate("hello", "de", null, alreadyTranslated = false))
    }

    @Test
    fun key_based_providers_need_their_credentials() {
        assertTrue(isTranslationProviderConfigured(TranslationProvider.GOOGLE, null, null, null, null))
        assertTrue(isTranslationProviderConfigured(TranslationProvider.LIBRE_TRANSLATE, null, null, null, null))
        assertFalse(isTranslationProviderConfigured(TranslationProvider.YOUDAO, "key", " ", null, null))
        assertTrue(isTranslationProviderConfigured(TranslationProvider.YOUDAO, "key", "secret", null, null))
        assertFalse(isTranslationProviderConfigured(TranslationProvider.DEEPL, null, null, "", null))
        assertTrue(isTranslationProviderConfigured(TranslationProvider.DEEPL, null, null, "key", null))
        assertFalse(isTranslationProviderConfigured(TranslationProvider.MICROSOFT, null, null, null, ""))
        assertTrue(isTranslationProviderConfigured(TranslationProvider.MICROSOFT, null, null, null, "key"))
    }
}
