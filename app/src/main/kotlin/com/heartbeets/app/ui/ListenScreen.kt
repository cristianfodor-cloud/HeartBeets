package com.heartbeets.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.heartbeets.sharing.Friend
import com.heartbeets.sharing.ListenStatus
import com.heartbeets.sharing.ListenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    onBack: () -> Unit,
    prefillCode: String? = null,
    vm: ListenViewModel = viewModel(),
) {
    val status by vm.status.collectAsState()
    val bpm by vm.bpm.collectAsState()
    val error by vm.error.collectAsState()
    val friendName by vm.friendName.collectAsState()
    val friends by vm.friends.collectAsState()
    val receivedPack by vm.receivedPack.collectAsState()

    // If opened via deep link with a code, show add-friend dialog immediately
    var showAddDialogWithCode by remember { mutableStateOf(prefillCode) }

    // Toast for feedback messages (save result, errors)
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(error) {
        error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = {
                        vm.stopListening()
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (status) {
                ListenStatus.IDLE -> {
                    FriendsListView(
                        friends = friends,
                        onListen = { vm.listenTo(it) },
                        onRemove = { vm.removeFriend(it.code) },
                        onAdd = { code, name -> vm.addFriend(code, name) },
                        error = error,
                    )
                }
                ListenStatus.CONNECTING -> {
                    Spacer(Modifier.height(48.dp))
                    Text("Connecting to ${friendName ?: "friend"}…", style = MaterialTheme.typography.headlineSmall)
                }
                ListenStatus.LISTENING, ListenStatus.SIGNAL_LOST -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        friendName ?: "Friend",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "\u2022 LIVE",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(24.dp))

                    if (bpm != null) {
                        Text(
                            text = "$bpm",
                            fontSize = 96.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text("BPM", style = MaterialTheme.typography.headlineSmall)
                    } else {
                        Text("Waiting for heartbeat…", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (status == ListenStatus.SIGNAL_LOST) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Signal lost — waiting for update…",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                    OutlinedButton(onClick = { vm.stopListening() }) {
                        Text("Stop Listening")
                    }

                    // Offer to save the received sound pack
                    if (receivedPack != null) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { vm.saveReceivedPack() }) {
                            Text("Save Sound: ${receivedPack!!.displayName}")
                        }
                    }
                }
                ListenStatus.OFFLINE -> {
                    Spacer(Modifier.height(48.dp))
                    Text("${friendName ?: "Friend"} is Offline", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("They're not sharing right now. You'll see when they go live.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { vm.stopListening() }) {
                        Text("Back to Friends")
                    }
                }
                ListenStatus.ERROR -> {
                    Spacer(Modifier.height(48.dp))
                    Text("Error", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { vm.stopListening() }) {
                        Text("Back")
                    }
                }
            }
        }
    }

    // Deep link: show add-friend dialog with code pre-filled
    showAddDialogWithCode?.let { code ->
        AddFriendDialog(
            initialCode = code,
            onDismiss = { showAddDialogWithCode = null },
            onAdd = { c, name ->
                vm.addFriend(c, name)
                showAddDialogWithCode = null
            },
        )
    }
}

@Composable
private fun ColumnScope.FriendsListView(
    friends: List<Friend>,
    onListen: (Friend) -> Unit,
    onRemove: (Friend) -> Unit,
    onAdd: (code: String, name: String) -> Unit,
    error: String?,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    if (friends.isEmpty()) {
        Spacer(Modifier.height(48.dp))
        Text(
            "No friends added yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Add a friend's code to listen to their heartbeat when they're live.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(friends, key = { it.code }) { friend ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(friend.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            friend.code,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { onListen(friend) }) {
                        Text("Listen")
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onRemove(friend) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { showAddDialog = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Add Friend")
    }

    if (showAddDialog) {
        AddFriendDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { code, name ->
                onAdd(code, name)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddFriendDialog(
    initialCode: String = "",
    onDismiss: () -> Unit,
    onAdd: (code: String, name: String) -> Unit,
) {
    var code by remember { mutableStateOf(initialCode.uppercase()) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Friend") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { value ->
                        code = value.filter { it.isLetterOrDigit() }.take(10).uppercase()
                    },
                    label = { Text("Their Code (10 chars)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(code, name) },
                enabled = code.length == 10 && name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
