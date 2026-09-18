package com.example.watchtogether.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaGreen
import com.example.ui.theme.CinemaPurple
import com.example.ui.theme.CinemaRed
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTextMuted
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import com.example.ui.theme.CinemaYellow
import com.example.watchtogether.model.ConnectionState
import com.example.watchtogether.model.RoomRole
import com.example.watchtogether.model.RoomState
import com.example.watchtogether.model.VideoReadinessState

@Composable
fun RoomSessionScreen(
    viewModel: WatchTogetherViewModel,
    onLeaveSession: () -> Unit,
    expectedRole: RoomRole? = null,
    onOpenLocalPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val playerState by viewModel.videoPlayer.state.collectAsState()
    val requiresUserInteraction by viewModel.syncManager.requiresUserInteraction.collectAsState()
    val activeRole = expectedRole ?: uiState.role ?: uiState.currentSession?.role ?: RoomRole.VIEWER
    val isHost = (activeRole == RoomRole.HOST) || uiState.isHost
    val context = LocalContext.current

    LaunchedEffect(expectedRole) {
        if (expectedRole != null && uiState.role != expectedRole) {
            val code = uiState.roomCode ?: ""
            if (code.isNotEmpty()) {
                viewModel.ensureSession(code, expectedRole)
            }
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }

    // System file picker for Host to select video locally
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "Movie"
            viewModel.videoPlayer.setMedia(uri, fileName, 0L)
            viewModel.videoPlayer.play()
            viewModel.notifyMediaSelected(fileName, viewModel.videoPlayer.state.value.durationMs, uri.toString())
            viewModel.syncManager.onLocalPlay()
        }
    }

    // System file picker for Viewer to select local copy of the movie
    val viewerVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "Movie"
            viewModel.onViewerSelectLocalMedia(uri, fileName)
        }
    }

    Scaffold(
        containerColor = CinemaBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Bar: Room Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaSurfaceCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Room info & code
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.participantConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = null,
                        tint = if (uiState.participantConnected) CinemaGreen else CinemaCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ROOM: ",
                                color = CinemaTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.roomCode ?: "------",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("session_room_code")
                            )
                        }
                        // Role Pill
                        Text(
                            text = if (isHost) "ROLE: HOST" else "ROLE: VIEWER",
                            color = if (isHost) CinemaCyan else CinemaPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Connection & participant status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CinemaSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when (uiState.connectionState) {
                                ConnectionState.CONNECTED -> if (uiState.participantConnected) CinemaGreen else CinemaYellow
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> CinemaYellow
                                else -> CinemaRed
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    !uiState.participantConnected -> "Waiting..."
                                    uiState.connectionState == ConnectionState.CONNECTED -> "Synced"
                                    uiState.connectionState == ConnectionState.RECONNECTING -> "Reconnecting"
                                    else -> "Connecting"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showExitDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Leave Room",
                            tint = CinemaRed
                        )
                    }
                }
            }

            if (uiState.errorMessage != null) {
                Surface(
                    color = CinemaRed.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = CinemaRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Main Content Area: Video Player / Stream Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (playerState.videoUri != null) {
                    // Both Host and Viewer display active PlayerView with full controls
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                controllerShowTimeoutMs = 3000
                                controllerHideOnTouch = true
                                viewModel.videoPlayer.attachSurfaceView(this)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(if (isHost) "host_player_view" else "viewer_player_view")
                    )

                    // Error overlay if playback fails on Viewer or Host
                    if (playerState.error != null || uiState.errorMessage != null) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurfaceCard.copy(alpha = 0.95f)),
                            modifier = Modifier
                                .padding(16.dp)
                                .border(1.dp, CinemaYellow.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = CinemaYellow,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Video Playback Notice",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = playerState.error?.message ?: uiState.errorMessage ?: "Could not decode video file",
                                    color = CinemaTextSecondary,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    if (!isHost) {
                                        Button(
                                            onClick = { viewModel.retryMediaRequest() },
                                            colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Retry Transfer", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewerVideoPickerLauncher.launch("video/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = CinemaPurple),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Select Local File", color = Color.White, fontSize = 12.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = { videoPickerLauncher.launch("video/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Pick Another Video", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (isHost) {
                    // Host hasn't picked a movie yet
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = CinemaCyan,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Select a Movie to Watch",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "The movie will play on your device and synchronize playback in real-time with your friend.",
                            color = CinemaTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                        )
                        Button(
                            onClick = {
                                if (onOpenLocalPicker != null) {
                                    onOpenLocalPicker()
                                } else {
                                    videoPickerLauncher.launch("video/*")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Choose Local Video",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                val sampleUri = Uri.parse("https://media.w3.org/2010/05/sintel/trailer.mp4")
                                val fileName = "Sintel Trailer (Sample)"
                                viewModel.videoPlayer.setMedia(sampleUri, fileName, 0L)
                                viewModel.videoPlayer.play()
                                viewModel.notifyMediaSelected(fileName, 52200L, sampleUri.toString())
                                viewModel.syncManager.onLocalPlay()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(
                                text = "Play Demo Video",
                                color = CinemaCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Viewer hasn't loaded video yet
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState.mediaTitle != null) {
                            // Host has selected movie
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CinemaSurfaceCard),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CinemaPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val isTransferring = uiState.isTransferring
                                    val isLoading = playerState.isLoading || uiState.videoState == VideoReadinessState.VIDEO_LOADING

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isTransferring || isLoading) CinemaYellow else CinemaCyan)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = when {
                                                isTransferring -> "RECEIVING VIDEO..."
                                                isLoading -> "LOADING VIDEO..."
                                                else -> "MOVIE READY TO SYNC"
                                            },
                                            color = if (isTransferring || isLoading) CinemaYellow else CinemaCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = uiState.mediaTitle ?: "Synchronized Video",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (isTransferring) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        LinearProgressIndicator(
                                            progress = { uiState.transferProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = CinemaCyan,
                                            trackColor = CinemaSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = uiState.transferStatusText.ifEmpty { "Receiving video from host..." },
                                            color = CinemaTextSecondary,
                                            fontSize = 13.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    } else if (isLoading) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        CircularProgressIndicator(
                                            color = CinemaCyan,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Preparing video playback...",
                                            color = CinemaTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        CircularProgressIndicator(
                                            color = CinemaCyan,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Connecting video stream...",
                                            color = CinemaTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewerVideoPickerLauncher.launch("video/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = CinemaPurple),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Select Local Copy", fontSize = 12.sp, color = Color.White)
                                        }
                                        Button(
                                            onClick = { viewModel.retryMediaRequest() },
                                            colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Sync, contentDescription = null, tint = CinemaCyan, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Resync", fontSize = 12.sp, color = CinemaCyan)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Waiting for host to select video
                            CircularProgressIndicator(
                                color = CinemaCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connected to Host",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Waiting for host to select and start a movie...",
                                color = CinemaTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Autoplay restriction prompt
            if (requiresUserInteraction) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CinemaPurple.copy(alpha = 0.25f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Click Play to start synchronized playback",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = { viewModel.onUserPlay() },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("autoplay_play_prompt")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Synchronized Playback Controls Bar
            if (playerState.videoUri != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CinemaSurfaceCard)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("synchronized_controls_bar")
                ) {
                    // Scrubber row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(playerState.positionMs),
                            color = CinemaTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("current_time_text")
                        )
                        val dur = playerState.durationMs.coerceAtLeast(1L)
                        var sliderPosition by remember { mutableStateOf<Float?>(null) }
                        Slider(
                            value = sliderPosition ?: (playerState.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                sliderPosition?.let { fraction ->
                                    val targetMs = (fraction * dur).toLong()
                                    viewModel.onUserSeek(targetMs)
                                }
                                sliderPosition = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("seek_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = CinemaCyan,
                                activeTrackColor = CinemaCyan,
                                inactiveTrackColor = CinemaSurfaceVariant
                            )
                        )
                        Text(
                            text = formatTime(playerState.durationMs),
                            color = CinemaTextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("duration_time_text")
                        )
                    }

                    // Transport controls: Rewind 10s, Play/Pause, Forward 10s
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.onUserRewind10s() },
                            modifier = Modifier.testTag("rewind_10s_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        IconButton(
                            onClick = { viewModel.onUserTogglePlayPause() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(CinemaCyan)
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        IconButton(
                            onClick = { viewModel.onUserForward10s() },
                            modifier = Modifier.testTag("forward_10s_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 10s",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Bottom Session Status & Controls Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaSurfaceCard)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = if (syncState.isInSync) CinemaGreen else CinemaYellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHost) {
                                "Host Mode • Play, pause, or seek to synchronize both screens"
                            } else {
                                "Viewer Mode • Play, pause, or seek to synchronize both screens"
                            },
                            color = CinemaTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (isHost) {
                        // Only Host can select or change the movie
                        TextButton(
                            onClick = { videoPickerLauncher.launch("video/*") }
                        ) {
                            Text("Change Movie", color = CinemaCyan, fontSize = 12.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { viewModel.syncManager.requestSync() },
                                modifier = Modifier.testTag("viewer_resync_button")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = CinemaCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resync", color = CinemaCyan, fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = { viewerVideoPickerLauncher.launch("video/*") },
                                modifier = Modifier.testTag("viewer_choose_video_button")
                            ) {
                                Text("Select Local Video", color = CinemaCyan, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Leave Confirmation Dialog
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Leave WatchTogether?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = if (isHost) "Leaving will end the session for both you and your friend." else "Are you sure you want to leave this WatchTogether room?",
                        color = CinemaTextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitDialog = false
                            viewModel.leaveRoom()
                            onLeaveSession()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaRed)
                    ) {
                        Text("Leave", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Cancel", color = CinemaCyan)
                    }
                },
                containerColor = CinemaSurfaceCard
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
