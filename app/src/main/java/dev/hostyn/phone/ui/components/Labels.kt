package dev.hostyn.phone.ui.components

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import dev.hostyn.phone.R
import dev.hostyn.phone.data.ContactField

/** Phone number types offered in the editor, with their label resource. */
val PhoneTypes = listOf(
    Phone.TYPE_MOBILE to R.string.phone_type_mobile,
    Phone.TYPE_HOME to R.string.phone_type_home,
    Phone.TYPE_WORK to R.string.phone_type_work,
    Phone.TYPE_MAIN to R.string.phone_type_main,
    Phone.TYPE_OTHER to R.string.phone_type_other,
)

/** Email types offered in the editor, with their label resource. */
val EmailTypes = listOf(
    Email.TYPE_HOME to R.string.email_type_personal,
    Email.TYPE_WORK to R.string.email_type_work,
    Email.TYPE_OTHER to R.string.email_type_other,
)

fun ContactField.phoneLabel(context: Context): String =
    if (type == Phone.TYPE_CUSTOM && !label.isNullOrBlank()) label
    else context.getString(PhoneTypes.firstOrNull { it.first == type }?.second ?: R.string.phone)

fun ContactField.emailLabel(context: Context): String =
    if (type == Email.TYPE_CUSTOM && !label.isNullOrBlank()) label
    else context.getString(EmailTypes.firstOrNull { it.first == type }?.second ?: R.string.email)
