package to.wim.tvfavorites.ui.phone

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.launch.OpenFavoriteActivity
import to.wim.tvfavorites.launch.Launcher
import to.wim.tvfavorites.logic.Airing
import to.wim.tvfavorites.logic.Draft
import to.wim.tvfavorites.logic.ServiceCatalog
import to.wim.tvfavorites.logic.SharedLink
import to.wim.tvfavorites.ui.theme.AppShapes
import to.wim.tvfavorites.ui.theme.SkeletonBlock
import to.wim.tvfavorites.ui.theme.SkeletonLine
import to.wim.tvfavorites.ui.theme.readableWidth

/** A new favorite: looks up the shared link, then shows the details to check and save. */
@Composable
fun AddScreen(
    link: SharedLink,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.app
    var draft by remember(link) { mutableStateOf<Draft?>(null) }
    LaunchedEffect(link) {
        draft = runCatching { app.details.fetch(link) }.getOrElse {
            // Offline or a page that can't be read: the link alone, to fill in by hand.
            to.wim.tvfavorites.logic.DraftBuilder.build(link, link.url, null)
        }
    }
    DetailsForm(
        title = stringResource(R.string.add_title),
        draft = draft,
        onBack = onBack,
        onSave = { saved ->
            app.library.add(saved)
            Toast.makeText(context, resources.getString(R.string.added, saved.title), Toast.LENGTH_SHORT).show()
            onSaved()
        },
    )
}

/** An existing favorite: change its details, open it or delete it. */
@Composable
fun EditScreen(
    id: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.app
    val library by app.library.library.collectAsStateWithLifecycle()
    val item = library?.favorites?.firstOrNull { it.id == id }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (library != null && item == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val draft =
        item?.let {
            Draft(it.url, it.serviceId, it.title, it.description, it.imageUrl, it.tvMazeId, it.lastAired, it.lastEpisode)
        }
    DetailsForm(
        title = stringResource(R.string.edit_title),
        draft = draft,
        onBack = onBack,
        actions = {
            if (item != null) {
                IconButton(onClick = {
                    val result = Launcher(context).openOnPhone(item.url)
                    OpenFavoriteActivity.showResult(context, result, ServiceCatalog.nameOf(item.serviceId))
                }) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = stringResource(R.string.action_open))
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.action_delete))
                }
            }
        },
        onSave = { saved ->
            if (item != null) {
                app.library.update(
                    item.copy(
                        title = saved.title.trim(),
                        url = saved.url.trim(),
                        serviceId = saved.serviceId,
                        description = saved.description.trim(),
                        imageUrl = saved.imageUrl.trim(),
                        tvMazeId = saved.tvMazeId,
                        lastAired = saved.lastAired,
                        lastEpisode = saved.lastEpisode,
                    ),
                )
            }
            onBack()
        },
    )
    if (confirmDelete && item != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body, item.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    app.library.delete(item.id)
                    onBack()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsForm(
    title: String,
    draft: Draft?,
    onBack: () -> Unit,
    onSave: (Draft) -> Unit,
    actions: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    // The form's own copy, filled once the details are known.
    var form by rememberSaveable(draft != null, stateSaver = DraftSaver) { mutableStateOf(draft) }
    if (form == null && draft != null) form = draft
    var refreshing by remember { mutableStateOf(false) }
    var choosingService by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        val current = form
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (current == null) {
                val loading = stringResource(R.string.loading)
                Column(
                    Modifier.readableWidth().padding(16.dp).semantics(mergeDescendants = true) { contentDescription = loading },
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SkeletonBlock(height = 180.dp)
                    SkeletonLine(Modifier.fillMaxWidth(0.6f), height = 24.dp)
                    SkeletonBlock(height = 56.dp)
                    SkeletonBlock(height = 120.dp)
                }
                return@Box
            }
            Column(
                Modifier
                    .readableWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val picture = current.imageUrl
                if (picture.isNotBlank()) {
                    AsyncImage(
                        model = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(AppShapes.Item)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
                Airing.dateAndEpisode(toFavorite(current)).takeIf { it.isNotEmpty() }?.let {
                    Text(stringResource(R.string.last_aired, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { form = current.copy(title = it) },
                    label = { Text(stringResource(R.string.field_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.field_service)) },
                    supportingContent = { Text(ServiceCatalog.nameOf(current.serviceId)) },
                    modifier = Modifier.clip(AppShapes.Item).selectable(selected = false, role = Role.Button) { choosingService = true },
                )
                OutlinedTextField(
                    value = current.description,
                    onValueChange = { form = current.copy(description = it) },
                    label = { Text(stringResource(R.string.field_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = current.url,
                    onValueChange = { form = current.copy(url = it) },
                    label = { Text(stringResource(R.string.field_link)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = current.imageUrl,
                    onValueChange = { form = current.copy(imageUrl = it) },
                    label = { Text(stringResource(R.string.field_picture)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onSave(current) }, enabled = current.title.isNotBlank() && current.url.startsWith("http")) {
                        Text(stringResource(R.string.action_save))
                    }
                    OutlinedButton(
                        enabled = !refreshing,
                        onClick = {
                            refreshing = true
                            scope.launch {
                                val link = SharedLink(current.url, current.title)
                                val fresh = runCatching { app.details.fetch(link) }.getOrNull()
                                if (fresh != null) {
                                    form =
                                        current.copy(
                                            title = fresh.title.ifBlank { current.title },
                                            description = fresh.description.ifBlank { current.description },
                                            imageUrl = fresh.imageUrl.ifBlank { current.imageUrl },
                                            tvMazeId = fresh.tvMazeId,
                                            lastAired = fresh.lastAired,
                                            lastEpisode = fresh.lastEpisode,
                                        )
                                } else {
                                    Toast.makeText(context, R.string.refresh_failed, Toast.LENGTH_SHORT).show()
                                }
                                refreshing = false
                            }
                        },
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                        }
                        Text(stringResource(R.string.action_refresh_details))
                    }
                }
            }
        }
    }

    val current = form
    if (choosingService && current != null) {
        ServiceChoiceDialog(
            selected = current.serviceId,
            linkServiceId = ServiceCatalog.serviceIdFor(current.url),
            onDismiss = { choosingService = false },
            onChoose = {
                form = current.copy(serviceId = it)
                choosingService = false
            },
        )
    }
}

private fun toFavorite(d: Draft) = to.wim.tvfavorites.model.Favorite(id = "", title = d.title, url = d.url, serviceId = d.serviceId, lastAired = d.lastAired, lastEpisode = d.lastEpisode)

@Composable
private fun ServiceChoiceDialog(
    selected: String,
    linkServiceId: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    // The link's own site first (also when the app doesn't know it), then every known service.
    val ids = (listOf(selected, linkServiceId) + ServiceCatalog.distinct.map { it.id }).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.field_service)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ids.forEach { id ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = id == selected, role = Role.RadioButton) { onChoose(id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == selected, onClick = null)
                        Text(ServiceCatalog.nameOf(id), Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Keeps the form across rotation. */
private val DraftSaver =
    androidx.compose.runtime.saveable.Saver<Draft?, List<String>>(
        save = { d -> d?.let { listOf(it.url, it.serviceId, it.title, it.description, it.imageUrl, it.tvMazeId.toString(), it.lastAired, it.lastEpisode) } ?: emptyList() },
        restore = { l -> if (l.size < 8) null else Draft(l[0], l[1], l[2], l[3], l[4], l[5].toLongOrNull() ?: 0, l[6], l[7]) },
    )
