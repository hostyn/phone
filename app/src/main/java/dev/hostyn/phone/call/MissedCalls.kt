package dev.hostyn.phone.call

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import dev.hostyn.phone.MainActivity
import dev.hostyn.phone.R
import dev.hostyn.phone.data.MissedCall
import dev.hostyn.phone.data.formatPhone
import dev.hostyn.phone.data.loadContactPhoto
import dev.hostyn.phone.data.markMissedCallsRead
import dev.hostyn.phone.data.newMissedCalls
import kotlin.concurrent.thread

/**
 * When we are the default phone app, Telecom doesn't show its own missed calls notification:
 * it sends us this broadcast with the count and we show ours.
 */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val count = intent.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0)
        val number = intent.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
        if (count <= 0) {
            MissedCallNotifier.cancel(context)
            return
        }
        val pending = goAsync()
        thread {
            try {
                MissedCallNotifier.show(context.applicationContext, count, number)
            } finally {
                pending.finish()
            }
        }
    }
}

object MissedCallNotifier {
    const val ID = 4243
    const val CHANNEL = "missed_calls"
    const val ACTION_SHOW_RECENTS = "dev.hostyn.phone.SHOW_RECENTS"

    /** Builds the notification from the call log (runs off the main thread). */
    fun show(context: Context, telecomCount: Int, telecomNumber: String?) {
        val calls = context.contentResolver.newMissedCalls().ifEmpty {
            // The call log may not be written yet: fall back to what Telecom told us.
            listOf(MissedCall(telecomNumber.orEmpty(), System.currentTimeMillis(), null))
        }
        val count = maxOf(calls.size, telecomCount)
        val latest = calls.first()

        val openRecents = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_SHOW_RECENTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_CLEAR_MISSED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_missed)
            .setColor(context.getColor(R.color.missed_call))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(openRecents)
            .setDeleteIntent(dismiss)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setWhen(latest.date)
            .setNumber(count)
            .setOnlyAlertOnce(false)

        val sameCaller = calls.all { it.number == latest.number }
        if (count == 1 || sameCaller) {
            val name = displayName(context, latest)
            val photo = latest.info?.let { context.contentResolver.loadContactPhoto(it.contactId) }
                ?: avatarBitmap(context, name, latest.info?.contactId, latest.number)
            builder
                .setContentTitle(name)
                .setContentText(
                    if (count == 1) context.getString(R.string.missed_call)
                    else context.resources.getQuantityString(R.plurals.missed_calls_count, count, count)
                )
                .setLargeIcon(photo)
                .addPerson(Person.Builder().setName(name).setIcon(IconCompat.createWithBitmap(photo)).build())
            if (latest.number.isNotBlank()) {
                builder.addAction(
                    R.drawable.ic_stat_call,
                    context.getString(R.string.call_back),
                    PendingIntent.getBroadcast(
                        context, 3,
                        Intent(context, CallActionReceiver::class.java)
                            .setAction(CallActionReceiver.ACTION_CALL_BACK)
                            .putExtra(CallActionReceiver.EXTRA_NUMBER, latest.number),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                builder.addAction(
                    R.drawable.ic_stat_message,
                    context.getString(R.string.message),
                    PendingIntent.getActivity(
                        context, 4,
                        Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", latest.number, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
        } else {
            val names = calls.map { displayName(context, it) }.distinct()
            val style = NotificationCompat.InboxStyle()
            calls.take(6).forEach { c ->
                val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(c.date))
                style.addLine("${displayName(context, c)} · $time")
            }
            builder
                .setContentTitle(context.resources.getQuantityString(R.plurals.missed_calls_count, count, count))
                .setContentText(names.joinToString(", "))
                .setStyle(style)
        }

        try {
            context.getSystemService(NotificationManager::class.java).notify(ID, builder.build())
        } catch (e: SecurityException) {
            Log.w("MissedCalls", "Missing notification permission", e)
        }
    }

    private fun displayName(context: Context, call: MissedCall): String =
        call.info?.name
            ?: call.number.takeIf { it.isNotBlank() }?.let { formatPhone(context, it) }
            ?: context.getString(R.string.private_number)

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    /** The user has seen their missed calls: mark them as read and remove the notification. */
    fun clear(context: Context) {
        cancel(context)
        val app = context.applicationContext
        thread {
            app.contentResolver.markMissedCallsRead()
            try {
                app.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification()
            } catch (_: Exception) {
            }
        }
    }
}
