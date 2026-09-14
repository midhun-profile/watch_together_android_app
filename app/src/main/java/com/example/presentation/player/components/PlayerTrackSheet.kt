package com.example.presentation.player.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AudioTrack
import com.example.domain.model.PlayerUiState
import com.example.domain.model.SubtitleTrack
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import com.example.utils.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerTrackSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onSelectAudioTrack: (AudioTrack) -> Unit,
    onSelectSubtitleTrack: (SubtitleTrack?) -> Unit,
    onAddExternalSubtitle: (Uri, String) -> Unit,
    onSetAudioDelay: (Long) -> Unit,
    onSetSubtitleDelay: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Subtitles, 1 = Audio
    val context = LocalContext.current

    val subPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = Formatters.getFileName(context, it)
            onAddExternalSubtitle(it, fileName)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CinemaSurfaceCard,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CinemaSurfaceCard,
                contentColor = CinemaCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CinemaCyan
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Subtitles", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audio Tracks", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Subtitles Content
                Text(
                    text = "SELECT SUBTITLE TRACK",
                    color = CinemaCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // "Off" option
                TrackSelectionRow(
                    label = "Off (Subtitles Disabled)",
                    subLabel = "No subtitles shown",
                    isSelected = state.selectedSubtitleTrack == null,
                    onClick = { onSelectSubtitleTrack(null) }
                )

                // Subtitle tracks
                state.subtitleTracks.forEach { track ->
                    TrackSelectionRow(
                        label = track.label,
                        subLabel = "${track.language ?: "Unknown"} ${if (track.isExternal) "(External)" else "(Embedded)"}",
                        isSelected = state.selectedSubtitleTrack?.id == track.id,
                        onClick = { onSelectSubtitleTrack(track) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Add external subtitle button
                Button(
                    onClick = {
                        subPickerLauncher.launch(
                            arrayOf(
                                "application/x-subrip",
                                "text/vtt",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("add_external_sub_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CinemaCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add External Subtitle (.srt, .vtt, .ass)", color = CinemaTextPrimary, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Subtitle Delay Slider
                Text(
                    text = "Subtitle Delay: ${state.subtitleDelayMs} ms",
                    color = CinemaTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = state.subtitleDelayMs.toFloat(),
                    onValueChange = { onSetSubtitleDelay(it.toLong()) },
                    valueRange = -5000f..5000f,
                    steps = 20,
                    colors = SliderDefaults.colors(thumbColor = CinemaCyan, activeTrackColor = CinemaCyan)
                )

            } else {
                // Audio Content
                Text(
                    text = "SELECT AUDIO TRACK",
                    color = CinemaCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (state.audioTracks.isEmpty()) {
                    Text(
                        text = "Default Audio (System Selected)",
                        color = CinemaTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    state.audioTracks.forEach { track ->
                        TrackSelectionRow(
                            label = track.label,
                            subLabel = "${track.language ?: "Default"} • ${track.channels} channels • ${track.sampleRate} Hz",
                            isSelected = state.selectedAudioTrack?.id == track.id || (state.selectedAudioTrack == null && track.isSelected),
                            onClick = { onSelectAudioTrack(track) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Audio Delay Slider
                Text(
                    text = "Audio Delay: ${state.audioDelayMs} ms",
                    color = CinemaTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = state.audioDelayMs.toFloat(),
                    onValueChange = { onSetAudioDelay(it.toLong()) },
                    valueRange = -500f..500f,
                    steps = 20,
                    colors = SliderDefaults.colors(thumbColor = CinemaCyan, activeTrackColor = CinemaCyan)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TrackSelectionRow(
    label: String,
    subLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = CinemaCyan)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (isSelected) CinemaCyan else CinemaTextPrimary,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = subLabel,
                color = CinemaTextSecondary,
                fontSize = 12.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = CinemaCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
