package com.example.snakegame.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SettingsState> =
        repository.settingsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = SettingsState.DEFAULT,
        )

    fun updateMasterVolume(value: Float) =
        update { copy(masterVolume = value) }

    fun updateMusicVolume(value: Float) =
        update { copy(musicVolume = value) }

    fun updateSfxVolume(value: Float) =
        update { copy(sfxVolume = value) }

    fun updateNormalSpeed(value: Float) =
        update { copy(normalSpeed = value) }

    fun updateBoostMultiplier(value: Float) =
        update { copy(boostMultiplier = value) }

    fun updateVibration(level: VibrationLevel) =
        update { copy(vibrationLevel = level) }

    fun resetToDefault() =
        update { SettingsState.DEFAULT }

    private fun update(transform: SettingsState.() -> SettingsState) {
        viewModelScope.launch {
            repository.update(transform)
        }
    }

    companion object {
        fun factory(repository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository) as T
                }
            }
    }
}

