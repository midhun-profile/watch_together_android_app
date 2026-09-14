package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.domain.model.VideoItem
import com.example.presentation.home.HomeScreen
import com.example.presentation.library.FolderVideosScreen
import com.example.presentation.player.PlayerScreen
import com.example.presentation.settings.SettingsScreen
import com.example.ui.theme.WatchTogetherTheme
import com.example.utils.Formatters

sealed class AppDestination {
    object Home : AppDestination()
    data class Player(val videoUri: String, val title: String, val startPositionMs: Long) : AppDestination()
    data class Folder(val folderName: String) : AppDestination()
    object Settings : AppDestination()
}

class MainActivity : ComponentActivity() {

    private var currentDestination by mutableStateOf<AppDestination>(AppDestination.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WatchTogetherApplication
        handleIncomingIntent(intent, app)

        setContent {
            WatchTogetherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val homeViewModel = remember { app.createHomeViewModel() }

                    when (val dest = currentDestination) {
                        is AppDestination.Home -> {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onPlayVideo = { video, startPos ->
                                    currentDestination = AppDestination.Player(
                                        videoUri = video.uri,
                                        title = video.displayName,
                                        startPositionMs = startPos
                                    )
                                },
                                onOpenFolder = { folderName ->
                                    currentDestination = AppDestination.Folder(folderName)
                                },
                                onNavigateSettings = {
                                    currentDestination = AppDestination.Settings
                                }
                            )
                        }

                        is AppDestination.Player -> {
                            val playerViewModel = remember { app.getOrCreatePlayerViewModel() }

                            BackHandler {
                                playerViewModel.pause()
                                currentDestination = AppDestination.Home
                            }

                            PlayerScreen(
                                videoUri = dest.videoUri,
                                videoTitle = dest.title,
                                startPositionMs = dest.startPositionMs,
                                viewModel = playerViewModel,
                                onNavigateBack = {
                                    playerViewModel.pause()
                                    currentDestination = AppDestination.Home
                                }
                            )
                        }

                        is AppDestination.Folder -> {
                            BackHandler {
                                currentDestination = AppDestination.Home
                            }

                            FolderVideosScreen(
                                folderName = dest.folderName,
                                videoRepository = app.videoRepository,
                                onNavigateBack = {
                                    currentDestination = AppDestination.Home
                                },
                                onPlayVideo = { video ->
                                    currentDestination = AppDestination.Player(
                                        videoUri = video.uri,
                                        title = video.displayName,
                                        startPositionMs = video.lastPositionMs.takeIf { it > 0 } ?: 0L
                                    )
                                },
                                onToggleFavorite = { video ->
                                    homeViewModel.toggleFavorite(video.uri)
                                },
                                onShowInfo = { /* info */ },
                                onDeleteVideo = { video ->
                                    homeViewModel.deleteVideo(video.uri)
                                }
                            )
                        }

                        is AppDestination.Settings -> {
                            val settingsViewModel = remember { app.createSettingsViewModel() }

                            BackHandler {
                                currentDestination = AppDestination.Home
                            }

                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = {
                                    currentDestination = AppDestination.Home
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val app = application as? WatchTogetherApplication ?: return
        handleIncomingIntent(intent, app)
    }

    private fun handleIncomingIntent(intent: Intent?, app: WatchTogetherApplication) {
        val uri = intent?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        val name = Formatters.getFileName(this, uri)
        currentDestination = AppDestination.Player(
            videoUri = uri.toString(),
            title = name,
            startPositionMs = 0L
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val isPlayer = currentDestination is AppDestination.Player

        if (isPlayer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPip = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (hasPip) {
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    enterPictureInPictureMode(params)
                } catch (_: Exception) {
                }
            }
        }
    }
}

// Kept for screenshot test backward-compatibility
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    WatchTogetherTheme { Greeting("Android") }
}
