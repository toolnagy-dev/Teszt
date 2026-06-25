package com.toolnagy.ringtonemanager.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toolnagy.ringtonemanager.data.api.YouTubeApiService
import com.toolnagy.ringtonemanager.data.model.YouTubeVideo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ytDataStore by preferencesDataStore(name = "youtube_prefs")

@Singleton
class YouTubeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: YouTubeApiService
) {
    companion object {
        private val KEY_API_KEY = stringPreferencesKey("youtube_api_key")
    }

    val apiKey: Flow<String?> = context.ytDataStore.data.map { it[KEY_API_KEY] }

    suspend fun saveApiKey(key: String) {
        context.ytDataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun searchVideos(query: String): List<YouTubeVideo> {
        val key = apiKey.firstOrNull() ?: return searchWithNewPipe(query)
        return try {
            val response = apiService.search(query = query, apiKey = key)
            response.items.mapNotNull { item ->
                val videoId = item.id.videoId ?: return@mapNotNull null
                YouTubeVideo(
                    id = videoId,
                    title = item.snippet.title,
                    channelName = item.snippet.channelTitle,
                    thumbnailUrl = item.snippet.thumbnails.medium?.url ?: "",
                    durationText = ""
                )
            }
        } catch (e: Exception) {
            searchWithNewPipe(query)
        }
    }

    private suspend fun searchWithNewPipe(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val service = NewPipe.getService(0)
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .map { item ->
                    YouTubeVideo(
                        id = item.url.substringAfter("v=").substringBefore("&"),
                        title = item.name ?: "",
                        channelName = item.uploaderName ?: "",
                        thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                        durationText = ""
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun extractAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val audioStreams: List<AudioStream> = extractor.audioStreams
            audioStreams
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()
                ?.url
        } catch (e: Exception) {
            null
        }
    }
}
