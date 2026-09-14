package com.example.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.player.TrackInfo
import com.example.domain.player.TrackType
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTextMuted
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionSheet(
    initialTab: Int = 0, // 0 for Audio, 1 for Subtitles
    audioTracks: List<TrackInfo>,
    selectedAudioTrackId: Int,
    subtitleTracks: List<TrackInfo>,
    selectedSubtitleTrackId: Int,
    audioDelayMs: Long,
    subtitleDelayMs: Long,
    subtitleSizeSp: Int,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onAdjustAudioDelay: (Long) -> Unit,
    onAdjustSubtitleDelay: (Long) -> Unit,
    onSelectSubtitleSize: (Int) -> Unit,
    onAddExternalSubtitle: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    val externalSubPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddExternalSubtitle(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CinemaSurfaceCard,
        modifier = Modifier.testTag("track_selection_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Tab Selector: Audio | Subtitles
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CinemaSurfaceCard,
                contentColor = CinemaCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = CinemaCyan
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Audio", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("track_tab_audio")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Subtitles", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("track_tab_subtitles")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Audio Section
                Text(
                    text = "Audio Streams",
                    color = CinemaTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (audioTracks.isEmpty()) {
                    Text(
                        text = "Default audio track playing",
                        color = CinemaTextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        items(audioTracks) { track ->
                            val isSelected = track.id == selectedAudioTrackId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) CinemaSurfaceVariant else Color.Transparent)
                                    .clickable { onSelectAudioTrack(track.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("audio_track_item_${track.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = track.name,
                                    color = if (isSelected) CinemaCyan else CinemaTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = CinemaCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = CinemaSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

                // Audio Delay Adjustment
                Text(
                    text = "Audio Sync Delay: ${audioDelayMs}ms",
                    color = CinemaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onAdjustAudioDelay(audioDelayMs - 50L) },
                        modifier = Modifier.weight(1f).testTag("audio_delay_minus_button")
                    ) {
                        Text("-50ms", color = CinemaTextPrimary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onAdjustAudioDelay(0L) },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                        modifier = Modifier.weight(1f).testTag("audio_delay_reset_button")
                    ) {
                        Text("Reset", color = CinemaCyan, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onAdjustAudioDelay(audioDelayMs + 50L) },
                        modifier = Modifier.weight(1f).testTag("audio_delay_plus_button")
                    ) {
                        Text("+50ms", color = CinemaTextPrimary, fontSize = 12.sp)
                    }
                }
            } else {
                // Subtitles Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtitle Streams",
                        color = CinemaTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    // Add external subtitle button
                    OutlinedButton(
                        onClick = {
                            externalSubPicker.launch(arrayOf("*/*", "text/*", "application/x-subrip"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("add_external_subtitle_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = CinemaCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Subtitle...", color = CinemaCyan, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    // Turn off subtitles item
                    item {
                        val isOff = selectedSubtitleTrackId == -1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isOff) CinemaSurfaceVariant else Color.Transparent)
                                .clickable { onSelectSubtitleTrack(-1) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("sub_track_off"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SubtitlesOff,
                                    contentDescription = null,
                                    tint = if (isOff) CinemaCyan else CinemaTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Disabled (Off)",
                                    color = if (isOff) CinemaCyan else CinemaTextPrimary,
                                    fontWeight = if (isOff) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                            if (isOff) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = CinemaCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    items(subtitleTracks) { track ->
                        val isSelected = track.id == selectedSubtitleTrackId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CinemaSurfaceVariant else Color.Transparent)
                                .clickable { onSelectSubtitleTrack(track.id) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("sub_track_item_${track.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = track.name,
                                color = if (isSelected) CinemaCyan else CinemaTextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = CinemaCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = CinemaSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))

                // Subtitle Delay
                Text(
                    text = "Subtitle Sync Delay: ${subtitleDelayMs}ms",
                    color = CinemaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onAdjustSubtitleDelay(subtitleDelayMs - 100L) },
                        modifier = Modifier.weight(1f).testTag("sub_delay_minus_button")
                    ) {
                        Text("-100ms", color = CinemaTextPrimary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onAdjustSubtitleDelay(0L) },
                        colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                        modifier = Modifier.weight(1f).testTag("sub_delay_reset_button")
                    ) {
                        Text("Reset", color = CinemaCyan, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { onAdjustSubtitleDelay(subtitleDelayMs + 100L) },
                        modifier = Modifier.weight(1f).testTag("sub_delay_plus_button")
                    ) {
                        Text("+100ms", color = CinemaTextPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Subtitle Size Selection
                Text(
                    text = "Subtitle Text Size",
                    color = CinemaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sizes = listOf("Small" to 14, "Normal" to 18, "Large" to 24, "Extra" to 30)
                    sizes.forEach { (label, sp) ->
                        val isChosen = subtitleSizeSp == sp
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isChosen) CinemaCyan else CinemaSurfaceVariant)
                                .clickable { onSelectSubtitleSize(sp) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isChosen) Color.Black else CinemaTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
