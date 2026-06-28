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
    onCreateClicked: () -> Unit = {},
    onListenClicked: () -> Unit = {},
    onMyHeartbeatClicked: () -> Unit = {},
) {
    var showInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("HeartBeets") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("How it works", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    Text("Create My Heartbeat", fontWeight = FontWeight.SemiBold)
                    Text("Craft a unique heartbeat with your own BPM timeline, binaural beats, solfeggio frequencies, background noise, and voice messages recorded in your own voice.")
                    Spacer(Modifier.height(8.dp))

                    Text("Share", fontWeight = FontWeight.SemiBold)
                    Text("Each heartbeat gets a code. Share it with anyone — they enter the code and your heartbeat plays on their phone.")
                    Spacer(Modifier.height(8.dp))

                    Text("Listen", fontWeight = FontWeight.SemiBold)
                    Text("Enter someone's code to download and listen to their heartbeat — including their voice messages.")
                    Spacer(Modifier.height(16.dp))

                    Text("Contact & Support", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Have a question or feedback?")
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:contact@dogplanet.app")
                                putExtra(Intent.EXTRA_SUBJECT, "HeartBeets Feedback")
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
                onClick = onCreateClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create My Heartbeat")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onMyHeartbeatClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("My Heartbeats")
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onListenClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Listen to a Heartbeat")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
