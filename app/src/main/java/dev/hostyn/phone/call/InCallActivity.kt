package dev.hostyn.phone.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hostyn.phone.ui.theme.PhoneTheme

/** In-call screen. Also shown over the lock screen. */
class InCallActivity : ComponentActivity() {

    private val proximityLock: PowerManager.WakeLock? by lazy {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "phone:proximity")
        } else null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }

        setContent {
            PhoneTheme {
                InCallRoute(
                    onFinish = ::finishAndRemoveTask,
                    onProximity = ::setProximity,
                    onKeepScreenOn = ::setKeepScreenOn,
                )
            }
        }
    }

    private fun setProximity(on: Boolean) {
        val lock = proximityLock ?: return
        if (on && !lock.isHeld) lock.acquire(3 * 60 * 60 * 1000L)
        else if (!on && lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
    }

    private fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
