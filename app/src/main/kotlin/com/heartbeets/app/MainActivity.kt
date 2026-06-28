package com.heartbeets.app

import android.content.Intent
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

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onCreateClicked = { nav.navigate("creator/new") },
                            onListenClicked = { /* TODO: listen screen */ },
                            onMyHeartbeatClicked = { nav.navigate("my_heartbeats") },
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
    }
}
