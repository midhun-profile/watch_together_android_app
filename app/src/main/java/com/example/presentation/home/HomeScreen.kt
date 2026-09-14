package com.example.presentation.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.domain.model.VideoItem
import com.example.domain.usecase.ResumeDecision
import com.example.presentation.components.EmptyState
import com.example.presentation.components.VideoGridCard
import com.example.presentation.components.VideoRowCard
import com.example.presentation.player.components.ResumePlaybackDialog
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaCyan
import com.example.ui.theme.CinemaPurple
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceCard
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CinemaTextMuted
import com.example.ui.theme.CinemaTextPrimary
import com.example.ui.theme.CinemaTextSecondary
import com.example.utils.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onPlayVideo: (VideoItem, Long) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val libraryState by viewModel.libraryState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Overview, 1=All Videos, 2=Folders, 3=Favorites
    var showSortMenu by remember { mutableStateOf(false) }

    // Resume Dialog state
    var pendingResumeVideo by remember { mutableStateOf<VideoItem?>(null) }
    var pendingResumePosition by remember { mutableStateOf(0L) }

    // Permission check
    val targetPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, targetPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            viewModel.rescanLibrary()
        }
    }

    // Direct single video file opener
    val singleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val name = Formatters.getFileName(context, it)
            val tempItem = VideoItem(
                uri = it.toString(),
                displayName = name,
                folderName = "Custom File"
            )
            onPlayVideo(tempItem, 0L)
        }
    }

    // SAF folder picker
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val folderName = it.lastPathSegment?.substringAfterLast(':') ?: "Custom Folder"
            viewModel.addSafFolder(it, folderName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Watch",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Together",
                            color = CinemaCyan,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CinemaSurfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PLAYER",
                                color = CinemaCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    // Rescan Library
                    IconButton(
                        onClick = { viewModel.rescanLibrary() },
                        enabled = !isScanning,
                        modifier = Modifier.testTag("rescan_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = CinemaCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Library",
                                tint = CinemaTextPrimary
                            )
                        }
                    }

                    // Open single file directly
                    IconButton(
                        onClick = { singleFilePicker.launch(arrayOf("video/*")) },
                        modifier = Modifier.testTag("open_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = "Open File",
                            tint = CinemaTextPrimary
                        )
                    }

                    // Add SAF Folder
                    IconButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.testTag("add_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Add Folder",
                            tint = CinemaTextPrimary
                        )
                    }

                    // Toggle Search
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier.testTag("search_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (showSearchBar) CinemaCyan else CinemaTextPrimary
                        )
                    }

                    // Settings
                    IconButton(
                        onClick = onNavigateSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = CinemaTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CinemaSurface)
            )
        },
        containerColor = CinemaBackground,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input Bar
            AnimatedVisibility(visible = showSearchBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search by title or folder...", color = CinemaTextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CinemaCyan) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = CinemaTextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CinemaSurfaceCard,
                            unfocusedContainerColor = CinemaSurfaceCard,
                            focusedBorderColor = CinemaCyan,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = CinemaTextPrimary,
                            unfocusedTextColor = CinemaTextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("search_text_field")
                    )
                }
            }

            // Storage Permission Warning Banner (if not granted)
            if (!hasPermission) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CinemaSurfaceCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = CinemaCyan, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Media Access Required",
                                color = CinemaTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant media permission to automatically discover videos on this device.",
                                color = CinemaTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(targetPermission) },
                            colors = ButtonDefaults.buttonColors(containerColor = CinemaCyan),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Grant", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Filter Chips & Sort row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Overview", "All Videos", "Folders", "Favorites").forEachIndexed { idx, label ->
                    FilterChip(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CinemaCyan,
                            selectedLabelColor = Color.Black,
                            containerColor = CinemaSurfaceCard,
                            labelColor = CinemaTextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box {
                    FilterChip(
                        selected = false,
                        onClick = { showSortMenu = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp), tint = CinemaCyan)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    when (sortOrder) {
                                        VideoSortOrder.NAME_ASC -> "A-Z"
                                        VideoSortOrder.NAME_DESC -> "Z-A"
                                        VideoSortOrder.DATE_ADDED_DESC -> "Newest"
                                        VideoSortOrder.SIZE_DESC -> "Largest"
                                        VideoSortOrder.DURATION_DESC -> "Longest"
                                    },
                                    color = CinemaTextPrimary
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(containerColor = CinemaSurfaceCard)
                    )

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A to Z)") },
                            onClick = {
                                viewModel.setSortOrder(VideoSortOrder.NAME_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z to A)") },
                            onClick = {
                                viewModel.setSortOrder(VideoSortOrder.NAME_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added (Newest)") },
                            onClick = {
                                viewModel.setSortOrder(VideoSortOrder.DATE_ADDED_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File Size (Largest)") },
                            onClick = {
                                viewModel.setSortOrder(VideoSortOrder.SIZE_DESC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duration (Longest)") },
                            onClick = {
                                viewModel.setSortOrder(VideoSortOrder.DURATION_DESC)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Main Content Area
            if (searchQuery.isNotBlank()) {
                // Showing Search Results
                if (searchResults.isEmpty()) {
                    EmptyState(
                        title = "No Matching Videos",
                        message = "No videos match '$searchQuery'."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "Search Results (${searchResults.size})",
                                color = CinemaCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(searchResults, key = { it.uri }) { video ->
                            VideoRowCard(
                                video = video,
                                onClick = {
                                    handleVideoClick(
                                        video = video,
                                        viewModel = viewModel,
                                        onPlayDirect = { pos -> onPlayVideo(video, pos) },
                                        onShowResumeDialog = { vid, pos ->
                                            pendingResumeVideo = vid
                                            pendingResumePosition = pos
                                        }
                                    )
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(video.uri) },
                                onShowInfo = { /* info */ },
                                onDelete = { viewModel.deleteVideo(video.uri) }
                            )
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        // Overview Tab
                        OverviewContent(
                            libraryState = libraryState,
                            onPlayVideo = { video ->
                                handleVideoClick(
                                    video = video,
                                    viewModel = viewModel,
                                    onPlayDirect = { pos -> onPlayVideo(video, pos) },
                                    onShowResumeDialog = { vid, pos ->
                                        pendingResumeVideo = vid
                                        pendingResumePosition = pos
                                    }
                                )
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it.uri) },
                            onOpenFolder = onOpenFolder,
                            onDeleteVideo = { viewModel.deleteVideo(it.uri) },
                            onOpenFile = { singleFilePicker.launch(arrayOf("video/*")) },
                            onScanMedia = { viewModel.rescanLibrary() }
                        )
                    }
                    1 -> {
                        // All Videos Tab
                        if (libraryState.allVideos.isEmpty()) {
                            EmptyState(
                                title = "Library is Empty",
                                message = "No video files found. Tap Open File to select any video directly, or scan your media storage.",
                                actionLabel = "Open Video File",
                                onActionClick = { singleFilePicker.launch(arrayOf("video/*")) }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text(
                                        text = "All Videos (${libraryState.allVideos.size})",
                                        color = CinemaCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(libraryState.allVideos, key = { it.uri }) { video ->
                                    VideoRowCard(
                                        video = video,
                                        onClick = {
                                            handleVideoClick(
                                                video = video,
                                                viewModel = viewModel,
                                                onPlayDirect = { pos -> onPlayVideo(video, pos) },
                                                onShowResumeDialog = { vid, pos ->
                                                    pendingResumeVideo = vid
                                                    pendingResumePosition = pos
                                                }
                                            )
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(video.uri) },
                                        onShowInfo = {},
                                        onDelete = { viewModel.deleteVideo(video.uri) }
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // Folders Tab
                        if (libraryState.folders.isEmpty()) {
                            EmptyState(
                                title = "No Folders Found",
                                message = "Use Add Folder to select any directory via the Storage Access Framework.",
                                actionLabel = "Add Custom Folder",
                                onActionClick = { folderPicker.launch(null) }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(libraryState.folders) { folder ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceCard),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenFolder(folder) }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(CinemaSurfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Folder, contentDescription = null, tint = CinemaCyan)
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = folder,
                                                    color = CinemaTextPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Folder containing videos",
                                                    color = CinemaTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Favorites Tab
                        if (libraryState.favorites.isEmpty()) {
                            EmptyState(
                                title = "No Favorite Videos",
                                message = "Mark videos as favorites using the star icon to access them quickly here."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                item {
                                    Text(
                                        text = "Favorites (${libraryState.favorites.size})",
                                        color = CinemaCyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(libraryState.favorites, key = { it.uri }) { video ->
                                    VideoRowCard(
                                        video = video,
                                        onClick = {
                                            handleVideoClick(
                                                video = video,
                                                viewModel = viewModel,
                                                onPlayDirect = { pos -> onPlayVideo(video, pos) },
                                                onShowResumeDialog = { vid, pos ->
                                                    pendingResumeVideo = vid
                                                    pendingResumePosition = pos
                                                }
                                            )
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(video.uri) },
                                        onShowInfo = {},
                                        onDelete = { viewModel.deleteVideo(video.uri) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Resume Dialog
    pendingResumeVideo?.let { video ->
        ResumePlaybackDialog(
            resumePositionMs = pendingResumePosition,
            onResume = {
                onPlayVideo(video, pendingResumePosition)
                pendingResumeVideo = null
            },
            onStartOver = {
                onPlayVideo(video, 0L)
                pendingResumeVideo = null
            },
            onDismiss = {
                pendingResumeVideo = null
            }
        )
    }
}

@Composable
private fun OverviewContent(
    libraryState: com.example.domain.usecase.LibraryState,
    onPlayVideo: (VideoItem) -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onOpenFolder: (String) -> Unit,
    onDeleteVideo: (VideoItem) -> Unit,
    onOpenFile: () -> Unit,
    onScanMedia: () -> Unit
) {
    if (libraryState.allVideos.isEmpty() && !libraryState.isLoading) {
        EmptyState(
            title = "No Videos Discovered",
            message = "Your library is ready. You can browse and select any video on your device, or add a custom folder.",
            actionLabel = "Open Video File",
            onActionClick = onOpenFile
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Continue Watching Row (if any)
        if (libraryState.continueWatching.isNotEmpty()) {
            item {
                SectionTitle(title = "CONTINUE WATCHING")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(libraryState.continueWatching, key = { "cw_${it.uri}" }) { video ->
                        VideoGridCard(
                            video = video,
                            onClick = { onPlayVideo(video) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Recently Added Row (if any)
        if (libraryState.recentlyAdded.isNotEmpty()) {
            item {
                SectionTitle(title = "RECENTLY ADDED")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(libraryState.recentlyAdded.take(10), key = { "ra_${it.uri}" }) { video ->
                        VideoGridCard(
                            video = video,
                            onClick = { onPlayVideo(video) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // All Videos List
        if (libraryState.allVideos.isNotEmpty()) {
            item {
                SectionTitle(title = "ALL VIDEOS (${libraryState.allVideos.size})")
            }
            items(libraryState.allVideos, key = { "all_${it.uri}" }) { video ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    VideoRowCard(
                        video = video,
                        onClick = { onPlayVideo(video) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onShowInfo = {},
                        onDelete = { onDeleteVideo(video) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = CinemaCyan,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun handleVideoClick(
    video: VideoItem,
    viewModel: HomeViewModel,
    onPlayDirect: (Long) -> Unit,
    onShowResumeDialog: (VideoItem, Long) -> Unit
) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        val decision: ResumeDecision = viewModel.checkResumeDecision(video.uri)
        if (decision.shouldPromptResume) {
            onShowResumeDialog(video, decision.positionMs)
        } else {
            onPlayDirect(video.lastPositionMs.takeIf { it > 0 } ?: 0L)
        }
    }
}
