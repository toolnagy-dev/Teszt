package com.toolnagy.ringtonemanager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

@HiltAndroidApp
class RingtoneManagerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader())
    }
}

private class NewPipeDownloader : Downloader() {
    private val client = OkHttpClient.Builder().build()

    override fun execute(request: Request): Response {
        val requestBuilder = okhttp3.Request.Builder().url(request.url())

        request.headers().forEach { (key, values) ->
            values.forEach { value -> requestBuilder.addHeader(key, value) }
        }

        val body = request.dataToSend()
        val okhttpRequest = if (body != null) {
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            requestBuilder.method(request.httpMethod(), body.toRequestBody(mediaType)).build()
        } else {
            requestBuilder.method(request.httpMethod(), null).build()
        }

        val response = client.newCall(okhttpRequest).execute()
        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers.values(name)
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            response.body?.string(),
            request.url()
        )
    }
}
