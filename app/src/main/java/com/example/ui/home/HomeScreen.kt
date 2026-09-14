package com.example.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.media.MediaMetadataHelper
import com.example.data.media.MediaRepository
import com.example.data.media.RecentMovieEntity
import com.example.domain.player.FileDetails
import com.example.ui.components.ResumePlaybackDialog
import com.example.ui.settings.FileInfoDialog
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaPurple
import com.example.ui.theme.CinemaRed
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTextMuted
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import com.example.utils.Formatters
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repository: MediaRepository,
    onPlayMovie: (uri: Uri, title: String, startPositionMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentMovies by repository.allRecentMovies.collectAsState(initial = emptyList())

    var pendingMovieForResume by remember { mutableStateOf<RecentMovieEntity?>(null) }
    var selectedFileForInfo by remember { mutableStateOf<FileDetails?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers don't support persistable permissions
            }

            scope.launch {
                val existing = repository.getMovie(uri.toString())
                val title = Formatters.getFileName(context, uri)
                if (existing != null && existing.lastPositionMs > 5000L &&
                    (existing.durationMs == 0L || existing.lastPositionMs < existing.durationMs - 10000L)
                ) {
                    pendingMovieForResume = existing
                } else {
                    onPlayMovie(uri, title, 0L)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .statusBarsPadding()
            .testTag("home_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CinemaCyan, CinemaPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Theaters,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "WatchTogether",
                            color = CinemaTextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Local Cinematic Video Player",
                            color = CinemaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Hero "OPEN MOVIE" Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CinemaSurfaceCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            filePickerLauncher.launch(
                                arrayOf("video/*", "application/octet-stream")
                            )
                        }
                        .testTag("open_movie_hero_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF131D31),
                                        Color(0xFF1E1435)
                                    )
                                )
                            )
                            .padding(24.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "OPEN MOVIE",
                                        color = CinemaCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Select Video File",
                                        color = CinemaTextPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "MKV, MP4, AVI, MOV, WebM, and any format",
                                        color = CinemaTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(CinemaCyan, CinemaPurple)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Open File Picker",
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Recent Movies Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Recent Movies",
                            color = CinemaTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (recentMovies.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(CinemaSurfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${recentMovies.size}",
                                    color = CinemaCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (recentMovies.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.testTag("clear_recents_button")
                        ) {
                            Text(
                                text = "Clear All",
                                color = CinemaTextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Empty state
            if (recentMovies.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                            .testTag("recents_empty_state"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(CinemaSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = CinemaTextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Recent Movies",
                            color = CinemaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap 'Open Movie' above to choose a video from your device storage.",
                            color = CinemaTextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                // List of recent movies
                items(recentMovies, key = { it.uriString }) { movie ->
                    RecentMovieItem(
                        movie = movie,
                        onPlayClick = {
                            val uri = Uri.parse(movie.uriString)
                            if (movie.lastPositionMs > 5000L &&
                                (movie.durationMs == 0L || movie.lastPositionMs < movie.durationMs - 10000L)
                            ) {
                                pendingMovieForResume = movie
                            } else {
                                onPlayMovie(uri, movie.fileName, 0L)
                            }
                        },
                        onInfoClick = {
                            val uri = Uri.parse(movie.uriString)
                            val details = MediaMetadataHelper.extractFileDetails(
                                context = context,
                                uri = uri,
                                knownDurationMs = movie.durationMs
                            )
                            selectedFileForInfo = details
                        },
                        onDeleteClick = {
                            scope.launch {
                                repository.deleteRecent(movie.uriString)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Resume Dialog
    pendingMovieForResume?.let { movie ->
        ResumePlaybackDialog(
            movieTitle = movie.fileName,
            resumePositionMs = movie.lastPositionMs,
            durationMs = movie.durationMs,
            onResume = {
                val uri = Uri.parse(movie.uriString)
                val pos = movie.lastPositionMs
                pendingMovieForResume = null
                onPlayMovie(uri, movie.fileName, pos)
            },
            onStartOver = {
                val uri = Uri.parse(movie.uriString)
                pendingMovieForResume = null
                onPlayMovie(uri, movie.fileName, 0L)
            },
            onDismiss = {
                pendingMovieForResume = null
            }
        )
    }

    // File Info Dialog
    selectedFileForInfo?.let { details ->
        FileInfoDialog(
            details = details,
            onDismiss = { selectedFileForInfo = null }
        )
    }

    // Clear Recents Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = CinemaSurfaceCard,
            title = {
                Text("Clear Recent Movies?", color = CinemaTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will remove the history of recently watched movies from this app. Your actual video files will not be deleted.",
                    color = CinemaTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.clearRecents()
                        }
                        showClearConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_recents_button")
                ) {
                    Text("Clear All", color = CinemaRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = CinemaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RecentMovieItem(
    movie: RecentMovieEntity,
    onPlayClick: () -> Unit,
    onInfoClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progress = if (movie.durationMs > 0) {
        (movie.lastPositionMs.toFloat() / movie.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val fileExt = movie.fileName.substringAfterLast('.', "").uppercase().take(4)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() }
            .testTag("recent_movie_item_${movie.fileName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Extension pill / badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CinemaSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (fileExt.isNotBlank()) fileExt else "VID",
                        color = CinemaCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.fileName,
                        color = CinemaTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${Formatters.formatDuration(movie.lastPositionMs)} / ${Formatters.formatDuration(movie.durationMs)}",
                            color = CinemaTextSecondary,
                            fontSize = 12.sp
                        )
                        if (movie.lastPlayedTimestamp > 0) {
                            Text(
                                text = " • ${Formatters.formatTimestamp(movie.lastPlayedTimestamp)}",
                                color = CinemaTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Action buttons
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(36.dp).testTag("recent_info_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Details",
                        tint = CinemaTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp).testTag("recent_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove",
                        tint = CinemaTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (progress > 0f) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = CinemaCyan,
                    trackColor = CinemaSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                )
            }
        }
    }
}
