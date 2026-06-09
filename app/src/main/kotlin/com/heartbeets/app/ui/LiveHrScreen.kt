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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
