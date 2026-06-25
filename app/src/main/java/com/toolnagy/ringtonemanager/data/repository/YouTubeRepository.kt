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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

private val Context.ytDataStore by preferencesDataStore(name = "youtube_prefs")

// InnerTube API models
private data class InnertubPlayerResponse(
    val streamingData: InnertubStreamingData?,
    val playabilityStatus: InnertubPlayabilityStatus?
)
private data class InnertubPlayabilityStatus(val status: String?)
private data class InnertubStreamingData(
    val adaptiveFormats: List<InnertubFormat>?,
    val formats: List<InnertubFormat>?
)
private data class InnertubFormat(
    val url: String?,
    val mimeType: String?,
    val bitrate: Int?,
    val audioQuality: String?
)

// Piped API models
private data class PipedStreamsResponse(val audioStreams: List<PipedAudioStream>?)
private data class PipedAudioStream(val url: String?, val quality: String?, val mimeType: String?)
private data class PipedSearchResponse(val items: List<PipedSearchItem>?)
private data class PipedSearchItem(
    val url: String?, val title: String?, val uploaderName: String?,
    val thumbnail: String?, val type: String?
)

// Invidious API models
private data class InvidiousVideoResponse(val adaptiveFormats: List<InvidiousAdaptiveFormat>?)
private data class InvidiousAdaptiveFormat(val url: String?, val type: String?, val bitrate: String?)

@Singleton
class YouTubeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: YouTubeApiService,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private val KEY_API_KEY = stringPreferencesKey("youtube_api_key")

        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://piped-api.garudalinux.org",
            "https://pipedapi.darkness.services",
            "https://pipedapi.tokhmi.xyz",
            "https://piped-api.privacy.com.de"
        )

        private val INVIDIOUS_INSTANCES = listOf(
            "https://inv.riverside.rocks",
            "https://invidious.tiekoetter.com",
            "https://y.com.sb",
            "https://invidious.privacydev.net",
            "https://invidious.lunar.icu"
        )
    }

    private val gson = Gson()

    val apiKey: Flow<String?> = context.ytDataStore.data.map { it[KEY_API_KEY] }

    suspend fun saveApiKey(key: String) {
        context.ytDataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun searchVideos(query: String): List<YouTubeVideo> {
        val key = apiKey.firstOrNull()
        if (!key.isNullOrBlank()) {
            try {
                val response = apiService.search(query = query, apiKey = key)
                val results = response.items.mapNotNull { item ->
                    val videoId = item.id.videoId ?: return@mapNotNull null
                    YouTubeVideo(
                        id = videoId,
                        title = item.snippet.title,
                        channelName = item.snippet.channelTitle,
                        thumbnailUrl = item.snippet.thumbnails.medium?.url ?: "",
                        durationText = ""
                    )
                }
                if (results.isNotEmpty()) return results
            } catch (_: Exception) {}
        }

        val pipedResults = searchWithPiped(query)
        if (pipedResults.isNotEmpty()) return pipedResults

        return searchWithNewPipe(query)
    }

    private suspend fun searchWithPiped(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        for (instance in PIPED_INSTANCES) {
            try {
                val url = "$instance/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&filter=music_songs"
                val request = Request.Builder().url(url)
                    .addHeader("User-Agent", "RingtoneManager/1.0")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) continue
                val body = response.body?.string() ?: continue
                val result = gson.fromJson(body, PipedSearchResponse::class.java)
                val items = result.items
                    ?.filter { it.type == "stream" && it.url != null }
                    ?.map { item ->
                        val videoId = item.url!!.substringAfter("v=").substringBefore("&")
                        YouTubeVideo(
                            id = videoId,
                            title = item.title ?: "",
                            channelName = item.uploaderName ?: "",
                            thumbnailUrl = item.thumbnail ?: "",
                            durationText = ""
                        )
                    } ?: continue
                if (items.isNotEmpty()) return@withContext items
            } catch (_: Exception) {}
        }
        emptyList()
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun extractAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val innertubeUrl = extractWithInnertube(videoId)
        if (innertubeUrl != null) return@withContext innertubeUrl

        val pipedUrl = extractWithPiped(videoId)
        if (pipedUrl != null) return@withContext pipedUrl

        val invidiousUrl = extractWithInvidious(videoId)
        if (invidiousUrl != null) return@withContext invidiousUrl

        extractWithNewPipe(videoId)
    }

    private fun extractWithInnertube(videoId: String): String? {
        return try {
            val json = """{"videoId":"$videoId","context":{"client":{"clientName":"ANDROID","clientVersion":"17.31.35","androidSdkVersion":30,"hl":"en","timeZone":"UTC","utcOffsetMinutes":0}}}"""
            val requestBody = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8&prettyPrint=false")
                .post(requestBody)
                .addHeader("User-Agent", "com.google.android.youtube/17.31.35 (Linux; U; Android 11) gzip")
                .addHeader("X-YouTube-Client-Name", "3")
                .addHeader("X-YouTube-Client-Version", "17.31.35")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val result = gson.fromJson(body, InnertubPlayerResponse::class.java)
            if (result.playabilityStatus?.status != "OK") return null
            result.streamingData?.adaptiveFormats
                ?.filter { it.url != null && it.mimeType?.startsWith("audio") == true }
                ?.maxByOrNull { it.bitrate ?: 0 }
                ?.url
        } catch (_: Exception) {
            null
        }
    }

    private fun extractWithPiped(videoId: String): String? {
        for (instance in PIPED_INSTANCES) {
            try {
                val url = "$instance/streams/$videoId"
                val request = Request.Builder().url(url)
                    .addHeader("User-Agent", "RingtoneManager/1.0")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) continue
                val body = response.body?.string() ?: continue
                val result = gson.fromJson(body, PipedStreamsResponse::class.java)
                val streamUrl = result.audioStreams
                    ?.filter { it.url != null && it.mimeType?.startsWith("audio") == true }
                    ?.maxByOrNull { it.quality?.replace("[^0-9]".toRegex(), "")?.toIntOrNull() ?: 0 }
                    ?.url
                if (streamUrl != null) return streamUrl
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractWithInvidious(videoId: String): String? {
        for (instance in INVIDIOUS_INSTANCES) {
            try {
                val url = "$instance/api/v1/videos/$videoId?fields=adaptiveFormats"
                val request = Request.Builder().url(url)
                    .addHeader("User-Agent", "RingtoneManager/1.0")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) continue
                val body = response.body?.string() ?: continue
                val result = gson.fromJson(body, InvidiousVideoResponse::class.java)
                val streamUrl = result.adaptiveFormats
                    ?.filter { it.url != null && it.type?.startsWith("audio") == true }
                    ?.maxByOrNull { it.bitrate?.toLongOrNull() ?: 0L }
                    ?.url
                if (streamUrl != null) return streamUrl
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractWithNewPipe(videoId: String): String? {
        return try {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val audioStreams: List<AudioStream> = extractor.audioStreams
            audioStreams
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()
                ?.let {
                    @Suppress("DEPRECATION")
                    it.content ?: it.url
                }
        } catch (_: Exception) {
            null
        }
    }
}
