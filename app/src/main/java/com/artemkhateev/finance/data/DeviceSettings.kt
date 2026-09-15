package com.artemkhateev.finance.data

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Настройки интерфейса на этом устройстве, например порядок категорий: переживают перезапуск и в облако не уходят. */
class DeviceSettings(private val prefs: SharedPreferences) {

    private val values = mutableMapOf<String, MutableStateFlow<String?>>()

    /** Значение по ключу и его изменения; null — ещё не задано. */
    @Synchronized
    fun string(key: String): StateFlow<String?> = valueOf(key).asStateFlow()

    @Synchronized
    fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
        valueOf(key).value = value
    }

    private fun valueOf(key: String) = values.getOrPut(key) { MutableStateFlow(prefs.getString(key, null)) }
}
