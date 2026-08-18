package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.aap.security.AapSslContext
import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AapSession(
    private val connection: HeadUnitConnection,
) {
    companion object {
        private val VERSION_REQUEST = byteArrayOf(0, 0, 0, 1, 0, 1, 0, 1)
        private val STATUS_OK = byteArrayOf(0, 0, 0, 2, 0, 3, 0, 0) // Placeholder
        private const val BUFFER_SIZE = 4096
    }

    private val sslContext = AapSslContext()

    suspend fun startHandshake(): AapTransport {
        return withContext(Dispatchers.IO) {
            // 1. Send Version Request
            connection.write(VERSION_REQUEST)

            // 2. Read Version Response
            val buffer = ByteArray(BUFFER_SIZE)
            val bytesRead = connection.read(buffer)
            if (bytesRead < 0) {
                throw IOException("Connection closed during version negotiation")
            }

            // Check if it is a version response
            if (buffer[0] != 0.toByte()) {
                throw IOException("Unexpected channel ID in version response")
            }

            // 3. TLS Handshake
            sslContext.performHandshake(connection)

            // 4. Send Status OK
            connection.write(STATUS_OK)

            // 5. Control Channel & Service Discovery
            val transport = AapTransport(connection, sslContext)
            val controlChannel = ControlChannel(transport)
            controlChannel.doServiceDiscovery()
            return@withContext transport
        }
    }
}
