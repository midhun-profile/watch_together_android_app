package com.example.presentation.player.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PlayerUiState
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTextMuted
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import com.example.utils.Formatters

@Composable
fun PlayerFileInfoDialog(
    state: PlayerUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurfaceCard,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Media Technical Details",
                color = CinemaTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                InfoItem("Title", state.videoTitle)
                InfoItem("Duration", Formatters.formatDuration(state.durationMs))
                InfoItem(
                    "Selected Audio",
                    state.selectedAudioTrack?.let { "${it.label} (${it.language ?: "Default"})" } ?: "System Audio"
                )
                InfoItem(
                    "Selected Subtitle",
                    state.selectedSubtitleTrack?.let { "${it.label} (${it.language ?: "Default"})" } ?: "Off"
                )
                InfoItem("Available Audio Tracks", "${state.audioTracks.size}")
                InfoItem("Available Subtitles", "${state.subtitleTracks.size}")
                InfoItem("Playback Speed", "${state.playbackSpeed}x")
                InfoItem("Resize Mode", state.resizeMode.name)
                InfoItem("Location URI", state.videoUri.orEmpty(), isMonospace = true)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan)
            ) {
                Text("Close", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun InfoItem(label: String, value: String, isMonospace: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = CinemaTextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = value.ifBlank { "N/A" },
            color = CinemaTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(2.dp))
        Divider(color = CinemaSurfaceVariant, thickness = 0.5.dp)
    }
}
