package com.example.presentation.player.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import com.example.utils.Formatters

@Composable
fun ResumePlaybackDialog(
    resumePositionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurfaceCard,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Resume Playback?",
                color = CinemaTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "You previously watched up to ${Formatters.formatDuration(resumePositionMs)}. Would you like to resume from where you left off?",
                color = CinemaTextSecondary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan)
            ) {
                Text("Resume (${Formatters.formatDuration(resumePositionMs)})", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onStartOver) {
                Text("Start from Beginning", color = CinemaTextPrimary)
            }
        }
    )
}
