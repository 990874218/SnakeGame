package com.example.snakegame.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val settingsFlow: Flow<SettingsState> =
        dataStore.data.map { prefs ->
            prefs.toSettingsState().coerce()
        }

    suspend fun update(transform: SettingsState.() -> SettingsState) {
        dataStore.edit { prefs ->
            val current = prefs.toSettingsState()
            val newState = transform(current).coerce()
            prefs[MASTER_VOLUME] = newState.masterVolume
            prefs[MUSIC_VOLUME] = newState.musicVolume
            prefs[SFX_VOLUME] = newState.sfxVolume
            prefs[NORMAL_SPEED] = newState.normalSpeed
            prefs[BOOST_MULTIPLIER] = newState.boostMultiplier
            prefs[VIBRATION_LEVEL] = newState.vibrationLevel.ordinal
        }
    }

    private fun Preferences.toSettingsState(): SettingsState =
        SettingsState(
            masterVolume =
                this[MASTER_VOLUME] ?: SettingsState.DEFAULT.masterVolume,
            musicVolume =
                this[MUSIC_VOLUME] ?: SettingsState.DEFAULT.musicVolume,
            sfxVolume =
                this[SFX_VOLUME] ?: SettingsState.DEFAULT.sfxVolume,
            normalSpeed =
                this[NORMAL_SPEED] ?: SettingsState.DEFAULT.normalSpeed,
            boostMultiplier =
                this[BOOST_MULTIPLIER] ?: SettingsState.DEFAULT.boostMultiplier,
            vibrationLevel =
                this[VIBRATION_LEVEL]?.toVibrationLevel() ?: SettingsState.DEFAULT.vibrationLevel,
        )

    private fun Int.toVibrationLevel(): VibrationLevel =
        VibrationLevel.entries.getOrElse(this) { SettingsState.DEFAULT.vibrationLevel }

    private companion object Keys {
        val MASTER_VOLUME = floatPreferencesKey("master_volume")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val NORMAL_SPEED = floatPreferencesKey("normal_speed")
        val BOOST_MULTIPLIER = floatPreferencesKey("boost_multiplier")
        val VIBRATION_LEVEL = intPreferencesKey("vibration_level")
    }
}

