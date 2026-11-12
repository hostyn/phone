package dev.hostyn.phone.call

import android.content.Context
import android.os.PowerManager
import android.telecom.CallAudioState

/**
 * Turns the screen off while the phone is held to the ear, so the cheek doesn't press buttons.
 * Held by the call service, not the in-call screen: it must work too when the call was answered
 * from the notification and the in-call screen isn't open.
 */
class ProximityLock(context: Context) {
    private val lock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)
        .takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
        ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "phone:proximity")

    fun update(calls: List<CallUi>, audio: CallAudioState?) {
        set(shouldHold(CallManager.primary(calls), audio))
    }

    fun release() = set(false)

    private fun set(on: Boolean) {
        val lock = lock ?: return
        if (on && !lock.isHeld) lock.acquire(3 * 60 * 60 * 1000L)
        // Released near the ear (the call ended), the screen stays off until the phone is moved away.
        else if (!on && lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    companion object {
        /** While talking or dialing through the earpiece (or a wired headset), not on speaker or Bluetooth. */
        fun shouldHold(call: CallUi?, audio: CallAudioState?): Boolean {
            if (call == null || (call.phase != CallPhase.ACTIVE && call.phase != CallPhase.DIALING)) return false
            val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
            return route == CallAudioState.ROUTE_EARPIECE || route == CallAudioState.ROUTE_WIRED_HEADSET
        }
    }
}
