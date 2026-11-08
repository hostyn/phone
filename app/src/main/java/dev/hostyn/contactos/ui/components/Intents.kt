package dev.hostyn.contactos.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No hay ninguna app para esta acción", Toast.LENGTH_SHORT).show()
    }
}

fun Context.sms(number: String) = launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)))
fun Context.email(address: String) = launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null)))
