package dev.hostyn.phone.call

import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The system binds this service when we are the default phone app.
 * While there is a call, it stays in the foreground with the call notification
 * (CallStyle), which the user can't dismiss.
 */
class CallService : InCallService() {
    private val scope = MainScope()
    private var foreground = false
    private var lastKey: Any? = null

    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
        CallNotifications.createChannels(this)
        scope.launch {
            CallManager.calls.collectLatest { calls ->
                // Post the call notification once we know who is calling. Android doesn't apply
                // updates to a ringing call notification right away (the heads-up kept showing
                // the number while the name was already known), so the first version must be
                // right. The lookup takes a few milliseconds; collectLatest cancels this wait as
                // soon as its result arrives, and the timeout keeps a slow lookup from delaying
                // the call.
                if (CallManager.primary(calls)?.identifying == true) delay(IDENTIFY_TIMEOUT_MS)
                updateNotification(calls)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        removeNotification()
        CallManager.detach(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        val state = call.stateCompat()
        if (state != Call.STATE_RINGING && state != Call.STATE_SIMULATED_RINGING) {
            // Outgoing call: show the in-call screen right away.
            try {
                startActivity(InCallActivity.intent(this))
            } catch (e: Exception) {
                Log.w(TAG, "Could not open the in-call screen", e)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        if (audioState != null) CallManager.onAudioState(audioState)
    }

    private fun updateNotification(calls: List<CallUi>) {
        val call = CallManager.primary(calls)?.takeIf { it.phase != CallPhase.ENDED }
        if (call == null) {
            removeNotification()
            return
        }
        val photo = CallManager.photoFor(call.id)
        val key = listOf(call.id, call.phase, call.name, call.label, call.connectTime, photo != null)
        if (key == lastKey) return
        lastKey = key

        val notification = CallNotifications.build(this, call, photo)
        if (!foreground) {
            try {
                ServiceCompat.startForeground(
                    this, CallNotifications.ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                )
                foreground = true
                return
            } catch (e: Exception) {
                Log.w(TAG, "startForeground failed; posting as a regular notification", e)
            }
        }
        try {
            getSystemService(NotificationManager::class.java).notify(CallNotifications.ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post the call notification", e)
        }
    }

    private fun removeNotification() {
        lastKey = null
        if (foreground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        getSystemService(NotificationManager::class.java).cancel(CallNotifications.ID)
    }

    private companion object {
        const val TAG = "CallService"
        const val IDENTIFY_TIMEOUT_MS = 1000L
    }
}
