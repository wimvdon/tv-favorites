package to.wim.tvfavorites.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import to.wim.tvfavorites.R
import to.wim.tvfavorites.model.Favorite

@Composable
fun TvDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

/** What can be done with a favorite (a long press on its card). */
@Composable
fun TvActionsDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<Pair<String, () -> Unit>>,
) {
    val first = remember { FocusRequester() }
    TvDialogFrame(title = title, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { index, (label, action) ->
                Button(
                    onClick = {
                        onDismiss()
                        action()
                    },
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                ) { Text(label) }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
fun TvConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val cancel = remember { FocusRequester() }
    TvDialogFrame(title = title, onDismiss = onDismiss) {
        Text(body, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cancel has the focus first: a destructive action is never one click away.
            OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(cancel)) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onConfirm) { Text(confirm) }
        }
    }
    LaunchedEffect(Unit) { runCatching { cancel.requestFocus() } }
}

/** Title and description; the other details are easier to change on the phone. */
@Composable
fun TvEditDialog(
    item: Favorite,
    onDismiss: () -> Unit,
    onSave: (Favorite) -> Unit,
) {
    var title by remember { mutableStateOf(item.title) }
    var description by remember { mutableStateOf(item.description) }
    TvDialogFrame(title = stringResource(R.string.edit_title), onDismiss = onDismiss) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { androidx.compose.material3.Text(stringResource(R.string.field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { androidx.compose.material3.Text(stringResource(R.string.field_description)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(item.copy(title = title.trim(), description = description.trim())) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
