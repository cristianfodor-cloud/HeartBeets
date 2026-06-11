package com.heartbeets.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heartbeets.audio.EasingCurve

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCreatorScreen(
    viewModel: ProfileCreatorViewModel,
    onBack: () -> Unit,
) {
    val name by viewModel.name.collectAsState()
    val stages by viewModel.stages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile Creator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.save { onBack() } },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Info card
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Stages define how the playback BPM changes over time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Offset is always relative to your starting BPM — set when you press Start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            stages.forEachIndexed { index, stage ->
                StageCard(
                    index = index,
                    stage = stage,
                    previousOffset = if (index == 0) 0 else stages[index - 1].targetOffsetBpm,
                    canDelete = stages.size > 1,
                    onUpdate = { viewModel.updateStage(index, it) },
                    onDelete = { viewModel.removeStage(index) },
                )
            }

            OutlinedButton(
                onClick = { viewModel.addStage() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Stage")
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StageCard(
    index: Int,
    stage: StageUiState,
    previousOffset: Int,
    canDelete: Boolean,
    onUpdate: (StageUiState) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Stage ${index + 1}", style = MaterialTheme.typography.titleSmall)
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove stage",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // BPM offset slider
            val offsetLabel = when {
                stage.targetOffsetBpm > 0 -> "+${stage.targetOffsetBpm} BPM from start"
                stage.targetOffsetBpm < 0 -> "${stage.targetOffsetBpm} BPM from start"
                else -> "Same as start BPM"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Target", style = MaterialTheme.typography.bodySmall)
                Text(offsetLabel, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = stage.targetOffsetBpm.toFloat(),
                onValueChange = { onUpdate(stage.copy(targetOffsetBpm = it.toInt())) },
                valueRange = -60f..60f,
                steps = 119,
            )

            // Duration slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Duration", style = MaterialTheme.typography.bodySmall)
                Text("${stage.durationSec}s", style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = stage.durationSec.toFloat(),
                onValueChange = { onUpdate(stage.copy(durationSec = it.toInt().coerceAtLeast(5))) },
                valueRange = 5f..300f,
                steps = 58,
            )

            // Easing curve
            Text("Transition", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EasingCurve.entries.forEach { curve ->
                    FilterChip(
                        selected = stage.curve == curve,
                        onClick = { onUpdate(stage.copy(curve = curve)) },
                        label = {
                            Text(
                                text = when (curve) {
                                    EasingCurve.LINEAR -> "Linear"
                                    EasingCurve.EASE_IN -> "Ease In"
                                    EasingCurve.EASE_OUT -> "Ease Out"
                                    EasingCurve.EASE_IN_OUT -> "Smooth"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }
        }
    }
}
