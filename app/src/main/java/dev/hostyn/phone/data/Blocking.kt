package dev.hostyn.phone.data

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class BlockedNumber(val id: Long, val number: String)

/**
 * System block list (the same one Messages and the system dialer use).
 * Only the default phone or SMS app can read and change it.
 */
class BlockedNumbersRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun canBlock(): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    } catch (_: Exception) {
        false
    }

    fun observe(): Flow<List<BlockedNumber>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = try {
            resolver.registerContentObserver(BlockedNumbers.CONTENT_URI, true, observer)
            true
        } catch (_: Exception) {
            false
        }
        trySend(Unit)
        awaitClose { if (registered) resolver.unregisterContentObserver(observer) }
    }.conflate().map { query() }.flowOn(Dispatchers.IO)

    private fun query(): List<BlockedNumber> = try {
        val out = mutableListOf<BlockedNumber>()
        resolver.query(
            BlockedNumbers.CONTENT_URI,
            arrayOf(BlockedNumbers.COLUMN_ID, BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
            null, null, "${BlockedNumbers.COLUMN_ID} DESC",
        )?.use { c ->
            while (c.moveToNext()) out += BlockedNumber(c.getLong(0), c.getString(1).orEmpty())
        }
        out
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun block(numbers: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            numbers.filter { it.isNotBlank() }.forEach { n ->
                if (!BlockedNumberContract.isBlocked(context, n)) {
                    resolver.insert(BlockedNumbers.CONTENT_URI, ContentValues().apply {
                        put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, n)
                    })
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun unblock(numbers: List<String>): Boolean = withContext(Dispatchers.IO) {
        try {
            numbers.filter { it.isNotBlank() }.forEach { BlockedNumberContract.unblock(context, it) }
            true
        } catch (_: Exception) {
            false
        }
    }
}

fun List<BlockedNumber>.isBlocked(number: String): Boolean =
    number.isNotBlank() && any { samePhone(it.number, number) }
