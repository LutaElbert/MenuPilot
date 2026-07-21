package com.menupilot.restaurant.assistant

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelFileVerifierTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `accepts only the pinned size and sha256`() {
        val bytes = "local model fixture".toByteArray()
        val file = temporaryFolder.newFile("model.litertlm").apply {
            writeBytes(bytes)
        }
        val expectedHash = bytes.sha256()
        val verifier = ModelFileVerifier(
            OnDeviceModelSpec(
                fileName = file.name,
                exactSizeBytes = bytes.size.toLong(),
                sha256 = expectedHash,
            ),
        )

        val verified = verifier.verify(file)

        assertEquals(file.absolutePath, verified.file.absolutePath)
        assertEquals(expectedHash, verified.sha256)
    }

    @Test
    fun `rejects wrong size and checksum`() {
        val file = temporaryFolder.newFile("bad-model.litertlm").apply {
            writeText("wrong")
        }
        assertThrows(ModelGenerationException.VerificationFailed::class.java) {
            ModelFileVerifier(
                OnDeviceModelSpec(file.name, exactSizeBytes = 4, sha256 = "00"),
            ).verify(file)
        }
        assertThrows(ModelGenerationException.VerificationFailed::class.java) {
            ModelFileVerifier(
                OnDeviceModelSpec(file.name, exactSizeBytes = 5, sha256 = "00"),
            ).verify(file)
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
