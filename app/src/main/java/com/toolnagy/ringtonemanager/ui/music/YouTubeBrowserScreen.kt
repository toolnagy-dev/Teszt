package com.toolnagy.ringtonemanager.ui.music

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.toolnagy.ringtonemanager.data.model.DownloadStatus
import com.toolnagy.ringtonemanager.data.model.YouTubeVideo
import com.toolnagy.ringtonemanager.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeBrowserScreen(
    contactId: String,
    onBack: () -> Unit,
    onRingtoneReady: (Uri) -> Unit,
    viewModel: YouTubeBrowserViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val completedUri by viewModel.completedRingtoneUri.collectAsStateWithLifecycle()
    val errorLog by viewModel.errorLog.collectAsStateWithLifecycle()

    LaunchedEffect(completedUri) {
        completedUri?.let { uri ->
            onRingtoneReady(uri)
            viewModel.consumeRingtoneUri()
        }
    }

    errorLog?.let { log ->
        ErrorDetailDialog(
            log = log,
            onDismiss = viewModel::dismissError
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = YouTubeRed)
                        Spacer(Modifier.width(8.dp))
                        Text("Zene keresése", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Előadó vagy dal címe…") },
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
                    CircularProgressIndicator(color = YouTubeRed)
                }
                videos.isEmpty() && searchQuery.length >= 3 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Nincs találat")
                    }
                }
                videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = YouTubeRed
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Keress zenét",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Írd be az előadót vagy a dal címét. A 30 másodperces részlet letöltődik és csengőhangként beállítható.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(videos, key = { it.id }) { video ->
                            val state = downloadStates[video.id]
                            YouTubeVideoItem(
                                video = video,
                                downloadStatus = state?.status ?: DownloadStatus.IDLE,
                                downloadProgress = state?.progress ?: 0f,
                                onDownload = { viewModel.downloadAndSetRingtone(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorDetailDialog(
    log: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Mi a hiba?") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Ez a részletes napló mutatja, melyik módszer pontosan hol akadt el. Másold ki és küldd el, ha kell segítség.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Bezárás") }
        },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(log))
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Másolás")
            }
        }
    )
}

@Composable
private fun YouTubeApiKeyBanner(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "YouTube API kulcs (opcionális)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "API kulcs nélkül korlátozott keresés érhető el. Add meg a Google Cloud Console-ból.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("YouTube Data API v3 kulcs") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    onSave()
                    expanded = false
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mentés")
                }
            }
        }
    }
}

@Composable
private fun YouTubeVideoItem(
    video: YouTubeVideo,
    downloadStatus: DownloadStatus,
    downloadProgress: Float,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (downloadStatus == DownloadStatus.DOWNLOADING) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = YouTubeRed
                )
            }
            if (downloadStatus == DownloadStatus.EXTRACTING) {
                Spacer(Modifier.height(4.dp))
                Text("Hang kinyerése…", style = MaterialTheme.typography.labelSmall, color = YouTubeRed)
            }
            if (downloadStatus == DownloadStatus.ERROR) {
                Spacer(Modifier.height(4.dp))
                Text("Hiba!", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.width(8.dp))

        when (downloadStatus) {
            DownloadStatus.IDLE, DownloadStatus.ERROR -> {
                IconButton(onClick = onDownload) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Letöltés és csengőhang beállítás",
                        tint = YouTubeRed
                    )
                }
            }
            DownloadStatus.EXTRACTING, DownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = YouTubeRed,
                    strokeWidth = 2.dp
                )
            }
            DownloadStatus.DONE -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Kész",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 84.dp))
}
