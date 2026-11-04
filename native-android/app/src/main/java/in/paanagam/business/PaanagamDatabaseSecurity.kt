package `in`.paanagam.business

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val PAANAGAM_DATABASE_SCHEMA_VERSION = 33

/**
 * Owns the per-install database key and the one-time plaintext -> SQLCipher conversion.
 * The raw database passphrase is wrapped by an Android Keystore AES-GCM key; only the
 * wrapped value is persisted in app-private preferences.
 */
internal object PaanagamDatabaseSecurity {
    private const val DATABASE_NAME = "paanagam-local.db"
    private const val KEY_ALIAS = "paanagam.room.database.key.v1"
    private const val PREFS = "paanagam_database_security"
    private const val WRAPPED_KEY = "wrapped_database_key"
    private const val RECOVERY_SUFFIX = ".plaintext-recovery"
    private const val TEMP_SUFFIX = ".encrypted-migration"
    private const val KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private val sqliteHeader = "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)

    fun prepare(context: Context): ByteArray {
        System.loadLibrary("sqlcipher")
        val live = context.getDatabasePath(DATABASE_NAME)
        val recovery = File(live.path + RECOVERY_SUFFIX)
        val restoreRecovery = File(live.parentFile ?: context.filesDir, "paanagam-before-restore.db")
        val temp = File(live.path + TEMP_SUFFIX)
        val invalidLive = File(live.path + ".invalid-before-restore")
        live.parentFile?.mkdirs()

        // Recover a pre-restore database before Room is constructed. This must
        // happen here as well as in MainActivity: prepare() runs before its
        // Room-open recovery handler and can itself reject a damaged live file.
        if (!live.exists() && restoreRecovery.exists()) {
            restoreRecovery.copyTo(live, overwrite = true)
        }

        // A process kill can bypass DatabaseBackupScreen's finally block.
        // Remove only app-generated staging files left by interrupted backup
        // or restore operations before opening the live database.
        live.parentFile?.listFiles()?.filter {
            it.isFile && it.name.startsWith("paanagam-restore-") &&
                (it.name.endsWith(".plain") || it.name.endsWith(".enc"))
        }?.forEach { stale ->
            check(stale.delete()) { "Could not remove an interrupted restore staging file: ${stale.name}" }
        }
        context.cacheDir.listFiles()?.filter {
            it.isFile && it.name.startsWith("paanagam-portable-") && it.name.endsWith(".db")
        }?.forEach { stale ->
            check(stale.delete()) { "Could not remove an interrupted backup staging file: ${stale.name}" }
        }

        val wrappedExists = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .contains(WRAPPED_KEY)

        if (live.exists() && !wrappedExists && !isPlaintext(live)) {
            error("The database is encrypted but its Android Keystore key is unavailable. The database has been preserved; restore a valid backup rather than creating a replacement key.")
        }

        val key = if (wrappedExists) unwrapKey(context) else createAndWrapKey(context)

        if (live.exists() && isPlaintext(live)) {
            temp.delete()
            encryptPlaintextDatabase(live, temp, key)
            verifyEncryptedDatabase(temp, key)
            if (recovery.exists()) check(recovery.delete()) { "Could not clear the old migration recovery file." }
            check(live.renameTo(recovery)) { "Could not preserve the original database before encryption." }
            if (!temp.renameTo(live)) {
                recovery.renameTo(live)
                error("Could not install the encrypted database. The original database was restored.")
            }
            try {
                verifyEncryptedDatabase(live, key)
                // Keep the plaintext recovery copy until Room opens and completes schema migrations.
            } catch (failure: Throwable) {
                live.delete()
                recovery.renameTo(live)
                throw failure
            }
        } else if (live.exists()) {
            try {
                verifyEncryptedDatabase(live, key, checkIntegrity = false)
                // Preserve recovery until MainActivity confirms Room can open.
            } catch (failure: Throwable) {
                if (restoreRecovery.exists()) {
                    // Keep the failed new database aside until the previous
                    // known-good database has been restored and validated.
                    if (invalidLive.exists()) check(invalidLive.delete()) { "Could not clear the invalid database staging file." }
                    check(live.renameTo(invalidLive)) { "Could not preserve the invalid restored database for diagnosis." }
                    try {
                        restoreRecovery.copyTo(live, overwrite = true)
                    } catch (copyFailure: Throwable) {
                        invalidLive.renameTo(live)
                        throw IllegalStateException("Encrypted restore validation failed and the previous database could not be recovered.", copyFailure)
                    }
                    try {
                        verifyEncryptedDatabase(live, key, checkIntegrity = false)
                    } catch (recoveryFailure: Throwable) {
                        live.delete()
                        invalidLive.renameTo(live)
                        throw recoveryFailure
                    }
                    temp.delete()
                    return key
                }
                if (recovery.exists()) {
                    live.delete()
                    check(recovery.renameTo(live)) { "Encrypted database validation failed and migration recovery could not be restored." }
                    temp.delete()
                    return prepare(context)
                }
                throw failure
            } finally {
                if (invalidLive.exists() && live.exists()) invalidLive.delete()
            }
        }

        temp.delete()
        return key
    }

    fun roomOpenHelperFactory(databaseKey: ByteArray): SupportOpenHelperFactory =
        SupportOpenHelperFactory(databaseKey.copyOf())

    /** Export a SQLCipher database to a temporary plaintext SQLite file for portable backup. */
    fun exportPlaintextForBackup(context: Context, databaseKey: ByteArray, destination: File) {
        val live = context.getDatabasePath(DATABASE_NAME)
        check(live.isFile && live.length() > 0L) { "The encrypted Paanagam database was not found." }
        destination.delete()
        val db = SQLiteDatabase.openDatabase(live.absolutePath, databaseKey, null, SQLiteDatabase.OPEN_READONLY, null)
        try {
            val status = scalarInt(db, "PRAGMA cipher_status")
            check(status == 1) { "The live database did not report active SQLCipher encryption." }
            val version = scalarInt(db, "PRAGMA user_version")
            db.execSQL("ATTACH DATABASE ? AS portable KEY ''", arrayOf(destination.absolutePath))
            try {
                db.rawQuery("SELECT sqlcipher_export('portable')", emptyArray<String>()).use { cursor ->
                    check(cursor.moveToFirst()) { "Could not export the portable backup." }
                }
                db.execSQL("PRAGMA portable.user_version = $version")
            } finally {
                db.execSQL("DETACH DATABASE portable")
            }
        } finally {
            db.close()
        }
        validatePlaintextDatabase(destination)
    }

    /** Validate a decrypted portable SQLite backup and encrypt it under this install's key. */
    fun encryptPortableRestore(plainFile: File, encryptedFile: File, databaseKey: ByteArray) {
        validatePlaintextDatabase(plainFile)
        encryptPlaintextDatabase(plainFile, encryptedFile, databaseKey)
        verifyEncryptedDatabase(encryptedFile, databaseKey)
    }

    fun validatePlaintextDatabase(file: File) {
        require(isPlaintext(file)) { "The selected backup is not a portable SQLite database." }
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
            file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        )
        try {
            val integrity = db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                if (it.moveToFirst()) it.getString(0) else ""
            }
            require(integrity.equals("ok", true)) { "Backup failed SQLite integrity validation." }
            val version = db.rawQuery("PRAGMA user_version", emptyArray<String>()).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            require(version in 1..PAANAGAM_DATABASE_SCHEMA_VERSION) { "Unsupported Paanagam database version: $version." }
            val tables = mutableSetOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", emptyArray<String>()).use { cursor ->
                while (cursor.moveToNext()) tables.add(cursor.getString(0))
            }
            require(setOf("room_master_table", "ingredients", "sales", "stock_lots").all { it in tables }) {
                "The file does not contain the required Paanagam database tables."
            }
        } finally {
            db.close()
        }
    }

    private fun encryptPlaintextDatabase(source: File, destination: File, key: ByteArray) {
        require(isPlaintext(source)) { "Source database is not plaintext SQLite." }
        val plain = SQLiteDatabase.openDatabase(
            source.absolutePath, null, SQLiteDatabase.OPEN_READWRITE, null
        )
        try {
            plain.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray<String>()).use { cursor ->
                if (cursor.moveToFirst()) check(cursor.getInt(0) == 0) { "Database is busy; retry encryption after closing active operations." }
            }
            val version = scalarInt(plain, "PRAGMA user_version")
            destination.delete()
            val keyText = String(key, StandardCharsets.US_ASCII)
            plain.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(destination.absolutePath, keyText))
            try {
                plain.rawQuery("SELECT sqlcipher_export('encrypted')", emptyArray<String>()).use { cursor ->
                    check(cursor.moveToFirst()) { "SQLCipher could not copy the database." }
                }
                plain.execSQL("PRAGMA encrypted.user_version = $version")
            } finally {
                plain.execSQL("DETACH DATABASE encrypted")
            }
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        } finally {
            plain.close()
        }
        verifyEncryptedDatabase(destination, key)
    }

    private fun verifyEncryptedDatabase(file: File, key: ByteArray, checkIntegrity: Boolean = true) {
        require(file.isFile && file.length() > 0L) { "Encrypted database file is missing or empty." }
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null
        )
        try {
            check(scalarInt(db, "PRAGMA cipher_status") == 1) { "SQLCipher encryption status check failed." }
            if (checkIntegrity) {
                val integrity = db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                    if (it.moveToFirst()) it.getString(0) else ""
                }
                check(integrity.equals("ok", true)) { "Encrypted database failed integrity validation." }
            }
            val version = scalarInt(db, "PRAGMA user_version")
            check(version in 1..PAANAGAM_DATABASE_SCHEMA_VERSION) { "Unexpected encrypted database schema version: $version." }
        } finally {
            db.close()
        }
    }

    private fun scalarInt(db: SQLiteDatabase, sql: String): Int =
        db.rawQuery(sql, emptyArray<String>()).use { cursor ->
            check(cursor.moveToFirst()) { "Database did not return a result for $sql" }
            cursor.getInt(0)
        }

    private fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < sqliteHeader.size) return false
        val header = ByteArray(sqliteHeader.size)
        FileInputStream(file).use { input ->
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read < 0) return false
                offset += read
            }
        }
        return header.contentEquals(sqliteHeader)
    }

    private fun createAndWrapKey(context: Context): ByteArray {
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val passphrase = key.joinToString("") { "%02x".format(it.toInt() and 0xff) }.toByteArray(StandardCharsets.US_ASCII)
        key.fill(0)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.doFinal(passphrase)
        val payload = cipher.iv + encrypted
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(WRAPPED_KEY, Base64.encodeToString(payload, Base64.NO_WRAP))
            .commit()
        check(saved) { "Could not securely save the database key wrapper." }
        return passphrase
    }

    private fun unwrapKey(context: Context): ByteArray {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(WRAPPED_KEY, null) ?: error("Database key wrapper is missing.")
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12 + 16) { "Database key wrapper is damaged." }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
