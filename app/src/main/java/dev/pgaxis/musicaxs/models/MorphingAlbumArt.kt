package dev.pgaxis.musicaxs.models

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.contrastColor

@Composable
fun MorphingAlbumArt(
    songUri: String?,
    sizeDp: Dp,
    offsetX: Dp,
    offsetY: Dp,
    cornerDp: Dp,
    fetchSizePx: Int,
    onBgColorChange: (Color) -> Unit,
    onTxtColorChange: (Color) -> Unit,
) {
    val context = LocalContext.current
    var artBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(songUri) {
        val request = ImageRequest.Builder(context)
            .data(songUri)
            .size(fetchSizePx)
            .build()
        when (val result = context.imageLoader.execute(request)) {
            is SuccessResult -> {
                val bmp = (result.drawable as BitmapDrawable)
                    .bitmap.copy(Bitmap.Config.ARGB_8888, false)
                artBitmap = bmp
                Palette.from(bmp).generate { palette ->
                    palette?.getDominantColor(Color.DarkGray.toArgb())
                        ?.let { dominant ->
                            onBgColorChange(Color(dominant))
                            onTxtColorChange(Color(dominant).contrastColor())
                        }
                }
            }
            else -> {
                artBitmap = null
                onBgColorChange(Color.DarkGray)
                onTxtColorChange(Color.DarkGray.contrastColor())
            }
        }
    }

    Crossfade(
        targetState = artBitmap,
        animationSpec = tween(500),
        label = "albumArt",
        modifier = Modifier
            .size(sizeDp)
            .offset(offsetX, offsetY)
            .clip(RoundedCornerShape(cornerDp))
    ) { bmp ->
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Album art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.drawable.default_cover),
                contentDescription = "Album art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}