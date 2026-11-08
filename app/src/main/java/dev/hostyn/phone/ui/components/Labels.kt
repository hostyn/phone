package dev.hostyn.phone.ui.components

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import dev.hostyn.phone.data.ContactField

val PhoneTypes = listOf(
    Phone.TYPE_MOBILE to "Mobile",
    Phone.TYPE_HOME to "Home",
    Phone.TYPE_WORK to "Work",
    Phone.TYPE_MAIN to "Main",
    Phone.TYPE_OTHER to "Other",
)

val EmailTypes = listOf(
    Email.TYPE_HOME to "Personal",
    Email.TYPE_WORK to "Work",
    Email.TYPE_OTHER to "Other",
)

fun ContactField.phoneLabel(): String =
    if (type == Phone.TYPE_CUSTOM && !label.isNullOrBlank()) label
    else PhoneTypes.firstOrNull { it.first == type }?.second ?: "Phone"

fun ContactField.emailLabel(): String =
    if (type == Email.TYPE_CUSTOM && !label.isNullOrBlank()) label
    else EmailTypes.firstOrNull { it.first == type }?.second ?: "Email"
