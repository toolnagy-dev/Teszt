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

// iTunes Search API models (ingyenes, kulcs nélkül, közvetlen 30s előnézet)
private data class ItunesResponse(val results: List<ItunesTrack>?)
private data class ItunesTrack(
    val trackId: Long?,
    val trackName: String?,
    val artistName: String?,
    val artworkUrl100: String?,
    val previewUrl: String?
)

// InnerTube kliens konfiguráció
private data class InnertubeClientConfig(
    val name: String,
    val clientName: String,
    val clientVersion: String,
    val clientNameId: String,
    val apiKey: String,
    val userAgent: String,
    val androidSdk: Int? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
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

        // Több InnerTube kliens — ha az egyik nem megy, a következőt próbálja.
        // ANDROID_VR az első: 2024-2025-ben ez a legmegbízhatóbb, nincs PO token követelmény.
        private val INNERTUBE_CLIENTS = listOf(
            // Android VR (Oculus Quest) — jelenleg a legmegbízhatóbb, nincs PO token
            InnertubeClientConfig(
                name = "ANDROID_VR",
                clientName = "ANDROID_VR",
                clientVersion = "1.60.19",
                clientNameId = "28",
                apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; en_US; Quest 3 Build/SQ3A.220605.009.A1) gzip",
                androidSdk = 32,
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12L"
            ),
            // iOS — friss verzió
            InnertubeClientConfig(
                name = "IOS",
                clientName = "IOS",
                clientVersion = "19.45.4",
                clientNameId = "5",
                apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUA",
                userAgent = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iPhone",
                osVersion = "18.1.0.22B83"
            ),
            // Mobil web
            InnertubeClientConfig(
                name = "MWEB",
                clientName = "MWEB",
                clientVersion = "2.20241202.07.00",
                clientNameId = "2",
                apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
                userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
            ),
            // TV HTML5 — friss verzió
            InnertubeClientConfig(
                name = "TVHTML5",
                clientName = "TVHTML5",
                clientVersion = "7.20241201.18.00",
                clientNameId = "7",
                apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
                userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
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
        // 1. iTunes – megbízható, közvetlen letölthető 30s előnézet (csengőhangnak ideális)
        val itunesResults = searchWithItunes(query)
        if (itunesResults.isNotEmpty()) return itunesResults

        // 2. YouTube Data API (ha van kulcs) – csak ha az iTunes nem adott találatot
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

        // 3. Piped / NewPipe fallback
        val pipedResults = searchWithPiped(query)
        if (pipedResults.isNotEmpty()) return pipedResults

        return searchWithNewPipe(query)
    }

    private suspend fun searchWithItunes(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://itunes.apple.com/search?term=${java.net.URLEncoder.encode(query, "UTF-8")}&media=music&entity=song&limit=25"
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", "RingtoneManager/1.0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val result = gson.fromJson(body, ItunesResponse::class.java)
            result.results
                ?.filter { it.previewUrl != null && it.trackId != null }
                ?.map { track ->
                    YouTubeVideo(
                        id = "itunes_${track.trackId}",
                        title = track.trackName ?: "",
                        channelName = track.artistName ?: "",
                        // Nagyobb borítókép: 100x100 → 300x300
                        thumbnailUrl = track.artworkUrl100?.replace("100x100", "300x300") ?: "",
                        durationText = "0:30",
                        directAudioUrl = track.previewUrl
                    )
                } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
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
            append(""""clientName":"${cfg.clientName}","clientVersion":"${cfg.clientVersion}","hl":"en","gl":"US","timeZone":"UTC","utcOffsetMinutes":0""")
            if (cfg.androidSdk != null) append(""","androidSdkVersion":${cfg.androidSdk}""")
            if (cfg.deviceMake != null) append(""","deviceMake":"${cfg.deviceMake}"""")
            if (cfg.deviceModel != null) append(""","deviceModel":"${cfg.deviceModel}"""")
            if (cfg.osName != null) append(""","osName":"${cfg.osName}"""")
            if (cfg.osVersion != null) append(""","osVersion":"${cfg.osVersion}"""")
        }
        val tail = ""","contentCheckOk":true,"racyCheckOk":true"""
        return if (cfg.embedUrl != null) {
            """{"videoId":"$videoId","context":{"client":{$clientBlock},"thirdParty":{"embedUrl":"${cfg.embedUrl}"}}$tail}"""
        } else {
            """{"videoId":"$videoId","context":{"client":{$clientBlock}}$tail}"""
        }
    }

    private fun extractWithInnertube(videoId: String, log: StringBuilder): String? {
        for (cfg in INNERTUBE_CLIENTS) {
            try {
                val json = buildInnertubeJson(videoId, cfg)
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val isWebClient = cfg.clientName == "MWEB" || cfg.clientName == "WEB" || cfg.clientName.startsWith("TVHTML5")
                val builder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?key=${cfg.apiKey}&prettyPrint=false")
                    .post(requestBody)
                    .addHeader("User-Agent", cfg.userAgent)
                    .addHeader("X-YouTube-Client-Name", cfg.clientNameId)
                    .addHeader("X-YouTube-Client-Version", cfg.clientVersion)
                    .addHeader("Content-Type", "application/json")
                if (isWebClient) {
                    builder.addHeader("Origin", "https://www.youtube.com")
                    builder.addHeader("Referer", "https://www.youtube.com/")
                }
                val request = builder.build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    log.appendLine("   ${cfg.name} → HTTP ${response.code}")
                    continue
                }
                val body = response.body?.string()
                if (body == null) {
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
