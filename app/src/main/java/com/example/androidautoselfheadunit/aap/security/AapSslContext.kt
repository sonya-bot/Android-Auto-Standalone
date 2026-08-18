package com.example.androidautoselfheadunit.aap.security

import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import java.io.IOException
import java.nio.ByteBuffer
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult

class AapSslContext {
    fun encrypt(payload: ByteArray): ByteArray {
        val appBuffer = ByteBuffer.wrap(payload)
        val netBuffer = ByteBuffer.allocate(sslEngine.session.packetBufferSize + payload.size)
        sslEngine.wrap(appBuffer, netBuffer)
        netBuffer.flip()
        val outBytes = ByteArray(netBuffer.remaining())
        netBuffer.get(outBytes)
        return outBytes
    }

    fun decrypt(encryptedPayload: ByteArray): ByteArray {
        val netBuffer = ByteBuffer.wrap(encryptedPayload)
        val appBuffer =
            ByteBuffer.allocate(
                sslEngine.session.applicationBufferSize + encryptedPayload.size,
            )
        sslEngine.unwrap(netBuffer, appBuffer)
        appBuffer.flip()
        val outBytes = ByteArray(appBuffer.remaining())
        appBuffer.get(outBytes)
        return outBytes
    }

    private val sslContext: SSLContext = SSLContext.getInstance("TLS")
    private lateinit var sslEngine: SSLEngine
    private lateinit var txBuffer: ByteBuffer
    private lateinit var rxBuffer: ByteBuffer

    init {
        sslContext.init(null, arrayOf(NoCheckTrustManager()), java.security.SecureRandom())
    }

    suspend fun performHandshake(connection: HeadUnitConnection) {
        sslEngine = sslContext.createSSLEngine()
        sslEngine.useClientMode = true

        val session = sslEngine.session
        txBuffer = ByteBuffer.allocateDirect(session.packetBufferSize)
        rxBuffer = ByteBuffer.allocateDirect(session.packetBufferSize)

        sslEngine.beginHandshake()

        var handshakeStatus = sslEngine.handshakeStatus
        val appBuffer = ByteBuffer.allocate(0)
        val netBuffer = ByteBuffer.allocate(session.packetBufferSize)

        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
            handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING
        ) {
            when (handshakeStatus) {
                SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                    netBuffer.clear()
                    val result = sslEngine.wrap(appBuffer, netBuffer)
                    handshakeStatus = result.handshakeStatus
                    netBuffer.flip()

                    val outBytes = ByteArray(netBuffer.remaining())
                    netBuffer.get(outBytes)
                    connection.write(outBytes)
                }
                SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                    val inBytes = ByteArray(session.packetBufferSize)
                    val bytesRead = connection.read(inBytes)
                    if (bytesRead < 0) {
                        throw IOException("Connection closed during TLS handshake")
                    }
                    val inBuffer = ByteBuffer.wrap(inBytes, 0, bytesRead)

                    appBuffer.clear()
                    val result = sslEngine.unwrap(inBuffer, appBuffer)
                    handshakeStatus = result.handshakeStatus
                }
                SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                    var task: Runnable? = sslEngine.delegatedTask
                    while (task != null) {
                        task.run()
                        task = sslEngine.delegatedTask
                    }
                    handshakeStatus = sslEngine.handshakeStatus
                }
                else -> {
                    throw IOException("Unexpected TLS handshake status: $handshakeStatus")
                }
            }
        }
    }
}
