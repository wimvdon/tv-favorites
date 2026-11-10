package to.wim.tvfavorites.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import to.wim.tvfavorites.R
import to.wim.tvfavorites.app
import to.wim.tvfavorites.logic.ServiceCatalog
import to.wim.tvfavorites.model.ServiceOverride
import to.wim.tvfavorites.ui.theme.SettingItem
import to.wim.tvfavorites.ui.theme.SettingsBlock
import to.wim.tvfavorites.ui.theme.SettingsScaffold
import to.wim.tvfavorites.ui.theme.SettingsSection

/**
 * Settings › Streaming apps: per service, which TV app opens it and in what form the link is
 * sent. Shared with the TV, where the app can also be picked from the installed ones.
 */
@Composable
fun ServicesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.app
    val library by app.library.library.collectAsStateWithLifecycle()
    val overrides = library?.overrides.orEmpty()
    val inList = library?.favorites.orEmpty().map { it.serviceId }.distinct()
    val others = ServiceCatalog.distinct.map { it.id }.filterNot { it in inList }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsScaffold(title = stringResource(R.string.services_title), backLabel = stringResource(R.string.action_back), onBack = onBack) {
        item {
            SettingsBlock { Text(stringResource(R.string.services_body), style = MaterialTheme.typography.bodyMedium) }
        }
        if (inList.isNotEmpty()) {
            item {
                SettingsSection(stringResource(R.string.services_in_list)) {
                    inList.forEach { id -> ServiceRow(id, overrides[id]) { editing = id } }
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.services_all)) {
                others.forEach { id -> ServiceRow(id, overrides[id]) { editing = id } }
            }
        }
    }

    editing?.let { id ->
        OverrideDialog(
            serviceId = id,
            initial = overrides[id] ?: ServiceOverride(),
            onDismiss = { editing = null },
            onSave = {
                app.library.setOverride(id, it)
                editing = null
            },
        )
    }
}

@Composable
private fun ServiceRow(
    id: String,
    override: ServiceOverride?,
    onClick: () -> Unit,
) {
    val summary =
        when {
            override == null || override.isEmpty -> stringResource(R.string.service_automatic)
            override.packageName.isNotBlank() -> override.packageName
            else -> override.linkTemplate
        }
    SettingItem(title = ServiceCatalog.nameOf(id), summary = summary, onClick = onClick)
}

@Composable
fun OverrideDialog(
    serviceId: String,
    initial: ServiceOverride,
    onDismiss: () -> Unit,
    onSave: (ServiceOverride) -> Unit,
) {
    var pkg by rememberSaveable { mutableStateOf(initial.packageName) }
    var template by rememberSaveable { mutableStateOf(initial.linkTemplate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ServiceCatalog.nameOf(serviceId)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pkg,
                    onValueChange = { pkg = it.trim() },
                    label = { Text(stringResource(R.string.override_package)) },
                    supportingText = { Text(stringResource(R.string.override_package_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it.trim() },
                    label = { Text(stringResource(R.string.override_template)) },
                    supportingText = { Text(stringResource(R.string.override_template_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(ServiceOverride(pkg, template)) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            TextButton(onClick = { onSave(ServiceOverride()) }) { Text(stringResource(R.string.action_reset)) }
        },
    )
}
