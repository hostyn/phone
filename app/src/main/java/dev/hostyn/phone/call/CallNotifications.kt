package dev.hostyn.phone.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.telecom.CallAudioState
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import dev.hostyn.phone.R

object CallNotifications {
    const val ID = 4242
    private const val CHANNEL_INCOMING = "incoming_calls"
    private const val CHANNEL_ONGOING = "ongoing_calls"

    /**
     * The call answered from the heads-up notification. Its ongoing notification keeps floating
     * (until the user swipes it up) instead of hiding in the status bar.
     */
    @Volatile
    var headsUpCallId: Int? = null

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // The system (Telecom) already plays the ringtone and vibrates: channels are silent.
        val incoming = NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.channel_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_ongoing), NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val missed = NotificationChannel(MissedCallNotifier.CHANNEL, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT).apply {
            setShowBadge(true)
        }
        nm.createNotificationChannels(listOf(incoming, ongoing, missed))
    }

    fun build(context: Context, call: CallUi, photo: Bitmap?, audio: CallAudioState?): Notification {
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
        val headsUp = !ringing && call.id == headsUpCallId
        val builder = NotificationCompat.Builder(context, if (ringing || headsUp) CHANNEL_INCOMING else CHANNEL_ONGOING)
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
                .setContentText(call.label ?: context.getString(R.string.incoming_call))
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
                .setContentText(
                    when (call.phase) {
                        CallPhase.ACTIVE -> context.getString(R.string.ongoing_call)
                        CallPhase.HOLDING -> context.getString(R.string.on_hold)
                        CallPhase.SELECT_ACCOUNT -> context.getString(R.string.choose_sim)
                        else -> context.getString(R.string.calling)
                    }
                )
            if (call.phase == CallPhase.ACTIVE && call.connectTime > 0) {
                builder.setUsesChronometer(true).setShowWhen(true).setWhen(call.connectTime)
            }
            speakerAction(context, call.id, audio)?.let(builder::addAction)
            if (headsUp) {
                // A heads-up with a full-screen intent is "sticky": Android keeps it floating
                // until it's swiped up, and it doesn't come back on later updates (alert once).
                // Its channel is the silent, high-importance one of incoming calls.
                builder
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setFullScreenIntent(openCall, true)
            } else {
                // setSilent() would also keep the notification from floating, so only here.
                builder.setSilent(true)
            }
        }
        return builder.build()
    }

    /** Speaker on, or back to Bluetooth, the wired headset or the earpiece. */
    private fun speakerAction(context: Context, callId: Int, audio: CallAudioState?): NotificationCompat.Action? {
        val supported = audio?.supportedRouteMask ?: (CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)
        // Only the speaker (or none): nothing to switch between.
        if (supported and CallAudioState.ROUTE_SPEAKER == 0 || supported == CallAudioState.ROUTE_SPEAKER) return null
        val onSpeaker = audio?.route == CallAudioState.ROUTE_SPEAKER
        val (icon, label) = when {
            !onSpeaker -> R.drawable.ic_speaker to R.string.speaker
            supported and CallAudioState.ROUTE_BLUETOOTH != 0 -> R.drawable.ic_speaker_off to R.string.bluetooth
            supported and CallAudioState.ROUTE_WIRED_HEADSET != 0 -> R.drawable.ic_speaker_off to R.string.headset
            else -> R.drawable.ic_speaker_off to R.string.speaker_off
        }
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, icon),
            context.getString(label),
            action(context, CallActionReceiver.ACTION_SPEAKER, callId),
        ).build()
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
