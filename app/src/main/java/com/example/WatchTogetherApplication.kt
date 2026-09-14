package com.example

import android.app.Application
import com.example.data.local.database.AppDatabase
import com.example.data.local.media.MediaStoreDataSource
import com.example.data.local.media.SafDataSource
import com.example.data.local.player.Media3PlayerManager
import com.example.data.local.player.VideoPlayer
import com.example.data.local.preferences.PlayerPreferences
import com.example.data.repository.PlaybackRepositoryImpl
import com.example.data.repository.SubtitleRepositoryImpl
import com.example.data.repository.VideoRepositoryImpl
import com.example.domain.repository.PlaybackRepository
import com.example.domain.repository.SubtitleRepository
import com.example.domain.repository.VideoRepository
import com.example.domain.usecase.GetLibraryUseCase
import com.example.domain.usecase.GetPlaybackResumeUseCase
import com.example.domain.usecase.SavePlaybackPositionUseCase
import com.example.domain.usecase.ScanVideosUseCase
import com.example.domain.usecase.ToggleFavoriteUseCase
import com.example.presentation.home.HomeViewModel
import com.example.presentation.player.PlayerViewModel
import com.example.presentation.settings.SettingsViewModel

class WatchTogetherApplication : Application() {

    lateinit var appDatabase: AppDatabase
        private set

    lateinit var videoRepository: VideoRepository
        private set

    lateinit var playbackRepository: PlaybackRepository
        private set

    lateinit var subtitleRepository: SubtitleRepository
        private set

    lateinit var playerPreferences: PlayerPreferences
        private set

    lateinit var getLibraryUseCase: GetLibraryUseCase
        private set

    lateinit var scanVideosUseCase: ScanVideosUseCase
        private set

    lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
        private set

    lateinit var getPlaybackResumeUseCase: GetPlaybackResumeUseCase
        private set

    lateinit var savePlaybackPositionUseCase: SavePlaybackPositionUseCase
        private set

    private var activeVideoPlayer: VideoPlayer? = null
    private var activePlayerViewModel: PlayerViewModel? = null

    override fun onCreate() {
        super.onCreate()

        appDatabase = AppDatabase.getInstance(this)
        val mediaStoreDataSource = MediaStoreDataSource(this)
        val safDataSource = SafDataSource(this)

        videoRepository = VideoRepositoryImpl(
            videoDao = appDatabase.videoDao(),
            safFolderDao = appDatabase.safFolderDao(),
            mediaStoreDataSource = mediaStoreDataSource,
            safDataSource = safDataSource
        )

        playbackRepository = PlaybackRepositoryImpl(
            playbackDao = appDatabase.playbackDao(),
            videoDao = appDatabase.videoDao()
        )

        subtitleRepository = SubtitleRepositoryImpl(
            subtitleDao = appDatabase.subtitleDao()
        )

        playerPreferences = PlayerPreferences(this)

        getLibraryUseCase = GetLibraryUseCase(videoRepository)
        scanVideosUseCase = ScanVideosUseCase(videoRepository)
        toggleFavoriteUseCase = ToggleFavoriteUseCase(videoRepository)
        getPlaybackResumeUseCase = GetPlaybackResumeUseCase(playbackRepository)
        savePlaybackPositionUseCase = SavePlaybackPositionUseCase(playbackRepository)
    }

    fun createHomeViewModel(): HomeViewModel {
        return HomeViewModel(
            videoRepository = videoRepository,
            getLibraryUseCase = getLibraryUseCase,
            scanVideosUseCase = scanVideosUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            getPlaybackResumeUseCase = getPlaybackResumeUseCase
        )
    }

    fun getOrCreatePlayerViewModel(): PlayerViewModel {
        val existing = activePlayerViewModel
        if (existing != null) return existing

        val player = Media3PlayerManager(
            context = this,
            preferHardwareDecoder = true
        )
        activeVideoPlayer = player

        val vm = PlayerViewModel(
            videoPlayer = player,
            savePlaybackPositionUseCase = savePlaybackPositionUseCase,
            playerPreferences = playerPreferences
        )
        activePlayerViewModel = vm
        return vm
    }

    fun createSettingsViewModel(): SettingsViewModel {
        return SettingsViewModel(
            preferences = playerPreferences,
            videoRepository = videoRepository
        )
    }

    fun releaseActivePlayer() {
        activeVideoPlayer?.release()
        activeVideoPlayer = null
        activePlayerViewModel = null
    }

    override fun onTerminate() {
        super.onTerminate()
        releaseActivePlayer()
    }
}
