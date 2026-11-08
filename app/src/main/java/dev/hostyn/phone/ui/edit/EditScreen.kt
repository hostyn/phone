package dev.hostyn.phone.ui.edit

import android.graphics.BitmapFactory
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.hostyn.phone.R
import dev.hostyn.phone.data.ContactAccount
import dev.hostyn.phone.data.ContactDetail
import dev.hostyn.phone.data.ContactDraft
import dev.hostyn.phone.data.ContactField
import dev.hostyn.phone.data.ContactGroup
import dev.hostyn.phone.data.ContactsRepository
import dev.hostyn.phone.data.ImField
import dev.hostyn.phone.data.PhotoChange
import dev.hostyn.phone.data.PostalAddress
import dev.hostyn.phone.ui.components.EventDate
import dev.hostyn.phone.ui.components.FieldKind
import dev.hostyn.phone.ui.components.ImProtocols
import dev.hostyn.phone.ui.components.accountTitle
import dev.hostyn.phone.ui.components.formatEventDate
import dev.hostyn.phone.ui.components.imProtocolLabel
import dev.hostyn.phone.ui.components.loadContactPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Optional parts of the form, shown when the contact has them or the user adds them. */
enum class Section { Nickname, Organization, Address, Event, Relation, Website, Im, Sip, Note, Labels }

class EditViewModel(
    private val repo: ContactsRepository,
    private val contactId: Long?,
    initialPhone: String?,
) : ViewModel() {
    var loading by mutableStateOf(contactId != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var draft by mutableStateOf(ContactDraft())
    var photo by mutableStateOf<PhotoChange>(PhotoChange.None)
    var existingPhoto by mutableStateOf<String?>(null)
        private set
    var showNameExtras by mutableStateOf(false)

    var accounts by mutableStateOf(listOf(ContactAccount.Local))
        private set
    var account by mutableStateOf(ContactAccount.Local)
        private set
    val groups = mutableStateListOf<ContactGroup>()
    val added = mutableStateListOf<Section>()

    private var original: ContactDetail? = null
    private var initial = ContactDraft()

    init {
        viewModelScope.launch {
            if (contactId == null) {
                draft = ContactDraft(phones = listOf(ContactField(initialPhone.orEmpty(), Phone.TYPE_MOBILE)))
                accounts = withContext(Dispatchers.IO) { repo.writableAccounts() }
                // Default to the first synced account, as other contacts apps do.
                account = accounts.firstOrNull { !it.isLocal } ?: ContactAccount.Local
            } else {
                repo.getContact(contactId)?.let { c ->
                    original = c
                    account = c.account
                    existingPhoto = c.photoUri
                    // Addresses that only have the single-text form are edited as the street.
                    draft = c.data.copy(
                        addresses = c.data.addresses.map {
                            if (it.street.isBlank() && it.city.isBlank() && it.formatted.isNotBlank()) it.copy(street = it.formatted) else it
                        },
                        events = c.data.events,
                    )
                    showNameExtras = c.data.name.hasExtras
                }
                if (draft.phones.isEmpty()) draft = draft.copy(phones = listOf(ContactField("", Phone.TYPE_MOBILE)))
            }
            initial = draft
            loadGroups()
            loading = false
        }
    }

    private suspend fun loadGroups() {
        groups.clear()
        groups += repo.groups(account)
    }

    fun selectAccount(a: ContactAccount) {
        if (a == account) return
        account = a
        draft = draft.copy(groups = emptySet())
        viewModelScope.launch { loadGroups() }
    }

    fun createGroup(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.createGroup(account, title)?.let { g ->
                groups += g
                groups.sortBy { it.title.lowercase() }
                draft = draft.copy(groups = draft.groups + g.id)
            }
        }
    }

    fun visible(s: Section): Boolean = s in added || when (s) {
        Section.Nickname -> !initial.nickname.isBlank
        Section.Organization -> !initial.organization.isBlank
        Section.Address -> initial.addresses.isNotEmpty()
        Section.Event -> initial.events.isNotEmpty()
        Section.Relation -> initial.relations.isNotEmpty()
        Section.Website -> initial.websites.isNotEmpty()
        Section.Im -> initial.ims.isNotEmpty()
        Section.Sip -> initial.sips.isNotEmpty()
        Section.Note -> !initial.note.isBlank
        Section.Labels -> initial.groups.isNotEmpty() || groups.isNotEmpty()
    }

    /** Shows a section, adding an empty entry to list sections. */
    fun add(s: Section) {
        added += s
        draft = when (s) {
            Section.Address -> draft.copy(addresses = draft.addresses + PostalAddress(type = StructuredPostal.TYPE_HOME))
            Section.Event -> draft.copy(events = draft.events + ContactField("", nextEventType(draft.events)))
            Section.Relation -> draft.copy(relations = draft.relations + ContactField("", Relation.TYPE_SPOUSE))
            Section.Website -> draft.copy(websites = draft.websites + ContactField("", Website.TYPE_HOMEPAGE))
            Section.Im -> draft.copy(ims = draft.ims + ImField("", Im.PROTOCOL_CUSTOM))
            Section.Sip -> draft.copy(sips = draft.sips + ContactField("", SipAddress.TYPE_HOME))
            else -> draft
        }
    }

    val isNew get() = contactId == null
    val canSave get() = !loading && !saving && !draft.isEmpty
    val isDirty get() = !loading && (draft != initial || photo != PhotoChange.None)

    fun save(onDone: (Long?) -> Unit, onError: () -> Unit) {
        saving = true
        viewModelScope.launch {
            try {
                val o = original
                if (o == null) onDone(repo.create(draft, account, photo))
                else { repo.update(o, draft, photo); onDone(o.id) }
            } catch (e: Exception) {
                saving = false
                onError()
            }
        }
    }
}

/** Birthday for the first date, then anniversary, then other. */
private fun nextEventType(events: List<ContactField>) = when {
    events.none { it.type == Event.TYPE_BIRTHDAY } -> Event.TYPE_BIRTHDAY
    events.none { it.type == Event.TYPE_ANNIVERSARY } -> Event.TYPE_ANNIVERSARY
    else -> Event.TYPE_OTHER
}

private fun <T> List<T>.replace(i: Int, item: T) = toMutableList().also { it[i] = item }
private fun <T> List<T>.without(i: Int) = toMutableList().also { it.removeAt(i) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    repo: ContactsRepository,
    contactId: Long?,
    initialPhone: String? = null,
    onClose: () -> Unit,
    onCreated: (Long) -> Unit,
) {
    val vm = viewModel(key = "edit_${contactId}_$initialPhone") { EditViewModel(repo, contactId, initialPhone) }
    val context = LocalContext.current
    var confirmDiscard by remember { mutableStateOf(false) }

    val requestClose: () -> Unit = { if (vm.isDirty) { confirmDiscard = true } else { onClose() } }
    BackHandler(onBack = requestClose)

    val saveError = stringResource(R.string.error_save_contact)
    fun save() = vm.save(
        onDone = { id -> if (vm.isNew && id != null) onCreated(id) else onClose() },
        onError = { Toast.makeText(context, saveError, Toast.LENGTH_SHORT).show() },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) stringResource(R.string.new_contact) else stringResource(R.string.edit_contact)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = requestClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
                },
                actions = {
                    Button(
                        onClick = ::save,
                        enabled = vm.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        if (vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val d = vm.draft
        fun update(block: ContactDraft.() -> ContactDraft) { vm.draft = vm.draft.block() }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            PhotoEditor(vm)

            if (vm.isNew && vm.accounts.size > 1) AccountChooser(vm)

            // Name
            FormSection(Icons.Rounded.Person) {
                val n = d.name
                fun name(block: (dev.hostyn.phone.data.NameField) -> dev.hostyn.phone.data.NameField) = update { copy(name = block(name)) }
                if (vm.showNameExtras) TextInput(n.prefix, { v -> name { it.copy(prefix = v) } }, stringResource(R.string.name_prefix), words = true)
                TextInput(n.given, { v -> name { it.copy(given = v) } }, stringResource(R.string.first_name), words = true)
                if (vm.showNameExtras) TextInput(n.middle, { v -> name { it.copy(middle = v) } }, stringResource(R.string.middle_name), words = true)
                TextInput(n.family, { v -> name { it.copy(family = v) } }, stringResource(R.string.last_name), words = true)
                if (vm.showNameExtras) {
                    TextInput(n.suffix, { v -> name { it.copy(suffix = v) } }, stringResource(R.string.name_suffix), words = true)
                    TextInput(n.phoneticGiven, { v -> name { it.copy(phoneticGiven = v) } }, stringResource(R.string.phonetic_first_name), words = true)
                    TextInput(n.phoneticMiddle, { v -> name { it.copy(phoneticMiddle = v) } }, stringResource(R.string.phonetic_middle_name), words = true)
                    TextInput(n.phoneticFamily, { v -> name { it.copy(phoneticFamily = v) } }, stringResource(R.string.phonetic_last_name), words = true)
                }
                TextButton(onClick = { vm.showNameExtras = !vm.showNameExtras }) {
                    Icon(if (vm.showNameExtras) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (vm.showNameExtras) R.string.fewer_name_fields else R.string.more_name_fields))
                }
            }

            if (vm.visible(Section.Nickname)) FormSection(Icons.Rounded.Face) {
                TextInput(d.nickname.value, { v -> update { copy(nickname = nickname.copy(value = v)) } }, stringResource(R.string.nickname), words = true)
            }

            if (vm.visible(Section.Organization)) FormSection(Icons.Rounded.Business) {
                val o = d.organization
                TextInput(o.company, { v -> update { copy(organization = organization.copy(company = v)) } }, stringResource(R.string.company), words = true)
                TextInput(o.title, { v -> update { copy(organization = organization.copy(title = v)) } }, stringResource(R.string.job_title), words = true)
                TextInput(o.department, { v -> update { copy(organization = organization.copy(department = v)) } }, stringResource(R.string.department), words = true)
            }

            FormSection(Icons.Rounded.Phone) {
                d.phones.forEachIndexed { i, p ->
                    TypedFieldEditor(
                        field = p, kind = FieldKind.Phones, label = stringResource(R.string.phone),
                        keyboardType = KeyboardType.Phone,
                        onChange = { f -> update { copy(phones = phones.replace(i, f)) } },
                        onRemove = { update { copy(phones = phones.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_phone)) { update { copy(phones = phones + ContactField("", Phone.TYPE_MOBILE)) } }
            }

            FormSection(Icons.Rounded.Email) {
                d.emails.forEachIndexed { i, e ->
                    TypedFieldEditor(
                        field = e, kind = FieldKind.Emails, label = stringResource(R.string.email),
                        keyboardType = KeyboardType.Email,
                        onChange = { f -> update { copy(emails = emails.replace(i, f)) } },
                        onRemove = { update { copy(emails = emails.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_email)) { update { copy(emails = emails + ContactField("", Email.TYPE_HOME)) } }
            }

            if (vm.visible(Section.Address)) FormSection(Icons.Rounded.LocationOn) {
                d.addresses.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    AddressEditor(
                        address = a,
                        onChange = { f -> update { copy(addresses = addresses.replace(i, f)) } },
                        onRemove = { update { copy(addresses = addresses.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_address)) {
                    update { copy(addresses = addresses + PostalAddress(type = StructuredPostal.TYPE_HOME)) }
                }
            }

            if (vm.visible(Section.Event)) FormSection(Icons.Rounded.Event) {
                d.events.forEachIndexed { i, e ->
                    EventEditor(
                        field = e,
                        onChange = { f -> update { copy(events = events.replace(i, f)) } },
                        onRemove = { update { copy(events = events.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_date)) { update { copy(events = events + ContactField("", nextEventType(events))) } }
            }

            if (vm.visible(Section.Relation)) FormSection(Icons.Rounded.People) {
                d.relations.forEachIndexed { i, r ->
                    TypedFieldEditor(
                        field = r, kind = FieldKind.Relations, label = stringResource(R.string.related_person),
                        keyboardType = KeyboardType.Text, words = true,
                        onChange = { f -> update { copy(relations = relations.replace(i, f)) } },
                        onRemove = { update { copy(relations = relations.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_relation)) { update { copy(relations = relations + ContactField("", Relation.TYPE_SPOUSE)) } }
            }

            if (vm.visible(Section.Website)) FormSection(Icons.Rounded.Language) {
                d.websites.forEachIndexed { i, w ->
                    TypedFieldEditor(
                        field = w, kind = FieldKind.Websites, label = stringResource(R.string.website),
                        keyboardType = KeyboardType.Uri,
                        onChange = { f -> update { copy(websites = websites.replace(i, f)) } },
                        onRemove = { update { copy(websites = websites.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_website)) { update { copy(websites = websites + ContactField("", Website.TYPE_HOMEPAGE)) } }
            }

            if (vm.visible(Section.Im)) FormSection(Icons.Rounded.Chat) {
                d.ims.forEachIndexed { i, im ->
                    ImEditor(
                        field = im,
                        onChange = { f -> update { copy(ims = ims.replace(i, f)) } },
                        onRemove = { update { copy(ims = ims.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_im)) { update { copy(ims = ims + ImField("", Im.PROTOCOL_CUSTOM)) } }
            }

            if (vm.visible(Section.Sip)) FormSection(Icons.Rounded.Dialpad) {
                d.sips.forEachIndexed { i, s ->
                    TypedFieldEditor(
                        field = s, kind = FieldKind.Sips, label = stringResource(R.string.sip_address),
                        keyboardType = KeyboardType.Email,
                        onChange = { f -> update { copy(sips = sips.replace(i, f)) } },
                        onRemove = { update { copy(sips = sips.without(i)) } },
                    )
                }
                AddButton(stringResource(R.string.add_sip)) { update { copy(sips = sips + ContactField("", SipAddress.TYPE_HOME)) } }
            }

            if (vm.visible(Section.Note)) FormSection(Icons.Rounded.Notes) {
                TextInput(
                    d.note.value, { v -> update { copy(note = note.copy(value = v)) } }, stringResource(R.string.notes),
                    singleLine = false, sentences = true,
                )
            }

            if (vm.visible(Section.Labels)) FormSection(Icons.Rounded.Label) { LabelsEditor(vm) }

            val hidden = Section.entries.filterNot { vm.visible(it) }
            if (hidden.isNotEmpty()) AddFieldsButton(hidden, vm::add)
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
}

// --- Photo and account ---

@Composable
private fun PhotoEditor(vm: EditViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { loadContactPhoto(context, uri) }?.let { vm.photo = PhotoChange.Set(it) }
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val photo = vm.photo
    val preview = remember(photo) {
        (photo as? PhotoChange.Set)?.jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val hasPhoto = preview != null || (photo == PhotoChange.None && vm.existingPhoto != null)

    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), Alignment.Center) {
        Box {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .clickable { if (hasPhoto) menu = true else pick() },
            ) {
                when {
                    preview != null -> Image(preview, stringResource(R.string.choose_photo), contentScale = ContentScale.Crop)
                    hasPhoto -> AsyncImage(vm.existingPhoto, stringResource(R.string.choose_photo), contentScale = ContentScale.Crop)
                    else -> Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(56.dp))
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).size(36.dp),
                onClick = { if (hasPhoto) menu = true else pick() },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PhotoCamera, stringResource(R.string.choose_photo), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.choose_photo)) }, onClick = { menu = false; pick() })
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_photo)) },
                    onClick = { menu = false; vm.photo = if (vm.existingPhoto != null) PhotoChange.Remove else PhotoChange.None },
                )
            }
        }
    }
}

@Composable
private fun AccountChooser(vm: EditViewModel) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val (title, _) = accountTitle(context, vm.account)
    Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Surface(
            onClick = { menu = true },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (vm.account.isLocal) Icons.Rounded.Smartphone else Icons.Rounded.CloudQueue, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.save_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ExpandMore, null)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            vm.accounts.forEach { a ->
                val (t, s) = accountTitle(context, a)
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(t)
                            if (s != null) Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = { Icon(if (a.isLocal) Icons.Rounded.Smartphone else Icons.Rounded.CloudQueue, null) },
                    trailingIcon = { if (a == vm.account) Icon(Icons.Rounded.Check, null) },
                    onClick = { menu = false; vm.selectAccount(a) },
                )
            }
        }
    }
}

// --- Building blocks ---

@Composable
private fun FormSection(icon: ImageVector, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, end = 16.dp, start = 4.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    words: Boolean = false,
    sentences: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = when {
                words -> KeyboardCapitalization.Words
                sentences -> KeyboardCapitalization.Sentences
                else -> KeyboardCapitalization.None
            },
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier,
    )
}

@Composable
private fun RemoveButton(onRemove: () -> Unit) {
    IconButton(onClick = onRemove) {
        Icon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Button with the current type that opens the list of types. The last entry, “Custom label”,
 * asks for a label of the user's own.
 */
@Composable
private fun TypeMenu(
    current: String,
    options: List<Pair<Int, String>>,
    customType: Int,
    customLabel: String?,
    onSelect: (type: Int, label: String?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var askCustom by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { menu = true }, shape = RoundedCornerShape(12.dp)) {
            Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { (type, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { menu = false; onSelect(type, null) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.custom_label)) }, onClick = { menu = false; askCustom = true })
        }
    }
    if (askCustom) {
        TextDialog(
            title = stringResource(R.string.custom_label),
            initial = customLabel.orEmpty(),
            onDismiss = { askCustom = false },
            onConfirm = { askCustom = false; onSelect(customType, it.trim()) },
        )
    }
}

@Composable
private fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TypedFieldEditor(
    field: ContactField,
    kind: FieldKind,
    label: String,
    keyboardType: KeyboardType,
    words: Boolean = false,
    onChange: (ContactField) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(field.value, { onChange(field.copy(value = it)) }, label, Modifier.weight(1f), keyboardType, words = words)
            RemoveButton(onRemove)
        }
        TypeMenu(
            current = kind.label(context, field.type, field.label),
            options = kind.types.map { it to kind.label(context, it, null) },
            customType = kind.custom,
            customLabel = field.label,
            onSelect = { type, l -> onChange(field.copy(type = type, label = l)) },
        )
    }
}

@Composable
private fun AddressEditor(address: PostalAddress, onChange: (PostalAddress) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val kind = FieldKind.Postal
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(address.street, { onChange(address.copy(street = it)) }, stringResource(R.string.street), Modifier.weight(1f), words = true, singleLine = false)
            RemoveButton(onRemove)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 48.dp)) {
            TextInput(address.postcode, { onChange(address.copy(postcode = it)) }, stringResource(R.string.postcode), Modifier.weight(1f))
            TextInput(address.city, { onChange(address.copy(city = it)) }, stringResource(R.string.city), Modifier.weight(1.6f), words = true)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 48.dp)) {
            TextInput(address.region, { onChange(address.copy(region = it)) }, stringResource(R.string.region), words = true)
            TextInput(address.country, { onChange(address.copy(country = it)) }, stringResource(R.string.country), words = true)
            TextInput(address.neighborhood, { onChange(address.copy(neighborhood = it)) }, stringResource(R.string.neighborhood), words = true)
            TextInput(address.poBox, { onChange(address.copy(poBox = it)) }, stringResource(R.string.po_box))
        }
        TypeMenu(
            current = kind.label(context, address.type, address.label),
            options = kind.types.map { it to kind.label(context, it, null) },
            customType = kind.custom,
            customLabel = address.label,
            onSelect = { type, l -> onChange(address.copy(type = type, label = l)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(field: ContactField, onChange: (ContactField) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val kind = FieldKind.Events
    var picking by remember { mutableStateOf(field.value.isBlank()) }
    val parsed = EventDate.parse(field.value)

    // The text field only opens the date picker.
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) picking = true }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = if (field.value.isBlank()) "" else formatEventDate(field.value),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.date)) },
                singleLine = true,
                interactionSource = interaction,
                modifier = Modifier.weight(1f),
            )
            RemoveButton(onRemove)
        }
        TypeMenu(
            current = kind.label(context, field.type, field.label),
            options = kind.types.map { it to kind.label(context, it, null) },
            customType = kind.custom,
            customLabel = field.label,
            onSelect = { type, l -> onChange(field.copy(type = type, label = l)) },
        )
    }

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = parsed?.utcMillis())
        var noYear by remember { mutableStateOf(parsed != null && parsed.year == null) }
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        picking = false
                        state.selectedDateMillis?.let { onChange(field.copy(value = EventDate.fromUtcMillis(it, !noYear).toStorage())) }
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            Column {
                DatePicker(state = state, showModeToggle = true, modifier = Modifier.weight(1f, fill = false))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { noYear = !noYear }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = noYear, onCheckedChange = { noYear = it })
                    Text(stringResource(R.string.no_year))
                }
            }
        }
    }
}

@Composable
private fun ImEditor(field: ImField, onChange: (ImField) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextInput(field.handle, { onChange(field.copy(handle = it)) }, stringResource(R.string.instant_messaging), Modifier.weight(1f), KeyboardType.Email)
            RemoveButton(onRemove)
        }
        @Suppress("DEPRECATION")
        TypeMenu(
            current = imProtocolLabel(context, field.protocol, field.customProtocol),
            options = ImProtocols.map { it to imProtocolLabel(context, it, null) },
            customType = Im.PROTOCOL_CUSTOM,
            customLabel = field.customProtocol,
            onSelect = { p, l -> onChange(field.copy(protocol = p, customProtocol = l)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsEditor(vm: EditViewModel) {
    var creating by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.labels),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        vm.groups.forEach { g ->
            val selected = g.id in vm.draft.groups
            FilterChip(
                selected = selected,
                onClick = {
                    vm.draft = vm.draft.copy(groups = if (selected) vm.draft.groups - g.id else vm.draft.groups + g.id)
                },
                label = { Text(g.title) },
                leadingIcon = if (selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
            )
        }
        FilterChip(
            selected = false,
            onClick = { creating = true },
            label = { Text(stringResource(R.string.new_label)) },
            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
    }
    if (creating) {
        TextDialog(
            title = stringResource(R.string.new_label),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { creating = false; vm.createGroup(it) },
        )
    }
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun AddFieldsButton(hidden: List<Section>, onAdd: (Section) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), Alignment.Center) {
        Box {
            Button(onClick = { menu = true }) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_field))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                hidden.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(stringResource(s.title)) },
                        leadingIcon = { Icon(s.icon, null) },
                        onClick = { menu = false; onAdd(s) },
                    )
                }
            }
        }
    }
}

private val Section.title get() = when (this) {
    Section.Nickname -> R.string.nickname
    Section.Organization -> R.string.company
    Section.Address -> R.string.address
    Section.Event -> R.string.significant_date
    Section.Relation -> R.string.related_person
    Section.Website -> R.string.website
    Section.Im -> R.string.instant_messaging
    Section.Sip -> R.string.sip_address
    Section.Note -> R.string.notes
    Section.Labels -> R.string.labels
}

private val Section.icon get() = when (this) {
    Section.Nickname -> Icons.Rounded.Face
    Section.Organization -> Icons.Rounded.Business
    Section.Address -> Icons.Rounded.LocationOn
    Section.Event -> Icons.Rounded.Event
    Section.Relation -> Icons.Rounded.People
    Section.Website -> Icons.Rounded.Language
    Section.Im -> Icons.Rounded.Chat
    Section.Sip -> Icons.Rounded.Dialpad
    Section.Note -> Icons.Rounded.Notes
    Section.Labels -> Icons.Rounded.Label
}
