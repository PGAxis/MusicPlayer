package dev.pgaxis.musicaxs.side_pages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.TabType
import dev.pgaxis.musicaxs.models.labelRes
import dev.pgaxis.musicaxs.services.Theme
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SideScreenHeader
import kotlin.collections.component1
import kotlin.collections.component2

enum class SettingsEntryType {
    TEXT,
    NUMBER
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    onTabOrder: () -> Unit,
    onArtistSeparators: () -> Unit,
    onAppInfo: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val ytcnvReady = remember { vm.isYTCnvInstalled(context) }

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
                text = stringResource(R.string.set_scr_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsGroup(title = stringResource(R.string.set_scr_library), initiallyExpanded = false) {
                SettingsToggleRow(
                    title = stringResource(R.string.set_scr_hide_wa_audio),
                    description = stringResource(R.string.set_scr_hide_wa_audio_desc),
                    checked = vm.settings.hideWhatsAppAudio
                ) { value ->
                    vm.onHideWhatsAppChanged(value)
                    onScan()
                }

                ListDivider(hasArt = false)

                SettingsEntryRow(
                    title = stringResource(R.string.set_scr_smart_limit),
                    description = stringResource(R.string.set_scr_smart_limit_desc),
                    value = vm.settings.smartLimitInput,
                    onValueChange = vm::onSmartLimitChanged,
                    onCleared = vm::onSmartLimitCleared,
                    type = SettingsEntryType.NUMBER
                )

                ListDivider(hasArt = false)

                SettingsActionRow(
                    title = stringResource(R.string.set_scr_tab_order),
                    onClick = onTabOrder
                ) {
                    var text = ""
                    vm.settings.tabs.filter { it.visible }.forEach {
                        val tabType = TabType.valueOf(it.tab)
                        text += "${stringResource(tabType.labelRes())}, "
                    }
                    text = text.removeSuffix(", ")
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true
                    )
                }

                ListDivider(hasArt = false)

                SettingsToggleRow(
                    title = stringResource(R.string.set_scr_play_full),
                    description = stringResource(R.string.set_scr_play_full_desc),
                    checked = vm.settings.playFull
                ) {
                    vm.onPlayFullChanged(it)
                }
            }

            SettingsGroup(title = stringResource(R.string.set_scr_customization), initiallyExpanded = false) {
                SettingsDropdownRow(
                    title = stringResource(R.string.set_scr_theme),
                    options = vm.themeOptions,
                    selected = vm.selectedTheme
                ) { vm.onThemeChanged(it as Theme) }

                ListDivider(hasArt = false)

                var realBars by remember { mutableStateOf(vm.settings.realBars) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        realBars = true
                        vm.onRealBarsChanged(true)
                    }
                }

                SettingsToggleRow(
                    title = stringResource(R.string.set_scr_real_bars),
                    description = stringResource(R.string.set_scr_real_bars_desc),
                    checked = realBars
                ) { turningOn ->
                    if (turningOn) {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (granted) {
                            realBars = true
                            vm.onRealBarsChanged(true)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        realBars = false
                        vm.onRealBarsChanged(false)
                    }
                }

                ListDivider(hasArt = false)

                SettingsActionRow(
                    title = stringResource(R.string.set_scr_artist_separators),
                    onClick = onArtistSeparators
                ) {
                    Text(
                        text = stringResource(R.string.set_scr_artist_separators_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.tab_orbit), initiallyExpanded = false) {
                SettingsEntryRow(
                    title = stringResource(R.string.set_scr_orbit_name),
                    description = stringResource(R.string.set_scr_orbit_name_desc),
                    value = vm.orbitDeviceName,
                    onValueChange = vm::onOrbitDeviceNameChanged,
                    type = SettingsEntryType.TEXT
                )
            }


            SettingsGroup(title = stringResource(R.string.set_scr_app_settings), initiallyExpanded = false) {
                SettingsDropdownRow(
                    title = stringResource(R.string.language),
                    options = vm.langOptions,
                    selected = vm.selectedLang
                ) { vm.onLanguageChange(it as String) }

                if (ytcnvReady) {
                    ListDivider(hasArt = false)

                    SettingsToggleRow(
                        title = stringResource(R.string.set_scr_ytconv_add_songs),
                        description = stringResource(R.string.set_scr_ytconv_add_songs_desc),
                        checked = vm.settings.allowYTCnv
                    ) {
                        vm.onAllowYTCnvChanged(it)
                    }
                }

                ListDivider(hasArt = false)

                SettingsActionRow(
                    title = stringResource(R.string.set_scr_app_info),
                    onClick = onAppInfo
                ) { }
            }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        if (description != null) {
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
    title: String,
    description: String? = null,
    options: Map<out Any, String>,
    selected: Any,
    onSelectChange: (Any) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val minDropdownWidth = remember(options) {
        val maxPx = options.values.maxOfOrNull { text ->
            textMeasurer.measure(text, TextStyle(fontSize = 16.sp)).size.width
        } ?: 0
        with(density) { maxPx.toDp() + 32.dp }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                Row(
                    modifier = Modifier
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                        .wrapContentWidth()
                        .border(
                            width = 2.dp,
                            color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = options.entries.find { it.key == selected }?.value ?: selected.toString(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                        contentDescription = null,
                        tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.height(15.dp)
                    )
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .widthIn(min = minDropdownWidth)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    options.forEach { (backendValue, displayLabel) ->
                        DropdownMenuItem(
                            text = { Text(displayLabel, color = MaterialTheme.colorScheme.onSecondaryContainer) },
                            onClick = {
                                onSelectChange(backendValue)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (description != null) {
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
fun SettingsEntryRow(
    title: String,
    description: String? = null,
    value: String,
    onValueChange: (String) -> Unit,
    onCleared: () -> Unit = {},
    type: SettingsEntryType = SettingsEntryType.TEXT
) {
    var focused by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val fieldWidth = remember(value) {
        val textPx = textMeasurer.measure(
            text = value.ifEmpty { "0" },
            style = TextStyle(fontSize = 16.sp)
        ).size.width
        with(density) { (textPx.toDp() + 24.dp).coerceIn(48.dp, 140.dp) }
    }

    val indicatorColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            BasicTextField(
                value = value,
                onValueChange = { new ->
                    when (type) {
                        SettingsEntryType.NUMBER -> if (new.isEmpty() || new.all { it.isDigit() }) {
                            onValueChange(new)
                        }
                        SettingsEntryType.TEXT -> onValueChange(new)
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (type == SettingsEntryType.NUMBER) KeyboardType.Number else KeyboardType.Text
                ),
                modifier = Modifier
                    .width(fieldWidth)
                    .onFocusChanged { state ->
                        if (focused && !state.isFocused && value.isEmpty()) {
                            onCleared()
                        }
                        focused = state.isFocused
                    }
                    .padding(bottom = 4.dp)
                    .drawBehind {
                        val strokeWidth = if (focused) 2.dp.toPx() else 1.dp.toPx()
                        drawLine(
                            color = indicatorColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
            )
        }
        if (description != null) {
            Text(
                description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}