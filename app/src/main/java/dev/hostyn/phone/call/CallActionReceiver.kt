package dev.hostyn.phone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.hostyn.phone.ui.components.placeCall

/** Handles the notification buttons (answer, decline, hang up). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_CALL_ID, -1)
        when (intent.action) {
            ACTION_ANSWER -> CallManager.answer(id)
            ACTION_DECLINE -> CallManager.reject(id)
            ACTION_HANG_UP -> CallManager.hangUp(id)
            ACTION_CALL_BACK -> {
                MissedCallNotifier.clear(context)
                intent.getStringExtra(EXTRA_NUMBER)?.let { context.placeCall(it) }
            }
            ACTION_CLEAR_MISSED -> MissedCallNotifier.clear(context)
        }
    }

    companion object {
        const val ACTION_ANSWER = "dev.hostyn.phone.ANSWER"
        const val ACTION_DECLINE = "dev.hostyn.phone.DECLINE"
        const val ACTION_HANG_UP = "dev.hostyn.phone.HANG_UP"
        const val ACTION_CALL_BACK = "dev.hostyn.phone.CALL_BACK"
        const val ACTION_CLEAR_MISSED = "dev.hostyn.phone.CLEAR_MISSED"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_NUMBER = "number"
    }
}
