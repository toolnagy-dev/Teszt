package com.toolnagy.ringtonemanager.ui.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolnagy.ringtonemanager.data.model.SpotifyTrack
import com.toolnagy.ringtonemanager.data.repository.SpotifyRepository
import com.toolnagy.ringtonemanager.util.RingtoneHelper
import com.toolnagy.ringtonemanager.util.SpotifyPkce
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SpotifyBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SpotifyRepository,
    private val ringtoneHelper: RingtoneHelper
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _tracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val tracks: StateFlow<List<SpotifyTrack>> = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _playingTrackId = MutableStateFlow<String?>(null)
    val playingTrackId: StateFlow<String?> = _playingTrackId.asStateFlow()

    private val _downloadingTrackId = MutableStateFlow<String?>(null)
    val downloadingTrackId: StateFlow<String?> = _downloadingTrackId.asStateFlow()

    private val _completedRingtoneUri = MutableStateFlow<Uri?>(null)
    val completedRingtoneUri: StateFlow<Uri?> = _completedRingtoneUri.asStateFlow()

    private val _authUrl = MutableStateFlow<String?>(null)
    val authUrl: StateFlow<String?> = _authUrl.asStateFlow()

    private val _clientId = MutableStateFlow("")
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private var codeVerifier: String = ""
    private var mediaPlayer: MediaPlayer? = null

    init {
        viewModelScope.launch {
            _isLoggedIn.value = repository.isLoggedIn()
            repository.clientId.collect { id -> id?.let { _clientId.value = it } }
        }

        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .filter { it.length >= 2 }
                .collectLatest { query ->
                    if (_isLoggedIn.value) search(query)
                }
        }
    }

    fun updateQuery(query: String) { _searchQuery.value = query }

    fun updateClientId(id: String) { _clientId.value = id }

    fun saveClientId() {
        viewModelScope.launch { repository.saveClientId(_clientId.value) }
    }

    fun startLogin() {
        val id = _clientId.value.trim()
        if (id.isBlank()) return
        codeVerifier = SpotifyPkce.generateCodeVerifier()
        val challenge = SpotifyPkce.generateCodeChallenge(codeVerifier)
        val state = UUID.randomUUID().toString()
        _authUrl.value = SpotifyPkce.buildAuthUrl(
            clientId = id,
            redirectUri = "ringtonemanager://callback",
            codeChallenge = challenge,
            state = state
        )
    }

    fun handleAuthCallback(code: String) {
        viewModelScope.launch {
            val success = repository.exchangeCodeForToken(
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = "ringtonemanager://callback"
            )
            _isLoggedIn.value = success
            _authUrl.value = null
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearTokens()
            _isLoggedIn.value = false
            _tracks.value = emptyList()
        }
    }

    private suspend fun search(query: String) {
        _isLoading.value = true
        _tracks.value = repository.searchTracks(query)
        _isLoading.value = false
    }

    fun togglePreview(track: SpotifyTrack) {
        val previewUrl = track.previewUrl ?: return
        if (_playingTrackId.value == track.id) {
            stopPreview()
        } else {
            stopPreview()
            _playingTrackId.value = track.id
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(previewUrl)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { _playingTrackId.value = null }
                prepareAsync()
            }
        }
    }

    fun stopPreview() {
        mediaPlayer?.release()
        mediaPlayer = null
        _playingTrackId.value = null
    }

    fun downloadAndSetRingtone(track: SpotifyTrack) {
        val previewUrl = track.previewUrl ?: return
        stopPreview()
        _downloadingTrackId.value = track.id
        viewModelScope.launch {
            val uri = ringtoneHelper.downloadAudioToRingtones(
                url = previewUrl,
                fileName = "${track.artistNames} - ${track.name}".take(60),
                onProgress = {}
            )
            _downloadingTrackId.value = null
            _completedRingtoneUri.value = uri
        }
    }

    fun consumeRingtoneUri() { _completedRingtoneUri.value = null }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}
