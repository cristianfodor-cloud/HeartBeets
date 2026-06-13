package com.heartbeets.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanClicked: () -> Unit = {},
    onListenClicked: () -> Unit = {},
    onHeartCodesClicked: () -> Unit = {},
    onSubscriptionClicked: () -> Unit = {},
    onOfflineModeClicked: () -> Unit = {},
) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { CenterAlignedTopAppBar(title = { Text("HeartBeets") }) },
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

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSubscriptionClicked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Subscription")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
