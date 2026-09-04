package com.example.androidautoselfheadunit.aap

import com.example.androidautoselfheadunit.connection.HeadUnitConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AapFrameCodecTest {
    @Test
    fun `version request uses valid AAP framing`() {
        val encoded =
            AapFrameCodec.encodePlainControl(
                AapFrameCodec.MESSAGE_VERSION_REQUEST,
                byteArrayOf(0, 1, 0, 7),
            )

        assertArrayEquals(
            byteArrayOf(0, 3, 0, 6, 0, 1, 0, 1, 0, 7),
            encoded,
        )
    }

    @Test
    fun `plain frame reader handles partial socket reads`() =
        runTest {
            val connection = ChunkedConnection(byteArrayOf(0, 3, 0, 6, 0, 2, 0, 1, 0, 2))

            val frame = AapFrameCodec.readPlainControl(connection)

            assertEquals(0, frame.channelId)
            assertEquals(3, frame.flags)
            assertEquals(AapFrameCodec.MESSAGE_VERSION_RESPONSE, frame.messageType)
            assertArrayEquals(byteArrayOf(0, 1, 0, 2), frame.payload)
        }

    private class ChunkedConnection(private val source: ByteArray) : HeadUnitConnection {
        private var offset = 0

        override suspend fun connect(
            host: String,
            port: Int,
        ) = Unit

        override suspend fun disconnect() = Unit

        override suspend fun read(buffer: ByteArray): Int {
            if (offset == source.size) return -1
            val count = minOf(1, buffer.size, source.size - offset)
            source.copyInto(buffer, endIndex = offset + count, startIndex = offset)
            offset += count
            return count
        }

        override suspend fun write(data: ByteArray) = Unit
    }
}
