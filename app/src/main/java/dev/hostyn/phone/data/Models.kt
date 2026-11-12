package dev.hostyn.phone.data

data class ContactSummary(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val numbers: List<String> = emptyList(),
    val lookupKey: String? = null,
    /** Text the list is sorted and grouped by (follows the sort order setting). */
    val sortKey: String = name,
)

/*
 * Contact fields follow Android's contact data standard (ContactsContract.CommonDataKinds),
 * which is what sync adapters (Google, CardDAV…) and vCard import/export map to.
 *
 * Every field remembers the ids of the data rows it came from (`ids`), so saving updates those
 * rows in place instead of deleting and recreating them: columns this app doesn't edit and the
 * sync adapters' own columns are preserved. A field can come from several rows when a contact is
 * made of linked raw contacts that repeat the same value.
 */

/** A value with a type (ContactsContract constants): phone, email, website, SIP, date, relation. */
data class ContactField(
    val value: String,
    val type: Int,
    val label: String? = null,
    val ids: List<Long> = emptyList(),
) {
    val isBlank get() = value.isBlank()
}

data class NameField(
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val phoneticGiven: String = "",
    val phoneticMiddle: String = "",
    val phoneticFamily: String = "",
    val ids: List<Long> = emptyList(),
) {
    val isBlank get() = listOf(prefix, given, middle, family, suffix, phoneticGiven, phoneticMiddle, phoneticFamily).all { it.isBlank() }
    /** Has anything besides first and last name. */
    val hasExtras get() = listOf(prefix, middle, suffix, phoneticGiven, phoneticMiddle, phoneticFamily).any { it.isNotBlank() }
}

data class OrganizationField(
    val company: String = "",
    val title: String = "",
    val department: String = "",
    val ids: List<Long> = emptyList(),
) {
    val isBlank get() = company.isBlank() && title.isBlank() && department.isBlank()
}

data class PostalAddress(
    val street: String = "",
    val poBox: String = "",
    val neighborhood: String = "",
    val city: String = "",
    val region: String = "",
    val postcode: String = "",
    val country: String = "",
    val type: Int,
    val label: String? = null,
    /** Address as a single text, when the source only provides that. */
    val formatted: String = "",
    val ids: List<Long> = emptyList(),
) {
    val isBlank get() = listOf(street, poBox, neighborhood, city, region, postcode, country).all { it.isBlank() } && formatted.isBlank()

    /** Multi-line address for display. */
    fun display(): String {
        val structured = listOf(
            street,
            poBox,
            neighborhood,
            listOf(postcode, city).filter { it.isNotBlank() }.joinToString(" "),
            region,
            country,
        ).map { it.trim() }.filter { it.isNotEmpty() }
        return if (structured.isNotEmpty()) structured.joinToString("\n") else formatted.trim()
    }
}

data class ImField(
    val handle: String,
    val protocol: Int,
    val customProtocol: String? = null,
    val ids: List<Long> = emptyList(),
) {
    val isBlank get() = handle.isBlank()
}

/** A single free-text field: nickname or note. */
data class TextField(val value: String = "", val ids: List<Long> = emptyList()) {
    val isBlank get() = value.isBlank()
}

/** Everything that can be edited in a contact. */
data class ContactDraft(
    val name: NameField = NameField(),
    val nickname: TextField = TextField(),
    val organization: OrganizationField = OrganizationField(),
    val phones: List<ContactField> = emptyList(),
    val emails: List<ContactField> = emptyList(),
    val addresses: List<PostalAddress> = emptyList(),
    val events: List<ContactField> = emptyList(),
    val relations: List<ContactField> = emptyList(),
    val websites: List<ContactField> = emptyList(),
    val ims: List<ImField> = emptyList(),
    val sips: List<ContactField> = emptyList(),
    val note: TextField = TextField(),
    /** Ids of the groups (labels) the contact belongs to. */
    val groups: Set<Long> = emptySet(),
) {
    val isEmpty get() = name.isBlank && nickname.isBlank && organization.isBlank &&
        phones.all { it.isBlank } && emails.all { it.isBlank } && addresses.all { it.isBlank } &&
        events.all { it.isBlank } && relations.all { it.isBlank } && websites.all { it.isBlank } &&
        ims.all { it.isBlank } && sips.all { it.isBlank } && note.isBlank
}

/** Account a raw contact is stored in. Null name and type = only on the device. */
data class ContactAccount(val name: String?, val type: String?) {
    val isLocal get() = name == null && type == null

    /** Identifies the account in the settings. */
    val key: String get() = if (isLocal) "local" else "$type/$name"

    companion object {
        val Local = ContactAccount(null, null)
    }
}

/** A label (contact group) of an account. */
data class ContactGroup(val id: Long, val title: String)

data class ContactDetail(
    val id: Long,
    val lookupKey: String?,
    val nameRawContactId: Long,
    val rawContactIds: List<Long>,
    val displayName: String,
    val photoUri: String?,
    val starred: Boolean,
    val ringtone: String?,
    val sendToVoicemail: Boolean,
    val account: ContactAccount,
    val data: ContactDraft,
    /** Titles of the groups in [ContactDraft.groups]. */
    val groupTitles: List<String> = emptyList(),
) {
    val phones get() = data.phones
    val emails get() = data.emails
}

/** Photo change requested in the editor. */
sealed interface PhotoChange {
    data object None : PhotoChange
    data object Remove : PhotoChange
    class Set(val jpeg: ByteArray) : PhotoChange
}
