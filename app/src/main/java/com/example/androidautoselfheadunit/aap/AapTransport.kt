@file:Suppress("UnusedPrivateProperty", "MagicNumber")

package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.aap.security.AapSslContext
import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer

class AapTransport(
    private val connection: HeadUnitConnection,
    private val sslContext: AapSslContext,
) {
    suspend fun sendEncrypted(message: AapMessage) {
        withContext(Dispatchers.IO) {
            val payloadWithType = ByteArray(message.payload.size + 2)
            payloadWithType[0] = (message.messageType shr 8).toByte()
            payloadWithType[1] = (message.messageType and 0xFF).toByte()
            System.arraycopy(message.payload, 0, payloadWithType, 2, message.payload.size)

            val encryptedPayload = sslContext.encrypt(payloadWithType)
            val out = ByteBuffer.allocate(AapMessage.HEADER_SIZE + encryptedPayload.size)
            out.put(message.channelId.toByte())
            out.put(message.flags)
            out.put((encryptedPayload.size shr 8).toByte())
            out.put((encryptedPayload.size and 0xFF).toByte())
            out.put(encryptedPayload)
            connection.write(out.array())
        }
    }

    suspend fun receiveEncrypted(): AapMessage {
        return withContext(Dispatchers.IO) {
            val headerBuffer = readExact(AapMessage.HEADER_SIZE)
            val channelId = headerBuffer[0].toInt() and 0xFF
            val flags = headerBuffer[1]
            val encLen = ((headerBuffer[2].toInt() and 0xFF) shl 8) or (headerBuffer[3].toInt() and 0xFF)

            if (flags.toInt() == 9) {
                readExact(4) // Skip fragment total size
            }

            val encryptedPayload = readExact(encLen)
            val decryptedPayload = sslContext.decrypt(encryptedPayload)

            var messageType = 0
            val payload: ByteArray
            val flagInt = flags.toInt()
            val isMiddleOrLast = (flagInt == 8) || (flagInt == 10)

            if (!isMiddleOrLast && decryptedPayload.size >= 2) {
                messageType = ((decryptedPayload[0].toInt() and 0xFF) shl 8) or (decryptedPayload[1].toInt() and 0xFF)
                payload = ByteArray(decryptedPayload.size - 2)
                System.arraycopy(decryptedPayload, 2, payload, 0, payload.size)
            } else {
                payload = decryptedPayload
            }

            AapMessage(
                channelId = channelId,
                flags = flags,
                messageType = messageType,
                payload = payload,
            )
        }
    }

    private suspend fun readExact(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var totalRead = 0
        while (totalRead < length) {
            val tempBuffer = ByteArray(length - totalRead)
            val bytesRead = connection.read(tempBuffer)
            if (bytesRead < 0) {
                throw IOException("Connection closed during read")
            }
            System.arraycopy(tempBuffer, 0, buffer, totalRead, bytesRead)
            totalRead += bytesRead
        }
        return buffer
    }
}
