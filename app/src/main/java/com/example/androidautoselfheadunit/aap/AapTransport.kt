@file:Suppress("UnusedPrivateProperty")

package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.aap.security.AapSslContext
import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AapTransport(
    private val connection: HeadUnitConnection,
    private val sslContext: AapSslContext,
) {
    companion object {
        private const val BUFFER_SIZE = 4096
    }

    suspend fun sendEncrypted(message: AapMessage) {
        withContext(Dispatchers.IO) {
            // Placeholder: serialize AapMessage, encrypt payload using sslContext, and write
            // For Phase 2, we just write the payload directly to mock the behavior
            connection.write(message.payload)
        }
    }

    suspend fun receiveEncrypted(): AapMessage {
        return withContext(Dispatchers.IO) {
            val buffer = ByteArray(BUFFER_SIZE)
            val bytesRead = connection.read(buffer)
            if (bytesRead < 0) {
                throw IOException("Connection closed")
            }
            // Placeholder: decrypt using sslContext and parse into AapMessage
            AapMessage(0, 0, 11.toByte(), buffer.copyOf(bytesRead))
        }
    }
}
