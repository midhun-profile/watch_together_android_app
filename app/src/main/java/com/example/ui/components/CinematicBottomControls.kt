package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.PlayerState
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaPurple
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import com.example.utils.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CinematicBottomControls(
    state: PlayerState,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onVolumeToggle: () -> Unit,
    onSpeedClick: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var draggingPositionMs by remember { mutableFloatStateOf(0f) }

    val currentDisplayTime = if (isDraggingSlider) {
        draggingPositionMs.toLong()
    } else {
        state.currentPositionMs
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x99050811),
                        Color(0xEE050811)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Seek bar and time labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatters.formatDuration(currentDisplayTime),
                    color = CinemaTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = Formatters.formatDuration(state.durationMs),
                    color = CinemaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Seek slider
            val sliderValue = if (state.durationMs > 0) {
                if (isDraggingSlider) {
                    (draggingPositionMs / state.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    state.progress
                }
            } else 0f

            Slider(
                value = sliderValue,
                onValueChange = { newFrac ->
                    isDraggingSlider = true
                    draggingPositionMs = newFrac * state.durationMs
                },
                onValueChangeFinished = {
                    isDraggingSlider = false
                    onSeek(draggingPositionMs.toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = CinemaCyan,
                    activeTrackColor = CinemaCyan,
                    inactiveTrackColor = Color(0x55FFFFFF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .testTag("player_seek_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Play / Pause & Quick Seek Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSeekBackward,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_seek_backward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Seek backward 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Large central play/pause button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CinemaCyan, CinemaPurple)
                            )
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onPlayPauseToggle
                        )
                        .testTag("player_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                IconButton(
                    onClick = onSeekForward,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_seek_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Seek forward 10 seconds",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Secondary controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume toggle
                IconButton(
                    onClick = onVolumeToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_volume_button")
                ) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = if (state.isMuted) "Unmute" else "Mute",
                        tint = if (state.isMuted) CinemaTextSecondary else Color.White
                    )
                }

                // Speed pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaSurfaceVariant)
                        .clickable(onClick = onSpeedClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("player_speed_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${state.playbackSpeed}x",
                        color = CinemaCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Audio track selector
                IconButton(
                    onClick = onAudioTrackClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_audio_track_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Select Audio Track",
                        tint = if (state.availableAudioTracks.size > 1) CinemaCyan else Color.White
                    )
                }

                // Subtitle selector
                IconButton(
                    onClick = onSubtitleClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_subtitles_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Select Subtitles",
                        tint = if (state.subtitlesEnabled && state.selectedSubtitleTrackId != -1) CinemaCyan else CinemaTextSecondary
                    )
                }

                // Aspect ratio toggle
                IconButton(
                    onClick = onAspectRatioClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_aspect_ratio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Aspect Ratio: ${state.aspectRatio.label}",
                        tint = Color.White
                    )
                }

                // Fullscreen toggle
                IconButton(
                    onClick = onFullscreenToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_fullscreen_button")
                ) {
                    Icon(
                        imageVector = if (state.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (state.isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
