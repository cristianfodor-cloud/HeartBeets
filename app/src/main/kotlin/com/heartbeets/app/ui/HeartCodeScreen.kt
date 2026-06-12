package com.heartbeets.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartbeets.sharing.HeartCode
import com.heartbeets.sharing.ShareViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartCodeScreen(
    onBack: () -> Unit,
    shareVm: ShareViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as androidx.activity.ComponentActivity,
    ),
) {
    val codes by shareVm.heartCodes.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var codeToDelete by remember { mutableStateOf<HeartCode?>(null) }
    var codeToShowQr by remember { mutableStateOf<HeartCode?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("HeartCodes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Generate codes and share them with friends. " +
                    "Enabled codes broadcast your heartbeat when you go live.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            if (codes.isEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    "No codes yet",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create a code to share with a friend.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(codes, key = { it.code }) { heartCode ->
                        HeartCodeCard(
                            heartCode = heartCode,
                            onToggle = { shareVm.setCodeEnabled(heartCode.code, !heartCode.enabled) },
                            onShare = heartCode,
                            onDelete = { codeToDelete = heartCode },
                            onShowQr = { codeToShowQr = heartCode },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Create New Code")
            }
        }
    }

    // Delete confirmation dialog
    codeToDelete?.let { code ->
        AlertDialog(
            onDismissRequest = { codeToDelete = null },
            title = { Text("Delete HeartCode?") },
            text = {
                Text("Delete \"${code.name}\"? Friends who have this code will no longer be able to listen to your heartbeat.")
            },
            confirmButton = {
                Button(onClick = {
                    shareVm.deleteCode(code.code)
                    codeToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { codeToDelete = null }) { Text("Cancel") }
            },
        )
    }

    // QR code dialog
    codeToShowQr?.let { code ->
        val context = LocalContext.current
        val deepLink = "heartbeets://add/${code.code}"
        val qrBitmap = remember(code.code) { QrGenerator.generate(deepLink) }
        AlertDialog(
            onDismissRequest = { codeToShowQr = null },
            title = { Text("QR Code: ${code.name}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code for ${code.name}",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            code.code,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Long-press the code to copy it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    // Share QR image + code text
                    val uri = saveQrToCache(context, qrBitmap, code.code)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Listen to my heartbeat Live on HeartBeets! \uD83D\uDC93\n\nMy code: ${code.code}\n\nheartbeets://add/${code.code}",
                        )
                        if (uri != null) {
                            putExtra(Intent.EXTRA_STREAM, uri)
                            type = "image/png"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } else {
                            type = "text/plain"
                        }
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share HeartCode"))
                }) { Text("Share") }
            },
            dismissButton = {
                OutlinedButton(onClick = { codeToShowQr = null }) { Text("Close") }
            },
        )
    }

    if (showCreateDialog) {
        CreateCodeDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                shareVm.createCode(name)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun HeartCodeCard(
    heartCode: HeartCode,
    onToggle: () -> Unit,
    onShare: HeartCode,
    onDelete: () -> Unit,
    onShowQr: () -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = heartCode.enabled,
                onCheckedChange = { onToggle() },
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(heartCode.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    heartCode.code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShowQr) {
                Text("\uD83D\uDCF7", style = MaterialTheme.typography.titleMedium) // 📷 as QR trigger
            }
            IconButton(onClick = {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Listen to my heartbeat Live on HeartBeets! \uD83D\uDC93\n\nMy code: ${onShare.code}\n\nheartbeets://add/${onShare.code}",
                    )
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share HeartCode"))
            }) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Share",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
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

@Composable
private fun CreateCodeDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New HeartCode") },
        text = {
            Column {
                Text("Give this code a name (e.g. the friend's name).")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun saveQrToCache(context: Context, bitmap: Bitmap, code: String): Uri? {
    return try {
        val file = java.io.File(context.cacheDir, "qr_$code.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    } catch (_: Exception) {
        null
    }
}
