package com.heartbeets.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartbeets.app.ui.HeartBeetsTheme
import com.heartbeets.app.ui.LiveHrScreen
import com.heartbeets.app.ui.ScanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartBeetsTheme {
                val nav = rememberNavController()
                NavHost(nav, startDestination = "scan") {
                    composable("scan") {
                        ScanScreen(
                            onDeviceSelected = { address, factoryId ->
                                nav.navigate("live/$address/$factoryId")
                            }
                        )
                    }
                    composable(
                        route = "live/{address}/{factoryId}",
                        arguments = listOf(
                            navArgument("address") { type = NavType.StringType },
                            navArgument("factoryId") { type = NavType.StringType },
                        ),
                    ) { backstack ->
                        LiveHrScreen(
                            address = backstack.arguments!!.getString("address")!!,
                            factoryId = backstack.arguments!!.getString("factoryId")!!,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
