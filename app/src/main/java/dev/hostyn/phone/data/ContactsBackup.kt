package dev.hostyn.phone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.hostyn.phone.PhoneApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Automatic backup: writes every contact as one vCard file to a folder the user picked (for
 * example one synced with Syncthing). It runs shortly after contacts change and once a day.
 *
 * Each day gets its own file (`contacts-2026-11-08.vcf`), rewritten if contacts change again that
 * day, and the newest [AppSettings.backupKeep] files are kept. Nothing is written when the
 * contacts are the same as in the last file, and an empty contacts list never produces a backup.
 * Files are written under a temporary name and renamed when complete, so a sync app never picks
 * up a half-written file.
 */
object ContactsBackup {
    private const val PREFIX = "contacts-"
    private const val SUFFIX = ".vcf"
    private const val TEMP_NAME = ".contacts-backup.tmp"
    private const val WORK_DAILY = "backup_daily"
    private const val WORK_ON_CHANGE = "backup_on_change"
    const val KEY_ON_CHANGE = "on_change"

    /** Error codes stored in [BackupStatus.error]. */
    const val ERROR_NO_FOLDER = "no_folder"
    const val ERROR_NO_PERMISSION = "no_permission"
    const val ERROR_NO_CONTACTS = "no_contacts"
    const val ERROR_WRITE = "write"

    private val lock = Mutex()

    private fun settings(context: Context) = (context.applicationContext as PhoneApp).settings

    /** Arms or cancels the background work to match the settings. Safe to call repeatedly. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val s = settings(context)
        if (!s.backupEnabled || s.backupFolder == null) {
            wm.cancelUniqueWork(WORK_DAILY)
            wm.cancelUniqueWork(WORK_ON_CHANGE)
            return
        }
        wm.enqueueUniquePeriodicWork(
            WORK_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build(),
        )
        armOnChange(context, ExistingWorkPolicy.KEEP)
    }

    /** A one-off job that fires when the contacts provider reports a change (it must be re-armed after each run). */
    private fun armOnChange(context: Context, policy: ExistingWorkPolicy) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(ContactsContract.AUTHORITY_URI, true)
            // Wait for edits to settle before writing.
            .setTriggerContentUpdateDelay(30, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(10, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_ON_CHANGE,
            policy,
            OneTimeWorkRequestBuilder<BackupWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_ON_CHANGE to true))
                .build(),
        )
    }

    internal fun rearm(context: Context) {
        // Appended after the running job: replacing it would cancel the job that is re-arming.
        if (settings(context).backupEnabled) armOnChange(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** Display name of the chosen folder, or null if it's no longer accessible. */
    fun folderName(context: Context, folder: String?): String? = folder?.let {
        try { DocumentFile.fromTreeUri(context, Uri.parse(it))?.takeIf { d -> d.exists() }?.name } catch (_: Exception) { null }
    }

    /**
     * Writes a backup now if contacts changed since the last one (always when [force]).
     * Returns the new status, which is also saved in the settings.
     */
    suspend fun run(context: Context, force: Boolean = false): BackupStatus = lock.withLock {
        withContext(Dispatchers.IO) {
            val s = settings(context)
            val previous = s.backupStatus
            fun fail(code: String) = previous.copy(time = System.currentTimeMillis(), error = code).also(s::updateBackupStatus)

            val folderUri = s.backupFolder ?: return@withContext fail(ERROR_NO_FOLDER)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return@withContext fail(ERROR_NO_PERMISSION)
            }
            val folder = try {
                DocumentFile.fromTreeUri(context, Uri.parse(folderUri))?.takeIf { it.exists() && it.canWrite() }
            } catch (_: Exception) {
                null
            } ?: return@withContext fail(ERROR_NO_FOLDER)

            val repo = (context.applicationContext as PhoneApp).repository
            val bytes: ByteArray
            val count: Int
            try {
                val out = ByteArrayOutputStream()
                count = writeAllContacts(repo, out)
                bytes = out.toByteArray()
            } catch (_: Exception) {
                return@withContext fail(ERROR_WRITE)
            }
            // Never replace a backup with an empty one (contacts wiped, provider not ready…).
            if (count == 0) return@withContext fail(ERROR_NO_CONTACTS)

            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val name = PREFIX + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + SUFFIX
            val unchanged = hash == s.backupHash && previous.file?.let { folder.findFile(it) } != null
            if (unchanged && !force) {
                return@withContext previous.copy(time = System.currentTimeMillis(), error = null).also(s::updateBackupStatus)
            }

            try {
                writeAtomically(context, folder, name, bytes)
            } catch (_: Exception) {
                return@withContext fail(ERROR_WRITE)
            }
            s.backupHash = hash
            prune(folder, s.backupKeep)
            BackupStatus(System.currentTimeMillis(), count, name, null).also(s::updateBackupStatus)
        }
    }

    private fun writeAtomically(context: Context, folder: DocumentFile, name: String, bytes: ByteArray) {
        folder.findFile(TEMP_NAME)?.delete()
        // A generic type so the provider doesn't append an extension to the temporary name.
        val temp = folder.createFile("application/octet-stream", TEMP_NAME) ?: error("create failed")
        context.contentResolver.openOutputStream(temp.uri, "wt")?.use { it.write(bytes) } ?: error("open failed")
        folder.findFile(name)?.delete()
        val renamed = DocumentsContract.renameDocument(context.contentResolver, temp.uri, name)
        if (renamed == null) {
            temp.delete()
            error("rename failed")
        }
    }

    /** Deletes the oldest backup files beyond [keep] (0 keeps everything). Only touches our own files. */
    private fun prune(folder: DocumentFile, keep: Int) {
        if (keep <= 0) return
        folder.listFiles()
            .filter { f -> f.isFile && f.name?.let { it.startsWith(PREFIX) && it.endsWith(SUFFIX) } == true }
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { it.delete() }
    }
}

class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ContactsBackup.run(applicationContext)
        // The content trigger only fires once; arm it again for the next change.
        if (inputData.getBoolean(ContactsBackup.KEY_ON_CHANGE, false)) ContactsBackup.rearm(applicationContext)
        return Result.success()
    }
}
