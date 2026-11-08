package dev.hostyn.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the notification buttons (answer, decline, hang up). */
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
        const val ACTION_ANSWER = "dev.hostyn.phone.ANSWER"
        const val ACTION_DECLINE = "dev.hostyn.phone.DECLINE"
        const val ACTION_HANG_UP = "dev.hostyn.phone.HANG_UP"
        const val EXTRA_CALL_ID = "call_id"
    }
}
