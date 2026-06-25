package com.toolnagy.ringtonemanager.ui.detail

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.toolnagy.ringtonemanager.R
import com.toolnagy.ringtonemanager.ui.contacts.ContactAvatar
import com.toolnagy.ringtonemanager.ui.theme.SpotifyGreen
import com.toolnagy.ringtonemanager.ui.theme.YouTubeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: String,
    onBack: () -> Unit,
    onChooseSpotify: (String) -> Unit,
    onChooseYouTube: (String) -> Unit,
    pendingRingtoneUri: Uri? = null,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    val contact by viewModel.contact.collectAsStateWithLifecycle()
    val result by viewModel.ringtoneSetResult.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingRingtoneUri) {
        pendingRingtoneUri?.let { viewModel.setRingtone(it) }
    }

    LaunchedEffect(result) {
        result?.let { r ->
            val msg = if (r == RingtoneSetResult.SUCCESS)
                "Csengőhang sikeresen beállítva!" else "Hiba a csengőhang beállításakor."
            snackbarHostState.showSnackbar(msg)
            viewModel.clearResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.name ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Vissza")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        contact?.let { c ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                ContactAvatar(contact = c, size = 96.dp)

                Spacer(Modifier.height(16.dp))

                Text(
                    text = c.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                if (c.phoneNumbers.isNotEmpty()) {
                    Text(
                        text = c.phoneNumbers.first(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (c.customRingtoneUri != null)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.current_ringtone),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (c.customRingtoneUri != null)
                                    "Egyéni csengőhang"
                                else
                                    stringResource(R.string.default_ringtone),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.choose_source),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MusicSourceCard(
                        modifier = Modifier.weight(1f),
                        title = "Spotify",
                        subtitle = "30mp előnézet",
                        icon = Icons.Default.LibraryMusic,
                        containerColor = SpotifyGreen,
                        onClick = { onChooseSpotify(contactId) }
                    )
                    MusicSourceCard(
                        modifier = Modifier.weight(1f),
                        title = "YouTube",
                        subtitle = "Letöltés",
                        icon = Icons.Default.VideoLibrary,
                        containerColor = YouTubeRed,
                        onClick = { onChooseYouTube(contactId) }
                    )
                }
            }
        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun MusicSourceCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    modifier = Modifier.padding(12.dp).size(32.dp),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
