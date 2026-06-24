package com.toolnagy.ringtonemanager.data.model

import com.google.gson.annotations.SerializedName

data class YouTubeVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationText: String
)

data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem>
)

data class YouTubeSearchItem(
    val id: YouTubeVideoId,
    val snippet: YouTubeSnippet
)

data class YouTubeVideoId(
    @SerializedName("videoId") val videoId: String?
)

data class YouTubeSnippet(
    val title: String,
    @SerializedName("channelTitle") val channelTitle: String,
    val thumbnails: YouTubeThumbnails
)

data class YouTubeThumbnails(
    val medium: YouTubeThumbnail?
)

data class YouTubeThumbnail(
    val url: String
)

data class DownloadState(
    val videoId: String,
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val localPath: String? = null,
    val errorMessage: String? = null
)

enum class DownloadStatus {
    IDLE, EXTRACTING, DOWNLOADING, DONE, ERROR
}
