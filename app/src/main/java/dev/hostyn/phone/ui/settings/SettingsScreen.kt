package dev.hostyn.phone.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Voicemail
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import dev.hostyn.phone.ui.components.rememberInstalledMessagingApps
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.R
import dev.hostyn.phone.data.AppSettings
import dev.hostyn.phone.data.BackupStatus
import dev.hostyn.phone.data.ContactsBackup
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.data.NameOrder
import dev.hostyn.phone.data.ThemeMode
import dev.hostyn.phone.data.exportContacts
import dev.hostyn.phone.ui.components.ImportDialog
import dev.hostyn.phone.ui.components.formatCallTime
import dev.hostyn.phone.ui.components.isDefaultDialer
import dev.hostyn.phone.ui.components.rememberOnResume
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    repo: ContactsRepository,
    onBack: () -> Unit,
    onBlockedNumbers: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val isDialer = rememberOnResume { context.isDefaultDialer() }

    var choice by remember { mutableStateOf<Choice?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var backingUp by remember { mutableStateOf(false) }

    fun open(intent: Intent) = try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_no_app), Toast.LENGTH_SHORT).show()
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { importUri = it }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) scope.launch {
            val count = exportContacts(context, repo, uri)
            val message = if (count == null) context.getString(R.string.error_export)
            else context.resources.getQuantityString(R.plurals.exported_count, count, count)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun backupNow() {
        backingUp = true
        scope.launch {
            val status = ContactsBackup.run(context, force = true)
            backingUp = false
            Toast.makeText(
                context,
                status.error?.let { context.getString(backupErrorText(it)) } ?: context.getString(R.string.backup_done),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Release the previous folder, then keep access to the new one across reboots.
        settings.backupFolder?.let { old ->
            try { context.contentResolver.releasePersistableUriPermission(Uri.parse(old), flags) } catch (_: Exception) {}
        }
        context.contentResolver.takePersistableUriPermission(uri, flags)
        settings.updateBackup(enabled = true, folder = uri.toString())
        ContactsBackup.schedule(context)
        backupNow()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            // --- Backup ---
            item {
                Section(stringResource(R.string.backup)) {
                    SwitchRow(
                        icon = Icons.Rounded.Backup,
                        title = stringResource(R.string.auto_backup),
                        summary = stringResource(R.string.auto_backup_summary),
                        checked = settings.backupEnabled && settings.backupFolder != null,
                        onChange = { on ->
                            if (on && settings.backupFolder == null) folderPicker.launch(null)
                            else {
                                settings.updateBackup(enabled = on)
                                ContactsBackup.schedule(context)
                                if (on) backupNow()
                            }
                        },
                    )
                    if (settings.backupEnabled && settings.backupFolder != null) {
                        val folderName = remember(settings.backupFolder) { ContactsBackup.folderName(context, settings.backupFolder) }
                        SettingRow(
                            icon = Icons.Rounded.Folder,
                            title = stringResource(R.string.backup_folder),
                            summary = folderName ?: stringResource(R.string.backup_folder_missing),
                            summaryIsError = folderName == null,
                            onClick = { folderPicker.launch(settings.backupFolder?.let(Uri::parse)) },
                        )
                        SettingRow(
                            icon = Icons.Rounded.Inventory2,
                            title = stringResource(R.string.backup_keep),
                            summary = keepLabel(context, settings.backupKeep),
                            onClick = {
                                choice = choiceOf(R.string.backup_keep, listOf(1, 7, 30, 0), settings.backupKeep, { keepLabel(context, it) }) {
                                    settings.updateBackup(keep = it)
                                }
                            },
                        )
                        BackupStatusRow(settings.backupStatus)
                        Row(Modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                            FilledTonalButton(onClick = ::backupNow, enabled = !backingUp) {
                                Text(stringResource(R.string.backup_now))
                            }
                        }
                        Text(
                            stringResource(R.string.backup_restore_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 64.dp, end = 24.dp, bottom = 12.dp),
                        )
                    } else {
                        SettingRow(
                            icon = Icons.Rounded.Folder,
                            title = stringResource(R.string.backup_folder),
                            summary = stringResource(R.string.backup_folder_none),
                            onClick = { folderPicker.launch(null) },
                        )
                    }
                }
            }

            // --- Contacts ---
            item {
                Section(stringResource(R.string.contacts_settings)) {
                    SettingRow(
                        icon = Icons.AutoMirrored.Rounded.Sort,
                        title = stringResource(R.string.sort_by),
                        summary = stringResource(sortLabel(settings.sortOrder)),
                        onClick = {
                            choice = choiceOf(R.string.sort_by, NameOrder.entries, settings.sortOrder, { context.getString(sortLabel(it)) }, settings::updateSortOrder)
                        },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Badge,
                        title = stringResource(R.string.name_format),
                        summary = stringResource(formatLabel(settings.nameFormat)),
                        onClick = {
                            choice = choiceOf(R.string.name_format, NameOrder.entries, settings.nameFormat, { context.getString(formatLabel(it)) }, settings::updateNameFormat)
                        },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Download,
                        title = stringResource(R.string.import_contacts),
                        onClick = { importPicker.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "application/octet-stream")) },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Upload,
                        title = stringResource(R.string.export_contacts),
                        onClick = { exportPicker.launch("contacts.vcf") },
                    )
                }
            }

            // --- Calls ---
            item {
                Section(stringResource(R.string.calls_settings)) {
                    SwitchRow(
                        icon = Icons.Rounded.VolumeUp,
                        title = stringResource(R.string.dialpad_tones),
                        checked = settings.dialpadTones,
                        onChange = settings::updateDialpadTones,
                    )
                    SwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = stringResource(R.string.dialpad_vibration),
                        checked = settings.dialpadVibration,
                        onChange = settings::updateDialpadVibration,
                    )
                    if (isDialer) {
                        SettingRow(
                            icon = Icons.Rounded.Block,
                            title = stringResource(R.string.blocked_numbers),
                            summary = stringResource(R.string.blocked_numbers_summary),
                            onClick = onBlockedNumbers,
                        )
                    }
                    SettingRow(
                        icon = Icons.Rounded.Voicemail,
                        title = stringResource(R.string.voicemail),
                        summary = stringResource(R.string.voicemail_settings_summary),
                        onClick = { open(Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)) },
                    )
                    SettingRow(
                        icon = Icons.Rounded.SimCard,
                        title = stringResource(R.string.calling_accounts),
                        summary = stringResource(R.string.calling_accounts_summary),
                        onClick = { open(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) },
                    )
                }
            }

            // --- Chat apps ---
            item {
                val apps = rememberInstalledMessagingApps()
                Section(stringResource(R.string.chat_buttons)) {
                    if (apps.isEmpty()) {
                        SettingRow(
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            title = stringResource(R.string.chat_buttons_none),
                            summary = stringResource(R.string.chat_buttons_none_summary),
                        )
                    } else {
                        apps.forEach { app ->
                            val checked = settings.showsMessagingApp(app.installed.app)
                            val onChange = { shown: Boolean -> settings.updateMessagingApp(app.installed.app, shown) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onChange(!checked) }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            ) {
                                Image(app.icon, null, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(20.dp))
                                Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Switch(checked = checked, onCheckedChange = onChange)
                            }
                        }
                    }
                }
            }

            // --- Appearance ---
            item {
                Section(stringResource(R.string.appearance)) {
                    SettingRow(
                        icon = Icons.Rounded.DarkMode,
                        title = stringResource(R.string.theme),
                        summary = stringResource(themeLabel(settings.theme)),
                        onClick = {
                            choice = choiceOf(R.string.theme, ThemeMode.entries, settings.theme, { context.getString(themeLabel(it)) }, settings::updateTheme)
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchRow(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.dynamic_color),
                            summary = stringResource(R.string.dynamic_color_summary),
                            checked = settings.dynamicColor,
                            onChange = settings::updateDynamicColor,
                        )
                    }
                }
            }

            // --- About ---
            item {
                Section(stringResource(R.string.about)) {
                    val version = remember {
                        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Exception) { null }
                    }
                    SettingRow(icon = Icons.Rounded.Info, title = stringResource(R.string.version), summary = version.orEmpty())
                }
            }
        }
    }

    choice?.let { c -> ChoiceDialog(c) { choice = null } }
    importUri?.let { uri -> ImportDialog(uri, repo) { importUri = null } }
}

// --- Labels ---

private fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

private fun sortLabel(o: NameOrder) = if (o == NameOrder.GivenFirst) R.string.sort_first_name else R.string.sort_last_name

private fun formatLabel(o: NameOrder) = if (o == NameOrder.GivenFirst) R.string.first_name_first else R.string.last_name_first

private fun keepLabel(context: Context, keep: Int): String = when (keep) {
    0 -> context.getString(R.string.backup_keep_all)
    1 -> context.getString(R.string.backup_keep_one)
    else -> context.resources.getQuantityString(R.plurals.backup_keep_count, keep, keep)
}

private fun backupErrorText(code: String) = when (code) {
    ContactsBackup.ERROR_NO_FOLDER -> R.string.backup_error_no_folder
    ContactsBackup.ERROR_NO_PERMISSION -> R.string.backup_error_no_permission
    ContactsBackup.ERROR_NO_CONTACTS -> R.string.backup_error_no_contacts
    else -> R.string.backup_error_write
}

@Composable
private fun BackupStatusRow(status: BackupStatus) {
    val context = LocalContext.current
    val summary = when {
        status.error != null -> stringResource(backupErrorText(status.error))
        status.file == null -> stringResource(R.string.backup_never)
        else -> listOf(
            formatCallTime(context, status.time),
            pluralStringResource(R.plurals.contacts_count, status.count, status.count),
            status.file,
        ).joinToString(" · ")
    }
    SettingRow(
        icon = Icons.Rounded.History,
        title = stringResource(R.string.backup_last),
        summary = summary,
        summaryIsError = status.error != null,
    )
}

// --- Building blocks ---

/** A single-choice dialog; labels are resolved when it is opened. */
private class Choice(val title: Int, val labels: List<String>, val selected: Int, val onSelect: (Int) -> Unit)

private fun <T> choiceOf(title: Int, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) =
    Choice(title, options.map(label), options.indexOf(selected)) { onSelect(options[it]) }

@Composable
private fun ChoiceDialog(choice: Choice, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(choice.title)) },
        text = {
            Column {
                choice.labels.forEachIndexed { i, label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(selected = i == choice.selected, role = Role.RadioButton) {
                                choice.onSelect(i)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    ) {
                        RadioButton(selected = i == choice.selected, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 76.dp, top = 24.dp, bottom = 4.dp),
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    summaryIsError: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!summary.isNullOrEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summaryIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, summary: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(icon = icon, title = title, summary = summary, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
