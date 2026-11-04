package `in`.paanagam.business

import android.content.Context
import android.net.Uri
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
import java.io.FileOutputStream
import java.util.UUID

private const val PAANAGAM_DB_NAME = "paanagam-local.db"

private object PaanagamDatabaseBackup {
    fun export(context: Context, db: PaanagamDatabase, destination: Uri, passphrase: CharArray, databaseKey: ByteArray) {
        val liveDb = db.openHelper.writableDatabase
        liveDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) != 0) error("Database is busy. Close other operations and try again.")
        }
        db.close()
        val portable = File(context.cacheDir, "paanagam-portable-${UUID.randomUUID()}.db")
        try {
            PaanagamDatabaseSecurity.exportPlaintextForBackup(context, databaseKey, portable)
            val output = context.contentResolver.openOutputStream(destination, "wt")
                ?: error("Could not open the selected backup destination.")
            portable.inputStream().use { input -> output.use { PortableBackupCrypto.encrypt(input, it, passphrase) } }
        } finally {
            portable.delete()
            passphrase.fill('\u0000')
        }
    }

    fun restore(context: Context, db: PaanagamDatabase, backupUri: Uri, passphrase: CharArray, databaseKey: ByteArray) {
        val liveFile = context.getDatabasePath(PAANAGAM_DB_NAME)
        val directory = liveFile.parentFile ?: error("Database directory is unavailable.")
        val stagedPlain = File(directory, "paanagam-restore-${UUID.randomUUID()}.plain")
        val stagedEncrypted = File(directory, "paanagam-restore-${UUID.randomUUID()}.enc")
        val previous = File(directory, "paanagam-before-restore.db")
        try {
            val input = context.contentResolver.openInputStream(backupUri)
                ?: error("Could not read the selected backup file.")
            input.use { encrypted ->
                stagedPlain.outputStream().use { decrypted ->
                    PortableBackupCrypto.decrypt(encrypted, decrypted, passphrase)
                }
            }
            PaanagamDatabaseSecurity.validatePlaintextDatabase(stagedPlain)
            PaanagamDatabaseSecurity.encryptPortableRestore(stagedPlain, stagedEncrypted, databaseKey)
            db.close()
            File(liveFile.path + "-wal").delete()
            File(liveFile.path + "-shm").delete()
            if (previous.exists()) require(previous.delete()) { "Could not clear the previous recovery file." }
            require(!liveFile.exists() || liveFile.renameTo(previous)) { "Could not preserve the current database before restore." }
            if (!stagedEncrypted.renameTo(liveFile)) {
                if (previous.exists()) previous.renameTo(liveFile)
                error("Could not install the restored database. The original database was preserved.")
            }
        } catch (error: Throwable) {
            if (!liveFile.exists() && previous.exists()) previous.renameTo(liveFile)
            throw error
        } finally {
            stagedPlain.delete()
            stagedEncrypted.delete()
            passphrase.fill('\u0000')
        }
    }
}

@Composable
fun DatabaseBackupScreen(db: PaanagamDatabase, databaseKey: ByteArray, onRecreate: () -> Unit) {
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
                    withContext(Dispatchers.IO) { PaanagamDatabaseBackup.export(context, db, uri, secret, databaseKey) }
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
                Text("The live on-device Room database uses SQLCipher encryption. Its random database key is protected by Android Keystore AES-256-GCM and is not stored as plaintext. Portable backup files use a separate AES-256-GCM passphrase envelope so they can be restored on another device. Keep the backup passphrase separately; it is never stored by the app.")
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
                                withContext(Dispatchers.IO) { PaanagamDatabaseBackup.restore(context, db, uri, secret, databaseKey) }
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
