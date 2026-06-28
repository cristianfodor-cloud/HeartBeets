package com.heartbeets.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanClicked: () -> Unit = {},
    onListenClicked: () -> Unit = {},
    onHeartCodesClicked: () -> Unit = {},
    onOfflineModeClicked: () -> Unit = {},
) {
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("HeartBeets Guide") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("How to use the app", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    Text("Scan for devices", fontWeight = FontWeight.SemiBold)
                    Text("Connect a Bluetooth heart rate monitor to hear your live heartbeat in real time.")
                    Spacer(Modifier.height(8.dp))

                    Text("Offline Mode", fontWeight = FontWeight.SemiBold)
                    Text("Listen to any BPM with any sound pack — no device needed. Great for meditation or relaxation at a fixed rhythm.")
                    Spacer(Modifier.height(8.dp))

                    Text("Listen to a Heartbeat", fontWeight = FontWeight.SemiBold)
                    Text("Enter a HeartCode shared by someone else to listen to their heartbeat remotely.")
                    Spacer(Modifier.height(8.dp))

                    Text("My HeartCodes", fontWeight = FontWeight.SemiBold)
                    Text("Create and share your own HeartCode so others can listen to your heartbeat in real time.")
                    Spacer(Modifier.height(8.dp))

                    Text("Sound Packs", fontWeight = FontWeight.SemiBold)
                    Text("Choose from built-in packs (classic, ambient, binaural, solfeggio) or create your own with custom noise, binaural beats, and solfeggio frequencies.")
                    Spacer(Modifier.height(8.dp))

                    Text("Profiles", fontWeight = FontWeight.SemiBold)
                    Text("Set up cadence profiles that smoothly vary BPM over time — perfect for workouts, breathing exercises, or sleep wind-downs.")
                    Spacer(Modifier.height(16.dp))

                    Text("Contact & Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Have a question or feedback? We'd love to hear from you.")
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:contact@dogplanet.app")
                                putExtra(Intent.EXTRA_SUBJECT, "HeartBeets Support")
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("contact@dogplanet.app")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("Close") }
            },
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("HeartBeets") },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Info & Help")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Spacer(Modifier.weight(1f))

            Button(
                onClick = onScanClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan for devices")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOfflineModeClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Offline Mode")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onListenClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Listen to a Heartbeat")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onHeartCodesClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("My HeartCodes")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
