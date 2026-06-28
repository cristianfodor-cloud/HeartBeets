package com.heartbeets.app.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartbeets.audio.AudioEngine
import com.heartbeets.audio.Heartbeat
import com.heartbeets.audio.HeartbeatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyHeartbeatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HeartbeatRepository(application)
    val audioEngine = AudioEngine(application)

    private val _heartbeats = MutableStateFlow<List<Heartbeat>>(emptyList())
    val heartbeats: StateFlow<List<Heartbeat>> = _heartbeats.asStateFlow()

    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch { _heartbeats.value = repository.loadAll() }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _heartbeats.value = repository.loadAll()
        }
    }

    fun play(heartbeat: Heartbeat) {
        if (_playingId.value == heartbeat.id) {
            audioEngine.stop()
            _playingId.value = null
        } else {
            audioEngine.setHeartbeat(heartbeat)
            audioEngine.play()
            _playingId.value = heartbeat.id
        }
    }

    override fun onCleared() {
        audioEngine.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyHeartbeatsScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    vm: MyHeartbeatsViewModel = viewModel(),
) {
    val heartbeats by vm.heartbeats.collectAsState()
    val playingId by vm.playingId.collectAsState()

    // Reload when screen appears
    LaunchedEffect(Unit) { vm.reload() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("My Heartbeats") },
                navigationIcon = {
                    IconButton(onClick = { vm.audioEngine.stop(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (heartbeats.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No heartbeats yet. Create one!", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(heartbeats, key = { _, h -> h.id }) { _, heartbeat ->
                    HeartbeatCard(
                        heartbeat = heartbeat,
                        isPlaying = playingId == heartbeat.id,
                        onPlay = { vm.play(heartbeat) },
                        onEdit = { onEdit(heartbeat.id) },
                        onDelete = { vm.delete(heartbeat.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeartbeatCard(
    heartbeat: Heartbeat,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(heartbeat.displayName, style = MaterialTheme.typography.titleMedium)
                val totalMin = heartbeat.totalDurationSec / 60
                val bpmRange = "${heartbeat.timeline.minOf { minOf(it.bpmStart, it.bpmEnd) }}-${heartbeat.timeline.maxOf { maxOf(it.bpmStart, it.bpmEnd) }} BPM"
                Text("${totalMin} min \u2022 $bpmRange", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onPlay) {
                Icon(
                    if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Delete")
            }
        }
    }
}
