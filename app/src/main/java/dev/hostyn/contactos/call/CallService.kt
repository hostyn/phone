package dev.hostyn.contactos.call

import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * El sistema enlaza este servicio cuando somos la app de teléfono predeterminada.
 * Mientras haya una llamada, se mantiene en primer plano con la notificación de llamada
 * (CallStyle), que el usuario no puede descartar.
 */
class CallService : InCallService() {
    private val scope = MainScope()
    private var foreground = false
    private var lastKey: Any? = null

    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
        CallNotifications.createChannels(this)
        scope.launch { CallManager.calls.collect { updateNotification(it) } }
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
            // Llamada saliente: mostrar directamente la pantalla de llamada.
            try {
                startActivity(InCallActivity.intent(this))
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo abrir la pantalla de llamada", e)
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
                Log.w(TAG, "startForeground falló; se publica como notificación normal", e)
            }
        }
        try {
            getSystemService(NotificationManager::class.java).notify(CallNotifications.ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso de notificaciones", e)
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
    }
}
