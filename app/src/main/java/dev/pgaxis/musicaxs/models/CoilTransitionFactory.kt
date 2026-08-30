package dev.pgaxis.musicaxs.models

import coil.transition.CrossfadeTransition
import coil.transition.Transition

val AlwaysCrossfade = Transition.Factory { target, result ->
    CrossfadeTransition(target, result, durationMillis = 300)
}