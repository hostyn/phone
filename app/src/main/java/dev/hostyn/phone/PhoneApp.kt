package dev.hostyn.phone

import android.app.Application
import dev.hostyn.phone.call.CallNotifications
import dev.hostyn.phone.data.BlockedNumbersRepository
import dev.hostyn.phone.data.CallLogRepository
import dev.hostyn.phone.data.ContactsRepository

class PhoneApp : Application() {
    val repository by lazy { ContactsRepository(contentResolver) }
    val callLog by lazy { CallLogRepository(this) }
    val blocked by lazy { BlockedNumbersRepository(this) }

    override fun onCreate() {
        super.onCreate()
        CallNotifications.createChannels(this)
    }
}
