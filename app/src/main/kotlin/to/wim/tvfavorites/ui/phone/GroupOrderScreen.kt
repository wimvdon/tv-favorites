package to.wim.tvfavorites.ui.phone

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.logic.Ordering
import to.wim.tvfavorites.ui.theme.DragHandle
import to.wim.tvfavorites.ui.theme.ReorderableColumn
import to.wim.tvfavorites.ui.theme.SettingsBlock
import to.wim.tvfavorites.ui.theme.SettingsScaffold

/** Settings › Service order: the order of the groups on the phone and the rows on the TV. */
@Composable
fun GroupOrderScreen(onBack: () -> Unit) {
    val app = LocalContext.current.app
    val library by app.library.library.collectAsStateWithLifecycle()
    val groups = Ordering.groups(library?.favorites.orEmpty(), order = library?.groupOrder.orEmpty())
    SettingsScaffold(title = stringResource(R.string.group_order_title), backLabel = stringResource(R.string.action_back), onBack = onBack) {
        item {
            SettingsBlock { Text(stringResource(R.string.group_order_body), style = MaterialTheme.typography.bodyMedium) }
        }
        item {
            ReorderableColumn(items = groups, key = { it.serviceId }, onMove = { from, to -> app.library.moveGroup(from, to) }) { group ->
                ListItem(
                    headlineContent = { Text(group.name) },
                    supportingContent = { Text(pluralStringResource(R.plurals.favorites_count, group.items.size, group.items.size)) },
                    trailingContent = { DragHandle(handleModifier) },
                    colors = ListItemDefaults.colors(containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
                )
            }
        }
    }
}
