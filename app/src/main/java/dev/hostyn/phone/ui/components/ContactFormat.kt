package dev.hostyn.phone.ui.components

import android.accounts.AccountManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import dev.hostyn.phone.R
import dev.hostyn.phone.data.ContactAccount
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

/** Date of a contact event. The provider stores `yyyy-MM-dd`, or `--MM-dd` when the year is unknown. */
data class EventDate(val year: Int?, val month: Int, val day: Int) {
    fun toStorage(): String =
        if (year == null) "--%02d-%02d".format(Locale.ROOT, month, day)
        else "%04d-%02d-%02d".format(Locale.ROOT, year, month, day)

    fun format(locale: Locale = Locale.getDefault()): String {
        val cal = Calendar.getInstance().apply { clear(); set(year ?: 2000, month - 1, day) }
        val pattern = DateFormat.getBestDateTimePattern(locale, if (year == null) "MMMMd" else "yMMMMd")
        return java.text.SimpleDateFormat(pattern, locale).format(cal.time)
    }

    /** UTC midnight, as the Material date picker expects. */
    fun utcMillis(): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(year ?: 2000, month - 1, day)
    }.timeInMillis

    companion object {
        private val full = Regex("""^(\d{4})-?(\d{2})-?(\d{2})""")
        private val noYear = Regex("""^--(\d{2})-?(\d{2})""")

        /** Parses the formats sync adapters write: `yyyy-MM-dd`, `yyyyMMdd`, `--MM-dd`, with or without a time. */
        fun parse(value: String): EventDate? {
            val v = value.trim()
            noYear.find(v)?.let { m ->
                return valid(null, m.groupValues[1].toInt(), m.groupValues[2].toInt())
            }
            full.find(v)?.let { m ->
                return valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
            return null
        }

        fun fromUtcMillis(millis: Long, withYear: Boolean): EventDate {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
            return EventDate(
                if (withYear) cal.get(Calendar.YEAR) else null,
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }

        private fun valid(year: Int?, month: Int, day: Int) = if (month in 1..12 && day in 1..31) EventDate(year, month, day) else null
    }
}

/** Event value for display: formatted when it's a date we understand, as stored otherwise. */
fun formatEventDate(value: String): String = EventDate.parse(value)?.format() ?: value

/** Loads [uri] as a square JPEG of at most [size] px, for the contact's display photo. */
fun loadContactPhoto(context: Context, uri: Uri, size: Int = 720): ByteArray? = try {
    val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val shortSide = min(info.size.width, info.size.height)
            var sample = 1
            while (shortSide / (sample * 2) >= size) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val shortSide = min(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (shortSide / (sample * 2) >= size) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
    bitmap?.let { b ->
        val side = min(b.width, b.height)
        val cropped = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        val target = min(side, size)
        val scaled = if (target == side) cropped else Bitmap.createScaledBitmap(cropped, target, target, true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }
} catch (_: Exception) {
    null
}

/** Name and account type label to show for an account. */
fun accountTitle(context: Context, account: ContactAccount): Pair<String, String?> {
    if (account.isLocal) return context.getString(R.string.device_account) to null
    val typeLabel = try {
        AccountManager.get(context).authenticatorTypes.firstOrNull { it.type == account.type }?.let { d ->
            context.packageManager.getResourcesForApplication(d.packageName).getString(d.labelId)
        }
    } catch (_: Exception) {
        null
    }
    return account.name.orEmpty() to (typeLabel ?: account.type)
}

/** The icon of the app an account belongs to (Atlas, Google…); null for the device. */
fun accountIcon(context: Context, account: ContactAccount): Drawable? {
    if (account.isLocal) return null
    return try {
        AccountManager.get(context).authenticatorTypes.firstOrNull { it.type == account.type }?.let { d ->
            context.packageManager.getDrawable(d.packageName, d.iconId, null)
        }
    } catch (_: Exception) {
        null
    }
}
