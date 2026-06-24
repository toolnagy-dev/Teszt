package com.toolnagy.ringtonemanager.di

import com.toolnagy.ringtonemanager.data.api.SpotifyApiService
import com.toolnagy.ringtonemanager.data.api.SpotifyAuthService
import com.toolnagy.ringtonemanager.data.api.YouTubeApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    @Provides
    @Singleton
    @Named("spotify")
    fun provideSpotifyRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.spotify.com/v1/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    @Named("spotify_auth")
    fun provideSpotifyAuthRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://accounts.spotify.com/api/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    @Named("youtube")
    fun provideYouTubeRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideSpotifyApiService(@Named("spotify") retrofit: Retrofit): SpotifyApiService =
        retrofit.create(SpotifyApiService::class.java)

    @Provides
    @Singleton
    fun provideSpotifyAuthService(@Named("spotify_auth") retrofit: Retrofit): SpotifyAuthService =
        retrofit.create(SpotifyAuthService::class.java)

    @Provides
    @Singleton
    fun provideYouTubeApiService(@Named("youtube") retrofit: Retrofit): YouTubeApiService =
        retrofit.create(YouTubeApiService::class.java)
}
