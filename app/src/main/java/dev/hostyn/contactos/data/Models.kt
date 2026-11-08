package dev.hostyn.contactos.data

data class ContactSummary(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val numbers: List<String> = emptyList(),
)

/** Un teléfono o correo con su tipo (constantes de ContactsContract). */
data class ContactField(
    val value: String,
    val type: Int,
    val label: String? = null,
)

data class ContactDetail(
    val id: Long,
    val nameRawContactId: Long,
    val rawContactIds: List<Long>,
    val displayName: String,
    val givenName: String,
    val familyName: String,
    val photoUri: String?,
    val starred: Boolean,
    val phones: List<ContactField>,
    val emails: List<ContactField>,
)

data class ContactDraft(
    val givenName: String = "",
    val familyName: String = "",
    val phones: List<ContactField> = emptyList(),
    val emails: List<ContactField> = emptyList(),
)
