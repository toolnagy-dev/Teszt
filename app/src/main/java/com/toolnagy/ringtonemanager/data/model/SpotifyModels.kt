package com.toolnagy.ringtonemanager.data.model

import com.google.gson.annotations.SerializedName

data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>,
    @SerializedName("preview_url") val previewUrl: String?,
    val album: SpotifyAlbum,
    @SerializedName("duration_ms") val durationMs: Long
) {
    val artistNames: String get() = artists.joinToString(", ") { it.name }
    val thumbnailUrl: String? get() = album.images.firstOrNull()?.url
}

data class SpotifyArtist(
    val id: String,
    val name: String
)

data class SpotifyAlbum(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>
)

data class SpotifyImage(
    val url: String,
    val width: Int,
    val height: Int
)

data class SpotifySearchResponse(
    val tracks: SpotifyTracksPage
)

data class SpotifyTracksPage(
    val items: List<SpotifyTrack>,
    val total: Int
)

data class SpotifyTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("refresh_token") val refreshToken: String?
)
