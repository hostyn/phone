package dev.hostyn.phone.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import dev.hostyn.phone.PhoneApp
import dev.hostyn.phone.R
import dev.hostyn.phone.data.InstalledMessagingApp
import dev.hostyn.phone.data.installedMessagingApps

/** An installed chat app, ready to draw. */
class MessagingButton(val installed: InstalledMessagingApp, val icon: ImageBitmap) {
    val label get() = installed.label
}

/** The installed chat apps, checked again whenever the screen comes back (one may have been installed). */
@Composable
fun rememberInstalledMessagingApps(): List<MessagingButton> {
    val context = LocalContext.current
    val apps = rememberOnResume { installedMessagingApps(context) }
    val size = (48 * context.resources.displayMetrics.density).toInt()
    return remember(apps.map { it.packageName }) {
        apps.map { MessagingButton(it, it.icon.toBitmap(size, size).asImageBitmap()) }
    }
}

/** The chat apps to show as buttons on a contact: installed and not turned off in settings. */
@Composable
fun rememberMessagingButtons(): List<MessagingButton> {
    val settings = (LocalContext.current.applicationContext as PhoneApp).settings
    val installed = rememberInstalledMessagingApps()
    return installed.filter { settings.showsMessagingApp(it.installed.app) }
}

/** Opens a chat with [number] in the app, or says why it couldn't. */
fun MessagingButton.open(context: android.content.Context, number: String) {
    try {
        installed.open(context, number)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_no_app), Toast.LENGTH_SHORT).show()
    }
}
