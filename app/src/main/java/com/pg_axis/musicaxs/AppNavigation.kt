package com.pg_axis.musicaxs

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pg_axis.musicaxs.side_pages.SongDetailScreen
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                goToDetail = { songUri -> navController.navigate("songdetail/$songUri") },
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
    }
}