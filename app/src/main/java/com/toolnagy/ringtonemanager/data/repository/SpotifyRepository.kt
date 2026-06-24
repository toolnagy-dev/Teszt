package com.toolnagy.ringtonemanager.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.toolnagy.ringtonemanager.data.api.SpotifyApiService
import com.toolnagy.ringtonemanager.data.api.SpotifyAuthService
import com.toolnagy.ringtonemanager.data.model.SpotifyTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "spotify_prefs")

@Singleton
class SpotifyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: SpotifyApiService,
    private val authService: SpotifyAuthService
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_CLIENT_ID = stringPreferencesKey("spotify_client_id")
        private val KEY_EXPIRES_AT = stringPreferencesKey("token_expires_at")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val clientId: Flow<String?> = context.dataStore.data.map { it[KEY_CLIENT_ID] }

    suspend fun saveClientId(clientId: String) {
        context.dataStore.edit { it[KEY_CLIENT_ID] = clientId }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String?, expiresIn: Int) {
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
        context.dataStore.edit {
            it[KEY_ACCESS_TOKEN] = accessToken
            if (refreshToken != null) it[KEY_REFRESH_TOKEN] = refreshToken
            it[KEY_EXPIRES_AT] = expiresAt.toString()
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit {
            it.remove(KEY_ACCESS_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
            it.remove(KEY_EXPIRES_AT)
        }
    }

    suspend fun exchangeCodeForToken(code: String, codeVerifier: String, redirectUri: String): Boolean {
        val clientId = clientId.firstOrNull() ?: return false
        return try {
            val response = authService.getToken(
                code = code,
                redirectUri = redirectUri,
                clientId = clientId,
                codeVerifier = codeVerifier
            )
            saveTokens(response.accessToken, response.refreshToken, response.expiresIn)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun searchTracks(query: String): List<SpotifyTrack> {
        val token = accessToken.firstOrNull() ?: return emptyList()
        return try {
            val response = apiService.search(auth = "Bearer $token", query = query)
            response.tracks.items
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun isLoggedIn(): Boolean = accessToken.firstOrNull() != null
}
