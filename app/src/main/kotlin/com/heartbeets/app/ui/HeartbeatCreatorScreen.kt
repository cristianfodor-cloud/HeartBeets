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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.heartbeets.audio.BinauralPreset
import com.heartbeets.audio.NoiseType
import com.heartbeets.audio.SolfeggioFrequency
import com.heartbeets.audio.SynthParams

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HeartbeatCreatorScreen(
    viewModel: HeartbeatCreatorViewModel,
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit = {},
) {
    val name by viewModel.name.collectAsState()
    val synthParams by viewModel.synthParams.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val noiseType by viewModel.noiseType.collectAsState()
    val noiseVolume by viewModel.noiseVolume.collectAsState()
    val binauralPreset by viewModel.binauralPreset.collectAsState()
    val binauralCarrierHz by viewModel.binauralCarrierHz.collectAsState()
    val binauralBeatHz by viewModel.binauralBeatHz.collectAsState()
    val binauralVolume by viewModel.binauralVolume.collectAsState()
    val solfeggioFrequency by viewModel.solfeggioFrequency.collectAsState()
    val solfeggioVolume by viewModel.solfeggioVolume.collectAsState()
    val voiceRecordings by viewModel.voiceRecordings.collectAsState()
    val voiceVolume by viewModel.voiceVolume.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val previewingFull by viewModel.previewingFull.collectAsState()

    // Permission
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
                title = { Text("Heartbeat Creator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {},
            )
        },
    ) { padding ->
        // Track which sections are expanded
        var soundExpanded by remember { mutableStateOf(false) }
        var profileExpanded by remember { mutableStateOf(false) }
        var backgroundExpanded by remember { mutableStateOf(false) }
        var messageExpanded by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // ===== 1. Create Heartbeat Sound =====
            CreatorSectionButton(
                title = "Create Heartbeat Sound",
                subtitle = "Shape the lub-dub sound",
                expanded = soundExpanded,
                onClick = { soundExpanded = !soundExpanded },
            )
            if (soundExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Style", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            data class PresetOption(val name: String, val params: SynthParams)
                            val presets = listOf(
                                PresetOption("Classic", SynthParams.CLASSIC),
                                PresetOption("Soft", SynthParams.SOFT),
                                PresetOption("Deep", SynthParams.DEEP),
                                PresetOption("Pulse", SynthParams.PULSE),
                                PresetOption("Mechanical", SynthParams.MECHANICAL),
                                PresetOption("Womb", SynthParams.WOMB),
                            )
                            presets.forEach { preset ->
                                FilterChip(
                                    selected = synthParams == preset.params,
                                    onClick = { viewModel.applySoundPreset(preset.params) },
                                    label = { Text(preset.name) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        ParamSlider("Warmth", synthParams.lubFrequency, 30f, 150f) {
                            // Lower = warmer/deeper, higher = brighter
                            val ratio = it / synthParams.lubFrequency
                            viewModel.updateSynthParams(synthParams.copy(
                                lubFrequency = it,
                                dubFrequency = (synthParams.dubFrequency * ratio).coerceIn(30f, 200f),
                                bodyFrequency = (synthParams.bodyFrequency * ratio).coerceIn(20f, 80f),
                            ))
                        }
                        ParamSlider("Intensity", synthParams.lubAmplitude, 0.3f, 1f) {
                            viewModel.updateSynthParams(synthParams.copy(
                                lubAmplitude = it,
                                dubAmplitude = (it * 0.6f).coerceIn(0.2f, 1f),
                                bodyAmplitude = (it * 0.3f).coerceIn(0.1f, 0.6f),
                            ))
                        }
                        ParamSlider("Volume", synthParams.masterGain, 0f, 1f) {
                            viewModel.updateSynthParams(synthParams.copy(masterGain = it))
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.previewBeat() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("\u2665 Preview Heartbeat")
                        }
                    }
                }
            }

            // ===== 2. Create Heartbeat Message =====
            CreatorSectionButton(
                title = "Create Heartbeat Message",
                subtitle = "Record your voice message",
                expanded = messageExpanded,
                onClick = { messageExpanded = !messageExpanded },
            )
            if (messageExpanded) {
                val recordingSecondsLeft by viewModel.recordingSecondsLeft.collectAsState()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Record one message (max 3m 33s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (voiceRecordings.isNotEmpty() && !isRecording) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Voice message recorded", modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteRecording(0) }) {
                                    Icon(Icons.Filled.Delete, "Delete")
                                }
                            }
                        }
                        if (isRecording) {
                            val rMin = recordingSecondsLeft / 60
                            val rSec = recordingSecondsLeft % 60
                            Text(
                                "Recording... ${rMin}m ${rSec}s left",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        val elapsedRecording = if (isRecording) 213 - recordingSecondsLeft else 0
                        Button(
                            onClick = {
                                if (isRecording) viewModel.stopRecording()
                                else if (hasRecordPermission) viewModel.startRecording()
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = !isRecording || elapsedRecording >= 11,
                            colors = if (isRecording) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ) else ButtonDefaults.buttonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isRecording) "\u23F9  Stop Recording" else "\u23FA  Record Message")
                        }
                        ParamSlider("Volume", voiceVolume, 0f, 1f) { viewModel.updateVoiceVolume(it) }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.previewVoice() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("\u2665 Preview Heartbeat")
                        }
                    }
                }
            }

            // ===== 3. Create Heartbeat Rhythm =====
            CreatorSectionButton(
                title = "Create Heartbeat Rhythm",
                subtitle = "Set start and end BPM",
                expanded = profileExpanded,
                onClick = { profileExpanded = !profileExpanded },
            )
            if (profileExpanded) {
                val seg = timeline.first()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ParamSliderInt("Start BPM", seg.bpmStart, 22, 222) {
                            viewModel.updateSegment(0, seg.copy(bpmStart = it))
                        }
                        ParamSliderInt("End BPM", seg.bpmEnd, 22, 222) {
                            viewModel.updateSegment(0, seg.copy(bpmEnd = it))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.previewAtBpm(seg.bpmStart) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("\u2665 Preview Start")
                            }
                            OutlinedButton(
                                onClick = { viewModel.previewAtBpm(seg.bpmEnd) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("\u2665 Preview End")
                            }
                        }
                    }
                }
            }

            // ===== 4. Create Heartbeat Background =====
            CreatorSectionButton(
                title = "Create Heartbeat Background",
                subtitle = "Noise, binaural beats & solfeggio",
                expanded = backgroundExpanded,
                onClick = { backgroundExpanded = !backgroundExpanded },
            )
            if (backgroundExpanded) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Background Noise
                        Text("Background Noise", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NoiseType.entries.forEach { type ->
                                FilterChip(
                                    selected = noiseType == type,
                                    onClick = { viewModel.updateNoiseType(type) },
                                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                )
                            }
                        }
                        if (noiseType != NoiseType.NONE) {
                            ParamSlider("Volume", noiseVolume, 0f, 1f) { viewModel.updateNoiseVolume(it) }
                        }
                        HorizontalDivider()
                        // Binaural Beats
                        Text("Binaural Beats", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BinauralPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = binauralPreset == preset,
                                    onClick = { viewModel.updateBinauralPreset(preset) },
                                    label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                )
                            }
                        }
                        if (binauralPreset != BinauralPreset.NONE) {
                            if (binauralPreset == BinauralPreset.CUSTOM) {
                                ParamSlider("Carrier Hz", binauralCarrierHz, 100f, 500f) { viewModel.updateBinauralCarrierHz(it) }
                                ParamSlider("Beat Hz", binauralBeatHz, 1f, 40f) { viewModel.updateBinauralBeatHz(it) }
                            }
                            ParamSlider("Volume", binauralVolume, 0f, 1f) { viewModel.updateBinauralVolume(it) }
                        }
                        HorizontalDivider()
                        // Solfeggio
                        Text("Solfeggio Frequency", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SolfeggioFrequency.entries.forEach { freq ->
                                FilterChip(
                                    selected = solfeggioFrequency == freq,
                                    onClick = { viewModel.updateSolfeggioFrequency(freq) },
                                    label = { Text(if (freq == SolfeggioFrequency.NONE) "None" else "${freq.hz.toInt()} Hz") },
                                )
                            }
                        }
                        if (solfeggioFrequency != SolfeggioFrequency.NONE) {
                            ParamSlider("Volume", solfeggioVolume, 0f, 1f) { viewModel.updateSolfeggioVolume(it) }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { viewModel.previewBackground() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("\u2665 Preview Heartbeat")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Full preview at the end
            Button(
                onClick = { viewModel.previewFull() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (previewingFull) "\u23F9 Stop Heartbeat" else "\u2665 Listen to the Heartbeat")
            }

            // Save with validation
            var showValidationError by remember { mutableStateOf<String?>(null) }
            if (showValidationError != null) {
                Text(
                    showValidationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Save
            Button(
                onClick = {
                    val error = viewModel.validate()
                    if (error != null) {
                        showValidationError = error
                    } else {
                        showValidationError = null
                        viewModel.save { onSaved(it) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Save Heartbeat")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreatorSectionButton(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}



@Composable
private fun ParamSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f),
        )
        Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ParamSliderInt(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(100.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text("$value", style = MaterialTheme.typography.bodySmall)
    }
}
