package com.toolnagy.ringtonemanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toolnagy.ringtonemanager.data.repository.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val youTubeRepository: YouTubeRepository
) : ViewModel() {

    private val _youtubeApiKey = MutableStateFlow("")
    val youtubeApiKey: StateFlow<String> = _youtubeApiKey.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        viewModelScope.launch {
            youTubeRepository.apiKey.collect { key ->
                key?.let { _youtubeApiKey.value = it }
            }
        }
    }

    fun updateYouTubeApiKey(key: String) { _youtubeApiKey.value = key }

    fun saveYouTubeApiKey() {
        viewModelScope.launch {
            youTubeRepository.saveApiKey(_youtubeApiKey.value.trim())
            _saved.value = true
        }
    }

    fun clearSaved() { _saved.value = false }
}
