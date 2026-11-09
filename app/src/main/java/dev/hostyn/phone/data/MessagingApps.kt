package dev.hostyn.phone.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneNumberUtils

/**
 * Chat apps that can open a conversation from a phone number. Only the installed ones are offered,
 * and each can be turned off in settings. [packages] are the app and its official variants, in
 * order of preference. They must also be listed in the manifest's <queries> to be seen.
 */
enum class MessagingApp(val packages: List<String>) {
    WhatsApp(listOf("com.whatsapp")),
    WhatsAppBusiness(listOf("com.whatsapp.w4b")),
    Telegram(listOf("org.telegram.messenger", "org.telegram.messenger.web")),
    Signal(listOf("org.thoughtcrime.securesms")),
    ;

    /** A link that opens a chat with [number], which must be international ("+34600123456"). */
    fun uri(number: String): Uri {
        val digits = number.removePrefix("+")
        return Uri.parse(
            when (this) {
                WhatsApp, WhatsAppBusiness -> "https://api.whatsapp.com/send?phone=$digits"
                Telegram -> "tg://resolve?phone=$digits"
                Signal -> "https://signal.me/#p/+$digits"
            },
        )
    }
}

/** An installed [MessagingApp], with what the system shows for it. */
class InstalledMessagingApp(val app: MessagingApp, val packageName: String, val label: String, val icon: android.graphics.drawable.Drawable) {
    fun open(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_VIEW, app.uri(internationalNumber(context, number)))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

fun installedMessagingApps(context: Context): List<InstalledMessagingApp> {
    val pm = context.packageManager
    return MessagingApp.entries.mapNotNull { app ->
        app.packages.firstNotNullOfOrNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                if (!info.enabled) return@firstNotNullOfOrNull null
                InstalledMessagingApp(app, pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}

/**
 * [number] in international format ("+34600123456"), as chat apps want it: local numbers get the
 * phone's country code, "00" becomes "+". Falls back to the digits as they are.
 */
fun internationalNumber(context: Context, number: String): String {
    PhoneNumberUtils.formatNumberToE164(number, countryIso(context))?.let { return it }
    val digits = number.filter { it.isDigit() || it == '+' }
    return when {
        digits.startsWith("+") -> digits
        digits.startsWith("00") -> "+" + digits.drop(2)
        else -> digits
    }
}
