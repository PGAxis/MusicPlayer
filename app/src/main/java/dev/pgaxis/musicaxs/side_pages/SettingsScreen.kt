package dev.pgaxis.musicaxs.side_pages

import android.os.Build
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.TabType
import dev.pgaxis.musicaxs.models.TitleVis
import dev.pgaxis.musicaxs.models.isMandatory
import dev.pgaxis.musicaxs.models.labelRes
import dev.pgaxis.musicaxs.services.Theme
import dev.pgaxis.musicaxs.templates.ListDivider
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onScan: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val ytcnvReady = remember { vm.isYTCnvInstalled(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, shape = RoundedCornerShape(0.dp)) {
                Icon(painterResource(R.drawable.back), "Back", tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = stringResource(R.string.set_scr_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
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
                    label = stringResource(R.string.set_scr_hide_wa_audio),
                    description = stringResource(R.string.set_scr_hide_wa_audio_desc),
                    checked = vm.settings.hideWhatsAppAudio,
                    onCheckedChange = { value ->
                        vm.onHideWhatsAppChanged(value)
                        onScan()
                    }
                )

                ListDivider(hasArt = false)

                SettingsListRow(
                    title = stringResource(R.string.set_scr_tab_order),
                    items = vm.settings.tabs,
                    onItemsChanged = { vm.settings.tabs = it }
                )
            }

            SettingsGroup(title = stringResource(R.string.set_scr_customization), initiallyExpanded = false) {
                SettingsDropdownRow(
                    label = stringResource(R.string.set_scr_theme),
                    options = vm.themeOptions,
                    selected = vm.selectedTheme,
                    onSelectChange = { vm.onThemeChanged(it as Theme) }
                )

                ListDivider(hasArt = false)

                SettingsArtistSeparatorsRow(
                    predefinedSeparators = vm.predefinedSeparators,
                    activeSeparators = vm.settings.artistSeparator,
                    customSeparators = vm.customSeparators,
                    onToggle = vm::onArtistSeparatorToggled,
                    onAdd = vm::onCustomSeparatorAdded,
                    onRemove = vm::onCustomSeparatorRemoved
                )
            }

            if (ytcnvReady) {
                SettingsGroup(title = stringResource(R.string.set_scr_app_settings), initiallyExpanded = false) {
                    SettingsDropdownRow(
                        label = stringResource(R.string.language),
                        options = vm.langOptions,
                        selected = vm.selectedLang,
                        onSelectChange = { vm.onLanguageChange(it as String) }
                    )

                    ListDivider(hasArt = false)

                    SettingsToggleRow(
                        label = stringResource(R.string.set_scr_ytconv_add_songs),
                        description = stringResource(R.string.set_scr_ytconv_add_songs_desc),
                        checked = vm.settings.allowYTCnv,
                        onCheckedChange = vm::onAllowYTCnvChanged
                    )
                }
            }
        }

        val packageInfo = LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        Text(
            text = "${packageInfo.versionName} ($versionCode)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(8.dp)
        )
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
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (description != null) {
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
    label: String,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (description != null) {
                Text(
                    description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
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
                modifier = Modifier.widthIn(min = minDropdownWidth).background(MaterialTheme.colorScheme.secondaryContainer)
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
}

@Composable
fun SettingsListRow(
    title: String,
    items: List<TitleVis>,
    onItemsChanged: (List<TitleVis>) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(11.dp)),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )

            HorizontalDivider()

            ReorderableColumn(
                list = items,
                onSettle = { from, to ->
                    val newList = items.toMutableList().apply { add(to, removeAt(from)) }
                    onItemsChanged(newList)
                },
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
            ) { index, item, _ ->
                val tabType = TabType.valueOf(item.tab)
                ReorderableItem {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.visible,
                                onCheckedChange = { checked ->
                                    val newList = items.toMutableList().apply {
                                        this[index] = item.copy(visible = checked)
                                    }
                                    onItemsChanged(newList)
                                },
                                enabled = !tabType.isMandatory(),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                            )

                            Text(
                                text = stringResource(tabType.labelRes()),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )

                            Icon(
                                painter = painterResource(R.drawable.drag_handle),
                                contentDescription = "Reorder",
                                modifier = Modifier
                                    .draggableHandle()
                                    .size(24.dp)
                                    .padding(4.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (index < items.lastIndex) {
                            ListDivider(hasArt = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsArtistSeparatorsRow(
    predefinedSeparators: List<String>,
    activeSeparators: List<String>,
    customSeparators: List<String>,
    onToggle: (String, Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var customInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(11.dp)),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Text(
                text = stringResource(R.string.set_scr_artist_separators),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
            Text(
                text = stringResource(R.string.set_scr_artist_separators_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
            )

            HorizontalDivider()

            predefinedSeparators.forEachIndexed { index, sep ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(sep, sep !in activeSeparators) }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = sep in activeSeparators,
                        onCheckedChange = { checked -> onToggle(sep, checked) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = sep,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (index < predefinedSeparators.lastIndex) {
                    ListDivider(hasArt = false)
                }
            }

            HorizontalDivider()

            AnimatedVisibility(
                visible = customSeparators.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    customSeparators.forEachIndexed { index, sep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sep,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemove(sep) }) {
                                Icon(
                                    painterResource(R.drawable.cross),
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < customSeparators.lastIndex) {
                            ListDivider(hasArt = false)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.set_scr_artist_separators_placeholder),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    )
                )
                IconButton(
                    onClick = {
                        onAdd(customInput)
                        customInput = ""
                    },
                    enabled = customInput.isNotBlank()
                ) {
                    Icon(
                        painterResource(R.drawable.plus),
                        contentDescription = "Add",
                        tint = if (customInput.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}