package com.example.snakegame.settings

data class SettingsState(
    val masterVolume: Float = DEFAULT_MASTER_VOLUME,
    val musicVolume: Float = DEFAULT_MUSIC_VOLUME,
    val sfxVolume: Float = DEFAULT_SFX_VOLUME,
    val normalSpeed: Float = DEFAULT_NORMAL_SPEED,
    val boostMultiplier: Float = DEFAULT_BOOST_MULTIPLIER,
    val vibrationLevel: VibrationLevel = VibrationLevel.MEDIUM,
) {

    fun coerce(): SettingsState =
        copy(
            masterVolume = masterVolume.coerceIn(VOLUME_RANGE),
            musicVolume = musicVolume.coerceIn(VOLUME_RANGE),
            sfxVolume = sfxVolume.coerceIn(VOLUME_RANGE),
            normalSpeed = normalSpeed.coerceIn(NORMAL_SPEED_RANGE),
            boostMultiplier = boostMultiplier.coerceIn(BOOST_RANGE),
        )

    companion object {
        private const val DEFAULT_MASTER_VOLUME = 0.8f
        private const val DEFAULT_MUSIC_VOLUME = 0.7f
        private const val DEFAULT_SFX_VOLUME = 0.7f
        private const val DEFAULT_NORMAL_SPEED = 1.0f
        private const val DEFAULT_BOOST_MULTIPLIER = 1.5f

        val VOLUME_RANGE: ClosedFloatingPointRange<Float> = 0f..1f
        val NORMAL_SPEED_RANGE: ClosedFloatingPointRange<Float> = 0.5f..2.0f
        val BOOST_RANGE: ClosedFloatingPointRange<Float> = 1.0f..3.0f

        val DEFAULT = SettingsState()
    }
}

enum class VibrationLevel {
    LOW,
    MEDIUM,
    HIGH,
}