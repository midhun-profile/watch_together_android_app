package com.example.presentation.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.ui.PlayerView
import com.example.domain.model.PlayerError
import com.example.domain.model.ResizeMode
import com.example.presentation.player.components.PlayerFileInfoDialog
import com.example.presentation.player.components.PlayerGestureOverlay
import com.example.presentation.player.components.PlayerHud
import com.example.presentation.player.components.PlayerTrackSheet
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaRed
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoUri: String,
    videoTitle: String,
    startPositionMs: Long = 0L,
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val state by viewModel.state.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var showTrackSheet by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Load media once
    LaunchedEffect(videoUri) {
        viewModel.loadVideo(Uri.parse(videoUri), videoTitle, startPositionMs)
    }

    // Auto-hide controls after 3.5s of inactivity when playing
    LaunchedEffect(showControls, state.isPlaying, state.isLocked) {
        if (showControls && state.isPlaying && !state.isLocked) {
            delay(3500L)
            showControls = false
        }
    }

    // Screen On & Immersive Mode Lifecycle
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Hide/show system bars with HUD
    LaunchedEffect(showControls) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showControls) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        if (state.isLocked) {
            viewModel.setControlsLocked(false)
        } else {
            onNavigateBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen")
    ) {
        // ExoPlayer View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    viewModel.attachPlayerView(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay
        PlayerGestureOverlay(
            isLocked = state.isLocked,
            onSingleTap = {
                showControls = !showControls
            },
            onDoubleTapSeek = { offsetMs ->
                viewModel.seekBy(offsetMs)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Player HUD Controls
        PlayerHud(
            state = state,
            visible = showControls,
            onNavigateBack = onNavigateBack,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekBy = { viewModel.seekBy(it) },
            onSeekTo = { viewModel.seekTo(it) },
            onCycleSpeed = {
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val curIdx = speeds.indexOf(state.playbackSpeed)
                val nextSpeed = if (curIdx == -1 || curIdx == speeds.lastIndex) speeds[0] else speeds[curIdx + 1]
                viewModel.setPlaybackSpeed(nextSpeed)
            },
            onCycleResizeMode = {
                val modes = ResizeMode.values()
                val nextMode = modes[(state.resizeMode.ordinal + 1) % modes.size]
                viewModel.setResizeMode(nextMode)
            },
            onOpenTrackSheet = { showTrackSheet = true },
            onOpenInfoDialog = { showInfoDialog = true },
            onEnterPiP = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                    try {
                        val params = PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build()
                        activity.enterPictureInPictureMode(params)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            onToggleLock = {
                viewModel.setControlsLocked(!state.isLocked)
                showControls = false
            }
        )

        // Error Banner Overlay if error occurs
        state.error?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = CinemaRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Playback Error",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (err) {
                            is PlayerError.UnsupportedCodec -> "Unsupported codec: ${err.message ?: "Format cannot be decoded natively"}"
                            is PlayerError.DecoderError -> "Decoder failure: ${err.message ?: "Could not initialize decoder"}"
                            is PlayerError.MissingFile -> "Media file not found or inaccessible: ${err.message}"
                            is PlayerError.CorruptedFile -> "The media file appears corrupted or invalid."
                            is PlayerError.Unknown -> err.message
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.loadVideo(Uri.parse(videoUri), videoTitle, state.positionMs)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Track and Subtitles Sheet
        if (showTrackSheet) {
            PlayerTrackSheet(
                state = state,
                onDismiss = { showTrackSheet = false },
                onSelectAudioTrack = { viewModel.selectAudioTrack(it) },
                onSelectSubtitleTrack = { viewModel.selectSubtitleTrack(it) },
                onAddExternalSubtitle = { uri, name ->
                    viewModel.addExternalSubtitle(uri, name)
                },
                onSetAudioDelay = { viewModel.setAudioDelay(it) },
                onSetSubtitleDelay = { viewModel.setSubtitleDelay(it) }
            )
        }

        // Media Info Dialog
        if (showInfoDialog) {
            PlayerFileInfoDialog(
                state = state,
                onDismiss = { showInfoDialog = false }
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
