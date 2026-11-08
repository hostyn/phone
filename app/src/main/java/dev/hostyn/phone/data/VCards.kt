package dev.hostyn.phone.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import androidx.core.content.FileProvider
import dev.hostyn.phone.R
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.property.Address
import ezvcard.property.Telephone
import ezvcard.property.Impp
import ezvcard.property.Organization
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import ezvcard.property.DateOrTimeProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor

/*
 * vCard 3.0 import and export with ez-vcard. Standard properties are used where vCard has them;
 * the rest uses the extensions Android and other phones understand (X-ANDROID-CUSTOM,
 * X-PHONETIC-*, X-SIP), so every field survives a round trip. Custom labels are written as
 * `X-<label>` types. (The provider's own composer, Contacts.CONTENT_VCARD_URI, drops custom
 * labels, extra dates and labels, so it isn't used.)
 */

private const val VCARD_MIME = "text/x-vcard"

/** Shares one contact as a .vcf file. */
suspend fun shareContact(context: Context, repo: ContactsRepository, contact: ContactDetail) {
    val file = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val name = contact.displayName.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_').ifEmpty { "contact" }
            File(dir, "$name.vcf").also { f ->
                f.outputStream().use { writeVCards(listOf(contact.toVCard(repo.photoBytes(contact.id))), it) }
            }
        } catch (_: Exception) {
            null
        }
    } ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(VCARD_MIME)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
}

/** Writes every contact to [target] as one vCard file. Returns how many were written, or null on failure. */
suspend fun exportContacts(context: Context, repo: ContactsRepository, target: Uri): Int? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(target, "wt")?.use { writeAllContacts(repo, it) }
    } catch (_: Exception) {
        null
    }
}

/** Adds the contacts in the vCard file at [source] to [account]. Returns how many, or null on failure. */
suspend fun importContacts(
    context: Context,
    repo: ContactsRepository,
    source: Uri,
    account: ContactAccount,
): Int? = withContext(Dispatchers.IO) {
    try {
        val cards = context.contentResolver.openInputStream(source)?.use { Ezvcard.parse(it).all() } ?: return@withContext null
        val groups = repo.groups(account).associateBy { it.title.lowercase() }.toMutableMap()
        var count = 0
        for (card in cards) {
            val (draft, photo, categories) = card.toDraft()
            if (draft.isEmpty) continue
            val groupIds = categories.mapNotNull { title ->
                groups[title.lowercase()]?.id ?: repo.createGroup(account, title)?.also { groups[title.lowercase()] = it }?.id
            }.toSet()
            if (repo.create(draft.copy(groups = groupIds), account, photo) != null) count++
        }
        count
    } catch (_: Exception) {
        null
    }
}

private data class Imported(val draft: ContactDraft, val photo: PhotoChange, val categories: List<String>)

@Suppress("DEPRECATION")
private fun VCard.toDraft(): Imported {
    val ext = extendedProperties
    fun ext(name: String) = ext.filter { it.propertyName.equals(name, ignoreCase = true) }
    fun extValue(name: String) = ext(name).firstOrNull()?.value?.trim().orEmpty()

    // Name
    val n = structuredName
    var name = NameField(
        prefix = n?.prefixes?.joinToString(" ").orEmpty(),
        given = n?.given.orEmpty(),
        middle = n?.additionalNames?.joinToString(" ").orEmpty(),
        family = n?.family.orEmpty(),
        suffix = n?.suffixes?.joinToString(" ").orEmpty(),
        phoneticGiven = extValue("X-PHONETIC-FIRST-NAME"),
        phoneticMiddle = extValue("X-PHONETIC-MIDDLE-NAME"),
        phoneticFamily = extValue("X-PHONETIC-LAST-NAME"),
    )
    if (name.given.isBlank() && name.family.isBlank()) {
        formattedName?.value?.takeIf { it.isNotBlank() }?.let { name = name.copy(given = it.trim()) }
    }

    var nickname = nicknames.flatMap { it.values }.firstOrNull { it.isNotBlank() }.orEmpty()
    val org = organization?.values.orEmpty()
    val organizationField = OrganizationField(
        company = org.getOrNull(0).orEmpty(),
        department = org.drop(1).filter { it.isNotBlank() }.joinToString(", "),
        title = titles.firstOrNull()?.value.orEmpty(),
    )

    val phones = mutableListOf<ContactField>()
    val sips = mutableListOf<ContactField>()
    telephoneNumbers.forEach { t ->
        val uri = t.uri
        val number = t.text ?: uri?.number ?: return@forEach
        if (number.startsWith("sip:", ignoreCase = true)) {
            sips += ContactField(number.substring(4), SipAddress.TYPE_OTHER)
        } else {
            val (type, label) = phoneType(t.parameters.types)
            phones += ContactField(number, type, label)
        }
    }

    val emails = this.emails.mapNotNull { e ->
        val v = e.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val (type, label) = simpleType(
            e.parameters.types,
            mapOf("home" to Email.TYPE_HOME, "work" to Email.TYPE_WORK, "cell" to Email.TYPE_MOBILE, "mobile" to Email.TYPE_MOBILE, "other" to Email.TYPE_OTHER),
            default = Email.TYPE_OTHER, custom = Email.TYPE_CUSTOM,
        )
        ContactField(v, type, label)
    }

    val addresses = this.addresses.map { a ->
        val (type, label) = simpleType(
            a.parameters.types,
            mapOf("home" to StructuredPostal.TYPE_HOME, "work" to StructuredPostal.TYPE_WORK, "other" to StructuredPostal.TYPE_OTHER),
            default = StructuredPostal.TYPE_HOME, custom = StructuredPostal.TYPE_CUSTOM,
        )
        PostalAddress(
            street = a.streetAddressFull.orEmpty(),
            poBox = a.poBoxes.joinToString(", "),
            neighborhood = a.extendedAddressFull.orEmpty(),
            city = a.localities.joinToString(", "),
            region = a.regions.joinToString(", "),
            postcode = a.postalCodes.joinToString(", "),
            country = a.countries.joinToString(", "),
            type = type, label = label,
            formatted = a.label.orEmpty(),
        )
    }.filterNot { it.isBlank }

    val events = mutableListOf<ContactField>()
    birthdays.forEach { b -> b.toStorageDate()?.let { events += ContactField(it, Event.TYPE_BIRTHDAY) } }
    anniversaries.forEach { a -> a.toStorageDate()?.let { events += ContactField(it, Event.TYPE_ANNIVERSARY) } }
    // ANNIVERSARY only exists in vCard 4.0; older files carry it as an extension.
    (ext("ANNIVERSARY") + ext("X-ANNIVERSARY") + ext("X-EVOLUTION-ANNIVERSARY")).forEach { p ->
        p.value?.trim()?.takeIf { it.isNotEmpty() }?.let { events += ContactField(it, Event.TYPE_ANNIVERSARY) }
    }

    val relations = this.relations.mapNotNull { r ->
        val v = (r.text ?: r.uri)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val types = r.parameters.types
        val type = types.firstNotNullOfOrNull { relationTypes[it.lowercase()] } ?: Relation.TYPE_CUSTOM
        ContactField(v, type, if (type == Relation.TYPE_CUSTOM) types.firstOrNull() else null)
    }.toMutableList()

    val websites = urls.mapNotNull { u ->
        val v = u.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val (type, label) = simpleType(
            listOfNotNull(u.type),
            mapOf(
                "homepage" to Website.TYPE_HOMEPAGE, "blog" to Website.TYPE_BLOG, "profile" to Website.TYPE_PROFILE,
                "home" to Website.TYPE_HOME, "work" to Website.TYPE_WORK, "ftp" to Website.TYPE_FTP, "other" to Website.TYPE_OTHER,
            ),
            default = Website.TYPE_HOMEPAGE, custom = Website.TYPE_CUSTOM,
        )
        ContactField(v, type, label)
    }

    val ims = mutableListOf<ImField>()
    impps.forEach { i ->
        val handle = i.handle ?: return@forEach
        val protocol = i.protocol.orEmpty().lowercase()
        if (protocol == "sip") sips += ContactField(handle, SipAddress.TYPE_OTHER)
        else ims += imppProtocols[protocol]?.let { ImField(handle, it) } ?: ImField(handle, Im.PROTOCOL_CUSTOM, protocol.ifEmpty { null })
    }
    imExtensions.forEach { (prop, protocol) -> ext(prop).forEach { p -> p.value?.takeIf { it.isNotBlank() }?.let { ims += ImField(it.trim(), protocol) } } }
    ext("X-SIP").forEach { p ->
        val v = p.value?.trim()?.removePrefix("sip:")?.takeIf { it.isNotEmpty() } ?: return@forEach
        val (type, label) = simpleType(
            p.parameters.types,
            mapOf("home" to SipAddress.TYPE_HOME, "work" to SipAddress.TYPE_WORK, "other" to SipAddress.TYPE_OTHER),
            default = SipAddress.TYPE_OTHER, custom = SipAddress.TYPE_CUSTOM,
        )
        sips += ContactField(v, type, label)
    }

    // Android's own extension, one row of contact data per property.
    ext("X-ANDROID-CUSTOM").forEach { p ->
        val parts = splitEscaped(p.value.orEmpty())
        val data = { i: Int -> parts.getOrNull(i)?.takeIf { it.isNotEmpty() } }
        when (parts.firstOrNull()) {
            Nickname.CONTENT_ITEM_TYPE -> if (nickname.isBlank()) nickname = data(1).orEmpty()
            Event.CONTENT_ITEM_TYPE -> data(1)?.let { v ->
                events += ContactField(v, data(2)?.toIntOrNull() ?: Event.TYPE_OTHER, data(3))
            }
            Relation.CONTENT_ITEM_TYPE -> data(1)?.let { v ->
                relations += ContactField(v, data(2)?.toIntOrNull() ?: Relation.TYPE_CUSTOM, data(3))
            }
            Im.CONTENT_ITEM_TYPE -> data(1)?.let { v ->
                ims += ImField(v, data(5)?.toIntOrNull() ?: Im.PROTOCOL_CUSTOM, data(6))
            }
        }
    }

    val note = notes.mapNotNull { it.value?.trim()?.takeIf(String::isNotEmpty) }.joinToString("\n")
    val photo = photos.firstOrNull { it.data != null }?.data?.let { PhotoChange.Set(it) } ?: PhotoChange.None
    val categories = categoriesList.flatMap { it.values }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    return Imported(
        ContactDraft(
            name = name,
            nickname = TextField(nickname),
            organization = organizationField,
            phones = phones,
            emails = emails,
            addresses = addresses,
            events = events.distinctBy { it.value to it.type },
            relations = relations,
            websites = websites,
            ims = ims.distinctBy { it.handle to it.protocol },
            sips = sips.distinctBy { it.value },
            note = TextField(note),
        ),
        photo,
        categories,
    )
}

/** Event date in the provider's format (`yyyy-MM-dd` or `--MM-dd`). */
private fun DateOrTimeProperty.toStorageDate(): String? {
    date?.let { t -> return storageDate(t) }
    partialDate?.let { p ->
        val m = p.month ?: return null
        val d = p.date ?: return null
        return if (p.year != null) "%04d-%02d-%02d".format(p.year, m, d) else "--%02d-%02d".format(m, d)
    }
    return text?.takeIf { it.isNotBlank() }
}

private fun storageDate(t: TemporalAccessor): String? {
    if (!t.isSupported(ChronoField.MONTH_OF_YEAR) || !t.isSupported(ChronoField.DAY_OF_MONTH)) return null
    val m = t.get(ChronoField.MONTH_OF_YEAR)
    val d = t.get(ChronoField.DAY_OF_MONTH)
    return if (t.isSupported(ChronoField.YEAR)) "%04d-%02d-%02d".format(t.get(ChronoField.YEAR), m, d) else "--%02d-%02d".format(m, d)
}

/** Picks the first known type among [types]; an unknown one (X-Foo) becomes a custom label. */
private fun simpleType(types: List<String>, known: Map<String, Int>, default: Int, custom: Int): Pair<Int, String?> {
    val clean = types.map { it.lowercase().removePrefix("x-") }.filter { it != "pref" && it != "internet" && it != "voice" }
    clean.firstNotNullOfOrNull { known[it] }?.let { return it to null }
    val other = types.firstOrNull { it.startsWith("x-", ignoreCase = true) }?.substring(2)
    return if (other != null) custom to other else default to null
}

private fun phoneType(raw: List<String>): Pair<Int, String?> {
    val t = raw.map { it.lowercase().removePrefix("x-") }.toSet()
    val type = when {
        "cell" in t && "work" in t -> Phone.TYPE_WORK_MOBILE
        "pager" in t && "work" in t -> Phone.TYPE_WORK_PAGER
        "fax" in t && "home" in t -> Phone.TYPE_FAX_HOME
        "fax" in t && "work" in t -> Phone.TYPE_FAX_WORK
        "cell" in t || "mobile" in t || "iphone" in t -> Phone.TYPE_MOBILE
        "fax" in t -> Phone.TYPE_OTHER_FAX
        "pager" in t -> Phone.TYPE_PAGER
        "car" in t -> Phone.TYPE_CAR
        "isdn" in t -> Phone.TYPE_ISDN
        "textphone" in t || "tty-tdd" in t || "tty" in t -> Phone.TYPE_TTY_TDD
        "msg" in t || "mms" in t -> Phone.TYPE_MMS
        "company-main" in t -> Phone.TYPE_COMPANY_MAIN
        "callback" in t -> Phone.TYPE_CALLBACK
        "radio" in t -> Phone.TYPE_RADIO
        "tlx" in t || "telex" in t -> Phone.TYPE_TELEX
        "assistant" in t -> Phone.TYPE_ASSISTANT
        "main" in t -> Phone.TYPE_MAIN
        "home" in t -> Phone.TYPE_HOME
        "work" in t -> Phone.TYPE_WORK
        else -> null
    }
    if (type != null) return type to null
    val custom = raw.firstOrNull { it.startsWith("x-", ignoreCase = true) }?.substring(2)
    return if (custom != null) Phone.TYPE_CUSTOM to custom else Phone.TYPE_OTHER to null
}

private val relationTypes = mapOf(
    "assistant" to Relation.TYPE_ASSISTANT, "sibling" to Relation.TYPE_BROTHER, "brother" to Relation.TYPE_BROTHER,
    "child" to Relation.TYPE_CHILD, "father" to Relation.TYPE_FATHER, "friend" to Relation.TYPE_FRIEND,
    "manager" to Relation.TYPE_MANAGER, "mother" to Relation.TYPE_MOTHER, "parent" to Relation.TYPE_PARENT,
    "sweetheart" to Relation.TYPE_PARTNER, "partner" to Relation.TYPE_PARTNER, "kin" to Relation.TYPE_RELATIVE,
    "relative" to Relation.TYPE_RELATIVE, "sister" to Relation.TYPE_SISTER, "spouse" to Relation.TYPE_SPOUSE,
    "domestic-partner" to Relation.TYPE_DOMESTIC_PARTNER, "referred-by" to Relation.TYPE_REFERRED_BY,
)

@Suppress("DEPRECATION")
private val imppProtocols = mapOf(
    "aim" to Im.PROTOCOL_AIM, "msn" to Im.PROTOCOL_MSN, "msnim" to Im.PROTOCOL_MSN, "ymsgr" to Im.PROTOCOL_YAHOO,
    "yahoo" to Im.PROTOCOL_YAHOO, "skype" to Im.PROTOCOL_SKYPE, "qq" to Im.PROTOCOL_QQ, "gtalk" to Im.PROTOCOL_GOOGLE_TALK,
    "icq" to Im.PROTOCOL_ICQ, "xmpp" to Im.PROTOCOL_JABBER, "jabber" to Im.PROTOCOL_JABBER,
)

@Suppress("DEPRECATION")
private val imExtensions = listOf(
    "X-AIM" to Im.PROTOCOL_AIM, "X-MSN" to Im.PROTOCOL_MSN, "X-YAHOO" to Im.PROTOCOL_YAHOO,
    "X-SKYPE-USERNAME" to Im.PROTOCOL_SKYPE, "X-SKYPE" to Im.PROTOCOL_SKYPE, "X-QQ" to Im.PROTOCOL_QQ,
    "X-GOOGLE-TALK" to Im.PROTOCOL_GOOGLE_TALK, "X-GTALK" to Im.PROTOCOL_GOOGLE_TALK, "X-ICQ" to Im.PROTOCOL_ICQ,
    "X-JABBER" to Im.PROTOCOL_JABBER, "X-NETMEETING" to Im.PROTOCOL_NETMEETING,
)

/** Splits on `;` not preceded by a backslash and unescapes each part. */
private fun splitEscaped(value: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '\\' && i + 1 < value.length -> {
                val next = value[i + 1]
                sb.append(if (next == 'n' || next == 'N') '\n' else next)
                i++
            }
            c == ';' -> { out += sb.toString(); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    out += sb.toString()
    return out
}

// --- Export ---

/** Writes every contact as vCard to [out] and returns how many were written. Blocking. */
fun writeAllContacts(repo: ContactsRepository, out: java.io.OutputStream): Int {
    val cards = repo.allContactIds().mapNotNull { id -> repo.readContact(id)?.toVCard(repo.photoBytes(id)) }
    writeVCards(cards, out)
    return cards.size
}

private fun writeVCards(cards: List<VCard>, out: java.io.OutputStream) {
    Ezvcard.write(cards).version(VCardVersion.V3_0).prodId(false).go(out)
}

/** `X-ANDROID-CUSTOM` value: the mimetype followed by DATA1… separated by `;`. */
private fun androidCustom(mime: String, vararg data: String?): String =
    (listOf(mime) + data.map { v -> v.orEmpty().replace("\\", "\\\\").replace(";", "\\;").replace("\n", "\\n") })
        .joinToString(";")

private fun customType(label: String?) = "X-" + (label?.trim()?.ifEmpty { null } ?: "Other")

@Suppress("DEPRECATION")
fun ContactDetail.toVCard(photo: ByteArray?): VCard = VCard().apply {
    val d = data
    val n = d.name
    structuredName = StructuredName().apply {
        family = n.family.trim().ifEmpty { null }
        given = n.given.trim().ifEmpty { null }
        n.middle.trim().takeIf { it.isNotEmpty() }?.let { additionalNames += it }
        n.prefix.trim().takeIf { it.isNotEmpty() }?.let { prefixes += it }
        n.suffix.trim().takeIf { it.isNotEmpty() }?.let { suffixes += it }
    }
    setFormattedName(displayName)
    n.phoneticGiven.takeIf { it.isNotBlank() }?.let { addExtendedProperty("X-PHONETIC-FIRST-NAME", it.trim()) }
    n.phoneticMiddle.takeIf { it.isNotBlank() }?.let { addExtendedProperty("X-PHONETIC-MIDDLE-NAME", it.trim()) }
    n.phoneticFamily.takeIf { it.isNotBlank() }?.let { addExtendedProperty("X-PHONETIC-LAST-NAME", it.trim()) }
    d.nickname.value.takeIf { it.isNotBlank() }?.let { setNickname(it.trim()) }

    val o = d.organization
    if (o.company.isNotBlank() || o.department.isNotBlank()) {
        organization = Organization().apply {
            values += o.company.trim()
            if (o.department.isNotBlank()) values += o.department.trim()
        }
    }
    o.title.takeIf { it.isNotBlank() }?.let { addTitle(it.trim()) }

    d.phones.forEach { p ->
        // Raw TYPE values: ez-vcard's type constants lowercase custom labels.
        addTelephoneNumber(Telephone(p.value.trim()).apply { phoneTypeNames(p).forEach { parameters.addType(it) } })
    }
    d.emails.forEach { e ->
        val type = when (e.type) {
            Email.TYPE_HOME -> "home"
            Email.TYPE_WORK -> "work"
            Email.TYPE_MOBILE -> "X-Mobile"
            Email.TYPE_CUSTOM -> customType(e.label)
            else -> "X-Other"
        }
        addEmail(ezvcard.property.Email(e.value.trim()).apply { parameters.addType("internet"); parameters.addType(type) })
    }
    d.addresses.forEach { a ->
        addAddress(Address().apply {
            if (a.street.isNotBlank()) streetAddress = a.street.trim()
            else if (a.city.isBlank() && a.formatted.isNotBlank()) label = a.formatted.trim()
            a.poBox.takeIf { it.isNotBlank() }?.let { poBox = it.trim() }
            a.neighborhood.takeIf { it.isNotBlank() }?.let { extendedAddress = it.trim() }
            a.city.takeIf { it.isNotBlank() }?.let { locality = it.trim() }
            a.region.takeIf { it.isNotBlank() }?.let { region = it.trim() }
            a.postcode.takeIf { it.isNotBlank() }?.let { postalCode = it.trim() }
            a.country.takeIf { it.isNotBlank() }?.let { country = it.trim() }
            parameters.addType(
                when (a.type) {
                    StructuredPostal.TYPE_HOME -> "home"
                    StructuredPostal.TYPE_WORK -> "work"
                    StructuredPostal.TYPE_CUSTOM -> customType(a.label)
                    else -> "X-Other"
                },
            )
        })
    }

    // The first dated birthday is BDAY; every other date uses Android's extension, which keeps
    // dates without a year and the date's type.
    val birthday = d.events.firstOrNull { e ->
        e.type == Event.TYPE_BIRTHDAY && dev.hostyn.phone.ui.components.EventDate.parse(e.value)?.year != null
    }
    birthday?.let { e ->
        val date = dev.hostyn.phone.ui.components.EventDate.parse(e.value)!!
        setBirthday(java.time.LocalDate.of(date.year!!, date.month, date.day))
    }
    d.events.filter { it !== birthday }.forEach { e ->
        addExtendedProperty("X-ANDROID-CUSTOM", androidCustom(Event.CONTENT_ITEM_TYPE, e.value, e.type.toString(), e.label))
    }
    d.relations.forEach { r ->
        addExtendedProperty("X-ANDROID-CUSTOM", androidCustom(Relation.CONTENT_ITEM_TYPE, r.value, r.type.toString(), r.label))
    }

    d.websites.forEach { w ->
        addUrl(w.value.trim()).type = when (w.type) {
            Website.TYPE_HOMEPAGE -> "homepage"
            Website.TYPE_BLOG -> "blog"
            Website.TYPE_PROFILE -> "profile"
            Website.TYPE_HOME -> "home"
            Website.TYPE_WORK -> "work"
            Website.TYPE_FTP -> "ftp"
            Website.TYPE_CUSTOM -> customType(w.label)
            else -> "other"
        }
    }

    d.ims.forEach { im ->
        val scheme = imppProtocols.entries.firstOrNull { it.value == im.protocol }?.key
        if (scheme != null) addImpp(Impp(scheme, im.handle.trim()))
        else addExtendedProperty(
            "X-ANDROID-CUSTOM",
            androidCustom(Im.CONTENT_ITEM_TYPE, im.handle.trim(), Im.TYPE_OTHER.toString(), null, null, im.protocol.toString(), im.customProtocol),
        )
    }
    d.sips.forEach { sip ->
        addExtendedProperty("X-SIP", sip.value.trim()).parameters.addType(
            when (sip.type) {
                SipAddress.TYPE_HOME -> "home"
                SipAddress.TYPE_WORK -> "work"
                SipAddress.TYPE_CUSTOM -> customType(sip.label)
                else -> "other"
            },
        )
    }

    d.note.value.takeIf { it.isNotBlank() }?.let { addNote(it.trim()) }
    if (groupTitles.isNotEmpty()) setCategories(*groupTitles.toTypedArray())
    photo?.let { addPhoto(Photo(it, ImageType.JPEG)) }
}

private fun phoneTypeNames(p: ContactField): List<String> = when (p.type) {
    Phone.TYPE_MOBILE -> listOf("cell")
    Phone.TYPE_HOME -> listOf("home")
    Phone.TYPE_WORK -> listOf("work")
    Phone.TYPE_FAX_WORK -> listOf("work", "fax")
    Phone.TYPE_FAX_HOME -> listOf("home", "fax")
    Phone.TYPE_PAGER -> listOf("pager")
    Phone.TYPE_CALLBACK -> listOf("X-Callback")
    Phone.TYPE_CAR -> listOf("car")
    Phone.TYPE_COMPANY_MAIN -> listOf("X-Company-Main")
    Phone.TYPE_ISDN -> listOf("isdn")
    Phone.TYPE_MAIN -> listOf("X-Main")
    Phone.TYPE_OTHER_FAX -> listOf("fax")
    Phone.TYPE_RADIO -> listOf("X-Radio")
    Phone.TYPE_TELEX -> listOf("X-Telex")
    Phone.TYPE_TTY_TDD -> listOf("X-TTY-TDD")
    Phone.TYPE_WORK_MOBILE -> listOf("work", "cell")
    Phone.TYPE_WORK_PAGER -> listOf("work", "pager")
    Phone.TYPE_ASSISTANT -> listOf("X-Assistant")
    Phone.TYPE_MMS -> listOf("msg")
    Phone.TYPE_CUSTOM -> listOf(customType(p.label))
    else -> listOf("voice")
}
