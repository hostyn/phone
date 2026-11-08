package dev.hostyn.contactos.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe los botones de la notificación (responder, rechazar, colgar). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_CALL_ID, -1)
        when (intent.action) {
            ACTION_ANSWER -> CallManager.answer(id)
            ACTION_DECLINE -> CallManager.reject(id)
            ACTION_HANG_UP -> CallManager.hangUp(id)
        }
    }

    companion object {
        const val ACTION_ANSWER = "dev.hostyn.contactos.ANSWER"
        const val ACTION_DECLINE = "dev.hostyn.contactos.DECLINE"
        const val ACTION_HANG_UP = "dev.hostyn.contactos.HANG_UP"
        const val EXTRA_CALL_ID = "call_id"
    }
}
