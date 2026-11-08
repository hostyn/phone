package dev.hostyn.contactos.ui.components

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import dev.hostyn.contactos.data.ContactField

val PhoneTypes = listOf(
    Phone.TYPE_MOBILE to "Móvil",
    Phone.TYPE_HOME to "Casa",
    Phone.TYPE_WORK to "Trabajo",
    Phone.TYPE_MAIN to "Principal",
    Phone.TYPE_OTHER to "Otro",
)

val EmailTypes = listOf(
    Email.TYPE_HOME to "Personal",
    Email.TYPE_WORK to "Trabajo",
    Email.TYPE_OTHER to "Otro",
)

fun ContactField.phoneLabel(): String =
    if (type == Phone.TYPE_CUSTOM && !label.isNullOrBlank()) label
    else PhoneTypes.firstOrNull { it.first == type }?.second ?: "Teléfono"

fun ContactField.emailLabel(): String =
    if (type == Email.TYPE_CUSTOM && !label.isNullOrBlank()) label
    else EmailTypes.firstOrNull { it.first == type }?.second ?: "Correo"
