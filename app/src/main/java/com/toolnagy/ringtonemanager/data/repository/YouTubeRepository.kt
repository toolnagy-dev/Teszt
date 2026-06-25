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

// InnerTube kliens konfiguráció
private data class InnertubeClientConfig(
    val name: String,
    val clientName: String,
    val clientVersion: String,
    val clientNameId: String,
    val apiKey: String,
    val userAgent: String,
    val androidSdk: Int? = null,
    val embedUrl: String? = null
)

@Singleton
class YouTubeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: YouTubeApiService,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private val KEY_API_KEY = stringPreferencesKey("youtube_api_key")

        // Több InnerTube kliens — ha az egyik 400-at ad, a következőt próbálja
        private val INNERTUBE_CLIENTS = listOf(
            // TV embedded player — nincs PO token követelmény, legtöbb videóhoz működik
            InnertubeClientConfig(
                name = "TV_EMBEDDED",
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                clientNameId = "85",
                apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
                userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/6.0 TV Safari/538.1",
                embedUrl = "https://www.youtube.com/"
            ),
            // Android Testsuite — egyszerűsített kliens, kevesebb megszorítással
            InnertubeClientConfig(
                name = "ANDROID_TESTSUITE",
                clientName = "ANDROID_TESTSUITE",
                clientVersion = "1.9",
                clientNameId = "30",
                apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
                userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 11) gzip",
                androidSdk = 30
            ),
            // Standard Android
            InnertubeClientConfig(
                name = "ANDROID",
                clientName = "ANDROID",
                clientVersion = "19.09.37",
                clientNameId = "3",
                apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
                userAgent = "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip",
                androidSdk = 30
            ),
            // iOS
            InnertubeClientConfig(
                name = "IOS",
                clientName = "IOS",
                clientVersion = "19.09.3",
                clientNameId = "5",
                apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUA",
                userAgent = "com.google.ios.youtube/19.09.3 (iPhone14,3; U; CPU iOS 15_6 like Mac OS X)"
            )
        )

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

    suspend fun extractAudioStreamUrl(videoId: String): ExtractionResult = withContext(Dispatchers.IO) {
        val log = StringBuilder()
        log.appendLine("Videó ID: $videoId")
        log.appendLine("─────────────────")

        log.appendLine("1) YouTube InnerTube API (${INNERTUBE_CLIENTS.size} kliens)…")
        val innertubeUrl = extractWithInnertube(videoId, log)
        if (innertubeUrl != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(innertubeUrl, log.toString())
        }

        log.appendLine("2) Piped instance-ok…")
        val pipedUrl = extractWithPiped(videoId, log)
        if (pipedUrl != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(pipedUrl, log.toString())
        }

        log.appendLine("3) Invidious instance-ok…")
        val invidiousUrl = extractWithInvidious(videoId, log)
        if (invidiousUrl != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(invidiousUrl, log.toString())
        }

        log.appendLine("4) NewPipe extractor…")
        val newpipeUrl = extractWithNewPipe(videoId, log)
        if (newpipeUrl != null) {
            log.appendLine("   ✓ SIKER")
            return@withContext ExtractionResult(newpipeUrl, log.toString())
        }

        log.appendLine("─────────────────")
        log.appendLine("✗ Minden módszer megbukott.")
        ExtractionResult(null, log.toString())
    }

    private fun buildInnertubeJson(videoId: String, cfg: InnertubeClientConfig): String {
        val clientBlock = buildString {
            append(""""clientName":"${cfg.clientName}","clientVersion":"${cfg.clientVersion}","hl":"en","timeZone":"UTC","utcOffsetMinutes":0""")
            if (cfg.androidSdk != null) append(""","androidSdkVersion":${cfg.androidSdk}""")
        }
        return if (cfg.embedUrl != null) {
            """{"videoId":"$videoId","context":{"client":{$clientBlock},"thirdParty":{"embedUrl":"${cfg.embedUrl}"}}}"""
        } else {
            """{"videoId":"$videoId","context":{"client":{$clientBlock}}}"""
        }
    }

    private fun extractWithInnertube(videoId: String, log: StringBuilder): String? {
        for (cfg in INNERTUBE_CLIENTS) {
            try {
                val json = buildInnertubeJson(videoId, cfg)
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?key=${cfg.apiKey}&prettyPrint=false")
                    .post(requestBody)
                    .addHeader("User-Agent", cfg.userAgent)
                    .addHeader("X-YouTube-Client-Name", cfg.clientNameId)
                    .addHeader("X-YouTube-Client-Version", cfg.clientVersion)
                    .addHeader("Origin", "https://www.youtube.com")
                    .addHeader("Referer", "https://www.youtube.com/")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    log.appendLine("   ${cfg.name} → HTTP ${response.code}")
                    continue
                }
                val body = response.body?.string() ?: run {
                    log.appendLine("   ${cfg.name} → üres válasz")
                    continue
                }
                val result = gson.fromJson(body, InnertubPlayerResponse::class.java)
                val status = result.playabilityStatus?.status
                if (status != "OK") {
                    log.appendLine("   ${cfg.name} → $status ${result.playabilityStatus?.reason ?: ""}")
                    continue
                }
                // Előnyben részesíti az audio-only adaptiveFormats-t
                val audioUrl = result.streamingData?.adaptiveFormats
                    ?.filter { it.url != null && it.mimeType?.startsWith("audio") == true }
                    ?.maxByOrNull { it.bitrate ?: 0 }
                    ?.url
                if (audioUrl != null) return audioUrl
                // Fallback: vegyes formátumok (audio+video)
                val mixedUrl = result.streamingData?.formats
                    ?.filter { it.url != null }
                    ?.maxByOrNull { it.bitrate ?: 0 }
                    ?.url
                if (mixedUrl != null) return mixedUrl
                log.appendLine("   ${cfg.name} → OK státusz, de nincs stream URL")
            } catch (e: Exception) {
                log.appendLine("   ${cfg.name} → ${e.javaClass.simpleName}: ${e.message?.take(80)}")
            }
        }
        return null
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
            log.appendLine("   ${e.javaClass.simpleName}: ${e.message?.take(100)}")
            null
        }
    }
}
