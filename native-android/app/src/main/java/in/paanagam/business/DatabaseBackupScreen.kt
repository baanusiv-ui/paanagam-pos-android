package `in`.paanagam.business

import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `in`.paanagam.business.data.PaanagamDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

private const val PAANAGAM_DB_NAME = "paanagam-local.db"

private object PaanagamDatabaseBackup {
    fun export(context: Context, db: PaanagamDatabase, destination: Uri, passphrase: CharArray) {
        val liveDb = db.openHelper.writableDatabase
        liveDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                error("Database is busy. Close other operations and try the backup again.")
            }
        }
        db.close()
        val source = context.getDatabasePath(PAANAGAM_DB_NAME)
        require(source.isFile && source.length() > 0L) { "Local database file was not found." }
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: error("Could not open the selected backup destination.")
        FileInputStream(source).use { input ->
            output.use { PortableBackupCrypto.encrypt(input, it, passphrase) }
        }
    }

    fun restore(context: Context, db: PaanagamDatabase, backupUri: Uri, passphrase: CharArray) {
        val liveFile = context.getDatabasePath(PAANAGAM_DB_NAME)
        val directory = liveFile.parentFile ?: error("Database directory is unavailable.")
        val staged = File(directory, "paanagam-restore-${UUID.randomUUID()}.tmp")
        val previous = File(directory, "paanagam-before-restore.db")
        try {
            val input = context.contentResolver.openInputStream(backupUri)
                ?: error("Could not read the selected backup file.")
            input.use { encrypted ->
                FileOutputStream(staged).use { decrypted ->
                    PortableBackupCrypto.decrypt(encrypted, decrypted, passphrase)
                }
            }
            require(staged.isFile && staged.length() > 0L) { "The decrypted backup is empty." }
            validateBackup(staged)

            db.close()
            File(liveFile.path + "-wal").delete()
            File(liveFile.path + "-shm").delete()
            if (previous.exists()) require(previous.delete()) { "Could not clear the previous recovery file." }
            require(!liveFile.exists() || liveFile.renameTo(previous)) { "Could not preserve the current database before restore." }
            if (!staged.renameTo(liveFile)) {
                if (previous.exists()) previous.renameTo(liveFile)
                error("Could not install the restored database. The original database was preserved.")
            }
        } catch (error: Throwable) {
            staged.delete()
            if (!liveFile.exists() && previous.exists()) previous.renameTo(liveFile)
            throw error
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private fun validateBackup(file: File) {
        val sqlite = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val integrity = sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }
            require(integrity.equals("ok", ignoreCase = true)) { "Backup failed SQLite integrity check." }
            val version = sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            require(version in 1..27) { "Unsupported backup database version: $version." }
            val tables = mutableSetOf<String>()
            sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                while (cursor.moveToNext()) tables.add(cursor.getString(0))
            }
            require(setOf("room_master_table", "ingredients", "sales", "stock_lots").all { it in tables }) {
                "This file is not a valid Paanagam database backup."
            }
        } finally {
            sqlite.close()
        }
    }
}

@Composable
fun DatabaseBackupScreen(db: PaanagamDatabase, onRecreate: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    var passwordDialog by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var restorePassword by remember { mutableStateOf("") }
    var exportPassphrase by remember { mutableStateOf<CharArray?>(null) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val secret = exportPassphrase
        exportPassphrase = null
        if (uri != null && secret != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { PaanagamDatabaseBackup.export(context, db, uri, secret) }
                    message = "Encrypted backup saved. Keep the passphrase separately; it cannot be recovered."
                } catch (error: Throwable) {
                    secret.fill('\u0000')
                    message = error.message ?: "Backup could not be completed."
                } finally {
                    runCatching { db.close() }
                    onRecreate()
                }
            }
        } else {
            secret?.fill('\u0000')
        }
    }
    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestore = uri }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Encrypted data backup & restore", color = Color(0xFFB87928), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("Create a portable, passphrase-protected backup that can be restored on another Android device.")
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E9))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Security")
                Text("Backup files use AES-256-GCM authenticated encryption with a key derived from your passphrase. The passphrase is not stored. The live on-device Room database remains unencrypted at this stage.")
            }
        }
        Button(
            onClick = { passwordDialog = "EXPORT" },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create encrypted backup") }
        OutlinedButton(
            onClick = { chooseBackup.launch(arrayOf("application/octet-stream", "*/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose encrypted backup to restore") }
        if (busy) CircularProgressIndicator()
        if (message.isNotBlank()) Text(message)
        Spacer(Modifier.height(4.dp))
        Text("Store the encrypted file separately from the POS device. If you lose the passphrase, the backup cannot be decrypted.")
    }

    if (passwordDialog == "EXPORT") {
        AlertDialog(
            onDismissRequest = { passwordDialog = ""; backupPassword = ""; confirmPassword = "" },
            title = { Text("Protect backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose a passphrase with at least 10 characters. You will need it to restore this backup.")
                    OutlinedTextField(backupPassword, { backupPassword = it }, label = { Text("Backup passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("Confirm passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (backupPassword.length < 10 || backupPassword != confirmPassword) {
                        message = "Passphrases must match and contain at least 10 characters."
                    } else {
                        exportPassphrase = backupPassword.toCharArray()
                        passwordDialog = ""
                        backupPassword = ""; confirmPassword = ""
                        createBackup.launch("paanagam-encrypted-backup.pgb")
                    }
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { passwordDialog = ""; backupPassword = ""; confirmPassword = "" }) { Text("Cancel") } }
        )
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null; restorePassword = "" },
            title = { Text("Restore encrypted backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the passphrase used when this backup was created. The file is authenticated and checked before replacing the current database.")
                    OutlinedTextField(restorePassword, { restorePassword = it }, label = { Text("Backup passphrase") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (restorePassword.length < 10) {
                        message = "Enter the backup passphrase."
                    } else {
                        val secret = restorePassword.toCharArray()
                        restorePassword = ""
                        pendingRestore = null
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { PaanagamDatabaseBackup.restore(context, db, uri, secret) }
                                message = "Encrypted backup restored and validated successfully."
                            } catch (error: Throwable) {
                                secret.fill('\u0000')
                                message = error.message ?: "Restore could not be completed."
                            } finally {
                                onRecreate()
                            }
                        }
                    }
                }) { Text("Restore data") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null; restorePassword = "" }) { Text("Cancel") } }
        )
    }
}
