package dev.hostyn.phone.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.os.SystemClock
import org.xmlpull.v1.XmlPullParser
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resolver: ContentResolver get() = context.contentResolver

    /** Emits whenever something changes in the contacts provider. */
    private fun changes(uri: Uri): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(uri, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    private val settings get() = (context.applicationContext as dev.hostyn.phone.PhoneApp).settings

    /** Emits when contacts change or when the name order settings change. */
    private fun contactChanges(): Flow<Unit> =
        combine(
            changes(Contacts.CONTENT_URI),
            snapshotFlow { Triple(settings.sortOrder, settings.nameFormat, settings.hiddenAccounts) },
        ) { _, _ -> }

    private val nameColumn get() =
        if (settings.nameFormat == NameOrder.FamilyFirst) Contacts.DISPLAY_NAME_ALTERNATIVE else Contacts.DISPLAY_NAME_PRIMARY
    private val sortColumn get() =
        if (settings.sortOrder == NameOrder.FamilyFirst) Contacts.SORT_KEY_ALTERNATIVE else Contacts.SORT_KEY_PRIMARY

    fun observeContacts(): Flow<List<ContactSummary>> =
        contactChanges().map { queryContacts() }.flowOn(Dispatchers.IO)

    /**
     * All contacts, kept for a while after the list leaves the screen: coming back to it shows the
     * last list straight away (and its rows are there for the container transform back to them).
     */
    val contacts: StateFlow<List<ContactSummary>?> =
        observeContacts().stateIn(cacheScope, SharingStarted.WhileSubscribed(60_000), null)

    fun observeContact(id: Long): Flow<ContactDetail?> =
        contactChanges().map { queryContact(id) }.flowOn(Dispatchers.IO)

    suspend fun getContact(id: Long): ContactDetail? = withContext(Dispatchers.IO) { queryContact(id) }

    private fun queryContacts(): List<ContactSummary> {
        val hidden = hiddenData()
        val numbers = HashMap<Long, MutableList<String>>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.RAW_CONTACT_ID),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (hidden != null && c.getLong(2) in hidden.rawContacts) continue
                val number = c.getString(1)
                if (number != null) numbers.getOrPut(c.getLong(0)) { mutableListOf() }.add(number)
            }
        }

        val result = mutableListOf<ContactSummary>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, nameColumn, Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED, Contacts.LOOKUP_KEY, sortColumn),
            "$nameColumn IS NOT NULL",
            null,
            "$sortColumn COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                if (hidden != null && id in hidden.contacts) continue
                result += ContactSummary(
                    id = id,
                    name = c.getString(1),
                    photoUri = c.getString(2),
                    starred = c.getInt(3) == 1,
                    numbers = numbers[id].orEmpty(),
                    lookupKey = c.getString(4),
                    sortKey = c.getString(5) ?: c.getString(1),
                )
            }
        }
        return result
    }

    // --- Reading a contact ---

    private class Row(val id: Long, val rawId: Long, val mime: String, val data: Array<String?>) {
        /** DATA1…DATA10 (1-based, like the column names). */
        fun d(n: Int): String = data[n - 1].orEmpty()
        fun int(n: Int, default: Int): Int = data[n - 1]?.toIntOrNull() ?: default
        fun nullable(n: Int): String? = data[n - 1]?.takeIf { it.isNotEmpty() }
    }

    /** Adds [field] to [list], merging it with an equal one (same value in a linked raw contact). */
    private fun <T> MutableList<T>.addMerged(field: T, key: (T) -> Any, ids: (T) -> List<Long>, withIds: (T, List<Long>) -> T) {
        val k = key(field)
        val i = indexOfFirst { key(it) == k }
        if (i < 0) add(field) else this[i] = withIds(this[i], ids(this[i]) + ids(field))
    }

    private fun MutableList<ContactField>.addField(field: ContactField, key: (String) -> String = { it.trim() }) =
        addMerged(field, { key(it.value) }, { it.ids }, { f, ids -> f.copy(ids = ids) })

    @Suppress("DEPRECATION")
    private fun queryContact(id: Long): ContactDetail? {
        val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id)
        var displayName = ""
        var photo: String? = null
        var starred = false
        var nameRawId = -1L
        var lookupKey: String? = null
        var ringtone: String? = null
        var toVoicemail = false
        resolver.query(
            contactUri,
            arrayOf(
                nameColumn, Contacts.PHOTO_URI, Contacts.STARRED, Contacts.NAME_RAW_CONTACT_ID,
                Contacts.LOOKUP_KEY, Contacts.CUSTOM_RINGTONE, Contacts.SEND_TO_VOICEMAIL,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            displayName = c.getString(0).orEmpty()
            photo = c.getString(1)
            starred = c.getInt(2) == 1
            nameRawId = c.getLong(3)
            lookupKey = c.getString(4)
            ringtone = c.getString(5)
            toVoicemail = c.getInt(6) == 1
        } ?: return null

        val rows = mutableListOf<Row>()
        val columns = (1..10).map { "data$it" }
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE) + columns,
            "${Data.CONTACT_ID} = ?",
            arrayOf(id.toString()),
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                rows += Row(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), Array(10) { c.getString(3 + it) })
            }
        }

        // Leave out what comes from hidden accounts, unless that is all the contact has.
        val accounts = rawContactAccounts(id)
        val editable = editableAccountTypes()
        // A raw contact of an account the user edits, not a chat app's link to the contact.
        fun isOwner(rawId: Long) = accounts[rawId]?.let { it.isLocal || it.type in editable } == true
        val hiddenKeys = settings.hiddenAccounts
        if (hiddenKeys.isNotEmpty()) {
            val shown = rows.filter { accounts[it.rawId]?.key !in hiddenKeys }
            if (shown.any { isOwner(it.rawId) } && shown.size < rows.size) {
                rows.retainAll(shown.toSet())
                if (accounts[nameRawId]?.key in hiddenKeys) nameRawId = -1
            }
        }

        val rawIds = linkedSetOf<Long>()
        rows.forEach { rawIds += it.rawId }
        if (nameRawId <= 0) {
            val owners = rawIds.filter(::isOwner)
            nameRawId = owners.firstOrNull { raw -> rows.any { it.rawId == raw && it.mime == StructuredName.CONTENT_ITEM_TYPE } }
                ?: owners.firstOrNull() ?: rawIds.firstOrNull() ?: return null
            rows.firstOrNull { it.rawId == nameRawId && it.mime == StructuredName.CONTENT_ITEM_TYPE }
                ?.d(1)?.takeIf { it.isNotBlank() }?.let { displayName = it }
        }
        // Single-value fields come from the raw contact that names the contact, if it has them.
        val ordered = rows.sortedBy { if (it.rawId == nameRawId) 0 else 1 }

        var name: NameField? = null
        var nickname: TextField? = null
        var org: OrganizationField? = null
        var note: TextField? = null
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()
        val addresses = mutableListOf<PostalAddress>()
        val events = mutableListOf<ContactField>()
        val relations = mutableListOf<ContactField>()
        val websites = mutableListOf<ContactField>()
        val ims = mutableListOf<ImField>()
        val sips = mutableListOf<ContactField>()
        val groups = linkedSetOf<Long>()

        for (r in ordered) {
            val ids = listOf(r.id)
            when (r.mime) {
                StructuredName.CONTENT_ITEM_TYPE -> if (name == null) name = NameField(
                    // DATA1 display name, 2 given, 3 family, 4 prefix, 5 middle, 6 suffix, 7-9 phonetic given/middle/family
                    prefix = r.d(4), given = r.d(2), middle = r.d(5), family = r.d(3), suffix = r.d(6),
                    phoneticGiven = r.d(7), phoneticMiddle = r.d(8), phoneticFamily = r.d(9),
                    ids = ids,
                ).let { n -> if (n.isBlank && r.d(1).isNotBlank()) n.copy(given = r.d(1)) else n }
                Nickname.CONTENT_ITEM_TYPE -> if (nickname == null && r.d(1).isNotBlank()) nickname = TextField(r.d(1), ids)
                Organization.CONTENT_ITEM_TYPE -> if (org == null) {
                    // DATA1 company, 4 title, 5 department
                    org = OrganizationField(r.d(1), r.d(4), r.d(5), ids).takeUnless { it.isBlank }
                }
                Note.CONTENT_ITEM_TYPE -> if (note == null && r.d(1).isNotBlank()) note = TextField(r.d(1), ids)
                Phone.CONTENT_ITEM_TYPE -> if (r.d(1).isNotBlank()) {
                    phones.addField(ContactField(r.d(1), r.int(2, Phone.TYPE_MOBILE), r.nullable(3), ids)) {
                        PhoneNumberUtils.normalizeNumber(it)
                    }
                }
                Email.CONTENT_ITEM_TYPE -> if (r.d(1).isNotBlank()) {
                    emails.addField(ContactField(r.d(1), r.int(2, Email.TYPE_HOME), r.nullable(3), ids)) { it.trim().lowercase() }
                }
                StructuredPostal.CONTENT_ITEM_TYPE -> {
                    // DATA1 formatted, 2 type, 3 label, 4 street, 5 PO box, 6 neighborhood, 7 city, 8 region, 9 postcode, 10 country
                    val a = PostalAddress(
                        street = r.d(4), poBox = r.d(5), neighborhood = r.d(6), city = r.d(7), region = r.d(8),
                        postcode = r.d(9), country = r.d(10), type = r.int(2, StructuredPostal.TYPE_HOME), label = r.nullable(3),
                        formatted = r.d(1), ids = ids,
                    )
                    if (!a.isBlank) addresses.addMerged(a, { it.display() }, { it.ids }, { f, i -> f.copy(ids = i) })
                }
                Event.CONTENT_ITEM_TYPE -> if (r.d(1).isNotBlank()) {
                    events.addField(ContactField(r.d(1), r.int(2, Event.TYPE_BIRTHDAY), r.nullable(3), ids))
                }
                Relation.CONTENT_ITEM_TYPE -> if (r.d(1).isNotBlank()) {
                    relations.addField(ContactField(r.d(1), r.int(2, Relation.TYPE_CUSTOM), r.nullable(3), ids))
                }
                Website.CONTENT_ITEM_TYPE -> if (r.d(1).isNotBlank()) {
                    websites.addField(ContactField(r.d(1), r.int(2, Website.TYPE_OTHER), r.nullable(3), ids))
                }
                SipAddress.CONTENT_ITEM_TYPE -> if (r.d(1).isNotBlank()) {
                    sips.addField(ContactField(r.d(1), r.int(2, SipAddress.TYPE_OTHER), r.nullable(3), ids))
                }
                Im.CONTENT_ITEM_TYPE -> if (r.d(1).isNotBlank()) {
                    // DATA1 handle, 5 protocol, 6 custom protocol
                    ims.addMerged(
                        ImField(r.d(1), r.int(5, Im.PROTOCOL_CUSTOM), r.nullable(6), ids),
                        { it.handle.trim() to it.protocol }, { it.ids }, { f, i -> f.copy(ids = i) },
                    )
                }
                GroupMembership.CONTENT_ITEM_TYPE -> r.data[0]?.toLongOrNull()?.let { groups += it }
            }
        }

        val draft = ContactDraft(
            name = name ?: NameField(given = displayName),
            nickname = nickname ?: TextField(),
            organization = org ?: OrganizationField(),
            phones = phones,
            emails = emails,
            addresses = addresses,
            events = events,
            relations = relations,
            websites = websites,
            ims = ims,
            sips = sips,
            note = note ?: TextField(),
            groups = groups,
        )

        return ContactDetail(
            id = id,
            lookupKey = lookupKey,
            nameRawContactId = nameRawId,
            rawContactIds = rawIds.toList(),
            displayName = displayName,
            photoUri = photo,
            starred = starred,
            ringtone = ringtone,
            sendToVoicemail = toVoicemail,
            account = accounts[nameRawId] ?: accountOf(nameRawId),
            data = draft,
            groupTitles = groupTitles(groups),
        )
    }

    private fun accountOf(rawId: Long): ContactAccount =
        resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE), null, null, null,
        )?.use { c -> if (c.moveToFirst()) ContactAccount(c.getString(0), c.getString(1)) else null } ?: ContactAccount.Local

    private fun groupTitles(ids: Set<Long>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups.TITLE),
            "${Groups._ID} IN (${ids.joinToString(",")}) AND ${Groups.AUTO_ADD} = 0 AND ${Groups.FAVORITES} = 0 AND ${Groups.DELETED} = 0",
            null, "${Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { out += it } }
        return out
    }

    // --- Accounts and labels ---

    /**
     * Accounts that hold contacts the user can edit: the device plus synced accounts such as
     * Google or Atlas. Chat apps (WhatsApp, Signal, Telegram…) also have contacts accounts, but
     * only to link their buttons to existing contacts: they are left out.
     */
    fun writableAccounts(): List<ContactAccount> {
        val types = editableAccountTypes()
        val synced = try {
            AccountManager.get(context).accounts.filter { it.type in types }.map { ContactAccount(it.name, it.type) }
        } catch (_: SecurityException) {
            emptyList()
        }
        return listOf(ContactAccount.Local) + synced
    }

    /** The accounts shown in the app, which are also the ones new contacts can be saved to. */
    fun shownAccounts(): List<ContactAccount> =
        writableAccounts().filter { it.key !in settings.hiddenAccounts }.ifEmpty { listOf(ContactAccount.Local) }

    /** Where a new contact goes unless the user picks another account: the default from Settings, or the first synced one. */
    fun defaultAccount(accounts: List<ContactAccount>): ContactAccount =
        accounts.firstOrNull { it.key == settings.defaultAccount }
            ?: accounts.firstOrNull { !it.isLocal }
            ?: accounts.firstOrNull()
            ?: ContactAccount.Local

    /** How many contacts each account holds, by [ContactAccount.key]. */
    suspend fun accountContactCounts(): Map<String, Int> = withContext(Dispatchers.IO) {
        val counts = HashMap<String, Int>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.DELETED} = 0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val key = ContactAccount(c.getString(0), c.getString(1)).key
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        counts
    }

    private fun rawContactAccounts(contactId: Long): Map<Long, ContactAccount> {
        val out = HashMap<Long, ContactAccount>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0", arrayOf(contactId.toString()), null,
        )?.use { c -> while (c.moveToNext()) out[c.getLong(0)] = ContactAccount(c.getString(1), c.getString(2)) }
        return out
    }

    /** Raw contacts in hidden accounts, and the contacts that only have those (plus chat app links). */
    private class Hidden(val rawContacts: Set<Long>, val contacts: Set<Long>)

    private fun hiddenData(): Hidden? {
        val hiddenKeys = settings.hiddenAccounts
        if (hiddenKeys.isEmpty()) return null
        val editable = editableAccountTypes()
        val raws = HashSet<Long>()
        val hasShownAccount = HashMap<Long, Boolean>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
            "${RawContacts.DELETED} = 0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val account = ContactAccount(c.getString(2), c.getString(3))
                if (!account.isLocal && account.type !in editable) continue
                val shown = account.key !in hiddenKeys
                if (!shown) raws += c.getLong(0)
                val contact = c.getLong(1)
                hasShownAccount[contact] = hasShownAccount[contact] == true || shown
            }
        }
        return Hidden(raws, hasShownAccount.filterValues { !it }.keys)
    }

    @Volatile private var editableTypes: Pair<Long, Set<String>>? = null

    /**
     * Account types with editable contacts. As in Android's own Contacts app: Google and Exchange,
     * and sync adapters whose contacts structure (`android.provider.CONTACTS_STRUCTURE`) has an
     * EditSchema. Chat apps only declare how to show their links, so they don't count.
     */
    private fun editableAccountTypes(): Set<String> {
        editableTypes?.let { (time, types) -> if (SystemClock.elapsedRealtime() - time < 60_000) return types }
        val contactTypes = ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
            .map { it.accountType }
            .toSet()
        val types = contactTypes.filterTo(mutableSetOf()) { it in BUILT_IN_EDITABLE }
        val pm = context.packageManager
        val services = try {
            pm.queryIntentServices(Intent("android.content.SyncAdapter"), PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }
        for (info in services) {
            val service = info.serviceInfo ?: continue
            try {
                val res = pm.getResourcesForApplication(service.applicationInfo)
                val type = service.loadXmlMetaData(pm, "android.content.SyncAdapter")?.use { contactsAccountType(it, res) } ?: continue
                if (type !in contactTypes || type in types) continue
                if (service.loadXmlMetaData(pm, "android.provider.CONTACTS_STRUCTURE")?.use { hasEditSchema(it) } == true) types += type
            } catch (_: Exception) {
                // A broken or missing description: not editable.
            }
        }
        editableTypes = SystemClock.elapsedRealtime() to types
        return types
    }

    /** The account type of a `<sync-adapter>` description for contacts, or null for other authorities. */
    private fun contactsAccountType(parser: XmlResourceParser, res: Resources): String? {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "sync-adapter") continue
            if (parser.androidAttribute("contentAuthority", res) != ContactsContract.AUTHORITY) return null
            return parser.androidAttribute("accountType", res)
        }
        return null
    }

    private fun hasEditSchema(parser: XmlResourceParser): Boolean {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "EditSchema") return true
        }
        return false
    }

    private fun XmlResourceParser.androidAttribute(name: String, res: Resources): String? {
        val id = getAttributeResourceValue(ANDROID_NS, name, 0)
        return if (id != 0) res.getString(id) else getAttributeValue(ANDROID_NS, name)
    }

    private fun accountSelection(account: ContactAccount, prefix: String = ""): Pair<String, Array<String>> =
        if (account.isLocal) "${prefix}account_name IS NULL AND ${prefix}account_type IS NULL" to emptyArray()
        else "${prefix}account_name = ? AND ${prefix}account_type = ?" to arrayOf(account.name!!, account.type!!)

    /** User labels of an account (system groups such as “My contacts” are left out). */
    suspend fun groups(account: ContactAccount): List<ContactGroup> = withContext(Dispatchers.IO) {
        val (sel, args) = accountSelection(account)
        val out = mutableListOf<ContactGroup>()
        resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups._ID, Groups.TITLE),
            "$sel AND ${Groups.DELETED} = 0 AND ${Groups.AUTO_ADD} = 0 AND ${Groups.FAVORITES} = 0",
            args, "${Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) c.getString(1)?.takeIf { it.isNotBlank() }?.let { out += ContactGroup(c.getLong(0), it) }
        }
        out
    }

    suspend fun createGroup(account: ContactAccount, title: String): ContactGroup? = withContext(Dispatchers.IO) {
        val uri = resolver.insert(Groups.CONTENT_URI, ContentValues().apply {
            put(Groups.TITLE, title.trim())
            put(Groups.ACCOUNT_NAME, account.name)
            put(Groups.ACCOUNT_TYPE, account.type)
            put(Groups.GROUP_VISIBLE, 1)
        }) ?: return@withContext null
        ContactGroup(ContentUris.parseId(uri), title.trim())
    }

    // --- Writing ---

    /** Where new data rows go: an existing raw contact, or the one inserted by the first operation. */
    private class Target(val rawId: Long?)

    /** Creates a new contact and returns its id, or null on failure. */
    suspend fun create(
        draft: ContactDraft,
        account: ContactAccount = ContactAccount.Local,
        photo: PhotoChange = PhotoChange.None,
    ): Long? = withContext(Dispatchers.IO) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, account.type)
            .withValue(RawContacts.ACCOUNT_NAME, account.name)
            .build()
        diffAll(ops, ContactDraft(), draft, Target(null), rawIds = emptyList())

        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawId = results.firstOrNull()?.uri?.let(ContentUris::parseId) ?: return@withContext null
        applyPhoto(photo, rawId, listOf(rawId))
        resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            arrayOf(RawContacts.CONTACT_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    suspend fun update(existing: ContactDetail, draft: ContactDraft, photo: PhotoChange = PhotoChange.None) = withContext(Dispatchers.IO) {
        val ops = arrayListOf<ContentProviderOperation>()
        diffAll(ops, existing.data, draft, Target(existing.nameRawContactId), existing.rawContactIds)
        if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        applyPhoto(photo, existing.nameRawContactId, existing.rawContactIds)
    }

    private fun diffAll(
        ops: MutableList<ContentProviderOperation>,
        old: ContactDraft,
        new: ContactDraft,
        target: Target,
        rawIds: List<Long>,
    ) {
        diff(ops, target, StructuredName.CONTENT_ITEM_TYPE, listOf(old.name).filterNot { it.isBlank && it.ids.isEmpty() },
            listOf(new.name), { it.ids }, { it.isBlank }, ::nameValues)
        diff(ops, target, Nickname.CONTENT_ITEM_TYPE, listOf(old.nickname).filter { it.ids.isNotEmpty() },
            listOf(new.nickname), { it.ids }, { it.isBlank }) { mapOf(Nickname.NAME to it.value.trim()) }
        diff(ops, target, Organization.CONTENT_ITEM_TYPE, listOf(old.organization).filter { it.ids.isNotEmpty() },
            listOf(new.organization), { it.ids }, { it.isBlank }) {
            mapOf(
                Organization.COMPANY to it.company.trim().ifEmpty { null },
                Organization.TITLE to it.title.trim().ifEmpty { null },
                Organization.DEPARTMENT to it.department.trim().ifEmpty { null },
            )
        }
        diff(ops, target, Note.CONTENT_ITEM_TYPE, listOf(old.note).filter { it.ids.isNotEmpty() },
            listOf(new.note), { it.ids }, { it.isBlank }) { mapOf(Note.NOTE to it.value.trim()) }

        fun typed(valueCol: String, typeCol: String, labelCol: String, custom: Int) = { f: ContactField ->
            mapOf(valueCol to f.value.trim(), typeCol to f.type, labelCol to if (f.type == custom) f.label?.trim() else null)
        }
        diff(ops, target, Phone.CONTENT_ITEM_TYPE, old.phones, new.phones, { it.ids }, { it.isBlank },
            typed(Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.TYPE_CUSTOM))
        diff(ops, target, Email.CONTENT_ITEM_TYPE, old.emails, new.emails, { it.ids }, { it.isBlank },
            typed(Email.ADDRESS, Email.TYPE, Email.LABEL, Email.TYPE_CUSTOM))
        diff(ops, target, Event.CONTENT_ITEM_TYPE, old.events, new.events, { it.ids }, { it.isBlank },
            typed(Event.START_DATE, Event.TYPE, Event.LABEL, Event.TYPE_CUSTOM))
        diff(ops, target, Relation.CONTENT_ITEM_TYPE, old.relations, new.relations, { it.ids }, { it.isBlank },
            typed(Relation.NAME, Relation.TYPE, Relation.LABEL, Relation.TYPE_CUSTOM))
        diff(ops, target, Website.CONTENT_ITEM_TYPE, old.websites, new.websites, { it.ids }, { it.isBlank },
            typed(Website.URL, Website.TYPE, Website.LABEL, Website.TYPE_CUSTOM))
        diff(ops, target, SipAddress.CONTENT_ITEM_TYPE, old.sips, new.sips, { it.ids }, { it.isBlank },
            typed(SipAddress.SIP_ADDRESS, SipAddress.TYPE, SipAddress.LABEL, SipAddress.TYPE_CUSTOM))
        diff(ops, target, StructuredPostal.CONTENT_ITEM_TYPE, old.addresses, new.addresses, { it.ids }, { it.isBlank }, ::addressValues)
        diff(ops, target, Im.CONTENT_ITEM_TYPE, old.ims, new.ims, { it.ids }, { it.isBlank }, ::imValues)

        // Labels: membership rows are matched by group id, not row id.
        (new.groups - old.groups).forEach { group ->
            ops += insertOp(target, GroupMembership.CONTENT_ITEM_TYPE, mapOf(GroupMembership.GROUP_ROW_ID to group))
        }
        val removed = old.groups - new.groups
        if (removed.isNotEmpty() && rawIds.isNotEmpty()) {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID} IN (${rawIds.joinToString(",")}) AND ${Data.MIMETYPE} = ? " +
                        "AND ${GroupMembership.GROUP_ROW_ID} IN (${removed.joinToString(",")})",
                    arrayOf(GroupMembership.CONTENT_ITEM_TYPE),
                ).build()
        }
    }

    /**
     * Turns the change from [old] to [new] into operations: rows whose field disappeared or was
     * blanked are deleted, changed fields update their rows, and new fields are inserted.
     */
    private fun <T> diff(
        ops: MutableList<ContentProviderOperation>,
        target: Target,
        mime: String,
        old: List<T>,
        new: List<T>,
        ids: (T) -> List<Long>,
        blank: (T) -> Boolean,
        values: (T) -> Map<String, Any?>,
    ) {
        val kept = new.flatMap(ids).toSet()
        old.forEach { o -> if (ids(o).none { it in kept }) ops += deleteOp(ids(o)) }
        new.forEach { n ->
            val rowIds = ids(n)
            when {
                rowIds.isEmpty() -> if (!blank(n)) ops += insertOp(target, mime, values(n))
                blank(n) -> ops += deleteOp(rowIds)
                else -> {
                    val before = old.firstOrNull { ids(it) == rowIds }
                    if (before == null || values(before) != values(n)) {
                        ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                            .withSelection("${Data._ID} IN (${rowIds.joinToString(",")})", null)
                            .withValues(values(n).toContentValues())
                            .build()
                    }
                }
            }
        }
    }

    private fun deleteOp(ids: List<Long>) = ContentProviderOperation.newDelete(Data.CONTENT_URI)
        .withSelection("${Data._ID} IN (${ids.joinToString(",")})", null)
        .build()

    private fun insertOp(target: Target, mime: String, values: Map<String, Any?>): ContentProviderOperation {
        val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
        if (target.rawId == null) b.withValueBackReference(Data.RAW_CONTACT_ID, 0)
        else b.withValue(Data.RAW_CONTACT_ID, target.rawId)
        val cv = values.toContentValues()
        cv.put(Data.MIMETYPE, mime)
        return b.withValues(cv).build()
    }

    private fun Map<String, Any?>.toContentValues() = ContentValues().also { cv ->
        forEach { (k, v) ->
            when (v) {
                null -> cv.putNull(k)
                is String -> cv.put(k, v)
                is Int -> cv.put(k, v)
                is Long -> cv.put(k, v)
                else -> cv.put(k, v.toString())
            }
        }
    }

    private fun nameValues(n: NameField): Map<String, Any?> = mapOf(
        StructuredName.PREFIX to n.prefix.trim().ifEmpty { null },
        StructuredName.GIVEN_NAME to n.given.trim().ifEmpty { null },
        StructuredName.MIDDLE_NAME to n.middle.trim().ifEmpty { null },
        StructuredName.FAMILY_NAME to n.family.trim().ifEmpty { null },
        StructuredName.SUFFIX to n.suffix.trim().ifEmpty { null },
        StructuredName.PHONETIC_GIVEN_NAME to n.phoneticGiven.trim().ifEmpty { null },
        StructuredName.PHONETIC_MIDDLE_NAME to n.phoneticMiddle.trim().ifEmpty { null },
        StructuredName.PHONETIC_FAMILY_NAME to n.phoneticFamily.trim().ifEmpty { null },
    )

    private fun addressValues(a: PostalAddress): Map<String, Any?> {
        val structured = listOf(a.street, a.poBox, a.neighborhood, a.city, a.region, a.postcode, a.country).any { it.isNotBlank() }
        return mapOf(
            StructuredPostal.STREET to a.street.trim().ifEmpty { null },
            StructuredPostal.POBOX to a.poBox.trim().ifEmpty { null },
            StructuredPostal.NEIGHBORHOOD to a.neighborhood.trim().ifEmpty { null },
            StructuredPostal.CITY to a.city.trim().ifEmpty { null },
            StructuredPostal.REGION to a.region.trim().ifEmpty { null },
            StructuredPostal.POSTCODE to a.postcode.trim().ifEmpty { null },
            StructuredPostal.COUNTRY to a.country.trim().ifEmpty { null },
            // With structured parts, the provider builds the formatted address itself.
            StructuredPostal.FORMATTED_ADDRESS to if (structured) null else a.formatted.trim(),
            StructuredPostal.TYPE to a.type,
            StructuredPostal.LABEL to if (a.type == StructuredPostal.TYPE_CUSTOM) a.label?.trim() else null,
        )
    }

    @Suppress("DEPRECATION")
    private fun imValues(im: ImField): Map<String, Any?> = mapOf(
        Im.DATA to im.handle.trim(),
        Im.TYPE to Im.TYPE_OTHER,
        Im.PROTOCOL to im.protocol,
        Im.CUSTOM_PROTOCOL to if (im.protocol == Im.PROTOCOL_CUSTOM) im.customProtocol?.trim() else null,
    )

    /** Photo goes to the display photo of [rawId], which also creates the thumbnail. */
    private fun applyPhoto(photo: PhotoChange, rawId: Long, rawIds: List<Long>) {
        when (photo) {
            PhotoChange.None -> Unit
            PhotoChange.Remove -> resolver.delete(
                Data.CONTENT_URI,
                "${Data.RAW_CONTACT_ID} IN (${rawIds.joinToString(",")}) AND ${Data.MIMETYPE} = ?",
                arrayOf(Photo.CONTENT_ITEM_TYPE),
            )
            is PhotoChange.Set -> {
                val uri = Uri.withAppendedPath(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
                    RawContacts.DisplayPhoto.CONTENT_DIRECTORY,
                )
                resolver.openAssetFileDescriptor(uri, "rw")?.use { fd ->
                    fd.createOutputStream().use { it.write(photo.jpeg) }
                }
            }
        }
    }

    // --- Contact-level settings ---

    suspend fun setStarred(id: Long, starred: Boolean) = setContactValue(id, Contacts.STARRED, if (starred) 1 else 0)

    suspend fun setRingtone(id: Long, uri: String?) = setContactValue(id, Contacts.CUSTOM_RINGTONE, uri)

    suspend fun setSendToVoicemail(id: Long, enabled: Boolean) = setContactValue(id, Contacts.SEND_TO_VOICEMAIL, if (enabled) 1 else 0)

    private suspend fun setContactValue(id: Long, column: String, value: Any?) = withContext(Dispatchers.IO) {
        resolver.update(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            mapOf(column to value).toContentValues(),
            null, null,
        )
        Unit
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), null, null)
        Unit
    }

    // --- vCard ---

    fun allContactIds(): List<Long> {
        val out = mutableListOf<Long>()
        resolver.query(
            Contacts.CONTENT_URI, arrayOf(Contacts._ID), null, null,
            "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
        return out
    }

    /** Blocking read of a contact, for bulk work already running off the main thread. */
    fun readContact(id: Long): ContactDetail? = queryContact(id)

    /** The contact's photo, full size when there is one. */
    fun photoBytes(id: Long): ByteArray? = try {
        Contacts.openContactPhotoInputStream(resolver, ContentUris.withAppendedId(Contacts.CONTENT_URI, id), true)
            ?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        /** Built into Android's Contacts app rather than described by the sync adapter. */
        val BUILT_IN_EDITABLE = setOf("com.google", "com.android.exchange", "com.google.android.exchange", "com.google.android.gm.exchange")
    }
}
