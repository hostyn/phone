package dev.hostyn.phone.ui.components

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import dev.hostyn.phone.R
import dev.hostyn.phone.data.ContactField
import dev.hostyn.phone.data.ImField
import dev.hostyn.phone.data.PostalAddress

/**
 * Type labels come from the framework (they're translated by the system and match what other
 * contacts apps show), except websites, which have no framework labels.
 */
enum class FieldKind(val types: List<Int>, val custom: Int) {
    Phones(
        listOf(
            Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN, Phone.TYPE_FAX_WORK,
            Phone.TYPE_FAX_HOME, Phone.TYPE_PAGER, Phone.TYPE_OTHER, Phone.TYPE_CALLBACK, Phone.TYPE_CAR,
            Phone.TYPE_COMPANY_MAIN, Phone.TYPE_ISDN, Phone.TYPE_OTHER_FAX, Phone.TYPE_RADIO, Phone.TYPE_TELEX,
            Phone.TYPE_TTY_TDD, Phone.TYPE_WORK_MOBILE, Phone.TYPE_WORK_PAGER, Phone.TYPE_ASSISTANT, Phone.TYPE_MMS,
        ),
        Phone.TYPE_CUSTOM,
    ),
    Emails(listOf(Email.TYPE_HOME, Email.TYPE_WORK, Email.TYPE_OTHER, Email.TYPE_MOBILE), Email.TYPE_CUSTOM),
    Postal(listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER), StructuredPostal.TYPE_CUSTOM),
    Events(listOf(Event.TYPE_BIRTHDAY, Event.TYPE_ANNIVERSARY, Event.TYPE_OTHER), Event.TYPE_CUSTOM),
    Relations(
        listOf(
            Relation.TYPE_ASSISTANT, Relation.TYPE_BROTHER, Relation.TYPE_CHILD, Relation.TYPE_DOMESTIC_PARTNER,
            Relation.TYPE_FATHER, Relation.TYPE_FRIEND, Relation.TYPE_MANAGER, Relation.TYPE_MOTHER,
            Relation.TYPE_PARENT, Relation.TYPE_PARTNER, Relation.TYPE_REFERRED_BY, Relation.TYPE_RELATIVE,
            Relation.TYPE_SISTER, Relation.TYPE_SPOUSE,
        ),
        Relation.TYPE_CUSTOM,
    ),
    Websites(
        listOf(
            Website.TYPE_HOMEPAGE, Website.TYPE_BLOG, Website.TYPE_PROFILE, Website.TYPE_HOME,
            Website.TYPE_WORK, Website.TYPE_FTP, Website.TYPE_OTHER,
        ),
        Website.TYPE_CUSTOM,
    ),
    Sips(listOf(SipAddress.TYPE_HOME, SipAddress.TYPE_WORK, SipAddress.TYPE_OTHER), SipAddress.TYPE_CUSTOM);

    /** Label of [type], or [label] for the custom type. */
    fun label(context: Context, type: Int, label: String?): String {
        if (type == custom && !label.isNullOrBlank()) return label
        val res = context.resources
        return when (this) {
            Phones -> Phone.getTypeLabel(res, type, label)
            Emails -> Email.getTypeLabel(res, type, label)
            Postal -> StructuredPostal.getTypeLabel(res, type, label)
            Events -> res.getString(Event.getTypeResource(type))
            Relations -> Relation.getTypeLabel(res, type, label)
            Sips -> SipAddress.getTypeLabel(res, type, label)
            Websites -> context.getString(
                when (type) {
                    Website.TYPE_HOMEPAGE -> R.string.website_homepage
                    Website.TYPE_BLOG -> R.string.website_blog
                    Website.TYPE_PROFILE -> R.string.website_profile
                    Website.TYPE_HOME -> R.string.website_home
                    Website.TYPE_WORK -> R.string.website_work
                    Website.TYPE_FTP -> R.string.website_ftp
                    Website.TYPE_CUSTOM -> R.string.custom_label
                    else -> R.string.website_other
                },
            )
        }.toString()
    }

    /** Name of the custom type in the type menu. */
    fun customLabel(context: Context): String = context.getString(R.string.custom_label)
}

@Suppress("DEPRECATION")
val ImProtocols = listOf(
    Im.PROTOCOL_AIM, Im.PROTOCOL_MSN, Im.PROTOCOL_YAHOO, Im.PROTOCOL_SKYPE, Im.PROTOCOL_QQ,
    Im.PROTOCOL_GOOGLE_TALK, Im.PROTOCOL_ICQ, Im.PROTOCOL_JABBER, Im.PROTOCOL_NETMEETING,
)

@Suppress("DEPRECATION")
fun imProtocolLabel(context: Context, protocol: Int, custom: String?): String =
    if (protocol == Im.PROTOCOL_CUSTOM && !custom.isNullOrBlank()) custom
    else if (protocol == Im.PROTOCOL_CUSTOM) context.getString(R.string.custom_label)
    else Im.getProtocolLabel(context.resources, protocol, custom).toString()

fun ImField.protocolLabel(context: Context) = imProtocolLabel(context, protocol, customProtocol)

fun ContactField.phoneLabel(context: Context): String = FieldKind.Phones.label(context, type, label)

fun ContactField.emailLabel(context: Context): String = FieldKind.Emails.label(context, type, label)

fun PostalAddress.typeLabel(context: Context): String = FieldKind.Postal.label(context, type, label)
