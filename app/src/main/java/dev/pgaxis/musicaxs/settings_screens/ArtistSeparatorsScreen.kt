package dev.pgaxis.musicaxs.settings_screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SideScreenHeader

@Composable
fun ArtistSeparatorsScreen(
    onBack: () -> Unit,
    vm: ArtistSeparatorsViewModel = viewModel()
) {
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
                text = stringResource(R.string.set_scr_artist_separators),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        ArtistSeparatorsRow(
            predefinedSeparators = vm.predefinedSeparators,
            activeSeparators = vm.settings.artistSeparator,
            customSeparators = vm.customSeparators,
            onToggle = vm::onArtistSeparatorToggled,
            onAdd = vm::onCustomSeparatorAdded,
            onRemove = vm::onCustomSeparatorRemoved
        )
    }
}

@Composable
fun ArtistSeparatorsRow(
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
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(11.dp)
            ),
        shape = RoundedCornerShape(11.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Text(
                text = stringResource(R.string.set_scr_artist_separators_desc),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
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