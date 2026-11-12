package dev.hostyn.phone.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/** Contact matching a phone number. */
data class CallerInfo(
    val contactId: Long,
    val name: String,
    val photoUri: String?,
    val type: Int,
    val label: String?,
)

fun ContentResolver.lookupNumber(number: String): CallerInfo? {
    if (number.isBlank()) return null
    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    return try {
        query(
            uri,
            arrayOf(PhoneLookup._ID, PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_THUMBNAIL_URI, PhoneLookup.TYPE, PhoneLookup.LABEL),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) CallerInfo(c.getLong(0), c.getString(1) ?: number, c.getString(2), c.getInt(3), c.getString(4))
            else null
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
