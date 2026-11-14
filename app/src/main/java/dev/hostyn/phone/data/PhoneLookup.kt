package dev.hostyn.phone.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.PhoneLookup
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import dev.hostyn.phone.PhoneApp
import java.util.Locale

/** Contact matching a phone number. */
data class CallerInfo(
    val contactId: Long,
    /** The contact's name, or their nickname when the settings prefer it. */
    val name: String,
    /** Thumbnail (about 96 px), for lists. */
    val photoUri: String?,
    val type: Int,
    val label: String?,
    /** Full-size photo when the contact has one, else the thumbnail: for the call screen. */
    val largePhotoUri: String? = photoUri,
)

/** Whether names should show the contact's nickname instead (see [AppSettings.preferNicknames]). */
val Context.preferNicknames: Boolean get() = (applicationContext as PhoneApp).settings.preferNicknames

/** The contact's nickname ("Grandma"), if they have one. */
fun ContentResolver.nicknameOf(contactId: Long): String? = try {
    query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(Nickname.NAME),
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${Nickname.NAME} != ''",
        arrayOf(contactId.toString(), Nickname.CONTENT_ITEM_TYPE),
        "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC, ${ContactsContract.Data.IS_PRIMARY} DESC",
    )?.use { c -> if (c.moveToFirst()) c.getString(0)?.trim()?.takeIf { it.isNotEmpty() } else null }
} catch (e: Exception) {
    null
}

/**
 * The contact's own visible name ("Grandma Tere"), when its source gave one that is not just the
 * first name and surnames. Android shows the name it builds from those whenever a contact has them,
 * and keeps the source's display name (vCard FN) unused in the name row.
 */
fun customName(visible: String?, fullName: String?, fullNameAlt: String?): String? =
    visible?.trim()?.takeIf { v -> v.isNotEmpty() && listOf(fullName, fullNameAlt).none { it?.trim().equals(v, ignoreCase = true) } }

/** [customName] of a contact, read from the raw contact that names it. */
fun ContentResolver.customNameOf(contactId: Long): String? = try {
    query(
        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
        arrayOf(ContactsContract.Contacts.NAME_RAW_CONTACT_ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE),
        null, null, null,
    )?.use { c ->
        if (!c.moveToFirst()) return null
        val visible = query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(StructuredName.DISPLAY_NAME),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(c.getLong(0).toString(), StructuredName.CONTENT_ITEM_TYPE),
            null,
        )?.use { n -> if (n.moveToFirst()) n.getString(0) else null }
        customName(visible, c.getString(1), c.getString(2))
    }
} catch (e: Exception) {
    null
}

/**
 * The contact with this number, named by their own visible name if they have one ([customName]),
 * else by their nickname when [preferNickname] and they have one.
 */
fun ContentResolver.lookupNumber(number: String, preferNickname: Boolean = false): CallerInfo? {
    if (number.isBlank()) return null
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    return try {
        query(
            uri,
            arrayOf(PhoneLookup._ID, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.TYPE, PhoneLookup.LABEL, PhoneLookup.PHOTO_URI),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val id = c.getLong(0)
            val name = customNameOf(id) ?: (if (preferNickname) nicknameOf(id) else null) ?: c.getString(1) ?: number
            CallerInfo(id, name, c.getString(2), c.getInt(3), c.getString(4), c.getString(5) ?: c.getString(2))
        }
    } catch (e: Exception) {
        Log.w("PhoneLookup", "Could not look up a number", e)
        null
    }
}

fun ContentResolver.loadContactPhoto(contactId: Long): Bitmap? = try {
    val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
    ContactsContract.Contacts.openContactPhotoInputStream(this, uri, true)?.use { BitmapFactory.decodeStream(it) }
} catch (_: Exception) {
    null
}

private var cachedIso: String? = null

internal fun countryIso(context: Context): String = cachedIso ?: run {
    val tm = context.getSystemService(TelephonyManager::class.java)
    val iso = listOf(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
        .firstOrNull { !it.isNullOrBlank() } ?: "ES"
    iso.uppercase().also { cachedIso = it }
}

/** Formats a number for display ("600 12 34 56"). Returns it unchanged if it can't be formatted. */
fun formatPhone(context: Context, number: String): String {
    if (number.isBlank()) return number
    return PhoneNumberUtils.formatNumber(number, countryIso(context)) ?: number
}

fun samePhone(a: String, b: String): Boolean =
    if (a.isBlank() || b.isBlank()) a.isBlank() && b.isBlank() else PhoneNumberUtils.compare(a, b)
