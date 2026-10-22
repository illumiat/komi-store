package zed.rainxch.details.presentation.utils

import zed.rainxch.core.domain.model.settings.TranslationProvider

// An unset target falls back from the user's explicit pick, to the app language, to the device
// language; blank values never win.
internal fun autoTranslateTargetLanguage(
    explicitTarget: String?,
    appLanguage: String?,
    deviceLanguage: String?,
): String? =
    sequenceOf(explicitTarget, appLanguage, deviceLanguage)
        .mapNotNull { it?.trim() }
        .firstOrNull { it.isNotEmpty() }

internal fun shouldAutoTranslate(
    text: String?,
    targetLanguage: String?,
    sourceLanguage: String?,
    alreadyTranslated: Boolean,
): Boolean {
    val target = targetLanguage?.trim().orEmpty()
    val source = sourceLanguage?.trim().orEmpty()
    val changesLanguage =
        target.isNotEmpty() && (source.isEmpty() || !source.equals(target, ignoreCase = true))
    return !alreadyTranslated && !text.isNullOrBlank() && changesLanguage
}

// Providers that need their own credentials cannot translate until they have them; the request
// would only fail, and the feature degrades to showing the original text.
internal fun isTranslationProviderConfigured(
    provider: TranslationProvider,
    youdaoAppKey: String?,
    youdaoAppSecret: String?,
    deeplAuthKey: String?,
    microsoftTranslatorKey: String?,
): Boolean =
    when (provider) {
        TranslationProvider.GOOGLE, TranslationProvider.LIBRE_TRANSLATE -> {
            true
        }

        TranslationProvider.YOUDAO -> {
            !youdaoAppKey.isNullOrBlank() && !youdaoAppSecret.isNullOrBlank()
        }

        TranslationProvider.DEEPL -> {
            !deeplAuthKey.isNullOrBlank()
        }

        TranslationProvider.MICROSOFT -> {
            !microsoftTranslatorKey.isNullOrBlank()
        }
    }
