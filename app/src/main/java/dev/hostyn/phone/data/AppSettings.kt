package dev.hostyn.phone.data

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode { System, Light, Dark }

/** How contact names are sorted in the list and shown everywhere. */
enum class NameOrder { GivenFirst, FamilyFirst }

/**
 * App preferences. Each value is Compose state (screens update when it changes) backed by
 * SharedPreferences, so it can also be read from workers and services.
 */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun <E : Enum<E>> enumPref(key: String, default: E, values: Array<E>) =
        values.firstOrNull { it.name == prefs.getString(key, null) } ?: default

    // --- Appearance ---

    var theme by mutableStateOf(enumPref("theme", ThemeMode.System, ThemeMode.entries.toTypedArray()))
        private set
    fun updateTheme(value: ThemeMode) { theme = value; prefs.edit().putString("theme", value.name).apply() }

    var dynamicColor by mutableStateOf(prefs.getBoolean("dynamic_color", true))
        private set
    fun updateDynamicColor(value: Boolean) { dynamicColor = value; prefs.edit().putBoolean("dynamic_color", value).apply() }

    // --- Contacts ---

    var sortOrder by mutableStateOf(enumPref("sort_order", NameOrder.GivenFirst, NameOrder.entries.toTypedArray()))
        private set
    fun updateSortOrder(value: NameOrder) { sortOrder = value; prefs.edit().putString("sort_order", value.name).apply() }

    var nameFormat by mutableStateOf(enumPref("name_format", NameOrder.GivenFirst, NameOrder.entries.toTypedArray()))
        private set
    fun updateNameFormat(value: NameOrder) { nameFormat = value; prefs.edit().putString("name_format", value.name).apply() }

    /** Accounts ([ContactAccount.key]) whose contacts the app doesn't show. */
    var hiddenAccounts by mutableStateOf(prefs.getStringSet("hidden_accounts", null).orEmpty().toSet())
        private set
    fun updateAccountShown(account: ContactAccount, shown: Boolean) {
        hiddenAccounts = if (shown) hiddenAccounts - account.key else hiddenAccounts + account.key
        prefs.edit().putStringSet("hidden_accounts", hiddenAccounts).apply()
    }

    /** Account ([ContactAccount.key]) new contacts are saved to; null = the first synced account. */
    var defaultAccount by mutableStateOf(prefs.getString("default_account", null))
        private set
    fun updateDefaultAccount(account: ContactAccount?) {
        defaultAccount = account?.key
        prefs.edit().putString("default_account", defaultAccount).apply()
    }

    // --- Keypad (defaults follow the system's own setting) ---

    var dialpadTones by mutableStateOf(
        prefs.getBoolean(
            "dialpad_tones",
            Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1,
        ),
    )
        private set
    fun updateDialpadTones(value: Boolean) { dialpadTones = value; prefs.edit().putBoolean("dialpad_tones", value).apply() }

    var dialpadVibration by mutableStateOf(prefs.getBoolean("dialpad_vibration", true))
        private set
    fun updateDialpadVibration(value: Boolean) { dialpadVibration = value; prefs.edit().putBoolean("dialpad_vibration", value).apply() }

    // --- Chat apps ---

    /** Chat apps turned off; the rest of the installed ones get a button on contacts. */
    var hiddenMessagingApps by mutableStateOf(prefs.getStringSet("hidden_messaging_apps", null).orEmpty().toSet())
        private set
    fun updateMessagingApp(app: MessagingApp, shown: Boolean) {
        hiddenMessagingApps = if (shown) hiddenMessagingApps - app.name else hiddenMessagingApps + app.name
        prefs.edit().putStringSet("hidden_messaging_apps", hiddenMessagingApps).apply()
    }
    fun showsMessagingApp(app: MessagingApp) = app.name !in hiddenMessagingApps

    // --- Automatic backup ---

    var backupEnabled by mutableStateOf(prefs.getBoolean("backup_enabled", false))
        private set
    /** Tree URI of the folder chosen with the system picker (with a persisted permission). */
    var backupFolder by mutableStateOf(prefs.getString("backup_folder", null))
        private set
    /** How many backup files to keep; 0 keeps all of them. */
    var backupKeep by mutableStateOf(prefs.getInt("backup_keep", 30))
        private set

    fun updateBackup(enabled: Boolean = backupEnabled, folder: String? = backupFolder, keep: Int = backupKeep) {
        backupEnabled = enabled
        backupFolder = folder
        backupKeep = keep
        prefs.edit()
            .putBoolean("backup_enabled", enabled)
            .putString("backup_folder", folder)
            .putInt("backup_keep", keep)
            .apply()
    }

    /** Result of the last backup run. */
    var backupStatus by mutableStateOf(
        BackupStatus(
            time = prefs.getLong("backup_time", 0),
            count = prefs.getInt("backup_count", 0),
            file = prefs.getString("backup_file", null),
            error = prefs.getString("backup_error", null),
        ),
    )
        private set

    /** Hash of the last file written, to skip writing when nothing changed. */
    var backupHash: String?
        get() = prefs.getString("backup_hash", null)
        set(value) { prefs.edit().putString("backup_hash", value).apply() }

    fun updateBackupStatus(status: BackupStatus) {
        backupStatus = status
        prefs.edit()
            .putLong("backup_time", status.time)
            .putInt("backup_count", status.count)
            .putString("backup_file", status.file)
            .putString("backup_error", status.error)
            .apply()
    }
}

/**
 * [time] is when the last backup was checked; [file] and [count] describe the latest file written.
 * [error] is set when the last attempt failed.
 */
data class BackupStatus(val time: Long, val count: Int, val file: String?, val error: String?)
