package to.wim.tvfavorites.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import androidx.compose.runtime.withFrameNanos
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.data.Connection
import to.wim.tvfavorites.launch.OpenFavoriteActivity
import to.wim.tvfavorites.launch.Launcher
import to.wim.tvfavorites.logic.Airing
import to.wim.tvfavorites.logic.ChannelPlan
import to.wim.tvfavorites.logic.Ordering
import to.wim.tvfavorites.logic.ServiceCatalog
import to.wim.tvfavorites.model.Favorite

/** A favorite being moved with the remote, within the row [rowKey] ("all" or a service id). */
private data class Moving(val id: String, val rowKey: String)

@Composable
fun TvHomeScreen(
    onPair: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val library by app.library.library.collectAsStateWithLifecycle()
    val connection by app.library.connection.collectAsStateWithLifecycle()
    var actionsFor by remember { mutableStateOf<Pair<Favorite, String>?>(null) }
    var editing by remember { mutableStateOf<Favorite?>(null) }
    var deleting by remember { mutableStateOf<Favorite?>(null) }
    var moving by remember { mutableStateOf<Moving?>(null) }
    val focus = remember { mutableStateMapOf<String, FocusRequester>() }

    fun requester(key: String) = focus.getOrPut(key) { FocusRequester() }

    fun open(item: Favorite) {
        val result = Launcher(context).open(item.url, item.serviceId, library?.overrides?.get(item.serviceId))
        OpenFavoriteActivity.showResult(context, result, ServiceCatalog.nameOf(item.serviceId))
    }

    val items = library?.favorites
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (connection !is Connection.Unavailable) {
                OutlinedButton(onClick = onPair) { Text(stringResource(R.string.tv_connect_phone)) }
                Spacer(Modifier.width(12.dp))
            }
            OutlinedButton(onClick = onSettings) { Text(stringResource(R.string.settings_title)) }
        }
        when {
            items == null -> Text(stringResource(R.string.loading), Modifier.padding(48.dp))
            items.isEmpty() -> TvEmpty(onPair = onPair, canPair = connection !is Connection.Unavailable)
            else -> {
                val rows = listOf(ChannelPlan.ALL to (stringResource(R.string.tv_row_all) to Ordering.sorted(items))) +
                    Ordering.groups(items).map { it.serviceId to (it.name to it.items) }
                LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(rows, key = { it.first }) { (rowKey, row) ->
                        val (name, list) = row
                        Column {
                            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                items(list, key = { it.id }) { item ->
                                    val isMoving = moving?.id == item.id && moving?.rowKey == rowKey
                                    TvFavoriteCard(
                                        item = item,
                                        moving = isMoving,
                                        modifier =
                                            Modifier
                                                .focusRequester(requester("$rowKey/${item.id}"))
                                                .onPreviewKeyEvent { event ->
                                                    if (!isMoving || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                                    val index = list.indexOfFirst { it.id == item.id }
                                                    val step =
                                                        when (event.key) {
                                                            Key.DirectionLeft -> -1
                                                            Key.DirectionRight -> 1
                                                            Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape -> {
                                                                moving = null
                                                                return@onPreviewKeyEvent true
                                                            }
                                                            else -> return@onPreviewKeyEvent true
                                                        }
                                                    val to = (index + step).coerceIn(0, list.lastIndex)
                                                    if (to != index) {
                                                        if (rowKey == ChannelPlan.ALL) app.library.move(index, to) else app.library.moveWithin(rowKey, index, to)
                                                    }
                                                    true
                                                },
                                        onClick = { if (isMoving) moving = null else open(item) },
                                        onLongClick = { actionsFor = item to rowKey },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Keeps the remote on the moving card while the row reorders around it.
    LaunchedEffect(moving, items) {
        val m = moving ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { requester("${m.rowKey}/${m.id}").requestFocus() }
    }

    actionsFor?.let { (item, rowKey) ->
        TvActionsDialog(
            title = item.title,
            onDismiss = { actionsFor = null },
            actions =
                listOf(
                    stringResource(R.string.action_open) to { open(item) },
                    stringResource(R.string.action_move) to { moving = Moving(item.id, rowKey) },
                    stringResource(R.string.action_edit) to { editing = item },
                    stringResource(R.string.action_delete) to { deleting = item },
                ),
        )
    }
    editing?.let { item ->
        TvEditDialog(item = item, onDismiss = { editing = null }, onSave = {
            app.library.update(it)
            editing = null
        })
    }
    deleting?.let { item ->
        TvConfirmDialog(
            title = stringResource(R.string.delete_title),
            body = stringResource(R.string.delete_body, item.title),
            confirm = stringResource(R.string.action_delete),
            onDismiss = { deleting = null },
            onConfirm = {
                app.library.delete(item.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun TvFavoriteCard(
    item: Favorite,
    moving: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val resources = LocalResources.current
    val aired = Airing.dateAndEpisode(item).takeIf { it.isNotEmpty() }?.let { resources.getString(R.string.last_aired, it) }
    Column(Modifier.width(240.dp)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier =
                modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .then(if (moving) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                val picture = item.imageUrl.ifBlank { item.posterUrl }
                if (picture.isNotBlank()) {
                    AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, modifier = Modifier.padding(12.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Fixed lines, so the row keeps one height while it scrolls (DESIGN.md).
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (moving) stringResource(R.string.tv_moving) else aired.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (moving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvEmpty(
    onPair: () -> Unit,
    canPair: Boolean,
) {
    Column(Modifier.fillMaxSize().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.tv_empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (canPair) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPair) { Text(stringResource(R.string.tv_connect_phone)) }
        }
    }
}
