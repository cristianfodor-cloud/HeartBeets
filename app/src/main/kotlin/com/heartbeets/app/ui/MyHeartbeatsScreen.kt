package com.heartbeets.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
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
import com.heartbeets.sharing.CodeManager
import com.heartbeets.sharing.HeartbeatCode
import com.heartbeets.sharing.HeartbeatShareService
import com.heartbeets.sharing.SharedHeartbeat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MyHeartbeatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HeartbeatRepository(application)
    private val codeManager = CodeManager(application)
    private val shareService = HeartbeatShareService(application)
    val audioEngine = AudioEngine(application)

    private val _heartbeats = MutableStateFlow<List<Heartbeat>>(emptyList())
    val heartbeats: StateFlow<List<Heartbeat>> = _heartbeats.asStateFlow()

    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    private val _sharingId = MutableStateFlow<String?>(null)
    val sharingId: StateFlow<String?> = _sharingId.asStateFlow()

    private val _sharedCode = MutableStateFlow<String?>(null)
    val sharedCode: StateFlow<String?> = _sharedCode.asStateFlow()

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

    fun share(heartbeat: Heartbeat) {
        _sharingId.value = heartbeat.id
        viewModelScope.launch {
            try {
                // Reuse existing code if this heartbeat was already shared
                val existing = codeManager.getCodes().find { it.heartbeatId == heartbeat.id }
                val code = existing?.code ?: codeManager.generateCode()
                val shared = SharedHeartbeat.from(heartbeat)
                shareService.upload(code, shared, heartbeat.voiceRecordings)
                if (existing == null) {
                    codeManager.addCode(HeartbeatCode(code, heartbeat.id, heartbeat.displayName))
                }
                _sharedCode.value = code
            } catch (e: Exception) {
                _sharedCode.value = "ERROR: ${e.message}"
            } finally {
                _sharingId.value = null
            }
        }
    }

    fun dismissShareDialog() {
        _sharedCode.value = null
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
    val sharingId by vm.sharingId.collectAsState()
    val sharedCode by vm.sharedCode.collectAsState()

    // Reload when screen appears
    LaunchedEffect(Unit) { vm.reload() }

    // When sharing completes, copy to clipboard and open share sheet
    val context = LocalContext.current
    LaunchedEffect(sharedCode) {
        val code = sharedCode ?: return@LaunchedEffect
        if (code.startsWith("ERROR:")) return@LaunchedEffect
        // Copy just the code to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("HeartBeets Code", code))
        // Open share sheet
        val shareText = "\u2764\uFE0F Listen to my heartbeat on HeartBeets!\n\nCode: $code\n\nOpen the HeartBeets app \u2192 Listen to a Heartbeat \u2192 paste the code above"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share Heartbeat Code"))
        vm.dismissShareDialog()
    }

    // Show error dialog if sharing failed
    if (sharedCode != null && sharedCode!!.startsWith("ERROR:")) {
        AlertDialog(
            onDismissRequest = { vm.dismissShareDialog() },
            title = { Text("Sharing Failed") },
            text = { Text(sharedCode!!) },
            confirmButton = {
                TextButton(onClick = { vm.dismissShareDialog() }) { Text("OK") }
            },
        )
    }

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
                        isSharing = sharingId == heartbeat.id,
                        onPlay = { vm.play(heartbeat) },
                        onShare = { vm.share(heartbeat) },
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
    isSharing: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
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
                val totalSec = heartbeat.totalDurationSec % 60
                val bpmRange = "${heartbeat.timeline.minOf { minOf(it.bpmStart, it.bpmEnd) }}-${heartbeat.timeline.maxOf { maxOf(it.bpmStart, it.bpmEnd) }} BPM"
                Text("${totalMin}m ${totalSec}s \u2022 $bpmRange", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onShare, enabled = !isSharing) {
                if (isSharing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Share, "Share")
                }
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
