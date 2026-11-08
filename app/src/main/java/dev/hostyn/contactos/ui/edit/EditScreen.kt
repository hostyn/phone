package dev.hostyn.contactos.ui.edit

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import dev.hostyn.contactos.data.ContactDetail
import dev.hostyn.contactos.data.ContactDraft
import dev.hostyn.contactos.data.ContactField
import dev.hostyn.contactos.data.ContactsRepository
import dev.hostyn.contactos.ui.components.EmailTypes
import dev.hostyn.contactos.ui.components.PhoneTypes
import dev.hostyn.contactos.ui.components.emailLabel
import dev.hostyn.contactos.ui.components.phoneLabel
import kotlinx.coroutines.launch

class EditViewModel(
    private val repo: ContactsRepository,
    private val contactId: Long?,
    initialPhone: String?,
) : ViewModel() {
    var loading by mutableStateOf(contactId != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var given by mutableStateOf("")
    var family by mutableStateOf("")
    val phones = mutableStateListOf<ContactField>()
    val emails = mutableStateListOf<ContactField>()

    private var original: ContactDetail? = null
    private var initial = ContactDraft()

    init {
        if (contactId == null) {
            phones += ContactField(initialPhone.orEmpty(), Phone.TYPE_MOBILE)
            initial = draft()
        } else {
            viewModelScope.launch {
                repo.getContact(contactId)?.let { c ->
                    original = c
                    given = c.givenName
                    family = c.familyName
                    phones += c.phones
                    emails += c.emails
                }
                if (phones.isEmpty()) phones += ContactField("", Phone.TYPE_MOBILE)
                initial = draft()
                loading = false
            }
        }
    }

    fun draft() = ContactDraft(given, family, phones.toList(), emails.toList())

    val isNew get() = contactId == null
    val canSave get() = !loading && !saving &&
        (given.isNotBlank() || family.isNotBlank() || phones.any { it.value.isNotBlank() } || emails.any { it.value.isNotBlank() })

    val isDirty get() = !loading && draft().normalized() != initial.normalized()

    private fun ContactDraft.normalized() = copy(
        givenName = givenName.trim(),
        familyName = familyName.trim(),
        phones = phones.filter { it.value.isNotBlank() },
        emails = emails.filter { it.value.isNotBlank() },
    )

    fun save(onDone: (Long?) -> Unit, onError: () -> Unit) {
        saving = true
        viewModelScope.launch {
            try {
                val o = original
                if (o == null) onDone(repo.create(draft()))
                else { repo.update(o, draft()); onDone(o.id) }
            } catch (e: Exception) {
                saving = false
                onError()
            }
        }
    }
}

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

    fun save() = vm.save(
        onDone = { id -> if (vm.isNew && id != null) onCreated(id) else onClose() },
        onError = { Toast.makeText(context, "No se pudo guardar el contacto", Toast.LENGTH_SHORT).show() },
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Nuevo contacto" else "Editar contacto") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = requestClose) { Icon(Icons.Rounded.Close, "Cerrar") }
                },
                actions = {
                    Button(
                        onClick = ::save,
                        enabled = vm.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Guardar") }
                },
            )
        },
    ) { padding ->
        if (vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
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
            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(104.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Person, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                }
            }

            FormSection(Icons.Rounded.Person) {
                OutlinedTextField(
                    value = vm.given,
                    onValueChange = { vm.given = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.family,
                    onValueChange = { vm.family = it },
                    label = { Text("Apellidos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FormSection(Icons.Rounded.Phone) {
                vm.phones.forEachIndexed { i, p ->
                    FieldEditor(
                        field = p,
                        label = "Teléfono",
                        typeLabel = p.phoneLabel(),
                        types = PhoneTypes,
                        keyboardType = KeyboardType.Phone,
                        onChange = { vm.phones[i] = it },
                        onRemove = { vm.phones.removeAt(i) },
                    )
                }
                AddButton("Añadir teléfono") { vm.phones += ContactField("", Phone.TYPE_MOBILE) }
            }

            FormSection(Icons.Rounded.Email) {
                vm.emails.forEachIndexed { i, e ->
                    FieldEditor(
                        field = e,
                        label = "Correo",
                        typeLabel = e.emailLabel(),
                        types = EmailTypes,
                        keyboardType = KeyboardType.Email,
                        onChange = { vm.emails[i] = it },
                        onRemove = { vm.emails.removeAt(i) },
                    )
                }
                AddButton("Añadir correo") { vm.emails += ContactField("", Email.TYPE_HOME) }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Los cambios que has hecho no se guardarán.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Descartar") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") } },
        )
    }
}

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
private fun FieldEditor(
    field: ContactField,
    label: String,
    typeLabel: String,
    types: List<Pair<Int, String>>,
    keyboardType: KeyboardType,
    onChange: (ContactField) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = field.value,
                onValueChange = { onChange(field.copy(value = it)) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.RemoveCircleOutline, "Quitar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            TextButton(onClick = { menu = true }, shape = RoundedCornerShape(12.dp)) {
                Text(typeLabel)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                types.forEach { (type, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { menu = false; onChange(field.copy(type = type, label = null)) },
                    )
                }
            }
        }
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
