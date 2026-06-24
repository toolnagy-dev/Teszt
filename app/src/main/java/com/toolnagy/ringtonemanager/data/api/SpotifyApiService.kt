package com.toolnagy.ringtonemanager.data.api

import com.toolnagy.ringtonemanager.data.model.SpotifySearchResponse
import com.toolnagy.ringtonemanager.data.model.SpotifyTokenResponse
import retrofit2.http.*

interface SpotifyApiService {
    @GET("search")
    suspend fun search(
        @Header("Authorization") auth: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 30,
        @Query("market") market: String = "HU"
    ): SpotifySearchResponse
}

interface SpotifyAuthService {
    @POST("token")
    @FormUrlEncoded
    suspend fun getToken(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String
    ): SpotifyTokenResponse

    @POST("token")
    @FormUrlEncoded
    suspend fun refreshToken(
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String
    ): SpotifyTokenResponse
}
