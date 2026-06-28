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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.heartbeets.audio.BinauralPreset
import com.heartbeets.audio.EasingCurve
import com.heartbeets.audio.NoiseType
import com.heartbeets.audio.SolfeggioFrequency
import com.heartbeets.audio.TimelineSegment

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HeartbeatCreatorScreen(
    viewModel: HeartbeatCreatorViewModel,
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit = {},
) {
    val name by viewModel.name.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val noiseType by viewModel.noiseType.collectAsState()
    val noiseVolume by viewModel.noiseVolume.collectAsState()
    val binauralPreset by viewModel.binauralPreset.collectAsState()
    val binauralCarrierHz by viewModel.binauralCarrierHz.collectAsState()
    val binauralBeatHz by viewModel.binauralBeatHz.collectAsState()
    val binauralVolume by viewModel.binauralVolume.collectAsState()
    val solfeggioFrequency by viewModel.solfeggioFrequency.collectAsState()
    val solfeggioVolume by viewModel.solfeggioVolume.collectAsState()
    val voiceEnabled by viewModel.voiceEnabled.collectAsState()
    val voiceRecordings by viewModel.voiceRecordings.collectAsState()
    val voiceIntervalSec by viewModel.voiceIntervalSec.collectAsState()
    val voiceVolume by viewModel.voiceVolume.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()

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
                actions = {
                    TextButton(onClick = { viewModel.save { onSaved(it) } }) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Preview
            OutlinedButton(
                onClick = { viewModel.previewBeat() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\u2665 Preview Beat")
            }

            HorizontalDivider()

            // --- BPM Timeline ---
            SectionHeader("BPM Timeline")
            Text(
                "Define how the heartbeat rhythm changes over time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            timeline.forEachIndexed { index, seg ->
                TimelineSegmentCard(
                    segment = seg,
                    index = index,
                    canDelete = timeline.size > 1,
                    onUpdate = { viewModel.updateSegment(index, it) },
                    onDelete = { viewModel.removeSegment(index) },
                )
            }
            OutlinedButton(
                onClick = {
                    val lastBpm = timeline.last().bpmEnd
                    viewModel.addSegment(TimelineSegment(lastBpm, lastBpm, 60))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Segment")
            }
            val totalMin = timeline.sumOf { it.durationSec } / 60
            val totalSec = timeline.sumOf { it.durationSec } % 60
            Text(
                "Total: ${totalMin}m ${totalSec}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            HorizontalDivider()

            // --- Background Noise ---
            SectionHeader("Background Noise")
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

            // --- Binaural Beats ---
            SectionHeader("Binaural Beats")
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

            // --- Solfeggio ---
            SectionHeader("Solfeggio Frequency")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SolfeggioFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = solfeggioFrequency == freq,
                        onClick = { viewModel.updateSolfeggioFrequency(freq) },
                        label = { Text(if (freq == SolfeggioFrequency.NONE) "Off" else "${freq.hz.toInt()} Hz") },
                    )
                }
            }
            if (solfeggioFrequency != SolfeggioFrequency.NONE) {
                ParamSlider("Volume", solfeggioVolume, 0f, 1f) { viewModel.updateSolfeggioVolume(it) }
            }

            HorizontalDivider()

            // --- Voice Messages ---
            SectionHeader("Voice Messages")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Record messages in your voice", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Switch(checked = voiceEnabled, onCheckedChange = { viewModel.updateVoiceEnabled(it) })
            }

            if (voiceEnabled) {
                voiceRecordings.forEachIndexed { index, _ ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Message ${index + 1}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.previewRecording(index) }) {
                            Icon(Icons.Filled.PlayArrow, "Play")
                        }
                        IconButton(onClick = { viewModel.deleteRecording(index) }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                    }
                }
                Button(
                    onClick = {
                        if (isRecording) viewModel.stopRecording()
                        else if (hasRecordPermission) viewModel.startRecording()
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    colors = if (isRecording) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ) else ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRecording) "\u23F9  Stop Recording" else "\u23FA  Record Message")
                }
                ParamSliderInt("Interval (sec)", voiceIntervalSec, 10, 120) { viewModel.updateVoiceIntervalSec(it) }
                ParamSlider("Volume", voiceVolume, 0f, 1f) { viewModel.updateVoiceVolume(it) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineSegmentCard(
    segment: TimelineSegment,
    index: Int,
    canDelete: Boolean,
    onUpdate: (TimelineSegment) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Segment ${index + 1}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, "Remove", Modifier.size(18.dp))
                    }
                }
            }
            ParamSliderInt("Start BPM", segment.bpmStart, 40, 180) {
                onUpdate(segment.copy(bpmStart = it))
            }
            ParamSliderInt("End BPM", segment.bpmEnd, 40, 180) {
                onUpdate(segment.copy(bpmEnd = it))
            }
            ParamSliderInt("Duration (sec)", segment.durationSec, 10, 3600) {
                onUpdate(segment.copy(durationSec = it))
            }
            // Easing (only relevant for ramps)
            if (segment.bpmStart != segment.bpmEnd) {
                Text("Easing", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EasingCurve.entries.forEach { curve ->
                        FilterChip(
                            selected = segment.easing == curve,
                            onClick = { onUpdate(segment.copy(easing = curve)) },
                            label = { Text(curve.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
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
