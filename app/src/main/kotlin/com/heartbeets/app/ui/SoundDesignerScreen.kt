package com.heartbeets.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heartbeets.audio.SynthParams

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundDesignerScreen(
    viewModel: SoundDesignerViewModel,
    onBack: () -> Unit,
) {
    val params by viewModel.params.collectAsState()
    val name by viewModel.name.collectAsState()
    val description by viewModel.description.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sound Designer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.preview() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Preview")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancel") }
                        Button(
                            onClick = { viewModel.save { onBack() } },
                            modifier = Modifier.weight(1f),
                        ) { Text("Save") }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description") },
                placeholder = { Text("User-created heartbeat sound.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Presets
            Text("Presets", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { viewModel.updateParams(SynthParams.CLASSIC) }, label = { Text("Classic") })
                AssistChip(onClick = { viewModel.updateParams(SynthParams.SOFT) }, label = { Text("Soft") })
                AssistChip(onClick = { viewModel.updateParams(SynthParams.MECHANICAL) }, label = { Text("Mechanical") })
            }

            HorizontalDivider()

            // --- S1 (Lub) ---
            SectionHeader("S1 — Lub")
            ParamSlider("Frequency", params.lubFrequency, 30f, 120f) {
                viewModel.updateParams(params.copy(lubFrequency = it))
            }
            ParamSlider("Amplitude", params.lubAmplitude, 0f, 1f) {
                viewModel.updateParams(params.copy(lubAmplitude = it))
            }
            ParamSliderInt("Duration (ms)", params.lubDurationMs, 20, 200) {
                viewModel.updateParams(params.copy(lubDurationMs = it))
            }
            ParamSliderInt("Attack (ms)", params.lubAttackMs, 1, 50) {
                viewModel.updateParams(params.copy(lubAttackMs = it))
            }
            ParamSliderInt("Decay (ms)", params.lubDecayMs, 10, 150) {
                viewModel.updateParams(params.copy(lubDecayMs = it))
            }

            HorizontalDivider()

            // --- S2 (Dub) ---
            SectionHeader("S2 — Dub")
            ParamSlider("Frequency", params.dubFrequency, 50f, 200f) {
                viewModel.updateParams(params.copy(dubFrequency = it))
            }
            ParamSlider("Amplitude", params.dubAmplitude, 0f, 1f) {
                viewModel.updateParams(params.copy(dubAmplitude = it))
            }
            ParamSliderInt("Duration (ms)", params.dubDurationMs, 20, 150) {
                viewModel.updateParams(params.copy(dubDurationMs = it))
            }
            ParamSliderInt("Attack (ms)", params.dubAttackMs, 1, 50) {
                viewModel.updateParams(params.copy(dubAttackMs = it))
            }
            ParamSliderInt("Decay (ms)", params.dubDecayMs, 10, 120) {
                viewModel.updateParams(params.copy(dubDecayMs = it))
            }
            ParamSliderInt("Offset (ms)", params.dubOffsetMs, 50, 300) {
                viewModel.updateParams(params.copy(dubOffsetMs = it))
            }

            HorizontalDivider()

            // --- Body thump ---
            SectionHeader("Body Thump")
            ParamSlider("Frequency", params.bodyFrequency, 15f, 80f) {
                viewModel.updateParams(params.copy(bodyFrequency = it))
            }
            ParamSlider("Amplitude", params.bodyAmplitude, 0f, 1f) {
                viewModel.updateParams(params.copy(bodyAmplitude = it))
            }
            ParamSliderInt("Duration (ms)", params.bodyDurationMs, 30, 200) {
                viewModel.updateParams(params.copy(bodyDurationMs = it))
            }

            HorizontalDivider()

            // --- Mix ---
            SectionHeader("Mix")
            ParamSlider("Low-pass (Hz, 0=off)", params.lowPassHz, 0f, 2000f) {
                viewModel.updateParams(params.copy(lowPassHz = it))
            }
            ParamSlider("Noise", params.noiseAmplitude, 0f, 0.15f) {
                viewModel.updateParams(params.copy(noiseAmplitude = it))
            }
            ParamSlider("Master Gain", params.masterGain, 0.1f, 1.5f) {
                viewModel.updateParams(params.copy(masterGain = it))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("%.1f".format(value), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
        )
    }
}

@Composable
private fun ParamSliderInt(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text("$value", style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
        )
    }
}
