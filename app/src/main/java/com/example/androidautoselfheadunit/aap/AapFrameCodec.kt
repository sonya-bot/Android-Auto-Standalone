@file:Suppress("TooManyFunctions", "MagicNumber", "MaxLineLength", "LongMethod", "CyclomaticComplexMethod", "ReturnCount", "UnusedPrivateProperty", "ThrowsCount", "Deprecation", "TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")

package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import java.io.IOException
import java.nio.ByteBuffer

internal object AapFrameCodec {
    const val CHANNEL_CONTROL = 0
    const val FLAG_BULK = 0x03
    const val MESSAGE_VERSION_REQUEST = 1
    const val MESSAGE_VERSION_RESPONSE = 2
    const val MESSAGE_ENCAPSULATED_SSL = 3
    const val MESSAGE_AUTH_COMPLETE = 4
    const val MAX_FRAME_PAYLOAD_SIZE = 4 * 1024 * 1024

    data class PlainFrame(
        val channelId: Int,
        val flags: Int,
        val messageType: Int,
        val payload: ByteArray,
    )

    fun encodePlainControl(
        messageType: Int,
        payload: ByteArray,
    ): ByteArray {
        val bodyLength = payload.size + MESSAGE_TYPE_SIZE
        require(bodyLength <= 0xFFFF) { "AAP control frame is too large: $bodyLength" }

        return ByteBuffer.allocate(AapMessage.HEADER_SIZE + bodyLength).apply {
            put(CHANNEL_CONTROL.toByte())
            put(FLAG_BULK.toByte())
            putShort(bodyLength.toShort())
            putShort(messageType.toShort())
            put(payload)
        }.array()
    }

    suspend fun readPlainControl(connection: HeadUnitConnection): PlainFrame {
        val header = readExact(connection, AapMessage.HEADER_SIZE)
        val channelId = header[0].toInt() and 0xFF
        val flags = header[1].toInt() and 0xFF
        val bodyLength = ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
        if (bodyLength < MESSAGE_TYPE_SIZE || bodyLength > MAX_FRAME_PAYLOAD_SIZE) {
            throw IOException("Invalid AAP frame length: $bodyLength")
        }

        val body = readExact(connection, bodyLength)
        val messageType = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
        return PlainFrame(channelId, flags, messageType, body.copyOfRange(2, body.size))
    }

    suspend fun readExact(
        connection: HeadUnitConnection,
        length: Int,
    ): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val chunk = ByteArray(length - offset)
            val count = connection.read(chunk)
            if (count <= 0) throw IOException("Connection closed during AAP read")
            chunk.copyInto(result, destinationOffset = offset, endIndex = count)
            offset += count
        }
        return result
    }

    private const val MESSAGE_TYPE_SIZE = 2
}
