package com.toolnagy.ringtonemanager.data.api

import com.toolnagy.ringtonemanager.data.model.YouTubeSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("part") part: String = "snippet",
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 25,
        @Query("videoCategoryId") categoryId: String = "10",
        @Query("key") apiKey: String
    ): YouTubeSearchResponse
}
