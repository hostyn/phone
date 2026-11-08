package dev.hostyn.phone.ui.components

import dev.hostyn.phone.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.isDefaultDialer(): Boolean =
    getSystemService(TelecomManager::class.java)?.defaultDialerPackage == packageName

/** Places the call through the system (Telecom). */
fun Context.placeCall(number: String) {
    try {
        getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), Bundle())
    } catch (_: SecurityException) {
        Toast.makeText(this, getString(R.string.error_call_permission), Toast.LENGTH_SHORT).show()
    }
}

/** Voicemail number of the default SIM, or null if it isn't set up (or we can't read it). */
fun Context.voicemailNumber(): String? = try {
    getSystemService(android.telephony.TelephonyManager::class.java)?.voiceMailNumber?.takeIf { it.isNotBlank() }
} catch (_: SecurityException) {
    ""  // Unknown: let the system decide.
}

/** Calls voicemail. Returns false if there's no voicemail number set up. */
fun Context.callVoicemail(): Boolean {
    if (voicemailNumber() == null) return false
    try {
        getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("voicemail", "", null), Bundle())
    } catch (_: SecurityException) {
        Toast.makeText(this, getString(R.string.error_call_permission), Toast.LENGTH_SHORT).show()
    }
    return true
}

/** Returns a function that calls a number, requesting the permission if needed. */
@Composable
fun rememberCallLauncher(): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pending
        pending = null
        if (granted && number != null) context.placeCall(number)
    }
    return remember(context) {
        { number: String ->
            if (number.isNotBlank()) {
                if (context.has(Manifest.permission.CALL_PHONE)) context.placeCall(number)
                else {
                    pending = number
                    launcher.launch(Manifest.permission.CALL_PHONE)
                }
            }
        }
    }
}

private val PhonePermissions = buildList {
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_CALL_LOG)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

/** Asks the system to make this app the default phone app. */
@Composable
fun rememberDialerRoleRequest(): () -> Unit {
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.isDefaultDialer()) {
            val missing = PhonePermissions.filterNot { context.has(it) }
            if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        }
    }
    return remember(context) {
        {
            val intent = if (Build.VERSION.SDK_INT >= 29) {
                context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                    .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            }
            role.launch(intent)
        }
    }
}

@Composable
fun DefaultDialerBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isDefault = rememberOnResume { context.isDefaultDialer() }
    val request = rememberDialerRoleRequest()
    if (isDefault) return

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.default_dialer_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.default_dialer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = request) { Text(stringResource(R.string.set_as_default)) }
        }
    }
}
