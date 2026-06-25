package com.toolnagy.ringtonemanager.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toolnagy.ringtonemanager.data.api.YouTubeApiService
import com.toolnagy.ringtonemanager.data.model.ExtractionResult
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
private data class InnertubPlayabilityStatus(val status: String?, val reason: String?)
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

    /**
     * Visszaadja a hang URL-t ÉS egy részletes naplót arról, hogy melyik
     * módszer mit csinált. Így a felhasználó pontosan látja, mi a hiba.
     */
    suspend fun extractAudioStreamUrl(videoId: String): ExtractionResult = withContext(Dispatchers.IO) {
        val log = StringBuilder()

        log.appendLine("Videó ID: $videoId")
        log.appendLine("─────────────────")

        // 1. InnerTube
        log.appendLine("1) YouTube InnerTube API…")
        val innertube = extractWithInnertube(videoId, log)
        if (innertube != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(innertube, log.toString())
        }

        // 2. Piped
        log.appendLine("2) Piped instance-ok…")
        val piped = extractWithPiped(videoId, log)
        if (piped != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(piped, log.toString())
        }

        // 3. Invidious
        log.appendLine("3) Invidious instance-ok…")
        val invidious = extractWithInvidious(videoId, log)
        if (invidious != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(invidious, log.toString())
        }

        // 4. NewPipe
        log.appendLine("4) NewPipe extractor…")
        val newpipe = extractWithNewPipe(videoId, log)
        if (newpipe != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(newpipe, log.toString())
        }

        log.appendLine("─────────────────")
        log.appendLine("✗ Minden módszer megbukott.")
        ExtractionResult(null, log.toString())
    }

    private fun extractWithInnertube(videoId: String, log: StringBuilder): String? {
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
            if (!response.isSuccessful) {
                log.appendLine("   HTTP ${response.code}")
                return null
            }
            val body = response.body?.string()
            if (body == null) {
                log.appendLine("   üres válasz")
                return null
            }
            val result = gson.fromJson(body, InnertubPlayerResponse::class.java)
            val status = result.playabilityStatus?.status
            if (status != "OK") {
                log.appendLine("   playability=$status ${result.playabilityStatus?.reason ?: ""}")
                return null
            }
            val streamUrl = result.streamingData?.adaptiveFormats
                ?.filter { it.url != null && it.mimeType?.startsWith("audio") == true }
                ?.maxByOrNull { it.bitrate ?: 0 }
                ?.url
            if (streamUrl == null) {
                log.appendLine("   nincs használható audio formátum (URL titkosított?)")
            }
            streamUrl
        } catch (e: Exception) {
            log.appendLine("   ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun extractWithPiped(videoId: String, log: StringBuilder): String? {
        for (instance in PIPED_INSTANCES) {
            val host = instance.removePrefix("https://")
            try {
                val url = "$instance/streams/$videoId"
                val request = Request.Builder().url(url)
                    .addHeader("User-Agent", "RingtoneManager/1.0")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    log.appendLine("   $host → HTTP ${response.code}")
                    continue
                }
                val body = response.body?.string() ?: continue
                val result = gson.fromJson(body, PipedStreamsResponse::class.java)
                val streamUrl = result.audioStreams
                    ?.filter { it.url != null && it.mimeType?.startsWith("audio") == true }
                    ?.maxByOrNull { it.quality?.replace("[^0-9]".toRegex(), "")?.toIntOrNull() ?: 0 }
                    ?.url
                if (streamUrl != null) return streamUrl
                log.appendLine("   $host → nincs audio stream")
            } catch (e: Exception) {
                log.appendLine("   $host → ${e.javaClass.simpleName}")
            }
        }
        return null
    }

    private fun extractWithInvidious(videoId: String, log: StringBuilder): String? {
        for (instance in INVIDIOUS_INSTANCES) {
            val host = instance.removePrefix("https://")
            try {
                val url = "$instance/api/v1/videos/$videoId?fields=adaptiveFormats"
                val request = Request.Builder().url(url)
                    .addHeader("User-Agent", "RingtoneManager/1.0")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    log.appendLine("   $host → HTTP ${response.code}")
                    continue
                }
                val body = response.body?.string() ?: continue
                val result = gson.fromJson(body, InvidiousVideoResponse::class.java)
                val streamUrl = result.adaptiveFormats
                    ?.filter { it.url != null && it.type?.startsWith("audio") == true }
                    ?.maxByOrNull { it.bitrate?.toLongOrNull() ?: 0L }
                    ?.url
                if (streamUrl != null) return streamUrl
                log.appendLine("   $host → nincs audio stream")
            } catch (e: Exception) {
                log.appendLine("   $host → ${e.javaClass.simpleName}")
            }
        }
        return null
    }

    private fun extractWithNewPipe(videoId: String, log: StringBuilder): String? {
        return try {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val audioStreams: List<AudioStream> = extractor.audioStreams
            val streamUrl = audioStreams
                .sortedByDescending { it.averageBitrate }
                .firstOrNull()
                ?.let {
                    @Suppress("DEPRECATION")
                    it.content ?: it.url
                }
            if (streamUrl == null) log.appendLine("   nincs audio stream")
            streamUrl
        } catch (e: Exception) {
            log.appendLine("   ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
