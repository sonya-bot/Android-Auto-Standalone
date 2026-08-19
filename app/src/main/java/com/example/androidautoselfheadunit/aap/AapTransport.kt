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

            if ((flags.toInt() and FRAME_TYPE_MASK) == FRAME_TYPE_FIRST) {
                readExact(4) // Skip fragment total size
            }

            if (encLen <= 0 || encLen > AapFrameCodec.MAX_FRAME_PAYLOAD_SIZE) {
                throw IOException("Invalid encrypted AAP payload length: $encLen")
            }

            val encryptedPayload = readExact(encLen)
            val decryptedPayload = sslContext.decrypt(encryptedPayload)

            var messageType = 0
            val payload: ByteArray
            val frameType = flags.toInt() and FRAME_TYPE_MASK
            val isMiddleOrLast = frameType == FRAME_TYPE_MIDDLE || frameType == FRAME_TYPE_LAST

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
        return AapFrameCodec.readExact(connection, length)
    }

    private companion object {
        const val FRAME_TYPE_MASK = 0x03
        const val FRAME_TYPE_MIDDLE = 0x00
        const val FRAME_TYPE_FIRST = 0x01
        const val FRAME_TYPE_LAST = 0x02
    }
}
