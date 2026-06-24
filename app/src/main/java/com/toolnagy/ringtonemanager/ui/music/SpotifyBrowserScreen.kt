package com.toolnagy.ringtonemanager.ui.music

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.toolnagy.ringtonemanager.data.model.SpotifyTrack
import com.toolnagy.ringtonemanager.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyBrowserScreen(
    contactId: String,
    onBack: () -> Unit,
    onRingtoneReady: (Uri) -> Unit,
    viewModel: SpotifyBrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val playingTrackId by viewModel.playingTrackId.collectAsStateWithLifecycle()
    val downloadingTrackId by viewModel.downloadingTrackId.collectAsStateWithLifecycle()
    val authUrl by viewModel.authUrl.collectAsStateWithLifecycle()
    val clientId by viewModel.clientId.collectAsStateWithLifecycle()
    val completedUri by viewModel.completedRingtoneUri.collectAsStateWithLifecycle()

    LaunchedEffect(authUrl) {
        authUrl?.let { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    LaunchedEffect(completedUri) {
        completedUri?.let { uri ->
            onRingtoneReady(uri)
            viewModel.consumeRingtoneUri()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = SpotifyGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Spotify", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                },
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = viewModel::logout) {
                            Icon(Icons.Default.Logout, contentDescription = "Kijelentkezés")
                        }
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = isLoggedIn,
            modifier = Modifier.padding(padding),
            label = "login_state"
        ) { loggedIn ->
            if (!loggedIn) {
                SpotifyLoginContent(
                    clientId = clientId,
                    onClientIdChange = viewModel::updateClientId,
                    onSave = viewModel::saveClientId,
                    onLogin = viewModel::startLogin
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::updateQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Keresés Spotify-on…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge
                    )

                    when {
                        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SpotifyGreen)
                        }
                        tracks.isEmpty() && searchQuery.length >= 2 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nincs találat")
                        }
                        else -> {
                            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(tracks, key = { it.id }) { track ->
                                    SpotifyTrackItem(
                                        track = track,
                                        isPlaying = playingTrackId == track.id,
                                        isDownloading = downloadingTrackId == track.id,
                                        onPreviewClick = { viewModel.togglePreview(track) },
                                        onSetRingtone = { viewModel.downloadAndSetRingtone(track) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyLoginContent(
    clientId: String,
    onClientIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = SpotifyGreen
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Spotify bejelentkezés",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "A Spotify kereséshez add meg a Client ID-t a Spotify Developer Dashboard-ból.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = clientId,
            onValueChange = onClientIdChange,
            label = { Text("Spotify Client ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onSave()
                onLogin()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
        ) {
            Text("Bejelentkezés Spotify-ra")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Megjegyzés: A Spotify csak 30 másodperces előnézeteket engedélyez ingyenesen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpotifyTrackItem(
    track: SpotifyTrack,
    isPlaying: Boolean,
    isDownloading: Boolean,
    onPreviewClick: () -> Unit,
    onSetRingtone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artistNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.previewUrl == null) {
                Text(
                    "Nincs előnézet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        if (track.previewUrl != null) {
            IconButton(onClick = onPreviewClick) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Megállítás" else "Lejátszás",
                    tint = SpotifyGreen
                )
            }
        }

        if (isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = SpotifyGreen, strokeWidth = 2.dp)
        } else {
            IconButton(
                onClick = onSetRingtone,
                enabled = track.previewUrl != null
            ) {
                Icon(
                    Icons.Default.NotificationAdd,
                    contentDescription = "Csengőhangnak beállít",
                    tint = if (track.previewUrl != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 84.dp))
}
