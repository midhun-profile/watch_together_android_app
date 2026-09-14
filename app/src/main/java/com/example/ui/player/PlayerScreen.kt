package com.example.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.media.MediaMetadataHelper
import com.example.domain.player.FileDetails
import com.example.player.PlayerController
import com.example.player.PlayerEvent
import com.example.ui.components.CinematicBottomControls
import com.example.ui.components.CinematicTopBar
import com.example.ui.components.DoubleTapSeekOverlay
import com.example.ui.components.TrackSelectionSheet
import com.example.ui.settings.FileInfoDialog
import com.example.ui.settings.VideoSettingsSheet
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaRed
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(
    controller: PlayerController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val state by controller.state.collectAsState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var showTrackSheet by remember { mutableStateOf(false) }
    var trackSheetTab by remember { mutableIntStateOf(0) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf(false) }

    var showSeekLeftIndicator by remember { mutableStateOf(false) }
    var showSeekRightIndicator by remember { mutableStateOf(false) }

    // Auto-hide controls after 3.5 seconds of user inactivity when playing
    LaunchedEffect(areControlsVisible, state.isPlaying) {
        if (areControlsVisible && state.isPlaying) {
            delay(3500)
            areControlsVisible = false
        }
    }

    // React to one-off controller events
    LaunchedEffect(controller) {
        controller.events.collect { event ->
            when (event) {
                is PlayerEvent.SeekFeedback -> {
                    if (event.isForward) {
                        showSeekRightIndicator = true
                        delay(650)
                        showSeekRightIndicator = false
                    } else {
                        showSeekLeftIndicator = true
                        delay(650)
                        showSeekLeftIndicator = false
                    }
                }
                is PlayerEvent.PlaybackEnded -> {
                    areControlsVisible = true
                }
                else -> {}
            }
        }
    }

    // Fullscreen system bars and orientation management
    DisposableEffect(state.isFullscreen) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            if (state.isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen")
    ) {
        // Embedded VLCVideoLayout
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    controller.attachLayout(this)
                }
            },
            onRelease = {
                controller.detachLayout()
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("vlc_video_layout")
        )

        // Gesture Overlay for single tap (toggle controls) and double tap (seek)
        DoubleTapSeekOverlay(
            onSingleTap = {
                areControlsVisible = !areControlsVisible
            },
            onDoubleTapLeft = {
                controller.seekBackward(10_000L)
            },
            onDoubleTapRight = {
                controller.seekForward(10_000L)
            },
            showIndicatorLeft = showSeekLeftIndicator,
            showIndicatorRight = showSeekRightIndicator
        )

        // Buffering indicator
        if (state.isBuffering && state.errorMessage == null) {
            CircularProgressIndicator(
                color = CinemaCyan,
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
                    .testTag("player_buffering_indicator")
            )
        }

        // Error Banner
        if (state.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE090D16))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(CinemaSurfaceCard)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = CinemaRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Playback Error",
                        color = CinemaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.errorMessage ?: "Unable to play this video file. The media container or codec may be unsupported or corrupted.",
                        color = CinemaTextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = {
                            controller.clearError()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("player_error_back_button")
                    ) {
                        Text("Choose Another Movie", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Top Bar (Animated Visibility)
        AnimatedVisibility(
            visible = areControlsVisible && state.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            CinematicTopBar(
                title = state.currentMedia?.title ?: "Video Player",
                onBackClick = {
                    controller.pause()
                    onNavigateBack()
                },
                onSettingsClick = {
                    showSettingsSheet = true
                },
                onInfoClick = {
                    showFileInfoDialog = true
                }
            )
        }

        // Bottom Controls (Animated Visibility)
        AnimatedVisibility(
            visible = areControlsVisible && state.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CinematicBottomControls(
                state = state,
                onPlayPauseToggle = { controller.togglePlayPause() },
                onSeek = { targetMs -> controller.seekTo(targetMs) },
                onSeekBackward = { controller.seekBackward(10_000L) },
                onSeekForward = { controller.seekForward(10_000L) },
                onVolumeToggle = { controller.toggleMute() },
                onSpeedClick = {
                    val current = state.playbackSpeed
                    val next = when (current) {
                        0.5f -> 0.75f
                        0.75f -> 1.0f
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 1.75f
                        1.75f -> 2.0f
                        else -> 1.0f
                    }
                    controller.setPlaybackSpeed(next)
                },
                onAudioTrackClick = {
                    trackSheetTab = 0
                    showTrackSheet = true
                },
                onSubtitleClick = {
                    trackSheetTab = 1
                    showTrackSheet = true
                },
                onAspectRatioClick = {
                    val next = when (state.aspectRatio) {
                        com.example.domain.player.AspectRatioMode.FIT -> com.example.domain.player.AspectRatioMode.FILL
                        com.example.domain.player.AspectRatioMode.FILL -> com.example.domain.player.AspectRatioMode.ORIGINAL
                        com.example.domain.player.AspectRatioMode.ORIGINAL -> com.example.domain.player.AspectRatioMode.RATIO_16_9
                        com.example.domain.player.AspectRatioMode.RATIO_16_9 -> com.example.domain.player.AspectRatioMode.RATIO_4_3
                        com.example.domain.player.AspectRatioMode.RATIO_4_3 -> com.example.domain.player.AspectRatioMode.FIT
                    }
                    controller.setAspectRatio(next)
                },
                onFullscreenToggle = { controller.toggleFullscreen() }
            )
        }
    }

    // Track Selection Sheet (Audio / Subtitles)
    if (showTrackSheet) {
        TrackSelectionSheet(
            initialTab = trackSheetTab,
            audioTracks = state.availableAudioTracks,
            selectedAudioTrackId = state.selectedAudioTrackId,
            subtitleTracks = state.availableSubtitleTracks,
            selectedSubtitleTrackId = state.selectedSubtitleTrackId,
            audioDelayMs = state.audioDelayMs,
            subtitleDelayMs = state.subtitleDelayMs,
            subtitleSizeSp = state.subtitleSizeSp,
            onSelectAudioTrack = { controller.setAudioTrack(it) },
            onSelectSubtitleTrack = { controller.setSubtitleTrack(it) },
            onAdjustAudioDelay = { controller.setAudioDelay(it) },
            onAdjustSubtitleDelay = { controller.setSubtitleDelay(it) },
            onSelectSubtitleSize = { controller.setSubtitleSize(it) },
            onAddExternalSubtitle = { controller.addExternalSubtitle(it) },
            onDismiss = { showTrackSheet = false }
        )
    }

    // Video Settings Sheet
    if (showSettingsSheet) {
        VideoSettingsSheet(
            currentSpeed = state.playbackSpeed,
            currentAspectRatio = state.aspectRatio,
            currentHw = state.hardwareAcceleration,
            volume = state.volume,
            onSpeedChange = { controller.setPlaybackSpeed(it) },
            onAspectRatioChange = { controller.setAspectRatio(it) },
            onHwChange = { controller.setHardwareAcceleration(it) },
            onVolumeChange = { controller.setVolume(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // File Info Dialog
    if (showFileInfoDialog) {
        val media = state.currentMedia
        val details: FileDetails = if (media != null) {
            MediaMetadataHelper.extractFileDetails(
                context = context,
                uri = media.uri,
                knownDurationMs = state.durationMs,
                audioCount = state.availableAudioTracks.size,
                subtitleCount = state.availableSubtitleTracks.size
            )
        } else {
            FileDetails(
                fileName = "No Media",
                uriString = "",
                container = "Unknown",
                durationFormatted = "00:00",
                fileSizeFormatted = "0 B"
            )
        }

        FileInfoDialog(
            details = details,
            onDismiss = { showFileInfoDialog = false }
        )
    }
}
