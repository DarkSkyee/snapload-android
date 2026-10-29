package com.example.mediadownloader.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    fun createLocalizedContext(baseContext: Context, languageCode: String): Context {
        val targetLocale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(targetLocale)

        val config = Configuration(baseContext.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = targetLocale
        }

        return baseContext.createConfigurationContext(config)
    }

    fun getLocalizedConfiguration(baseConfiguration: Configuration, languageCode: String): Configuration {
        val targetLocale = Locale.forLanguageTag(languageCode)
        val config = Configuration(baseConfiguration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = targetLocale
        }
        return config
    }

    fun applyLanguage(context: Context, languageCode: String): Context {
        val targetLocale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(targetLocale)

        val res = context.resources
        val config = Configuration(res.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = targetLocale
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
                localeManager?.applicationLocales = LocaleList.forLanguageTags(languageCode)
            } catch (_: Exception) {}
        }

        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)

        return context.createConfigurationContext(config)
    }

    fun updateConfigurationLocale(config: Configuration, languageCode: String) {
        val targetLocale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(targetLocale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(targetLocale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = targetLocale
        }
    }
}
