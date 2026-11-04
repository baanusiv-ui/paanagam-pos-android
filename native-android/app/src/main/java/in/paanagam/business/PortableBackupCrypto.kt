package `in`.paanagam.business

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable backup envelope. The key is derived from a user-held passphrase,
 * never persisted on the device. AES-GCM authenticates the complete payload.
 */
internal object PortableBackupCrypto {
    private val magic = byteArrayOf(0x50, 0x41, 0x41, 0x4E, 0x41, 0x47, 0x41, 0x4D) // PAANAGAM
    private const val FORMAT_VERSION = 1
    private const val ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        require(passphrase.size >= 10) { "Use a backup passphrase with at least 10 characters." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val header = DataOutputStream(output)
        header.write(magic)
        header.writeByte(FORMAT_VERSION)
        header.writeInt(ITERATIONS)
        header.write(salt)
        header.write(nonce)
        header.flush()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        CipherOutputStream(output, cipher).use { encrypted -> input.copyTo(encrypted) }
    }

    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray) {
        val header = DataInputStream(input)
        val actualMagic = ByteArray(magic.size)
        header.readFully(actualMagic)
        require(actualMagic.contentEquals(magic)) { "This is not a Paanagam encrypted backup." }
        val version = header.readUnsignedByte()
        require(version == FORMAT_VERSION) { "Unsupported encrypted backup version." }
        val iterations = header.readInt()
        require(iterations in 100_000..1_000_000) { "Invalid backup encryption parameters." }
        val salt = ByteArray(SALT_BYTES).also(header::readFully)
        val nonce = ByteArray(NONCE_BYTES).also(header::readFully)
        val key = deriveKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        // Explicitly call doFinal() so an invalid or truncated GCM payload fails
        // authentication before restore validation or database replacement.
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            val plaintext = cipher.update(buffer, 0, count)
            if (plaintext != null && plaintext.isNotEmpty()) output.write(plaintext)
        }
        val authenticatedTail = cipher.doFinal()
        if (authenticatedTail.isNotEmpty()) output.write(authenticatedTail)
        output.flush()
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(encoded, "AES").also { encoded.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
