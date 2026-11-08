package dev.hostyn.contactos.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContactsRepository(private val resolver: ContentResolver) {

    /** Emite cada vez que cambia algo en el proveedor de contactos. */
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

    fun observeContacts(): Flow<List<ContactSummary>> =
        changes(Contacts.CONTENT_URI).map { queryContacts() }.flowOn(Dispatchers.IO)

    fun observeContact(id: Long): Flow<ContactDetail?> =
        changes(Contacts.CONTENT_URI).map { queryContact(id) }.flowOn(Dispatchers.IO)

    suspend fun getContact(id: Long): ContactDetail? = withContext(Dispatchers.IO) { queryContact(id) }

    private fun queryContacts(): List<ContactSummary> {
        val numbers = HashMap<Long, MutableList<String>>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1)
                if (number != null) numbers.getOrPut(c.getLong(0)) { mutableListOf() }.add(number)
            }
        }

        val result = mutableListOf<ContactSummary>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED),
            "${Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL",
            null,
            "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                result += ContactSummary(
                    id = id,
                    name = c.getString(1),
                    photoUri = c.getString(2),
                    starred = c.getInt(3) == 1,
                    numbers = numbers[id].orEmpty(),
                )
            }
        }
        return result
    }

    private fun queryContact(id: Long): ContactDetail? {
        val contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id)
        var displayName = ""
        var photo: String? = null
        var starred = false
        var nameRawId = -1L
        resolver.query(
            contactUri,
            arrayOf(Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI, Contacts.STARRED, Contacts.NAME_RAW_CONTACT_ID),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            displayName = c.getString(0).orEmpty()
            photo = c.getString(1)
            starred = c.getInt(2) == 1
            nameRawId = c.getLong(3)
        } ?: return null

        var given = ""
        var family = ""
        val rawIds = linkedSetOf<Long>()
        val phones = mutableListOf<ContactField>()
        val emails = mutableListOf<ContactField>()
        val seenPhones = hashSetOf<String>()
        val seenEmails = hashSetOf<String>()

        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3),
            "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (?, ?, ?)",
            arrayOf(id.toString(), StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE),
            "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val rawId = c.getLong(0)
                rawIds += rawId
                when (c.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> if (rawId == nameRawId || (given.isEmpty() && family.isEmpty())) {
                        // DATA2 = GIVEN_NAME, DATA3 = FAMILY_NAME
                        given = c.getString(3).orEmpty()
                        family = c.getString(4).orEmpty()
                    }
                    Phone.CONTENT_ITEM_TYPE -> {
                        val number = c.getString(2)
                        if (number != null && seenPhones.add(PhoneNumberUtils.normalizeNumber(number))) {
                            phones += ContactField(number, c.getInt(3), c.getString(4))
                        }
                    }
                    Email.CONTENT_ITEM_TYPE -> {
                        val address = c.getString(2)
                        if (address != null && seenEmails.add(address.lowercase())) {
                            emails += ContactField(address, c.getInt(3), c.getString(4))
                        }
                    }
                }
            }
        }

        if (given.isEmpty() && family.isEmpty()) given = displayName
        if (nameRawId <= 0) nameRawId = rawIds.firstOrNull() ?: return null

        return ContactDetail(
            id = id,
            nameRawContactId = nameRawId,
            rawContactIds = rawIds.toList().ifEmpty { listOf(nameRawId) },
            displayName = displayName,
            givenName = given,
            familyName = family,
            photoUri = photo,
            starred = starred,
            phones = phones,
            emails = emails,
        )
    }

    /** Crea un contacto nuevo y devuelve su id, o null si falla. */
    suspend fun create(draft: ContactDraft): Long? = withContext(Dispatchers.IO) {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build()
        ops += dataOps(draft, rawContactId = null)

        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawId = results.firstOrNull()?.uri?.let(ContentUris::parseId) ?: return@withContext null
        resolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            arrayOf(RawContacts.CONTACT_ID), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    suspend fun update(existing: ContactDetail, draft: ContactDraft) = withContext(Dispatchers.IO) {
        val ops = arrayListOf<ContentProviderOperation>()
        // Nombre: sólo se reescribe en el raw contact que da nombre al contacto.
        ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
            .withSelection(
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(existing.nameRawContactId.toString(), StructuredName.CONTENT_ITEM_TYPE),
            ).build()
        // Teléfonos y correos: el formulario muestra la unión de todos, así que se sustituyen todos.
        existing.rawContactIds.forEach { rawId ->
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} IN (?, ?)",
                    arrayOf(rawId.toString(), Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE),
                ).build()
        }
        ops += dataOps(draft, rawContactId = existing.nameRawContactId)
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        Unit
    }

    private fun dataOps(draft: ContactDraft, rawContactId: Long?): List<ContentProviderOperation> {
        fun insert(mime: String, values: ContentValues): ContentProviderOperation {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            if (rawContactId == null) b.withValueBackReference(Data.RAW_CONTACT_ID, 0)
            else b.withValue(Data.RAW_CONTACT_ID, rawContactId)
            values.put(Data.MIMETYPE, mime)
            return b.withValues(values).build()
        }

        val ops = mutableListOf<ContentProviderOperation>()
        if (draft.givenName.isNotBlank() || draft.familyName.isNotBlank()) {
            ops += insert(StructuredName.CONTENT_ITEM_TYPE, ContentValues().apply {
                put(StructuredName.GIVEN_NAME, draft.givenName.trim())
                put(StructuredName.FAMILY_NAME, draft.familyName.trim())
            })
        }
        draft.phones.filter { it.value.isNotBlank() }.forEach {
            ops += insert(Phone.CONTENT_ITEM_TYPE, ContentValues().apply {
                put(Phone.NUMBER, it.value.trim())
                put(Phone.TYPE, it.type)
                if (it.type == Phone.TYPE_CUSTOM) put(Phone.LABEL, it.label)
            })
        }
        draft.emails.filter { it.value.isNotBlank() }.forEach {
            ops += insert(Email.CONTENT_ITEM_TYPE, ContentValues().apply {
                put(Email.ADDRESS, it.value.trim())
                put(Email.TYPE, it.type)
                if (it.type == Email.TYPE_CUSTOM) put(Email.LABEL, it.label)
            })
        }
        return ops
    }

    suspend fun setStarred(id: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        resolver.update(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) },
            null, null,
        )
        Unit
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        resolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), null, null)
        Unit
    }
}
