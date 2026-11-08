package dev.hostyn.phone.data

import android.content.Context
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog.Calls
import dev.hostyn.phone.ui.components.phoneLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/** A Recents row: consecutive calls from the same number on the same day, grouped. */
data class RecentCall(
    val id: Long,
    val number: String,
    val type: Int,
    val date: Long,
    val count: Int,
    /** Call log ids of every call grouped in this row. */
    val ids: List<Long>,
    val contactId: Long?,
    val name: String?,
    val photoUri: String?,
    val numberLabel: String?,
) {
    val missed get() = type == Calls.MISSED_TYPE
}

class CallLogRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver


    fun observe(): Flow<List<RecentCall>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Calls.CONTENT_URI, true, observer)
        // Names depend on contacts: refresh when they change too.
        resolver.registerContentObserver(android.provider.ContactsContract.Contacts.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().map { query() }.flowOn(Dispatchers.IO)

    private data class Raw(val id: Long, val number: String, val type: Int, val date: Long)

    private fun query(): List<RecentCall> {
        val raw = mutableListOf<Raw>()
        try {
            resolver.query(
                Calls.CONTENT_URI,
                arrayOf(Calls._ID, Calls.NUMBER, Calls.TYPE, Calls.DATE, Calls.NUMBER_PRESENTATION),
                null, null, "${Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && raw.size < 500) {
                    val hidden = c.getInt(4) != Calls.PRESENTATION_ALLOWED
                    raw += Raw(c.getLong(0), if (hidden) "" else c.getString(1).orEmpty(), c.getInt(2), c.getLong(3))
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }

        val zone = ZoneId.systemDefault()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        val cache = HashMap<String, CallerInfo?>()
        fun info(number: String): CallerInfo? =
            if (cache.containsKey(number)) cache[number]
            else resolver.lookupNumber(number).also { cache[number] = it }

        val out = mutableListOf<RecentCall>()
        for (r in raw) {
            val last = out.lastOrNull()
            if (last != null && samePhone(last.number, r.number) && day(last.date) == day(r.date)) {
                out[out.lastIndex] = last.copy(count = last.count + 1, ids = last.ids + r.id)
            } else {
                val i = info(r.number)
                out += RecentCall(
                    id = r.id,
                    number = r.number,
                    type = r.type,
                    date = r.date,
                    count = 1,
                    ids = listOf(r.id),
                    contactId = i?.contactId,
                    name = i?.name,
                    photoUri = i?.photoUri,
                    numberLabel = i?.let { ContactField(r.number, it.type, it.label).phoneLabel(context) },
                )
            }
        }
        return out
    }

    /** Deletes calls from the history. */
    fun delete(ids: List<Long>): Boolean = try {
        if (ids.isNotEmpty()) {
            resolver.delete(Calls.CONTENT_URI, "${Calls._ID} IN (${ids.joinToString(",")})", null)
        }
        true
    } catch (_: Exception) {
        false
    }

    fun clearAll(): Boolean = try {
        resolver.delete(Calls.CONTENT_URI, null, null)
        true
    } catch (_: Exception) {
        false
    }
}

/** A missed call nobody has looked at yet. */
data class MissedCall(val number: String, val date: Long, val info: CallerInfo?)

/** Missed calls the user hasn't seen yet, newest first. */
fun ContentResolver.newMissedCalls(): List<MissedCall> {
    val out = mutableListOf<MissedCall>()
    try {
        query(
            Calls.CONTENT_URI,
            arrayOf(Calls.NUMBER, Calls.DATE, Calls.NUMBER_PRESENTATION),
            "${Calls.TYPE} = ? AND ${Calls.NEW} = 1",
            arrayOf(Calls.MISSED_TYPE.toString()),
            "${Calls.DATE} DESC",
        )?.use { c ->
            val cache = HashMap<String, CallerInfo?>()
            while (c.moveToNext() && out.size < 50) {
                val hidden = c.getInt(2) != Calls.PRESENTATION_ALLOWED
                val number = if (hidden) "" else c.getString(0).orEmpty()
                val info = cache.getOrPut(number) { lookupNumber(number) }
                out += MissedCall(number, c.getLong(1), info)
            }
        }
    } catch (_: SecurityException) {
    }
    return out
}

/** Marks missed calls as seen, so they don't show up in the notification again. */
fun ContentResolver.markMissedCallsRead() {
    try {
        update(
            Calls.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Calls.NEW, 0)
                put(Calls.IS_READ, 1)
            },
            "${Calls.TYPE} = ? AND (${Calls.NEW} = 1 OR ${Calls.IS_READ} = 0)",
            arrayOf(Calls.MISSED_TYPE.toString()),
        )
    } catch (_: Exception) {
    }
}
