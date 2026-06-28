package com.heartbeets.app.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartbeets.audio.AudioEngine
import com.heartbeets.audio.Heartbeat
import com.heartbeets.audio.HeartbeatRepository
import com.heartbeets.sharing.HeartbeatShareService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ListenViewModel(application: Application) : AndroidViewModel(application) {
    private val shareService = HeartbeatShareService(application)
    private val receivedRepo = HeartbeatRepository(application, "received_heartbeats.json")
    val audioEngine = AudioEngine(application)

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _receivedHeartbeats = MutableStateFlow<List<Heartbeat>>(emptyList())
    val receivedHeartbeats: StateFlow<List<Heartbeat>> = _receivedHeartbeats.asStateFlow()

    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    init {
        viewModelScope.launch {
            _receivedHeartbeats.value = receivedRepo.loadAll()
        }
    }

    fun updateCode(c: String) {
        _code.value = c.uppercase().take(10)
    }

    fun download() {
        val c = _code.value.trim()
        if (c.length < 5) {
            _error.value = "Code must be at least 5 characters"
            return
        }
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val shared = shareService.downloadConfig(c)
                if (shared == null) {
                    _error.value = "Heartbeat not found"
                    _loading.value = false
                    return@launch
                }
                // Download voice files from Storage
                val localPaths = if (shared.voiceRecordingUrls.isNotEmpty()) {
                    shareService.downloadVoiceFiles(c, shared.voiceRecordingUrls)
                } else emptyList()

                val hb = shared.toHeartbeat(localPaths)
                // Save to received heartbeats
                receivedRepo.save(hb)
                _receivedHeartbeats.value = receivedRepo.loadAll()
                _code.value = ""
                _loading.value = false
            } catch (e: Exception) {
                _error.value = e.message ?: "Download failed"
                _loading.value = false
            }
        }
    }

    fun togglePlay(heartbeat: Heartbeat) {
        if (_playingId.value == heartbeat.id) {
            audioEngine.stop()
            _playingId.value = null
        } else {
            audioEngine.stop()
            audioEngine.setHeartbeat(heartbeat)
            audioEngine.play()
            _playingId.value = heartbeat.id
        }
    }

    fun deleteReceived(heartbeat: Heartbeat) {
        viewModelScope.launch {
            receivedRepo.delete(heartbeat.id)
            _receivedHeartbeats.value = receivedRepo.loadAll()
            if (_playingId.value == heartbeat.id) {
                audioEngine.stop()
                _playingId.value = null
            }
        }
    }

    override fun onCleared() {
        audioEngine.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    onBack: () -> Unit,
    initialCode: String = "",
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = (context as android.app.Activity).application
    val vm: ListenViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(app) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ListenViewModel(app) as T
        }
    )

    // Pre-fill code from deep link or navigation
    LaunchedEffect(initialCode) {
        if (initialCode.isNotBlank()) {
            vm.updateCode(initialCode)
        }
    }

    val code by vm.code.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val receivedHeartbeats by vm.receivedHeartbeats.collectAsState()
    val playingId by vm.playingId.collectAsState()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Listen to a Heartbeat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter the code someone shared with you:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { vm.updateCode(it) },
                    label = { Text("Heartbeat Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { vm.download() },
                    enabled = !loading && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Downloading...")
                    } else {
                        Text("Download Heartbeat")
                    }
                }
            }

            // Show received heartbeats list
            if (receivedHeartbeats.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Received Heartbeats",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                itemsIndexed(receivedHeartbeats) { _, hb ->
                    ReceivedHeartbeatCard(
                        heartbeat = hb,
                        isPlaying = playingId == hb.id,
                        onTogglePlay = { vm.togglePlay(hb) },
                        onDelete = { vm.deleteReceived(hb) },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ReceivedHeartbeatCard(
    heartbeat: Heartbeat,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                heartbeat.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            val totalSec = heartbeat.timeline.sumOf { it.durationSec }
            Text(
                "Duration: ${totalSec / 60}m ${totalSec % 60}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (heartbeat.voiceEnabled && heartbeat.voiceRecordings.isNotEmpty()) {
                Text(
                    "${heartbeat.voiceRecordings.size} voice message(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = onTogglePlay) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isPlaying) "Stop" else "Listen")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
