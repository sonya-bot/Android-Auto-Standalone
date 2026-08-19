package com.example.androidautoselfheadunit.aap.security

import android.content.Context
import com.example.androidautoselfheadunit.aap.AapFrameCodec
import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

class AapSslContext(context: Context) {
    private val sslContext: SSLContext =
        SSLContext.getInstance("TLS").apply {
            init(arrayOf(SingleKeyKeyManager(context)), arrayOf(NoCheckTrustManager()), null)
        }
    private lateinit var sslEngine: SSLEngine

    suspend fun performHandshake(connection: HeadUnitConnection) {
        sslEngine = sslContext.createSSLEngine("android-auto", 5277).apply {
            useClientMode = true
            enabledProtocols = enabledProtocols.filter { it == "TLSv1.2" }.toTypedArray()
            beginHandshake()
        }

        var pendingTls = ByteArray(0)
        val empty = ByteBuffer.allocate(0)
        val handshakeAppBuffer = ByteBuffer.allocate(sslEngine.session.applicationBufferSize)

        while (true) {
            when (sslEngine.handshakeStatus) {
                SSLEngineResult.HandshakeStatus.FINISHED,
                SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                -> return

                SSLEngineResult.HandshakeStatus.NEED_TASK -> runDelegatedTasks()

                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    val networkBuffer = ByteBuffer.allocate(sslEngine.session.packetBufferSize)
                    val result = sslEngine.wrap(empty, networkBuffer)
                    runDelegatedTasks()
                    if (result.status != SSLEngineResult.Status.OK) {
                        throw IOException("TLS handshake wrap failed: ${result.status}")
                    }
                    networkBuffer.flip()
                    val tlsBytes = ByteArray(networkBuffer.remaining())
                    networkBuffer.get(tlsBytes)
                    if (tlsBytes.isNotEmpty()) {
                        connection.write(
                            AapFrameCodec.encodePlainControl(
                                AapFrameCodec.MESSAGE_ENCAPSULATED_SSL,
                                tlsBytes,
                            ),
                        )
                    }
                }

                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    if (pendingTls.isEmpty()) {
                        pendingTls = readHandshakePayload(connection)
                    }

                    val networkBuffer = ByteBuffer.wrap(pendingTls)
                    handshakeAppBuffer.clear()
                    val result = sslEngine.unwrap(networkBuffer, handshakeAppBuffer)
                    runDelegatedTasks()

                    val remainder = ByteArray(networkBuffer.remaining())
                    networkBuffer.get(remainder)
                    pendingTls = remainder

                    when (result.status) {
                        SSLEngineResult.Status.OK -> Unit
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                            pendingTls += readHandshakePayload(connection)
                        }
                        else -> throw IOException("TLS handshake unwrap failed: ${result.status}")
                    }
                }
            }
        }
    }

    @Synchronized
    fun encrypt(payload: ByteArray): ByteArray {
        check(::sslEngine.isInitialized) { "TLS session is not initialized" }
        val input = ByteBuffer.wrap(payload)
        val output = ByteArrayOutputStream()
        while (input.hasRemaining()) {
            val networkBuffer = ByteBuffer.allocate(sslEngine.session.packetBufferSize)
            val result = sslEngine.wrap(input, networkBuffer)
            runDelegatedTasks()
            if (result.status != SSLEngineResult.Status.OK) {
                throw IOException("TLS encryption failed: ${result.status}")
            }
            networkBuffer.flip()
            val bytes = ByteArray(networkBuffer.remaining())
            networkBuffer.get(bytes)
            output.write(bytes)
        }
        return output.toByteArray()
    }

    @Synchronized
    fun decrypt(encryptedPayload: ByteArray): ByteArray {
        check(::sslEngine.isInitialized) { "TLS session is not initialized" }
        val input = ByteBuffer.wrap(encryptedPayload)
        val output = ByteArrayOutputStream()
        while (input.hasRemaining()) {
            val appBuffer = ByteBuffer.allocate(
                sslEngine.session.applicationBufferSize.coerceAtLeast(encryptedPayload.size),
            )
            val result = sslEngine.unwrap(input, appBuffer)
            runDelegatedTasks()
            when (result.status) {
                SSLEngineResult.Status.OK -> {
                    appBuffer.flip()
                    val bytes = ByteArray(appBuffer.remaining())
                    appBuffer.get(bytes)
                    output.write(bytes)
                }
                SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                    throw IOException("Incomplete TLS record in AAP frame")
                }
                else -> throw IOException("TLS decryption failed: ${result.status}")
            }
        }
        return output.toByteArray()
    }

    private suspend fun readHandshakePayload(connection: HeadUnitConnection): ByteArray {
        val frame = AapFrameCodec.readPlainControl(connection)
        if (frame.channelId != AapFrameCodec.CHANNEL_CONTROL ||
            frame.messageType != AapFrameCodec.MESSAGE_ENCAPSULATED_SSL
        ) {
            throw IOException(
                "Unexpected message during TLS handshake: channel=${frame.channelId}, type=${frame.messageType}",
            )
        }
        return frame.payload
    }

    private fun runDelegatedTasks() {
        while (sslEngine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            val task = sslEngine.delegatedTask ?: break
            task.run()
        }
    }
}
