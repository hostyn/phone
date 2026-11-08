package dev.hostyn.phone.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.R
import dev.hostyn.phone.data.ContactAccount
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.data.importContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asks for confirmation (and the account, if there's a choice) before importing a vCard file. */
@Composable
fun ImportDialog(uri: Uri, repo: ContactsRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accounts by remember { mutableStateOf(listOf(ContactAccount.Local)) }
    var selected by remember { mutableStateOf(ContactAccount.Local) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accounts = withContext(Dispatchers.IO) { repo.writableAccounts() }
        selected = accounts.firstOrNull { !it.isLocal } ?: ContactAccount.Local
    }

    AlertDialog(
        onDismissRequest = { if (!importing) onDone() },
        icon = { Icon(Icons.Rounded.Download, null) },
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column {
                Text(stringResource(R.string.import_body))
                if (accounts.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.save_to), style = MaterialTheme.typography.labelLarge)
                    accounts.forEach { a ->
                        val (title, subtitle) = accountTitle(context, a)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = a == selected, enabled = !importing, role = Role.RadioButton) { selected = a }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = a == selected, onClick = null, enabled = !importing, modifier = Modifier.padding(horizontal = 8.dp))
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(title, style = MaterialTheme.typography.bodyLarge)
                                if (subtitle != null) {
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (importing) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !importing,
                onClick = {
                    importing = true
                    scope.launch {
                        val count = importContacts(context, repo, uri, selected)
                        val message = if (count == null) context.getString(R.string.error_import)
                        else context.resources.getQuantityString(R.plurals.imported_count, count, count)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                },
            ) { Text(stringResource(R.string.import_action)) }
        },
        dismissButton = {
            TextButton(enabled = !importing, onClick = onDone) { Text(stringResource(R.string.cancel)) }
        },
    )
}
