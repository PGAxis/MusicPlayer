package com.pg_axis.musicaxs

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pg_axis.musicaxs.side_pages.SongDetailScreen
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pg_axis.musicaxs.side_pages.PlaylistDetailScreen
import com.pg_axis.musicaxs.side_pages.SearchScreen
import com.pg_axis.musicaxs.side_pages.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                goToDetail = { songUri -> navController.navigate("songdetail/$songUri") },
                goToPlaylist = { playlistId -> navController.navigate("playlist/$playlistId") },
                goToSearch = { navController.navigate("search") },
                goToSettings = { navController.navigate("settings") },
                onChooseSongsForPlaylist = { playlistId -> navController.navigate("search/choose/$playlistId") },
                vm = mainViewModel
            )
        }
        composable("songdetail/{songUri}") { backStackEntry ->
            val uriString = backStackEntry.arguments?.getString("songUri")!!
            val uri = Uri.decode(uriString).toUri()

            SongDetailScreen(
                songUri = uri,
                onBack = { navController.popBackStack() }
            )
        }
        composable("playlist/{playlistId}") { backstackEntry ->
            val idString = backstackEntry.arguments?.getString("playlistId")!!
            val id = idString.toLong()

            PlaylistDetailScreen(
                id,
                onBack = { navController.popBackStack() },
                onSeeDetail = { songUri -> navController.navigate("songdetail/$songUri") })
        }
        composable("search") {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onSeeDetail = { songUri -> navController.navigate("songdetail/$songUri") }
            )
        }
        composable("search/choose/{playlistId}") { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")!!.toLong()
            SearchScreen(
                onBack = {
                    navController.navigate("main") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                onSeeDetail = { songUri -> navController.navigate("songdetail/$songUri") },
                choosingForPlaylistId = playlistId
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}