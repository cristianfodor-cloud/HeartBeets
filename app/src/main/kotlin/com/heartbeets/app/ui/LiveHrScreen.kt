package com.heartbeets.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartbeets.audio.PlaybackMode
import com.heartbeets.audio.HeartbeatProfile
import com.heartbeets.audio.ProfileAnchorMode
import com.heartbeets.audio.SoundPackRegistry
import com.heartbeets.audio.SynthParams
import com.heartbeets.core.ConnectionState
import com.heartbeets.sharing.ShareViewModel
import com.heartbeets.sharing.SharedProfile
import com.heartbeets.sharing.SynthParamsDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveHrScreen(
    address: String,
    factoryId: String,
    onBack: () -> Unit,
    onOpenSoundDesigner: (packId: String?) -> Unit = {},
    onOpenProfileCreator: (profileId: String?) -> Unit = {},
    vm: LiveHrViewModel = viewModel(
        factory = LiveHrViewModelFactory(
            application = LocalContext.current.applicationContext as android.app.Application,
            address = address,
            factoryId = factoryId,
        ),
    ),
    shareVm: ShareViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
    billingVm: com.heartbeets.app.billing.BillingViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
) {
    val offlineMode = address == "offline"
    val bpm by vm.bpm.collectAsState()
    val state by vm.connectionState.collectAsState()
    val battery by vm.battery.collectAsState()
    val lastUpdatedMs by vm.lastUpdatedMs.collectAsState()
    val playbackMode by vm.playbackMode.collectAsState()
    val cadence by vm.playbackCadence.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val bpmOffset by vm.bpmOffset.collectAsState()
    val phaseOffset by vm.phaseOffsetMs.collectAsState()

    // Refresh profiles whenever the screen resumes (e.g. after returning from creator)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.refreshProfiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Push BPM to Firebase when live
    val sharingActive by shareVm.isLive.collectAsState()
    androidx.compose.runtime.LaunchedEffect(bpm, sharingActive) {
        if (sharingActive && bpm != null) {
            shareVm.pushBpm(bpm!!)
        }
    }

    var showProfileSheet by remember { mutableStateOf(false) }
    var showSoundPackSheet by remember { mutableStateOf(false) }
    var absoluteBpmDialogProfile by remember { mutableStateOf<HeartbeatProfile?>(null) }
    var absoluteBpmInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (offlineMode) "Offline Mode" else vm.displayName) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!offlineMode) vm.disconnect()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!offlineMode) {
            // 1. Connection status — fixed 24dp slot
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                val statusText = when (state) {
                    ConnectionState.Connecting -> "Connecting…"
                    ConnectionState.Connected -> ""
                    ConnectionState.Disconnected -> "Disconnected"
                    ConnectionState.Error -> "Error"
                }
                if (statusText.isNotEmpty()) {
                    Text(statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // 2. Reconnect — fixed 40dp slot
            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                if (state == ConnectionState.Disconnected || state == ConnectionState.Error) {
                    Button(onClick = { vm.connect() }) { Text("Reconnect") }
                }
            }
            } else {
            // Offline: match the 64dp taken by connection status + reconnect in live mode
            Spacer(Modifier.height(64.dp))
            }

            // 3. Playback status — fixed 24dp slot
            Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                if (playbackMode != PlaybackMode.STOPPED) {
                    Text("♫ Playing at $cadence BPM", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }

            // 4. Audio buttons row — always present
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (playbackMode) {
                    PlaybackMode.STOPPED -> {
                        if (!offlineMode) {
                            FilledTonalButton(onClick = { vm.startMirrorMode() }) { Text("Mirror BPM") }
                        }
                        OutlinedButton(onClick = { showProfileSheet = true }) { Text("Profiles") }
                    }
                    PlaybackMode.MIRROR, PlaybackMode.PROFILE -> {
                        Button(onClick = { vm.stopAudio() }) { Text("Stop") }
                        val activeProfileName by vm.activeProfileName.collectAsState()
                        OutlinedButton(onClick = { showProfileSheet = true }) {
                            Text(activeProfileName?.let { "Profile: $it" } ?: "Profiles")
                        }
                    }
                }
            }

            // 5. Sound Packs — always present
            Spacer(Modifier.height(4.dp))
            val activePackId by vm.activeSoundPackId.collectAsState()
            val activePackName = remember(activePackId) {
                SoundPackRegistry.getById(activePackId)?.displayName ?: "Classic"
            }
            OutlinedButton(onClick = { showSoundPackSheet = true }) {
                Text("Sound Packs: $activePackName")
            }

            // 6. Go Live — always present, fixed height slot
            if (!offlineMode) {
            val isLive by shareVm.isLive.collectAsState()
            val shareError by shareVm.error.collectAsState()
            val toastContext = LocalContext.current
            androidx.compose.runtime.LaunchedEffect(shareError) {
                shareError?.let {
                    android.widget.Toast.makeText(toastContext, it, android.widget.Toast.LENGTH_SHORT).show()
                    shareVm.clearError()
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                if (!isLive) {
                    OutlinedButton(onClick = {
                        if (!billingVm.hasAccess) {
                            android.widget.Toast.makeText(toastContext, "Trial ended — subscribe to share live", android.widget.Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        if (bpm == null) {
                            android.widget.Toast.makeText(toastContext, "No HR available yet", android.widget.Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        val packId = vm.activeSoundPackId.value
                        val pack = SoundPackRegistry.getById(packId)
                        val synthParams = pack?.synthParams ?: SynthParams.CLASSIC
                        val profile = SharedProfile(
                            id = packId,
                            name = pack?.displayName ?: "Heartbeat",
                            version = 1,
                            createdBy = "",
                            synthParams = SynthParamsDto.from(synthParams),
                        )
                        shareVm.goLive(profile)
                    }) { Text("Go Live") }
                } else {
                    Button(onClick = { shareVm.goOffline() }) { Text("\u2764 LIVE — Tap to stop") }
                }
            }

            // 7. Top spacer — pushes BPM toward center
            Spacer(Modifier.weight(0.2f))

            // 8. BPM display — ALWAYS same height, shows "--" when no data
            Box(modifier = Modifier.height(160.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val pulse by remember(lastUpdatedMs) { derivedStateOf { lastUpdatedMs % 2L == 0L } }
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (bpm != null && pulse) 1f else 0.2f,
                        animationSpec = tween(durationMillis = 400),
                        label = "liveDot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .alpha(dotAlpha)
                            .background(Color(0xFFE53935), CircleShape)
                            .padding(5.dp),
                    ) {}
                    Text(
                        text = bpm?.toString() ?: "--",
                        fontSize = 96.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (bpm == null) {
                        Text("Waiting for data…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // 9. Spacer between BPM and battery/smoothing
            Spacer(Modifier.weight(0.12f))

            // 10. Battery — fixed 20dp slot
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                battery?.let { pct ->
                    Text("Battery: $pct %", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 11. Smoothing — fixed height
            val smoothing by vm.smoothingEnabled.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(36.dp),
            ) {
                RadioButton(selected = smoothing, onClick = { vm.toggleSmoothing() })
                Text("Smooth BPM (avg last 10)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
            }

            } // end if (!offlineMode) for live section

            // D-pad — visible in both live and offline mode, dimmed when stopped
            Spacer(Modifier.weight(0.1f))

            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                if (bpmOffset != 0 || phaseOffset != 0) {
                    Text(
                        text = "BPM offset: ${if (bpmOffset >= 0) "+" else ""}$bpmOffset | Phase: ${if (phaseOffset >= 0) "+" else ""}${phaseOffset}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val dpadEnabled = playbackMode != PlaybackMode.STOPPED
            val dpadAlpha = if (dpadEnabled) 1f else 0.3f
            val iconSize = 40.dp
            Column(
                modifier = Modifier.alpha(dpadAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = { vm.adjustBpmOffset(1) }, enabled = dpadEnabled, modifier = Modifier.size(iconSize)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "BPM +1", modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.adjustPhase(-50) }, enabled = dpadEnabled, modifier = Modifier.size(iconSize)) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Phase -50ms", modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(onClick = { vm.resetAdjustments() }, enabled = dpadEnabled, modifier = Modifier.height(iconSize)) {
                        Text("Reset")
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { vm.adjustPhase(50) }, enabled = dpadEnabled, modifier = Modifier.size(iconSize)) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Phase +50ms", modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = { vm.adjustBpmOffset(-1) }, enabled = dpadEnabled, modifier = Modifier.size(iconSize)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "BPM -1", modifier = Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Profile selection bottom sheet
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Select Profile", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                LazyColumn {
                    items(profiles) { profile ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                profile.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!offlineMode) {
                                    FilledTonalButton(onClick = {
                                        vm.startProfile(profile, ProfileAnchorMode.RELATIVE)
                                        showProfileSheet = false
                                    }) {
                                        Text("From my BPM")
                                    }
                                }
                                OutlinedButton(onClick = {
                                    absoluteBpmInput = (bpm ?: 72).toString()
                                    absoluteBpmDialogProfile = profile
                                    showProfileSheet = false
                                }) {
                                    Text("Fixed BPM")
                                }
                                if (!profile.isPreset) {
                                    IconButton(
                                        onClick = {
                                            showProfileSheet = false
                                            onOpenProfileCreator(profile.id)
                                        },
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Filled.Edit,
                                            contentDescription = "Edit profile",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        showProfileSheet = false
                        onOpenProfileCreator(null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create Custom Profile")
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Dialog to enter start BPM for absolute mode
    absoluteBpmDialogProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { absoluteBpmDialogProfile = null },
            title = { Text("Start BPM") },
            text = {
                Column {
                    Text("Enter the starting BPM for this profile:")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = absoluteBpmInput,
                        onValueChange = { value ->
                            absoluteBpmInput = value.filter { it.isDigit() }.take(3)
                        },
                        label = { Text("BPM") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val startBpm = absoluteBpmInput.toIntOrNull()?.coerceIn(1, 220) ?: 72
                        val adjusted = profile.copy(
                            anchorMode = ProfileAnchorMode.ABSOLUTE,
                            startBpm = startBpm,
                        )
                        vm.startProfile(adjusted, ProfileAnchorMode.ABSOLUTE)
                        absoluteBpmDialogProfile = null
                    }
                ) { Text("Start") }
            },
            dismissButton = {
                OutlinedButton(onClick = { absoluteBpmDialogProfile = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Sound pack selection bottom sheet
    if (showSoundPackSheet) {
        val activePackId by vm.activeSoundPackId.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showSoundPackSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Select Sound Pack", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                SoundPackRegistry.getAll().forEach { pack ->
                    val isSelected = pack.id == activePackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isSelected) Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.shapes.medium,
                                ) else Modifier
                            )
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pack.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                pack.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { vm.previewPack(pack) }) {
                            Text("\u25B6")
                        }
                        Spacer(Modifier.width(4.dp))
                        if (isSelected) {
                            FilledTonalButton(onClick = {}) {
                                Text("\u2713 Used")
                            }
                        } else {
                            Button(onClick = {
                                vm.setSoundPack(pack)
                            }) {
                                Text("Use")
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = {
                        showSoundPackSheet = false
                        onOpenSoundDesigner(null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Create Custom Sound")
                }
            }
        }
    }
}
