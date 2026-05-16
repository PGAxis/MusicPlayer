package dev.pgaxis.musicaxs

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

object PlayerBarDefaults {
    val Height = 65.dp
    val VerticalMargin = 10.dp

}

val LocalPlayerBarTotalHeight = compositionLocalOf { 0.dp }