package to.wim.tvfavorites.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.data.Settings
import to.wim.tvfavorites.logic.Airing
import to.wim.tvfavorites.logic.Ordering
import to.wim.tvfavorites.logic.ServiceCatalog
import to.wim.tvfavorites.logic.SharedLink
import to.wim.tvfavorites.logic.SharedText
import to.wim.tvfavorites.model.Favorite
import to.wim.tvfavorites.ui.theme.DragHandle
import to.wim.tvfavorites.ui.theme.EmptyState
import to.wim.tvfavorites.ui.theme.ReorderableColumn
import to.wim.tvfavorites.ui.theme.SkeletonBlock
import to.wim.tvfavorites.ui.theme.readableWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: Settings,
    onAdd: (SharedLink) -> Unit,
    onEdit: (Favorite) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.app
    val library by app.library.library.collectAsStateWithLifecycle()
    val problem by app.library.problem.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pasting by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(problem) {
        problem?.let { snackbar.showSnackbar(resources.getString(R.string.sync_problem, it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (!library?.favorites.isNullOrEmpty()) {
                        IconButton(onClick = { scope.launch { app.settings.setGroupByService(!settings.groupByService) } }) {
                            Icon(
                                painterResource(if (settings.groupByService) R.drawable.ic_view_list else R.drawable.ic_view_agenda),
                                contentDescription = stringResource(if (settings.groupByService) R.string.action_one_list else R.string.action_group_by_service),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pasting = true }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_add_link))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val items = library?.favorites
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                items == null -> LoadingRows()
                items.isEmpty() ->
                    EmptyState(
                        icon = painterResource(R.drawable.ic_favorite),
                        title = stringResource(R.string.empty_title),
                        body = stringResource(R.string.empty_body),
                    ) {
                        Button(onClick = { pasting = true }) { Text(stringResource(R.string.action_add_link)) }
                    }
                else ->
                    Column(
                        Modifier
                            .readableWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 88.dp),
                    ) {
                        if (settings.groupByService) {
                            Ordering.groups(items).forEach { group ->
                                Text(
                                    group.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
                                )
                                FavoriteRows(group.items, showService = false, onEdit = onEdit) { from, to ->
                                    app.library.moveWithin(group.serviceId, from, to)
                                }
                            }
                        } else {
                            FavoriteRows(Ordering.sorted(items), showService = true, onEdit = onEdit) { from, to -> app.library.move(from, to) }
                        }
                    }
            }
        }
    }

    if (pasting) {
        PasteLinkDialog(
            onDismiss = { pasting = false },
            onAdd = {
                pasting = false
                onAdd(it)
            },
        )
    }
}

@Composable
private fun FavoriteRows(
    items: List<Favorite>,
    showService: Boolean,
    onEdit: (Favorite) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    val resources = LocalResources.current
    ReorderableColumn(items = items, key = { it.id }, onMove = onMove) { item ->
        val aired = Airing.dateAndEpisode(item).takeIf { it.isNotEmpty() }?.let { resources.getString(R.string.last_aired, it) }
        val supporting = listOfNotNull(ServiceCatalog.nameOf(item.serviceId).takeIf { showService }, aired).joinToString(" · ")
        ListItem(
            headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = supporting.takeIf { it.isNotEmpty() }?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            leadingContent = { Poster(item) },
            trailingContent = { DragHandle(handleModifier) },
            colors = ListItemDefaults.colors(containerColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface),
            modifier = Modifier.clickable { onEdit(item) },
        )
    }
}

@Composable
fun Poster(item: Favorite, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    AsyncImage(
        model = item.posterUrl.ifBlank { item.imageUrl }.ifBlank { null },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .size(width = 48.dp, height = 72.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

@Composable
private fun LoadingRows() {
    Column(Modifier.readableWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val loading = stringResource(R.string.loading)
        Box(Modifier.semantics(mergeDescendants = true) { contentDescription = loading }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(height = 72.dp, modifier = Modifier.width(48.dp))
                        Spacer(Modifier.width(16.dp))
                        SkeletonBlock(height = 20.dp, modifier = Modifier.fillMaxWidth(0.7f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PasteLinkDialog(
    onDismiss: () -> Unit,
    onAdd: (SharedLink) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val link = SharedText.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.paste_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.field_link)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { link?.let(onAdd) }, enabled = link != null) { Text(stringResource(R.string.action_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
