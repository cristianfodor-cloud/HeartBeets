package com.heartbeets.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartbeets.app.ui.HeartBeetsTheme
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
}
