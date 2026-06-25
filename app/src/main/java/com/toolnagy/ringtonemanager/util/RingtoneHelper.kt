package com.toolnagy.ringtonemanager.util

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingtoneHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    data class DownloadResult(val uri: Uri?, val error: String? = null)

    suspend fun downloadAudioToRingtones(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext DownloadResult(null, "Letöltés HTTP ${response.code}")
            }
            val body = response.body
                ?: return@withContext DownloadResult(null, "Üres válasz a szervertől")
            val contentLength = body.contentLength()

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$fileName.mp3")
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val insertUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext DownloadResult(null, "Nem sikerült fájlt létrehozni a Csengőhangok mappában")

                context.contentResolver.openOutputStream(insertUri)?.use { out ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var read: Int
                    val input = body.byteStream()
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        if (contentLength > 0) onProgress(downloaded.toFloat() / contentLength)
                    }
                }

                val update = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                context.contentResolver.update(insertUri, update, null, null)
                insertUri
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                dir.mkdirs()
                val file = File(dir, "$fileName.mp3")
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var read: Int
                    val input = body.byteStream()
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        downloaded += read
                        if (contentLength > 0) onProgress(downloaded.toFloat() / contentLength)
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DATA, file.absolutePath)
                    put(MediaStore.Audio.Media.TITLE, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.IS_RINGTONE, true)
                }
                context.contentResolver.insert(MediaStore.Audio.Media.getContentUriForPath(file.absolutePath)!!, values)
            }
            DownloadResult(uri, if (uri == null) "A mentés nem adott vissza URI-t" else null)
        } catch (e: Exception) {
            DownloadResult(null, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun getDefaultRingtone(): Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
}
