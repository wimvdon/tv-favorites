package to.wim.tvfavorites.ui.phone

import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.data.Connection
import to.wim.tvfavorites.data.DeviceState
import to.wim.tvfavorites.data.Settings
import to.wim.tvfavorites.data.ThemeMode
import to.wim.tvfavorites.data.await
import to.wim.tvfavorites.logic.Pairing
import to.wim.tvfavorites.logic.Tmdb
import to.wim.tvfavorites.ui.theme.AppearanceSettings
import to.wim.tvfavorites.ui.theme.ExpandableSetting
import to.wim.tvfavorites.ui.theme.SettingItem
import to.wim.tvfavorites.ui.theme.SettingsBlock
import to.wim.tvfavorites.ui.theme.SettingsFooter
import to.wim.tvfavorites.ui.theme.SettingsScaffold
import to.wim.tvfavorites.ui.theme.SettingsSection
import to.wim.tvfavorites.ui.theme.SwitchSetting
import to.wim.tvfavorites.ui.theme.rememberAppVersion

@Composable
fun SettingsScreen(
    settings: Settings,
    device: DeviceState,
    onBack: () -> Unit,
    onServices: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val connection by app.library.connection.collectAsStateWithLifecycle()
    val library by app.library.library.collectAsStateWithLifecycle()
    var editingToken by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val sharedToken = library?.tmdbToken.orEmpty()
    val token = device.tmdbToken.ifBlank { sharedToken }
    val version = rememberAppVersion()

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        scope.launch {
            val raw = runCatching { GmsBarcodeScanning.getClient(context, options).startScan().await().rawValue }.getOrNull() ?: return@launch
            val household = Pairing.parse(raw)
            if (household == null) {
                snackbar.showSnackbar(resources.getString(R.string.pair_wrong_code))
                return@launch
            }
            busy = true
            val result = runCatching { app.library.join(household) }
            busy = false
            snackbar.showSnackbar(
                if (result.isSuccess) resources.getString(R.string.pair_done) else resources.getString(R.string.pair_failed, result.exceptionOrNull()?.message.orEmpty()),
            )
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        backLabel = stringResource(R.string.action_back),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        item {
            SettingsSection(stringResource(R.string.section_connection)) {
                when (connection) {
                    Connection.Unavailable -> SettingsBlock { Text(stringResource(R.string.sync_unavailable), style = MaterialTheme.typography.bodyMedium) }
                    Connection.Local ->
                        SettingItem(
                            title = stringResource(R.string.pair_title),
                            summary = stringResource(if (busy) R.string.pair_busy else R.string.pair_summary),
                            onClick = ::scan,
                            enabled = !busy,
                            icon = painterResource(R.drawable.ic_qr_code),
                        )
                    is Connection.Shared -> {
                        SettingItem(
                            title = stringResource(R.string.paired_title),
                            summary = stringResource(R.string.paired_summary),
                            onClick = ::scan,
                            enabled = !busy,
                            icon = painterResource(R.drawable.ic_tv),
                        )
                        SettingItem(
                            title = stringResource(R.string.unpair_title),
                            summary = null,
                            onClick = { confirmLeave = true },
                        )
                    }
                }
                SettingItem(
                    title = stringResource(R.string.tmdb_title),
                    summary = stringResource(if (token.isBlank()) R.string.tmdb_summary_missing else R.string.tmdb_summary_set),
                    onClick = { editingToken = true },
                    icon = painterResource(R.drawable.ic_key),
                )
                ExpandableSetting(key = "tmdb_steps", title = stringResource(R.string.tmdb_steps_title)) {
                    Text(stringResource(R.string.tmdb_steps), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.section_favorites)) {
                SwitchSetting(
                    title = stringResource(R.string.action_group_by_service),
                    summary = null,
                    checked = settings.groupByService,
                    onCheckedChange = { scope.launch { app.settings.setGroupByService(it) } },
                )
                SettingItem(
                    title = stringResource(R.string.services_title),
                    summary = stringResource(R.string.services_summary),
                    onClick = onServices,
                    icon = painterResource(R.drawable.ic_apps),
                    trailing = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
                )
            }
        }
        item {
            SettingsSection(stringResource(R.string.section_appearance)) {
                AppearanceSettings(
                    themeLabel = stringResource(R.string.theme),
                    themeOptions = listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    selectedTheme = settings.theme.ordinal,
                    onThemeSelect = { scope.launch { app.settings.setTheme(ThemeMode.entries[it]) } },
                    dynamicColorTitle = stringResource(R.string.dynamic_color),
                    dynamicColorSummary = stringResource(R.string.dynamic_color_summary),
                    dynamicColor = settings.dynamicColor,
                    onDynamicColorChange = { scope.launch { app.settings.setDynamicColor(it) } },
                    dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                )
            }
        }
        item {
            SettingsFooter(stringResource(R.string.footer, stringResource(R.string.app_name), version)) {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.themoviedb.org".toUri())) }
                }) { Text(stringResource(R.string.tmdb_attribution)) }
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.tvmaze.com".toUri())) }
                }) { Text(stringResource(R.string.tvmaze_attribution)) }
            }
        }
    }

    if (editingToken) {
        TokenDialog(
            initial = device.tmdbToken,
            onDismiss = { editingToken = false },
            onSave = {
                editingToken = false
                scope.launch { app.library.setTmdbToken(it) }
            },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.unpair_title)) },
            text = { Text(stringResource(R.string.unpair_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    scope.launch { app.library.leave() }
                }) { Text(stringResource(R.string.unpair_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun TokenDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val valid = text.isBlank() || Tmdb.looksLikeToken(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tmdb_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tmdb_dialog_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    label = { Text(stringResource(R.string.tmdb_field)) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) ({ Text(stringResource(R.string.tmdb_invalid)) }) else null,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
