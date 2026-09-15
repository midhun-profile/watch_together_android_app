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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoFile
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@Composable
fun RoomSessionScreen(
    viewModel: WatchTogetherViewModel,
    onLeaveSession: () -> Unit,
    onOpenLocalPicker: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isHost = uiState.isHost
    val context = LocalContext.current

    var showExitDialog by remember { mutableStateOf(false) }

    // System file picker for Host to select video locally
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment ?: "Movie"
            viewModel.videoPlayer.setMedia(uri, fileName, 0L)
            viewModel.videoPlayer.play()
            viewModel.notifyMediaSelected(fileName, viewModel.videoPlayer.state.value.durationMs)
            viewModel.syncManager.onHostPlay()
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

            // Main Content Area: Video Player / Stream Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isHost) {
                    // HOST VIEW
                    if (uiState.mediaTitle != null) {
                        // Media is loaded: display existing PlayerView
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    viewModel.videoPlayer.attachSurfaceView(this)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("host_player_view")
                        )
                    } else {
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
                                text = "Select a Movie to Stream",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "The movie will play on your device and stream in real-time to your friend via WebRTC.",
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
                        }
                    }
                } else {
                    // VIEWER VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (uiState.mediaTitle != null) {
                            // Active movie streaming from host
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CinemaSurfaceCard),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CinemaPurple.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (syncState.isPlaying) CinemaGreen else CinemaYellow)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (syncState.isPlaying) "PLAYING (STREAMING)" else "PAUSED BY HOST",
                                            color = if (syncState.isPlaying) CinemaGreen else CinemaYellow,
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

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Timeline progress
                                    val progress = if (syncState.durationMs > 0) {
                                        (syncState.currentPositionMs.toFloat() / syncState.durationMs.toFloat()).coerceIn(0f, 1f)
                                    } else 0f

                                    LinearProgressIndicator(
                                        progress = { progress },
                                        color = CinemaCyan,
                                        trackColor = CinemaSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = formatTime(syncState.currentPositionMs),
                                            color = CinemaTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = formatTime(syncState.durationMs),
                                            color = CinemaTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Drift info pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CinemaSurfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Sync,
                                                contentDescription = null,
                                                tint = CinemaCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Sync Drift: ±${syncState.driftMs}ms (Host Authoritative)",
                                                color = CinemaCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
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
                                text = "Waiting for host to choose and start a movie...",
                                color = CinemaTextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
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
                    if (isHost) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = CinemaCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "You are the Host. Your playback controls the room.",
                                color = CinemaTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        // Host button to change movie
                        TextButton(
                            onClick = { videoPickerLauncher.launch("video/*") }
                        ) {
                            Text("Change Movie", color = CinemaCyan, fontSize = 12.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = CinemaGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync active. Playback is synchronized with the host.",
                                color = CinemaTextSecondary,
                                fontSize = 12.sp
                            )
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
