@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.hostyn.phone.ui.edit

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Intents.Insert
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.hostyn.phone.PhoneApp
import dev.hostyn.phone.R
import dev.hostyn.phone.data.ContactDraft
import dev.hostyn.phone.data.ContactField
import dev.hostyn.phone.data.ContactSummary
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.data.NameField
import dev.hostyn.phone.data.OrganizationField
import dev.hostyn.phone.data.PostalAddress
import dev.hostyn.phone.data.TextField
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.ui.PermissionGate
import dev.hostyn.phone.ui.components.ContactAvatar
import dev.hostyn.phone.ui.components.foldAccents
import dev.hostyn.phone.ui.theme.PhoneTheme

/**
 * "Add to contacts" from other apps (WhatsApp, Messages…): ACTION_INSERT opens the editor with
 * what the app sent, saved to the default account; ACTION_INSERT_OR_EDIT first lets the user
 * pick a contact to add it to, or create a new one. It runs in the calling app's task, so saving
 * or going back returns there, and the app gets the contact as the result.
 */
class InsertContactActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = (application as PhoneApp).repository
        val prefill = prefillFrom(intent)
        val pickFirst = intent.action == Intent.ACTION_INSERT_OR_EDIT

        setContent {
            PhoneTheme {
                PermissionGate {
                    // null = still choosing (insert or edit), -1 = new contact, else the contact to add to.
                    var target by rememberSaveable { mutableStateOf(if (pickFirst) null else -1L) }
                    when (val t = target) {
                        null -> PickContactScreen(
                            repo = repo,
                            onNew = { target = -1L },
                            onPick = { target = it },
                            onBack = ::finish,
                        )
                        else -> EditScreen(
                            repo = repo,
                            contactId = t.takeIf { it > 0 },
                            prefill = prefill,
                            onClose = { if (pickFirst) target = null else finish() },
                            onCreated = ::done,
                            onSaved = ::done,
                        )
                    }
                }
            }
        }
    }

    private fun done(contactId: Long) {
        setResult(RESULT_OK, Intent().setData(ContactsContract.Contacts.getLookupUri(contentResolver, contactUri(contactId))))
        finish()
    }

    private fun contactUri(id: Long) = android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
}

/** The contact data in an insert intent's extras ([Insert]), as the editor's draft. */
private fun prefillFrom(intent: Intent): ContactDraft {
    val extras = intent.extras ?: return ContactDraft()

    fun typed(valueKey: String, typeKey: String, default: Int): ContactField? {
        val value = extras.getCharSequence(valueKey)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // The type is an int, or a custom label as text.
        @Suppress("DEPRECATION")
        return when (val type = extras.get(typeKey)) {
            is Int -> ContactField(value, type)
            is CharSequence -> ContactField(value, Phone.TYPE_CUSTOM, type.toString())
            else -> ContactField(value, default)
        }
    }

    val phones = mutableListOf<ContactField>()
    val emails = mutableListOf<ContactField>()
    val websites = mutableListOf<ContactField>()
    listOfNotNull(
        typed(Insert.PHONE, Insert.PHONE_TYPE, Phone.TYPE_MOBILE),
        typed(Insert.SECONDARY_PHONE, Insert.SECONDARY_PHONE_TYPE, Phone.TYPE_OTHER),
        typed(Insert.TERTIARY_PHONE, Insert.TERTIARY_PHONE_TYPE, Phone.TYPE_OTHER),
    ).forEach { phones += it }
    listOfNotNull(
        typed(Insert.EMAIL, Insert.EMAIL_TYPE, Email.TYPE_HOME),
        typed(Insert.SECONDARY_EMAIL, Insert.SECONDARY_EMAIL_TYPE, Email.TYPE_OTHER),
        typed(Insert.TERTIARY_EMAIL, Insert.TERTIARY_EMAIL_TYPE, Email.TYPE_OTHER),
    ).forEach { emails += it }

    var name = splitName(extras.getCharSequence(Insert.NAME)?.toString())
    // Apps can also send rows of data, like the provider's (Insert.DATA).
    @Suppress("DEPRECATION")
    (extras.getParcelableArrayList<ContentValues>(Insert.DATA)).orEmpty().forEach { row ->
        val v = row.getAsString(ContactsContract.Data.DATA1)?.trim().orEmpty()
        val type = row.getAsInteger(ContactsContract.Data.DATA2)
        val label = row.getAsString(ContactsContract.Data.DATA3)
        when (row.getAsString(ContactsContract.Data.MIMETYPE)) {
            Phone.CONTENT_ITEM_TYPE -> if (v.isNotEmpty()) phones += ContactField(v, type ?: Phone.TYPE_MOBILE, label)
            Email.CONTENT_ITEM_TYPE -> if (v.isNotEmpty()) emails += ContactField(v, type ?: Email.TYPE_HOME, label)
            Website.CONTENT_ITEM_TYPE -> if (v.isNotEmpty()) websites += ContactField(v, type ?: Website.TYPE_OTHER, label)
            StructuredName.CONTENT_ITEM_TYPE -> if (name.isBlank) {
                name = NameField(
                    given = row.getAsString(StructuredName.GIVEN_NAME).orEmpty(),
                    family = row.getAsString(StructuredName.FAMILY_NAME).orEmpty(),
                ).let { if (it.isBlank) splitName(v) else it }
            }
        }
    }

    val postal = extras.getCharSequence(Insert.POSTAL)?.toString()?.trim().orEmpty()
    return ContactDraft(
        name = name,
        organization = OrganizationField(
            company = extras.getCharSequence(Insert.COMPANY)?.toString().orEmpty(),
            title = extras.getCharSequence(Insert.JOB_TITLE)?.toString().orEmpty(),
        ),
        phones = phones.distinctBy { android.telephony.PhoneNumberUtils.normalizeNumber(it.value) },
        emails = emails.distinctBy { it.value.lowercase() },
        addresses = if (postal.isEmpty()) emptyList() else listOf(
            PostalAddress(street = postal, type = extras.getInt(Insert.POSTAL_TYPE, StructuredPostal.TYPE_HOME)),
        ),
        websites = websites,
        note = TextField(extras.getCharSequence(Insert.NOTES)?.toString().orEmpty()),
    )
}

/** "Ana María López" → given "Ana", family "María López"; the form shows both to fix it. */
private fun splitName(full: String?): NameField {
    val parts = full?.trim()?.split(Regex("\\s+"), limit = 2).orEmpty().filter { it.isNotEmpty() }
    return NameField(given = parts.getOrElse(0) { "" }, family = parts.getOrElse(1) { "" })
}

/** "Add to a contact": search a contact, or create a new one. */
@Composable
private fun PickContactScreen(
    repo: ContactsRepository,
    onNew: () -> Unit,
    onPick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val contacts by repo.contacts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, query) {
        val q = query.trim().foldAccents()
        val digits = query.filter { it.isDigit() }
        contacts.orEmpty().filter { c ->
            q.isEmpty() || c.name.foldAccents().contains(q, ignoreCase = true) ||
                (digits.length >= 3 && c.numbers.any { n -> n.filter { it.isDigit() }.contains(digits) })
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_to_contact)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_contacts)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                PickRow(
                    leading = { Icon(Icons.Rounded.PersonAdd, null, tint = MaterialTheme.colorScheme.primary) },
                    title = stringResource(R.string.create_new_contact),
                    onClick = onNew,
                )
            }
            items(shown, key = { it.id }) { c -> ContactRow(c) { onPick(c.id) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ContactRow(c: ContactSummary, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    PickRow(
        leading = { ContactAvatar(c.name, c.photoUri, c.id, size = 40.dp) },
        title = c.name,
        subtitle = c.numbers.firstOrNull()?.let { formatPhone(context, it) },
        onClick = onClick,
    )
}

@Composable
private fun PickRow(leading: @Composable () -> Unit, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = leading,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
    )
}
