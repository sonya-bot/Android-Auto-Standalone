package com.example.androidautoselfheadunit.audio

import com.example.androidautoselfheadunit.aap.AapMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioChannelTest {
    // A dummy wrapper for testing without mockito
    class MockAudioTrackWrapper : AudioTrackWrapper(44100, 12, 2) {
        var writtenData: ByteArray? = null
        var writtenOffset: Int = -1
        var writtenLength: Int = -1
        var stopped = false

        override fun start() {
            // Do nothing to avoid Android SDK crash
        }

        override fun write(
            data: ByteArray,
            offset: Int,
            length: Int,
        ) {
            writtenData = data
            writtenOffset = offset
            writtenLength = length
        }

        override fun stop() {
            stopped = true
        }
    }

    @Test
    fun `handleMessage writes payload to wrapper with offset`() {
        val wrapper = MockAudioTrackWrapper()
        val channel = AudioChannel(wrapper)

        val payload = ByteArray(20) { it.toByte() }
        val message = AapMessage(channelId = 2, messageType = 0, flags = 11, payload = payload)

        channel.handleMessage(message)

        assertEquals(8, wrapper.writtenOffset)
        assertEquals(12, wrapper.writtenLength)
        assertArrayEquals(payload, wrapper.writtenData)
    }

    @Test
    fun `stop calls wrapper stop`() {
        val wrapper = MockAudioTrackWrapper()
        val channel = AudioChannel(wrapper)

        channel.stop()
        assertEquals(true, wrapper.stopped)
    }
}
