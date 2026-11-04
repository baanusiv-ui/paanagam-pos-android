package `in`.paanagam.business

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import `in`.paanagam.business.data.PaanagamDao
import `in`.paanagam.business.data.PaanagamDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow

internal class DatabaseHandle(val db: PaanagamDatabase, val key: ByteArray)

/**
 * Process-wide scope for database WRITES (checkout, refunds, purchases). Unlike
 * rememberCoroutineScope(), it is not cancelled when a screen leaves composition,
 * so a bill that is being saved cannot be abandoned half-way by navigation.
 */
val commitScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Remembers the Room Flow so it is created once per DB, not on every recomposition. */
@Composable
fun <T> rememberDbFlow(db: PaanagamDatabase, initial: T, query: (PaanagamDao) -> Flow<T>): State<T> {
    val flow = remember(db) { query(db.dao()) }
    return flow.collectAsState(initial)
}

/** One database instance per process; opened off the main thread. */
object PaanagamDatabaseProvider {
    private const val DB_NAME = "paanagam-local.db"
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cached: Deferred<DatabaseHandle>? = null

    @Synchronized
    internal fun open(context: Context): Deferred<DatabaseHandle> {
        val app = context.applicationContext
        cached?.let { return it }
        val deferred = ioScope.async { build(app) }
        cached = deferred
        deferred.invokeOnCompletion { failure ->
            // A failed open must not be cached, otherwise "Retry" would never rebuild.
            if (failure != null) synchronized(this) { if (cached === deferred) cached = null }
        }
        return deferred
    }

    /** Call after backup/restore (which close the DB) and before recreate(). */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    fun invalidate() {
        val deferred = cached
        cached = null
        if (deferred != null && deferred.isCompleted && !deferred.isCancelled) {
            runCatching { deferred.getCompleted().db.close() }
        }
    }

    private fun build(context: Context): DatabaseHandle {
        val liveFile = context.getDatabasePath(DB_NAME)
        val restoreRecovery = context.getDatabasePath("paanagam-before-restore.db")
        val migrationRecovery = context.getDatabasePath("paanagam-local.db.plaintext-recovery")

        // Recover a pre-restore database before SQLCipher/Room can create an empty live file.
        if (!liveFile.exists() && restoreRecovery.exists()) {
            check(restoreRecovery.renameTo(liveFile)) {
                "An interrupted restore was detected, but the previous database could not be recovered. The recovery file has been preserved."
            }
        }
        val key = PaanagamDatabaseSecurity.prepare(context)
        val db = Room.databaseBuilder(context, PaanagamDatabase::class.java, DB_NAME)
            .openHelperFactory(PaanagamDatabaseSecurity.roomOpenHelperFactory(key))
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        try {
            db.openHelper.writableDatabase
        } catch (failure: Throwable) {
            db.close()
            if (restoreRecovery.exists()) {
                liveFile.delete()
                check(restoreRecovery.renameTo(liveFile)) {
                    "Database open failed and the pre-restore database could not be recovered."
                }
            } else if (migrationRecovery.exists()) {
                liveFile.delete()
                check(migrationRecovery.renameTo(liveFile)) {
                    "Room migration failed and the original plaintext database could not be recovered."
                }
            }
            throw failure
        }
        // Clean up recovery copies only after Room has opened successfully.
        if (restoreRecovery.exists()) check(restoreRecovery.delete()) {
            "Database opened, but the previous restore recovery file could not be removed. Retry startup before using POS."
        }
        if (migrationRecovery.exists()) check(migrationRecovery.delete()) {
            "Database opened, but the plaintext migration recovery file could not be removed. Retry startup before using POS."
        }
        return DatabaseHandle(db, key)
    }
}

@Composable
internal fun StartupSplash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun StartupError(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Paanagam could not start")
        Text(message)
        Text("Your data has not been deleted. Retry, and if this persists restore your latest encrypted backup.")
        Button(onClick = onRetry) { Text("Retry") }
    }
}
