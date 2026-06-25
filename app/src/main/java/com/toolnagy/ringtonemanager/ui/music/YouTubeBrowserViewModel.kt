package com.toolnagy.ringtonemanager.ui.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolnagy.ringtonemanager.data.model.DownloadState
import com.toolnagy.ringtonemanager.data.model.DownloadStatus
import com.toolnagy.ringtonemanager.data.model.YouTubeVideo
import com.toolnagy.ringtonemanager.data.repository.YouTubeRepository
import com.toolnagy.ringtonemanager.util.RingtoneHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class YouTubeBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YouTubeRepository,
    private val ringtoneHelper: RingtoneHelper
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _videos = MutableStateFlow<List<YouTubeVideo>>(emptyList())
    val videos: StateFlow<List<YouTubeVideo>> = _videos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _completedRingtoneUri = MutableStateFlow<Uri?>(null)
    val completedRingtoneUri: StateFlow<Uri?> = _completedRingtoneUri.asStateFlow()

    // Részletes hibanapló a felugró ablakhoz
    private val _errorLog = MutableStateFlow<String?>(null)
    val errorLog: StateFlow<String?> = _errorLog.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private val _playingVideoId = MutableStateFlow<String?>(null)
    val playingVideoId: StateFlow<String?> = _playingVideoId.asStateFlow()

    init {
        viewModelScope.launch {
            repository.apiKey.collect { key -> key?.let { _apiKey.value = it } }
        }

        viewModelScope.launch {
            _searchQuery
                .debounce(600)
                .filter { it.length >= 3 }
                .collectLatest { query -> search(query) }
        }
    }

    fun updateQuery(query: String) { _searchQuery.value = query }

    fun updateApiKey(key: String) { _apiKey.value = key }

    fun saveApiKey() {
        viewModelScope.launch {
            repository.saveApiKey(_apiKey.value)
            val query = _searchQuery.value
            if (query.length >= 3) search(query)
        }
    }

    private suspend fun search(query: String) {
        _isLoading.value = true
        _videos.value = repository.searchVideos(query)
        _isLoading.value = false
    }

    fun downloadAndSetRingtone(video: YouTubeVideo) {
        viewModelScope.launch {
            val streamUrl: String
            if (video.directAudioUrl != null) {
                // Közvetlen letölthető URL (pl. iTunes előnézet) – nincs szükség kinyerésre
                streamUrl = video.directAudioUrl
            } else {
                updateDownloadState(video.id, DownloadStatus.EXTRACTING, 0f)
                val extraction = repository.extractAudioStreamUrl(video.id)
                val extracted = extraction.url
                if (extracted == null) {
                    updateDownloadState(video.id, DownloadStatus.ERROR, 0f, "Nem sikerült kinyerni a hangot")
                    _errorLog.value = "HANG KINYERÉSE SIKERTELEN\n\n${extraction.log}"
                    return@launch
                }
                streamUrl = extracted
            }

            updateDownloadState(video.id, DownloadStatus.DOWNLOADING, 0f)

            val result = ringtoneHelper.downloadAudioToRingtones(
                url = streamUrl,
                fileName = video.title.take(60),
                onProgress = { progress -> updateDownloadState(video.id, DownloadStatus.DOWNLOADING, progress) }
            )

            if (result.uri != null) {
                updateDownloadState(video.id, DownloadStatus.DONE, 1f, localPath = result.uri.toString())
                _completedRingtoneUri.value = result.uri
            } else {
                updateDownloadState(video.id, DownloadStatus.ERROR, 0f, "Letöltés sikertelen")
                _errorLog.value = "LETÖLTÉS SIKERTELEN\n\n" +
                    "A hang URL megvolt, de a letöltés/mentés nem sikerült:\n\n" +
                    "${result.error}\n\n" +
                    "Forrás URL:\n${streamUrl.take(120)}…"
            }
        }
    }

    fun dismissError() { _errorLog.value = null }

    private fun updateDownloadState(
        videoId: String,
        status: DownloadStatus,
        progress: Float,
        error: String? = null,
        localPath: String? = null
    ) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[videoId] = DownloadState(videoId, progress, status, localPath, error)
        }
    }

    fun consumeRingtoneUri() { _completedRingtoneUri.value = null }

    fun stopPreview() {
        mediaPlayer?.release()
        mediaPlayer = null
        _playingVideoId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}
