package com.heartbeets.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartbeets.app.ui.HeartBeetsTheme
import com.heartbeets.app.ui.HeartbeatCreatorScreen
import com.heartbeets.app.ui.HeartbeatCreatorViewModel
import com.heartbeets.app.ui.HeartbeatCreatorViewModelFactory
import com.heartbeets.app.ui.HomeScreen
import com.heartbeets.app.ui.ListenScreen
import com.heartbeets.app.ui.MyHeartbeatsScreen


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartBeetsTheme {
                Box(Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(R.drawable.bg_app),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    val nav = rememberNavController()

                // Handle deep link heartbeets://listen/{code}
                LaunchedEffect(Unit) {
                    val code = extractCodeFromIntent(intent)
                    if (code != null) {
                        nav.navigate("listen?code=$code")
                    }
                }

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onCreateClicked = { nav.navigate("creator/new") },
                            onListenClicked = { nav.navigate("listen") },
                            onMyHeartbeatClicked = { nav.navigate("my_heartbeats") },
                        )
                    }
                    composable(
                        route = "listen?code={initialCode}",
                        arguments = listOf(
                            navArgument("initialCode") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { backstack ->
                        val initialCode = backstack.arguments?.getString("initialCode") ?: ""
                        ListenScreen(
                            onBack = { nav.popBackStack() },
                            initialCode = initialCode,
                        )
                    }
                    composable("my_heartbeats") {
                        MyHeartbeatsScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { id -> nav.navigate("creator/$id") },
                        )
                    }
                    composable(
                        route = "creator/{heartbeatId}",
                        arguments = listOf(
                            navArgument("heartbeatId") { type = NavType.StringType },
                        ),
                    ) { backstack ->
                        val heartbeatId = backstack.arguments!!.getString("heartbeatId")!!
                        val editId = if (heartbeatId == "new") null else heartbeatId
                        val vm: HeartbeatCreatorViewModel = viewModel(
                            factory = HeartbeatCreatorViewModelFactory(
                                application = application,
                                editId = editId,
                            )
                        )
                        HeartbeatCreatorScreen(
                            viewModel = vm,
                            onBack = { nav.popBackStack() },
                            onSaved = { nav.popBackStack() },
                        )
                    }
                }
                } // Box
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Deep link will be handled by LaunchedEffect on recomposition
    }

    private fun extractCodeFromIntent(intent: Intent?): String? {
        val uri: Uri = intent?.data ?: return null
        // heartbeets://listen/{code}
        if (uri.scheme == "heartbeets" && uri.host == "listen") {
            return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        return null
    }
}
