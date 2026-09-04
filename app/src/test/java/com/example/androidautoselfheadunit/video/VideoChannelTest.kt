package com.example.androidautoselfheadunit.video

import com.example.androidautoselfheadunit.aap.AapMessage
import com.example.androidautoselfheadunit.decoder.VideoFrameDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoChannelTest {
    @Test
    fun `fragmented codec config preserves config flag`() {
        val decoder = RecordingDecoder()
        val channel = VideoChannel(FragmentReconstructor())
        channel.attachDecoder(decoder)

        assertFalse(channel.handleMessage(message(9, byteArrayOf(0, 0, 0, 1, 7)), isConfig = true))
        assertTrue(channel.handleMessage(message(10, byteArrayOf(1, 2, 3))))

        assertEquals(1, decoder.frames.size)
        assertTrue(decoder.frames.single().isConfig)
    }

    @Test
    fun `new decoder drops non idr then accepts idr`() {
        val decoder = RecordingDecoder()
        val channel = VideoChannel(FragmentReconstructor())
        channel.attachDecoder(decoder)

        assertTrue(channel.handleMessage(message(11, nal(type = 1))))
        assertEquals(0, decoder.frames.size)

        assertTrue(channel.handleMessage(message(11, nal(type = 5))))
        assertEquals(1, decoder.frames.size)
    }

    private fun message(
        flags: Int,
        payload: ByteArray,
    ) = AapMessage(channelId = 2, flags = flags.toByte(), messageType = 0, payload = payload)

    private fun nal(type: Int): ByteArray = byteArrayOf(0, 0, 0, 1, type.toByte(), 1, 2, 3)

    private class RecordingDecoder : VideoFrameDecoder {
        data class Frame(val data: ByteArray, val isConfig: Boolean)

        val frames = mutableListOf<Frame>()

        override fun decode(
            data: ByteArray,
            offset: Int,
            length: Int,
            isConfig: Boolean,
        ): Boolean {
            frames += Frame(data.copyOfRange(offset, offset + length), isConfig)
            return true
        }
    }
}
