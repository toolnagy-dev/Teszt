package com.toolnagy.ringtonemanager.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.toolnagy.ringtonemanager.ui.contacts.ContactsScreen
import com.toolnagy.ringtonemanager.ui.detail.ContactDetailScreen
import com.toolnagy.ringtonemanager.ui.music.SpotifyBrowserScreen
import com.toolnagy.ringtonemanager.ui.music.YouTubeBrowserScreen
import com.toolnagy.ringtonemanager.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Contacts : Screen("contacts")
    object Settings : Screen("settings")
    object ContactDetail : Screen("contact/{contactId}") {
        fun createRoute(contactId: String) = "contact/$contactId"
    }
    object SpotifyBrowser : Screen("spotify/{contactId}") {
        fun createRoute(contactId: String) = "spotify/$contactId"
    }
    object YouTubeBrowser : Screen("youtube/{contactId}") {
        fun createRoute(contactId: String) = "youtube/$contactId"
    }
}

@Composable
fun AppNavigation(pendingSpotifyCode: String?) {
    val navController = rememberNavController()
    var pendingRingtoneUri by remember { mutableStateOf<Uri?>(null) }
    var pendingContactId by remember { mutableStateOf<String?>(null) }

    NavHost(navController = navController, startDestination = Screen.Contacts.route) {

        composable(Screen.Contacts.route) {
            ContactsScreen(
                onContactClick = { contactId ->
                    navController.navigate(Screen.ContactDetail.createRoute(contactId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ContactDetail.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
            val thisRingtoneUri = if (pendingContactId == contactId) pendingRingtoneUri else null

            LaunchedEffect(thisRingtoneUri) {
                if (thisRingtoneUri != null) {
                    pendingRingtoneUri = null
                    pendingContactId = null
                }
            }

            ContactDetailScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onChooseSpotify = { cId ->
                    navController.navigate(Screen.SpotifyBrowser.createRoute(cId))
                },
                onChooseYouTube = { cId ->
                    navController.navigate(Screen.YouTubeBrowser.createRoute(cId))
                },
                pendingRingtoneUri = thisRingtoneUri
            )
        }

        composable(
            route = Screen.SpotifyBrowser.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
            SpotifyBrowserScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onRingtoneReady = { uri ->
                    pendingRingtoneUri = uri
                    pendingContactId = contactId
                    navController.navigate(Screen.ContactDetail.createRoute(contactId)) {
                        popUpTo(Screen.ContactDetail.createRoute(contactId)) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.YouTubeBrowser.route,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
            YouTubeBrowserScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onRingtoneReady = { uri ->
                    pendingRingtoneUri = uri
                    pendingContactId = contactId
                    navController.navigate(Screen.ContactDetail.createRoute(contactId)) {
                        popUpTo(Screen.ContactDetail.createRoute(contactId)) { inclusive = true }
                    }
                }
            )
        }
    }
}
