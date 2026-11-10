package to.wim.tvfavorites.ui.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.data.Connection
import to.wim.tvfavorites.launch.Launcher
import to.wim.tvfavorites.logic.InstalledApp
import to.wim.tvfavorites.logic.Pairing
import to.wim.tvfavorites.logic.ServiceCatalog
import to.wim.tvfavorites.logic.Tmdb
import to.wim.tvfavorites.model.ServiceOverride
import to.wim.tvfavorites.tv.HomeChannels
import to.wim.tvfavorites.ui.theme.rememberAppVersion

@Composable
private fun TvPage(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 48.dp, vertical = 27.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(16.dp))
        content()
    }
}

/** The QR code a phone scans to share this TV's list. */
@Composable
fun TvPairScreen(onDone: () -> Unit) {
    val app = LocalContext.current.app
    val connection by app.library.connection.collectAsStateWithLifecycle()
    val household = (connection as? Connection.Shared)?.household
    val done = remember { FocusRequester() }
    TvPage(stringResource(R.string.tv_connect_phone)) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(300.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                if (household != null) {
                    val bitmap = remember(household) { qrBitmap(Pairing.qrContent(household), 512) }
                    Image(bitmap.asImageBitmap(), contentDescription = stringResource(R.string.tv_qr_description), modifier = Modifier.fillMaxSize())
                } else {
                    Text(stringResource(R.string.tv_qr_waiting), color = androidx.compose.ui.graphics.Color.Black)
                }
            }
            Column(Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tv_pair_steps), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.tv_pair_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onDone, modifier = Modifier.focusRequester(done)) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { done.requestFocus() } }
}

fun qrBitmap(content: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun TvSettingsScreen(
    onPair: () -> Unit,
    onServices: () -> Unit,
    onRows: () -> Unit,
) {
    val app = LocalContext.current.app
    val scope = rememberCoroutineScope()
    val connection by app.library.connection.collectAsStateWithLifecycle()
    val library by app.library.library.collectAsStateWithLifecycle()
    val device by app.settings.device.collectAsStateWithLifecycle(to.wim.tvfavorites.data.DeviceState())
    var editingToken by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val token = device.tmdbToken.ifBlank { library?.tmdbToken.orEmpty() }
    val version = rememberAppVersion()
    TvPage(stringResource(R.string.settings_title)) {
        LazyColumn(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (connection !is Connection.Unavailable) {
                item {
                    ListItem(
                        selected = false,
                        onClick = onPair,
                        headlineContent = { Text(stringResource(R.string.tv_connect_phone)) },
                        supportingContent = { Text(stringResource(R.string.tv_connect_phone_summary)) },
                        modifier = Modifier.focusRequester(first),
                    )
                }
            } else {
                item { Text(stringResource(R.string.sync_unavailable), modifier = Modifier.padding(16.dp)) }
            }
            item {
                ListItem(
                    selected = false,
                    onClick = { editingToken = true },
                    headlineContent = { Text(stringResource(R.string.tmdb_title)) },
                    supportingContent = { Text(stringResource(if (token.isBlank()) R.string.tmdb_summary_missing_tv else R.string.tmdb_summary_set)) },
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = onServices,
                    headlineContent = { Text(stringResource(R.string.services_title)) },
                    supportingContent = { Text(stringResource(R.string.services_summary)) },
                )
            }
            item {
                ListItem(
                    selected = false,
                    onClick = onRows,
                    headlineContent = { Text(stringResource(R.string.tv_rows_title)) },
                    supportingContent = { Text(stringResource(R.string.tv_rows_summary)) },
                )
            }
            item {
                Text(
                    stringResource(R.string.footer, stringResource(R.string.app_name), version) + "\n" +
                        stringResource(R.string.tmdb_attribution) + "\n" + stringResource(R.string.tvmaze_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    if (editingToken) {
        var text by remember { mutableStateOf(device.tmdbToken) }
        TvDialogFrame(title = stringResource(R.string.tmdb_title), onDismiss = { editingToken = false }) {
            Text(stringResource(R.string.tmdb_tv_body), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.trim() },
                label = { androidx.compose.material3.Text(stringResource(R.string.tmdb_field)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = text.isBlank() || Tmdb.looksLikeToken(text),
                    onClick = {
                        editingToken = false
                        scope.launch { app.library.setTmdbToken(text) }
                    },
                ) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = { editingToken = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }
}

/** Per service: the installed TV app it opens in (picked from a list) and an optional link pattern. */
@Composable
fun TvServicesScreen() {
    val context = LocalContext.current
    val app = context.app
    val library by app.library.library.collectAsStateWithLifecycle()
    val installed by produceState(emptyList<InstalledApp>()) { value = Launcher(context).installedApps() }
    var editing by remember { mutableStateOf<String?>(null) }
    val inList = library?.favorites.orEmpty().map { it.serviceId }.distinct()
    val ids = (inList + ServiceCatalog.distinct.map { it.id }).distinct()
    val overrides = library?.overrides.orEmpty()
    val first = remember { FocusRequester() }
    TvPage(stringResource(R.string.services_title)) {
        Text(stringResource(R.string.services_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        LazyColumn(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(ids, key = { it }) { id ->
                val found = to.wim.tvfavorites.logic.DeepLinks.findApp(id, overrides[id], installed)
                ListItem(
                    selected = false,
                    onClick = { editing = id },
                    headlineContent = { Text(ServiceCatalog.nameOf(id)) },
                    supportingContent = {
                        Text(found?.label ?: stringResource(R.string.tv_app_not_found))
                    },
                    modifier = if (id == ids.firstOrNull()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(ids.isNotEmpty()) { runCatching { first.requestFocus() } }
    editing?.let { id ->
        TvOverrideDialog(
            serviceId = id,
            initial = overrides[id] ?: ServiceOverride(),
            installed = installed,
            onDismiss = { editing = null },
            onSave = {
                app.library.setOverride(id, it)
                editing = null
            },
        )
    }
}

@Composable
private fun TvOverrideDialog(
    serviceId: String,
    initial: ServiceOverride,
    installed: List<InstalledApp>,
    onDismiss: () -> Unit,
    onSave: (ServiceOverride) -> Unit,
) {
    var pkg by remember { mutableStateOf(initial.packageName) }
    var template by remember { mutableStateOf(initial.linkTemplate) }
    TvDialogFrame(title = ServiceCatalog.nameOf(serviceId), onDismiss = onDismiss) {
        Text(stringResource(R.string.tv_pick_app), style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListItem(selected = pkg.isBlank(), onClick = { pkg = "" }, headlineContent = { Text(stringResource(R.string.service_automatic)) })
            installed.forEach { a ->
                ListItem(
                    selected = pkg == a.packageName,
                    onClick = { pkg = a.packageName },
                    headlineContent = { Text(a.label) },
                    supportingContent = { Text(a.packageName) },
                )
            }
        }
        OutlinedTextField(
            value = template,
            onValueChange = { template = it.trim() },
            label = { androidx.compose.material3.Text(stringResource(R.string.override_template)) },
            supportingText = { androidx.compose.material3.Text(stringResource(R.string.override_template_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(ServiceOverride(pkg, template)) }) { Text(stringResource(R.string.action_save)) }
            OutlinedButton(onClick = { onSave(ServiceOverride()) }) { Text(stringResource(R.string.action_reset)) }
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/** The service rows on the home screen: Android asks once per row whether to show it. */
@Composable
fun TvRowsScreen() {
    val context = LocalContext.current
    val channels by produceState(emptyMap<String, Long>()) { value = HomeChannels(context).serviceChannels() }
    val first = remember { FocusRequester() }
    TvPage(stringResource(R.string.tv_rows_title)) {
        Text(stringResource(R.string.tv_rows_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        if (channels.isEmpty()) {
            Text(stringResource(R.string.tv_rows_none), style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(channels.entries.toList(), key = { it.key }) { (key, id) ->
                ListItem(
                    selected = false,
                    onClick = { runCatching { context.startActivity(HomeChannels(context).browsableRequest(id)) } },
                    headlineContent = { Text(ServiceCatalog.nameOf(key)) },
                    supportingContent = { Text(stringResource(R.string.tv_rows_add)) },
                    modifier = if (key == channels.keys.firstOrNull()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
    LaunchedEffect(channels.isNotEmpty()) { runCatching { first.requestFocus() } }
}
