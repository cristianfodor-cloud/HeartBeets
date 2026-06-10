package com.heartbeets.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartbeets.core.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveHrScreen(
    address: String,
    factoryId: String,
    onBack: () -> Unit,
    vm: LiveHrViewModel = viewModel(
        factory = LiveHrViewModelFactory(
            application = LocalContext.current.applicationContext as android.app.Application,
            address = address,
            factoryId = factoryId,
        ),
    ),
) {
    val bpm by vm.bpm.collectAsState()
    val state by vm.connectionState.collectAsState()
    val battery by vm.battery.collectAsState()
    val lastUpdatedMs by vm.lastUpdatedMs.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(vm.displayName) },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.disconnect()
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
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (state) {
                    ConnectionState.Connecting -> "Connecting…"
                    ConnectionState.Connected -> ""
                    ConnectionState.Disconnected -> "Disconnected"
                    ConnectionState.Error -> "Error"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )

            Spacer(Modifier.height(24.dp))

            if (bpm != null) {
                // Live indicator — toggles alpha on every new sample so user can see data is streaming
                val pulse by remember(lastUpdatedMs) { derivedStateOf { lastUpdatedMs % 2L == 0L } }
                val dotAlpha by animateFloatAsState(
                    targetValue = if (pulse) 1f else 0.2f,
                    animationSpec = tween(durationMillis = 400),
                    label = "liveDot",
                )
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .padding(bottom = 8.dp)
                        .alpha(dotAlpha)
                        .background(Color(0xFFE53935), CircleShape)
                        .padding(6.dp),
                ) {}
                Text(
                    text = "${bpm}",
                    fontSize = 96.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("BPM", style = MaterialTheme.typography.headlineSmall)
            } else if (state == ConnectionState.Connected) {
                Text("Waiting for data…", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))

            battery?.let { pct ->
                Text("Battery: $pct %", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(32.dp))

            if (state == ConnectionState.Disconnected || state == ConnectionState.Error) {
                Button(onClick = { vm.connect() }) { Text("Reconnect") }
            }
        }
    }
}
