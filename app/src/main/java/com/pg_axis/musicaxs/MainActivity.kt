package com.pg_axis.musicaxs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.pg_axis.musicaxs.ui.theme.MusicaxsTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MusicaxsTheme {
                val mainViewModel: MainViewModel = viewModel()
                MainScreen(vm = mainViewModel)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainPreview() {
    MusicaxsTheme {
        val mainViewModel: MainViewModel = viewModel()
        MainScreen(mainViewModel)
    }
}