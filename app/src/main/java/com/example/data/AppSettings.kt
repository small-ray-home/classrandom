package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val floatingEnabled: Boolean = false,
    val nonRepeating: Boolean = true,
    val animationEnabled: Boolean = true,
    val resultDurationSeconds: Int = 3,
    val showEnglishName: Boolean = true,
    val buttonSizeDp: Int = 56,
    val buttonOpacity: Float = 0.85f,
    val rememberPosition: Boolean = true,
    val lastPositionXPercent: Float = 0.85f,
    val lastPositionYPercent: Float = 0.5f,
    val bootAutoStart: Boolean = false,
    val breakDvdEnabled: Boolean = false
)

class AppSettingsManager(private val context: Context) {

    private object Keys {
        val FLOATING_ENABLED = booleanPreferencesKey("floating_enabled")
        val NON_REPEATING = booleanPreferencesKey("non_repeating")
        val ANIMATION_ENABLED = booleanPreferencesKey("animation_enabled")
        val RESULT_DURATION_SECONDS = intPreferencesKey("result_duration_seconds")
        val SHOW_ENGLISH_NAME = booleanPreferencesKey("show_english_name")
        val BUTTON_SIZE_DP = intPreferencesKey("button_size_dp")
        val BUTTON_OPACITY = floatPreferencesKey("button_opacity")
        val REMEMBER_POSITION = booleanPreferencesKey("remember_position")
        val LAST_POSITION_X_PERCENT = floatPreferencesKey("last_position_x_percent")
        val LAST_POSITION_Y_PERCENT = floatPreferencesKey("last_position_y_percent")
        val BOOT_AUTO_START = booleanPreferencesKey("boot_auto_start")
        val BREAK_DVD_ENABLED = booleanPreferencesKey("break_dvd_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            floatingEnabled = prefs[Keys.FLOATING_ENABLED] ?: false,
            nonRepeating = prefs[Keys.NON_REPEATING] ?: true,
            animationEnabled = prefs[Keys.ANIMATION_ENABLED] ?: true,
            resultDurationSeconds = prefs[Keys.RESULT_DURATION_SECONDS] ?: 3,
            showEnglishName = prefs[Keys.SHOW_ENGLISH_NAME] ?: true,
            buttonSizeDp = prefs[Keys.BUTTON_SIZE_DP] ?: 56,
            buttonOpacity = prefs[Keys.BUTTON_OPACITY] ?: 0.85f,
            rememberPosition = prefs[Keys.REMEMBER_POSITION] ?: true,
            lastPositionXPercent = prefs[Keys.LAST_POSITION_X_PERCENT] ?: 0.85f,
            lastPositionYPercent = prefs[Keys.LAST_POSITION_Y_PERCENT] ?: 0.5f,
            bootAutoStart = prefs[Keys.BOOT_AUTO_START] ?: false,
            breakDvdEnabled = prefs[Keys.BREAK_DVD_ENABLED] ?: false
        )
    }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun setFloatingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FLOATING_ENABLED] = enabled }
    }

    suspend fun setNonRepeating(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NON_REPEATING] = enabled }
    }

    suspend fun setAnimationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANIMATION_ENABLED] = enabled }
    }

    suspend fun setResultDurationSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.RESULT_DURATION_SECONDS] = seconds.coerceIn(1, 10) }
    }

    suspend fun setShowEnglishName(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ENGLISH_NAME] = show }
    }

    suspend fun setButtonSizeDp(sizeDp: Int) {
        context.dataStore.edit { it[Keys.BUTTON_SIZE_DP] = sizeDp.coerceIn(44, 80) }
    }

    suspend fun setButtonOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.BUTTON_OPACITY] = opacity.coerceIn(0.3f, 1.0f) }
    }

    suspend fun setRememberPosition(remember: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_POSITION] = remember }
    }

    suspend fun savePosition(xPercent: Float, yPercent: Float) {
        context.dataStore.edit {
            it[Keys.LAST_POSITION_X_PERCENT] = xPercent.coerceIn(0f, 1f)
            it[Keys.LAST_POSITION_Y_PERCENT] = yPercent.coerceIn(0f, 1f)
        }
    }

    suspend fun setBootAutoStart(autoStart: Boolean) {
        context.dataStore.edit { it[Keys.BOOT_AUTO_START] = autoStart }
    }

    suspend fun setBreakDvdEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BREAK_DVD_ENABLED] = enabled }
    }
}
