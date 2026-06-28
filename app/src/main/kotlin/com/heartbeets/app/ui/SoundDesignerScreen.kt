package com.heartbeets.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.core.content.ContextCompat
import com.heartbeets.audio.AffirmationMode
import com.heartbeets.audio.AffirmationSet
import com.heartbeets.audio.BinauralPreset
import com.heartbeets.audio.NoiseType
import com.heartbeets.audio.SolfeggioFrequency
import com.heartbeets.audio.SynthParams

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoundDesignerScreen(
    viewModel: SoundDesignerViewModel,
    onBack: () -> Unit,
    onSaved: (packId: String) -> Unit = {},
) {
    val params by viewModel.params.collectAsState()
    val name by viewModel.name.collectAsState()
    val description by viewModel.description.collectAsState()
    val noiseType by viewModel.noiseType.collectAsState()
    val noiseVolume by viewModel.noiseVolume.collectAsState()
    val binauralPreset by viewModel.binauralPreset.collectAsState()
    val binauralCarrierHz by viewModel.binauralCarrierHz.collectAsState()
    val binauralBeatHz by viewModel.binauralBeatHz.collectAsState()
    val binauralVolume by viewModel.binauralVolume.collectAsState()
    val solfeggioFrequency by viewModel.solfeggioFrequency.collectAsState()
    val solfeggioVolume by viewModel.solfeggioVolume.collectAsState()
    val affirmationSet by viewModel.affirmationSet.collectAsState()
    val affirmationCustomTexts by viewModel.affirmationCustomTexts.collectAsState()
    val affirmationIntervalSec by viewModel.affirmationIntervalSec.collectAsState()
    val affirmationVolume by viewModel.affirmationVolume.collectAsState()
    val affirmationSpeechRate by viewModel.affirmationSpeechRate.collectAsState()
    val affirmationPitch by viewModel.affirmationPitch.collectAsState()
    val affirmationVoiceName by viewModel.affirmationVoiceName.collectAsState()
    val affirmationMode by viewModel.affirmationMode.collectAsState()
    val affirmationRecordings by viewModel.affirmationRecordings.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()
    var customTextInput by remember { mutableStateOf("") }
    var voiceDropdownExpanded by remember { mutableStateOf(false) }

    // Permission handling for RECORD_AUDIO
    val context = LocalContext.current
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted) viewModel.startRecording()
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                            onClick = { viewModel.save { packId -> onSaved(packId); onBack() } },
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

            HorizontalDivider()

            // --- Background Noise ---
            SectionHeader("Background Noise")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoiseType.entries.forEach { type ->
                    FilterChip(
                        selected = noiseType == type,
                        onClick = { viewModel.updateNoiseType(type) },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            if (noiseType != NoiseType.NONE) {
                ParamSlider("Volume", noiseVolume, 0f, 1f) {
                    viewModel.updateNoiseVolume(it)
                }
            }

            HorizontalDivider()

            // --- Binaural Beats ---
            SectionHeader("Binaural Beats")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BinauralPreset.entries.filter { it != BinauralPreset.CUSTOM }.forEach { preset ->
                    FilterChip(
                        selected = binauralPreset == preset ||
                            (preset == BinauralPreset.NONE && binauralPreset == BinauralPreset.NONE),
                        onClick = { viewModel.updateBinauralPreset(preset) },
                        label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            if (binauralPreset != BinauralPreset.NONE) {
                ParamSlider("Carrier (Hz)", binauralCarrierHz, 80f, 500f) {
                    viewModel.updateBinauralCarrierHz(it)
                }
                ParamSlider("Beat (Hz)", binauralBeatHz, 0.5f, 40f) {
                    viewModel.updateBinauralBeatHz(it)
                }
                ParamSlider("Volume", binauralVolume, 0f, 1f) {
                    viewModel.updateBinauralVolume(it)
                }
                if (binauralPreset == BinauralPreset.CUSTOM) {
                    Text(
                        "Custom — adjust carrier & beat freely",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            // --- Solfeggio Tone ---
            SectionHeader("Solfeggio Frequency")
            Text(
                "A continuous pure tone layered under the heartbeat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            // Show None chip + all frequencies in a scrollable flow
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SolfeggioFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = solfeggioFrequency == freq,
                        onClick = { viewModel.updateSolfeggioFrequency(freq) },
                        label = { Text(if (freq == SolfeggioFrequency.NONE) "None" else freq.label) },
                    )
                }
            }
            if (solfeggioFrequency != SolfeggioFrequency.NONE) {
                Text(
                    solfeggioFrequency.purpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                ParamSlider("Volume", solfeggioVolume, 0f, 1f) {
                    viewModel.updateSolfeggioVolume(it)
                }
            }

            HorizontalDivider()

            // --- Affirmations ---
            SectionHeader("Affirmations")
            Text(
                "Positive messages at regular intervals \u2014 via text-to-speech or your own recorded voice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            // Mode selector
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = affirmationMode == AffirmationMode.NONE,
                    onClick = { viewModel.updateAffirmationMode(AffirmationMode.NONE) },
                    label = { Text("Off") },
                )
                FilterChip(
                    selected = affirmationMode == AffirmationMode.TTS,
                    onClick = { viewModel.updateAffirmationMode(AffirmationMode.TTS) },
                    label = { Text("Text-to-Speech") },
                )
                FilterChip(
                    selected = affirmationMode == AffirmationMode.RECORDED,
                    onClick = { viewModel.updateAffirmationMode(AffirmationMode.RECORDED) },
                    label = { Text("Record Voice") },
                )
            }

            if (affirmationMode == AffirmationMode.TTS) {
                // TTS affirmation set chooser
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AffirmationSet.entries.filter { it != AffirmationSet.NONE }.forEach { set ->
                        FilterChip(
                            selected = affirmationSet == set,
                            onClick = { viewModel.updateAffirmationSet(set) },
                            label = { Text(set.label) },
                        )
                    }
                }
                if (affirmationSet == AffirmationSet.CUSTOM) {
                    OutlinedTextField(
                        value = customTextInput,
                        onValueChange = { customTextInput = it },
                        label = { Text("Add affirmation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (customTextInput.isNotBlank()) {
                                IconButton(onClick = {
                                    viewModel.updateAffirmationCustomTexts(
                                        affirmationCustomTexts + customTextInput.trim()
                                    )
                                    customTextInput = ""
                                }) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        },
                    )
                    affirmationCustomTexts.forEachIndexed { index, text ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = {
                                viewModel.updateAffirmationCustomTexts(
                                    affirmationCustomTexts.toMutableList().also { it.removeAt(index) }
                                )
                            }) {
                                Text("\u2715", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (affirmationSet != AffirmationSet.NONE) {
                    Text(
                        affirmationSet.affirmations.take(3).joinToString(" \u2022 ") + " \u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                // TTS settings
                ParamSliderInt("Interval (sec)", affirmationIntervalSec, 10, 120) {
                    viewModel.updateAffirmationIntervalSec(it)
                }
                ParamSlider("Volume", affirmationVolume, 0f, 1f) {
                    viewModel.updateAffirmationVolume(it)
                }
                ParamSlider("Speech Rate", affirmationSpeechRate, 0.5f, 1.5f) {
                    viewModel.updateAffirmationSpeechRate(it)
                }
                ParamSlider("Pitch", affirmationPitch, 0.5f, 1.5f) {
                    viewModel.updateAffirmationPitch(it)
                }
                // Voice picker
                if (availableVoices.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Voice", style = MaterialTheme.typography.bodySmall)
                    Box {
                        val selectedLabel = availableVoices.firstOrNull { it.first == affirmationVoiceName }?.second ?: "Default"
                        OutlinedButton(
                            onClick = { voiceDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(selectedLabel, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = voiceDropdownExpanded,
                            onDismissRequest = { voiceDropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default") },
                                onClick = {
                                    viewModel.updateAffirmationVoiceName(null)
                                    voiceDropdownExpanded = false
                                },
                            )
                            availableVoices.forEach { (name, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.updateAffirmationVoiceName(name)
                                        voiceDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { viewModel.previewAffirmation() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Preview Voice")
                }
            }

            if (affirmationMode == AffirmationMode.RECORDED) {
                // Recorded voice messages
                Spacer(Modifier.height(8.dp))
                Text(
                    "Record affirmation messages using your own voice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                // List existing recordings
                affirmationRecordings.forEachIndexed { index, _ ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Message ${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.previewRecording(index) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                        }
                        IconButton(onClick = { viewModel.deleteRecording(index) }) {
                            Text("\u2715", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Record button
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (isRecording) {
                            viewModel.stopRecording()
                        } else if (hasRecordPermission) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    colors = if (isRecording) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ) else ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRecording) "\u23F9  Stop Recording" else "\u23FA  Record Message")
                }

                // Shared settings
                ParamSliderInt("Interval (sec)", affirmationIntervalSec, 10, 120) {
                    viewModel.updateAffirmationIntervalSec(it)
                }
                ParamSlider("Volume", affirmationVolume, 0f, 1f) {
                    viewModel.updateAffirmationVolume(it)
                }
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
