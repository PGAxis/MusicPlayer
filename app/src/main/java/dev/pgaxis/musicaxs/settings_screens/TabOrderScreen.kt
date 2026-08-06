package dev.pgaxis.musicaxs.settings_screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pgaxis.musicaxs.R
import dev.pgaxis.musicaxs.models.TabType
import dev.pgaxis.musicaxs.models.TitleVis
import dev.pgaxis.musicaxs.models.isMandatory
import dev.pgaxis.musicaxs.models.labelRes
import dev.pgaxis.musicaxs.templates.ListDivider
import dev.pgaxis.musicaxs.templates.SideScreenHeader
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun TabOrderScreen(
    onBack: () -> Unit,
    vm: TabOrderViewModel = viewModel()
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
                text = stringResource(R.string.set_scr_tab_order),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        SettingsListRow(
            items = vm.settings.tabs
        ) {
            vm.settings.tabs = it
        }
    }
}

@Composable
fun SettingsListRow(
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