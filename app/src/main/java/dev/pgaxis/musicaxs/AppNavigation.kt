package dev.pgaxis.musicaxs

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.pgaxis.musicaxs.side_pages.SongDetailScreen
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.side_pages.AlbumDetailScreen
import dev.pgaxis.musicaxs.side_pages.ArtistDetailScreen
import dev.pgaxis.musicaxs.side_pages.PlaylistDetailScreen
import dev.pgaxis.musicaxs.side_pages.PodcastDetailScreen
import dev.pgaxis.musicaxs.side_pages.SearchScreen
import dev.pgaxis.musicaxs.side_pages.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()

    fun popBack() {
        if (navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "main") {
            composable("main") {
                MainScreen(
                    goToDetail = { songUri -> navController.navigate("songdetail/$songUri") },
                    goToPlaylist = { playlistId -> navController.navigate("playlist/$playlistId") },
                    goToSearch = { navController.navigate("search") },
                    goToSettings = { navController.navigate("settings") },
                    onChooseSongsForPlaylist = { playlistId -> navController.navigate("search/choose/$playlistId") },
                    onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
                    onOpenArtist = { name -> navController.navigate("artist/$name") },
                    onOpenPodcast = { feedUrl -> navController.navigate("podcast/${Uri.encode(feedUrl)}") },
                    vm = mainViewModel
                )
            }
            composable("songdetail/{songUri}") { backStackEntry ->
                val uriString = backStackEntry.arguments?.getString("songUri")!!
                val uri = Uri.decode(uriString).toUri()

                SongDetailScreen(
                    songUri = uri,
                    onScan = mainViewModel::scanAll,
                    onBack = { popBack() }
                )
            }
            composable("playlist/{playlistId}") { backstackEntry ->
                val idString = backstackEntry.arguments?.getString("playlistId")!!
                val id = idString.toLong()

                PlaylistDetailScreen(
                    id,
                    onBack = { popBack() },
                    onSeeDetail = { songUri -> navController.navigate("songdetail/$songUri") })
            }
            composable("search") {
                SearchScreen(
                    onBack = { popBack() },
                    onSeeDetail = { songUri -> navController.navigate("songdetail/$songUri") },
                    onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
                    onOpenArtist = { name -> navController.navigate("artist/$name") }
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
                    choosingForPlaylistId = playlistId,
                    onOpenAlbum = { albumId -> navController.navigate("album/$albumId") },
                    onOpenArtist = { name -> navController.navigate("artist/$name") }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { popBack() }, onScan = { mainViewModel.scanAll() })
            }
            composable("album/{albumId}") { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId")!!.toLong()
                AlbumDetailScreen(
                    albumId = albumId,
                    onBack = { popBack() },
                    onSeeDetail = { songUri -> navController.navigate("songdetail/$songUri") }
                )
            }
            composable("artist/{artistName}") { backStackEntry ->
                val artistName = Uri.decode(backStackEntry.arguments?.getString("artistName")!!)
                ArtistDetailScreen(
                    artistName = artistName,
                    onBack = { popBack() },
                    onSeeDetail = { songUri -> navController.navigate("songdetail/$songUri") }
                )
            }
            composable("podcast/{feedUrl}") { backStackEntry ->
                val feedUrl = Uri.decode(backStackEntry.arguments?.getString("feedUrl")!!)
                PodcastDetailScreen(
                    feedUrl = feedUrl,
                    onBack = { popBack() }
                )
            }
        }

        TopBannerHost(modifier = Modifier.align(Alignment.TopCenter))
    }
}
