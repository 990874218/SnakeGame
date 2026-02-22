package com.example.snakegame.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onPreviewSfx: (() -> Unit)? = null,
) {
      

    val state by viewModel.state.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        SettingsCard(title = "音频") {
            SettingSlider(
                label = "主音量",
                value = state.masterVolume,
                valueRange = SettingsState.VOLUME_RANGE,
                onValueChange = viewModel::updateMasterVolume,
            )
            Spacer(Modifier.height(12.dp))
            SettingSlider(
                label = "背景音乐",
                value = state.musicVolume,
                valueRange = SettingsState.VOLUME_RANGE,
                onValueChange = viewModel::updateMusicVolume,
            )
            Spacer(Modifier.height(12.dp))
            SettingSlider(
                label = "音效音量",
                value = state.sfxVolume,
                valueRange = SettingsState.VOLUME_RANGE,
                onValueChange = {
                    viewModel.updateSfxVolume(it)
                    onPreviewSfx?.invoke()
                },
            )
            onPreviewSfx?.let {
                Spacer(Modifier.height(8.dp))
                Button(onClick = it) {
                    Text("播放音效预览")
                }
            }
        }

        SettingsCard(title = "游戏参数") {
            SettingSlider(
                label = "蛇速度",
                value = state.normalSpeed,
                valueRange = SettingsState.NORMAL_SPEED_RANGE,
                formatter = { String.format("%.1fx", it) },
                onValueChange = viewModel::updateNormalSpeed,
            )
            Spacer(Modifier.height(12.dp))
            SettingSlider(
                label = "加速倍数",
                value = state.boostMultiplier,
                valueRange = SettingsState.BOOST_RANGE,
                formatter = { String.format("%.1fx", it) },
                onValueChange = viewModel::updateBoostMultiplier,
            )
        }

        SettingsCard(title = "震动强度") {
            VibrationSelector(
                current = state.vibrationLevel,
                onSelect = viewModel::updateVibration,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Button(onClick = viewModel::resetToDefault) {
                Text("恢复默认")
            }
            Button(onClick = onBack) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formatter: (Float) -> String = { String.format("%.0f%%", it * 100) },
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text("$label：${formatter(value)}", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Composable
private fun VibrationSelector(
    current: VibrationLevel,
    onSelect: (VibrationLevel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VibrationLevel.entries.forEach { level ->
            Button(
                onClick = { onSelect(level) },
                enabled = current != level,
            ) {
                val label =
                    when (level) {
                        VibrationLevel.LOW -> "弱"
                        VibrationLevel.MEDIUM -> "中"
                        VibrationLevel.HIGH -> "强"
                    }
                Text(label)
            }
        }
    }
}