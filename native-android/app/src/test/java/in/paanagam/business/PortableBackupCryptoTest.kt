package `in`.paanagam.business

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PortableBackupCryptoTest {
    @Test fun roundTripPreservesPayload() {
        val payload = ByteArray(128_000) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        PortableBackupCrypto.encrypt(ByteArrayInputStream(payload), encrypted, "correct horse battery".toCharArray())
        val restored = ByteArrayOutputStream()
        PortableBackupCrypto.decrypt(ByteArrayInputStream(encrypted.toByteArray()), restored, "correct horse battery".toCharArray())
        assertArrayEquals(payload, restored.toByteArray())
    }

    @Test fun wrongPassphraseCannotDecrypt() {
        val encrypted = ByteArrayOutputStream()
        PortableBackupCrypto.encrypt(ByteArrayInputStream(byteArrayOf(1, 2, 3)), encrypted, "correct horse battery".toCharArray())
        assertThrows(Exception::class.java) {
            PortableBackupCrypto.decrypt(ByteArrayInputStream(encrypted.toByteArray()), ByteArrayOutputStream(), "incorrect passphrase".toCharArray())
        }
    }

    @Test fun modifiedCiphertextIsRejected() {
        val encrypted = ByteArrayOutputStream()
        PortableBackupCrypto.encrypt(ByteArrayInputStream(ByteArray(64) { it.toByte() }), encrypted, "correct horse battery".toCharArray())
        val bytes = encrypted.toByteArray()
        bytes[bytes.lastIndex - 2] = (bytes[bytes.lastIndex - 2].toInt() xor 0x40).toByte()
        assertThrows(Exception::class.java) {
            PortableBackupCrypto.decrypt(ByteArrayInputStream(bytes), ByteArrayOutputStream(), "correct horse battery".toCharArray())
        }
    }


    @Test fun truncatedCiphertextIsRejected() {
        val encrypted = ByteArrayOutputStream()
        PortableBackupCrypto.encrypt(
            ByteArrayInputStream(ByteArray(4096) { (it % 239).toByte() }),
            encrypted,
            "correct horse battery".toCharArray()
        )
        val truncated = encrypted.toByteArray().copyOf(encrypted.size() - 8)
        assertThrows(Exception::class.java) {
            PortableBackupCrypto.decrypt(
                ByteArrayInputStream(truncated),
                ByteArrayOutputStream(),
                "correct horse battery".toCharArray()
            )
        }
    }

    @Test fun rejectsShortPassphrases() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupCrypto.encrypt(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream(), "short".toCharArray())
        }
    }
}
