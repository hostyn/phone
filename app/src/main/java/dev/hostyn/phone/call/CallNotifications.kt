package dev.hostyn.phone.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import dev.hostyn.phone.R

object CallNotifications {
    const val ID = 4242
    private const val CHANNEL_INCOMING = "incoming_calls"
    private const val CHANNEL_ONGOING = "ongoing_calls"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // The system (Telecom) already plays the ringtone and vibrates: channels are silent.
        val incoming = NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(incoming, ongoing))
    }

    fun build(context: Context, call: CallUi, photo: Bitmap?): Notification {
        val person = Person.Builder()
            .setName(call.name)
            .setImportant(true)
            .setIcon(IconCompat.createWithBitmap(photo ?: avatarBitmap(context, call)))
            .build()

        val openCall = PendingIntent.getActivity(
            context, 0, InCallActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val ringing = call.phase == CallPhase.RINGING
        val builder = NotificationCompat.Builder(context, if (ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentIntent(openCall)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (ringing) {
            builder
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        action(context, CallActionReceiver.ACTION_DECLINE, call.id),
                        action(context, CallActionReceiver.ACTION_ANSWER, call.id),
                    )
                )
                .setContentText(call.label ?: "Incoming call")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                // Locked/off screen: opens the call full screen.
                // Screen on and unlocked: Android shows it as a heads-up notification.
                .setFullScreenIntent(openCall, true)
        } else {
            builder
                .setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(
                        person,
                        action(context, CallActionReceiver.ACTION_HANG_UP, call.id),
                    )
                )
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentText(
                    when (call.phase) {
                        CallPhase.ACTIVE -> "Ongoing call"
                        CallPhase.HOLDING -> "On hold"
                        CallPhase.SELECT_ACCOUNT -> "Choose a SIM"
                        else -> "Calling…"
                    }
                )
            if (call.phase == CallPhase.ACTIVE && call.connectTime > 0) {
                builder.setUsesChronometer(true).setShowWhen(true).setWhen(call.connectTime)
            }
        }
        return builder.build()
    }

    private fun action(context: Context, action: String, callId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            callId * 10 + action.hashCode().mod(10),
            Intent(context, CallActionReceiver::class.java)
                .setAction(action)
                .putExtra(CallActionReceiver.EXTRA_CALL_ID, callId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
