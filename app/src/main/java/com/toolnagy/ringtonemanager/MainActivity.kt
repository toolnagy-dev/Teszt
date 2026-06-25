package com.toolnagy.ringtonemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.toolnagy.ringtonemanager.navigation.AppNavigation
import com.toolnagy.ringtonemanager.ui.theme.RingtoneManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val spotifyCode = intent?.data?.let { uri ->
            if (uri.scheme == "ringtonemanager" && uri.host == "callback") {
                uri.getQueryParameter("code")
            } else null
        }

        setContent {
            RingtoneManagerTheme {
                AppNavigation(pendingSpotifyCode = spotifyCode)
            }
        }
    }
}
