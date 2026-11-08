package dev.hostyn.contactos

import android.app.Application
import dev.hostyn.contactos.call.CallNotifications
import dev.hostyn.contactos.data.CallLogRepository
import dev.hostyn.contactos.data.ContactsRepository

class ContactsApp : Application() {
    val repository by lazy { ContactsRepository(contentResolver) }
    val callLog by lazy { CallLogRepository(contentResolver) }

    override fun onCreate() {
        super.onCreate()
        CallNotifications.createChannels(this)
    }
}
