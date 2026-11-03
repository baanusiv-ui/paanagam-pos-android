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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.RoomDatabase
import `in`.paanagam.business.data.PaanagamDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

private const val PAANAGAM_DB_NAME = "paanagam-local.db"

private object PaanagamDatabaseBackup {
    fun export(context: Context, db: PaanagamDatabase, destination: Uri) {
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
        FileInputStream(source).use { input -> output.use { input.copyTo(it) } }
    }

    fun restore(context: Context, db: PaanagamDatabase, backupUri: Uri) {
        val liveFile = context.getDatabasePath(PAANAGAM_DB_NAME)
        val directory = liveFile.parentFile ?: error("Database directory is unavailable.")
        val staged = File(directory, "paanagam-restore-${UUID.randomUUID()}.tmp")
        val previous = File(directory, "paanagam-before-restore.db")
        try {
            val input = context.contentResolver.openInputStream(backupUri)
                ?: error("Could not read the selected backup file.")
            input.use { source -> staged.outputStream().use { source.copyTo(it) } }
            require(staged.isFile && staged.length() > 0L) { "The selected backup is empty." }
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
            previous.delete()
        } catch (error: Throwable) {
            staged.delete()
            if (!liveFile.exists() && previous.exists()) previous.renameTo(liveFile)
            throw error
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
            require(version in 1..21) { "Unsupported backup database version: $version." }
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

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { PaanagamDatabaseBackup.export(context, db, uri) }
                    message = "Backup saved. The app will reopen its local database."
                } catch (error: Throwable) {
                    message = error.message ?: "Backup could not be completed."
                } finally {
                    runCatching { db.close() }
                    onRecreate()
                }
            }
        }
    }
    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestore = uri }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Local data backup & restore", color = Color(0xFFB87928), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text("Export the complete Paanagam database to a file you control. Restore it on this device or another Android device with Paanagam installed.")
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E9))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Important")
                Text("A backup contains sales, stock, recipes, staff names and PIN hashes. Store it securely. Restoring replaces all current app data with the selected backup.")
            }
        }
        Button(
            onClick = { createBackup.launch("paanagam-backup.db") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create database backup") }
        OutlinedButton(
            onClick = { chooseBackup.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "*/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose backup to restore") }
        if (busy) CircularProgressIndicator()
        if (message.isNotBlank()) Text(message)
        Spacer(Modifier.height(4.dp))
        Text("Use the Android file picker to save the backup to local storage, USB storage or a location you trust. Keep a separate copy away from the POS device.")
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace this device's data?") },
            text = { Text("Paanagam will validate the selected SQLite backup first. If valid, it will replace the current database. This cannot be undone unless you have a separate backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { PaanagamDatabaseBackup.restore(context, db, uri) }
                            message = "Backup restored successfully."
                        } catch (error: Throwable) {
                            message = error.message ?: "Restore could not be completed."
                        } finally {
                            onRecreate()
                        }
                    }
                }) { Text("Restore data") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
        )
    }
}
