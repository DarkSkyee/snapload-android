package com.example.mediadownloader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _languageFlow = MutableStateFlow(getLanguageInternal())
    val languageFlow: StateFlow<String> = _languageFlow.asStateFlow()

    private val _autoUpdateEngineFlow = MutableStateFlow(getAutoUpdateEngineInternal())
    val autoUpdateEngineFlow: StateFlow<Boolean> = _autoUpdateEngineFlow.asStateFlow()

    private val _autoUpdateAppFlow = MutableStateFlow(getAutoUpdateAppInternal())
    val autoUpdateAppFlow: StateFlow<Boolean> = _autoUpdateAppFlow.asStateFlow()

    var selectedLanguage: String
        get() = getLanguageInternal()
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
            _languageFlow.value = value
        }

    var autoUpdateEngine: Boolean
        get() = getAutoUpdateEngineInternal()
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_UPDATE_ENGINE, value).apply()
            _autoUpdateEngineFlow.value = value
        }

    var autoUpdateApp: Boolean
        get() = getAutoUpdateAppInternal()
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_UPDATE_APP, value).apply()
            _autoUpdateAppFlow.value = value
        }

    var lastEngineUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_ENGINE_CHECK, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_ENGINE_CHECK, value).apply()
        }

    var lastAppUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_APP_CHECK, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_APP_CHECK, value).apply()
        }

    private fun getLanguageInternal(): String {
        return prefs.getString(KEY_LANGUAGE, null) ?: "en"
    }

    private fun getAutoUpdateEngineInternal(): Boolean {
        return prefs.getBoolean(KEY_AUTO_UPDATE_ENGINE, true)
    }

    private fun getAutoUpdateAppInternal(): Boolean {
        return prefs.getBoolean(KEY_AUTO_UPDATE_APP, true)
    }

    companion object {
        private const val PREFS_NAME = "snapload_preferences"
        private const val KEY_LANGUAGE = "key_language"
        private const val KEY_AUTO_UPDATE_ENGINE = "key_auto_update_engine"
        private const val KEY_AUTO_UPDATE_APP = "key_auto_update_app"
        private const val KEY_LAST_ENGINE_CHECK = "key_last_engine_check"
        private const val KEY_LAST_APP_CHECK = "key_last_app_check"
    }
}
