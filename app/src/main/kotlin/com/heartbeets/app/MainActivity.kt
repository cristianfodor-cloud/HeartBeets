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
import com.heartbeets.app.ui.HeartCodeScreen
import com.heartbeets.app.ui.HomeScreen
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
                Box(Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(R.drawable.bg_app),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    val nav = rememberNavController()

                // Handle deep link: heartbeets://add/{code}
                LaunchedEffect(Unit) {
                    val code = extractCodeFromIntent(intent)
                    if (code != null) {
                        nav.navigate("listen/$code")
                    }
                }

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onScanClicked = { nav.navigate("scan") },
                            onListenClicked = { nav.navigate("listen/none") },
                            onHeartCodesClicked = { nav.navigate("heartcodes") },

                            onOfflineModeClicked = { nav.navigate("live/offline/offline") },
                        )
                    }
                    composable("scan") {
                        ScanScreen(
                            onDeviceSelected = { address, factoryId ->
                                nav.navigate("live/$address/$factoryId")
                            },
                            onBack = { nav.popBackStack() },
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
                        // Auto-select pack saved from SoundDesigner
                        val savedPackId = backstack.savedStateHandle.get<String>("saved_pack_id")
                        LiveHrScreen(
                            address = backstack.arguments!!.getString("address")!!,
                            factoryId = backstack.arguments!!.getString("factoryId")!!,
                            onBack = { nav.popBackStack() },
                            savedPackId = savedPackId,
                            onSavedPackConsumed = { backstack.savedStateHandle.remove<String>("saved_pack_id") },
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
                            onSaved = { packId ->
                                nav.previousBackStackEntry?.savedStateHandle?.set("saved_pack_id", packId)
                            },
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
                } // Box
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
