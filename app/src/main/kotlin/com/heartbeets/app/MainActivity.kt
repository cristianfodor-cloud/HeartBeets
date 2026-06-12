package com.heartbeets.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartbeets.app.ui.HeartBeetsTheme
import com.heartbeets.app.ui.HeartCodeScreen
import com.heartbeets.app.ui.ListenScreen
import com.heartbeets.app.ui.LiveHrScreen
import com.heartbeets.app.ui.ScanScreen
import com.heartbeets.app.ui.ProfileCreatorScreen
import com.heartbeets.app.ui.ProfileCreatorViewModel
import com.heartbeets.app.ui.ProfileCreatorViewModelFactory
import com.heartbeets.app.ui.SoundDesignerScreen
import com.heartbeets.app.ui.SoundDesignerViewModel
import com.heartbeets.app.ui.SoundDesignerViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeartBeetsTheme {
                val nav = rememberNavController()

                // Handle deep link: heartbeets://add/{code}
                LaunchedEffect(Unit) {
                    val code = extractCodeFromIntent(intent)
                    if (code != null) {
                        nav.navigate("listen/$code")
                    }
                }

                NavHost(nav, startDestination = "scan") {
                    composable("scan") {
                        ScanScreen(
                            onDeviceSelected = { address, factoryId ->
                                nav.navigate("live/$address/$factoryId")
                            },
                            onListenClicked = { nav.navigate("listen/none") },
                            onHeartCodesClicked = { nav.navigate("heartcodes") },
                        )
                    }
                    composable(
                        route = "listen/{prefillCode}",
                        arguments = listOf(
                            navArgument("prefillCode") { type = NavType.StringType },
                        ),
                    ) { backstack ->
                        val prefillCode = backstack.arguments!!.getString("prefillCode")!!
                        ListenScreen(
                            onBack = { nav.popBackStack() },
                            prefillCode = if (prefillCode == "none") null else prefillCode,
                        )
                    }
                    composable("heartcodes") {
                        HeartCodeScreen(onBack = { nav.popBackStack() })
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
                            onOpenSoundDesigner = { packId ->
                                if (packId != null) {
                                    nav.navigate("sound_designer/$packId")
                                } else {
                                    nav.navigate("sound_designer/new")
                                }
                            },
                            onOpenProfileCreator = { profileId ->
                                nav.navigate("profile_creator/${profileId ?: "new"}")
                            },
                        )
                    }
                    composable(
                        route = "sound_designer/{packId}",
                        arguments = listOf(
                            navArgument("packId") { type = NavType.StringType },
                        ),
                    ) { backstack ->
                        val packId = backstack.arguments!!.getString("packId")!!
                        val editId = if (packId == "new") null else packId
                        val vm: SoundDesignerViewModel = viewModel(
                            factory = SoundDesignerViewModelFactory(
                                application = application,
                                editPackId = editId,
                            )
                        )
                        SoundDesignerScreen(
                            viewModel = vm,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        route = "profile_creator/{profileId}",
                        arguments = listOf(
                            navArgument("profileId") { type = NavType.StringType },
                        ),
                    ) { backstack ->
                        val profileId = backstack.arguments!!.getString("profileId")!!
                        val editId = if (profileId == "new") null else profileId
                        val vm: ProfileCreatorViewModel = viewModel(
                            factory = ProfileCreatorViewModelFactory(
                                application = application,
                                editProfileId = editId,
                            )
                        )
                        ProfileCreatorScreen(
                            viewModel = vm,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractCodeFromIntent(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        // heartbeets://add/{code}
        if (uri.scheme == "heartbeets" && uri.host == "add") {
            val code = uri.pathSegments.firstOrNull()
            if (code != null && code.length == 10) return code.uppercase()
        }
        return null
    }
}
