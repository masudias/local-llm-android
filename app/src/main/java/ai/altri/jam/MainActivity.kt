package ai.altri.jam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ai.altri.jam.ui.theme.LLMInferenceTheme

const val START_SCREEN = "start_screen"
const val LOAD_SCREEN = "load_screen"
const val CHAT_SCREEN = "chat_screen"
const val debug = false

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LLMInferenceTheme {
                Scaffold(
                    topBar = { AppBar() },
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                ) { innerPadding ->
                    // A surface container using the 'background' color from the theme
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        val navController = rememberNavController()

                        // Determine start destination - bypass SelectionScreen if using local model
                        val startDestination =
                            if (BuildConfig.USE_LOCAL_MODEL) {
                                // Skip to loading screen when using local model
                                LOAD_SCREEN
                            } else {
                                // Otherwise use normal flow or intent override
                                intent.getStringExtra("NAVIGATE_TO") ?: START_SCREEN
                            }

                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable(START_SCREEN) {
                                SelectionRoute(
                                    onModelSelected = {
                                        navController.navigate(LOAD_SCREEN) {
                                            popUpTo(START_SCREEN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }

                            composable(LOAD_SCREEN) {
                                LoadingRoute(
                                    onModelLoaded = {
                                        navController.navigate(CHAT_SCREEN) {
                                            popUpTo(LOAD_SCREEN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    onGoBack = {
                                        if (debug) {
                                            // go back to all model selection screen for debugging
                                            navController.navigate(START_SCREEN) {
                                                popUpTo(LOAD_SCREEN) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                            }

                            composable(CHAT_SCREEN) {
                                ChatRoute(
                                    onClose = {
                                        navController.navigate(START_SCREEN) {
                                            popUpTo(LOAD_SCREEN) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    })
                            }
                        }
                    }
                }
            }
        }
    }

    // TopAppBar is marked as experimental in Material 3
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AppBar() {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            Box(
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    text = stringResource(R.string.disclaimer),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
    }
}
