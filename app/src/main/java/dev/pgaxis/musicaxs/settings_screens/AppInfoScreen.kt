package dev.pgaxis.musicaxs.settings_screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.templates.SideScreenHeader
import androidx.core.net.toUri

@Composable
fun AppInfoScreen(
    onBack: () -> Unit,
    vm: AppInfoViewModel = viewModel()
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SideScreenHeader(
            onBack = onBack
        ) {
            Spacer(Modifier.width(4.dp))

            Text(
                text = stringResource(R.string.set_scr_app_info),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(15.dp)
                    .fillMaxSize()
            ) {
                AppInfoRow(
                    text1 = stringResource(R.string.app_info_version),
                    text2 = "${vm.packageInfo.versionName}"
                )

                AppInfoRow(
                    text1 = stringResource(R.string.app_info_vcode),
                    text2 = vm.versionCode.toString()
                )

                AppInfoRow(
                    text1 = stringResource(R.string.app_info_source),
                    text2 = "GitHub"
                ) {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://github.com/PGAxis/MusicPlayer".toUri()
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AppInfoRow(
    modifier: Modifier = Modifier,
    text1: String,
    text2: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = text1,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.width(15.dp))

        Box(
            modifier = Modifier
                .weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text2,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textDecoration = if (onClick == null) TextDecoration.None else TextDecoration.Underline,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                modifier = if (onClick == null) Modifier else Modifier.clickable { onClick() }
            )
        }
    }
}